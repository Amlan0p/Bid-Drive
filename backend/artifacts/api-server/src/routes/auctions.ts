import { Router, type IRouter } from "express";
import { and, asc, desc, eq, ilike, gte, lte, inArray, or, sql, type SQL } from "drizzle-orm";
import { db, auctionsTable, carsTable, bidsTable, usersTable } from "@workspace/db";
import { requireAuth, requireAdmin, optionalAuth } from "../middlewares/requireAuth";
import {
  CreateAuctionBody, UpdateAuctionBody, UpdateAuctionParams, DeleteAuctionParams,
} from "@workspace/api-zod";
import { createNotification } from "../lib/notifications";
import { broadcastBid } from "../lib/socketio";
import { derivedStatus, minNextBid, serializeAuction, syncAuctionStatuses } from "../lib/auctions";

const router: IRouter = Router();

const MAX_BID = 100_000_000_000; // 10,000 crore rupees - a sanity ceiling, not a business rule.
const isAdmin = (req: any) => req.dbUser?.role === "admin";
const parseId = (raw: unknown): number | null => {
  const n = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isInteger(n) && n > 0 && n < 2_147_483_647 ? n : null;
};
const listParam = (v: unknown): string[] =>
  (typeof v === "string" ? v.split(",") : []).map((s) => s.trim()).filter(Boolean).slice(0, 20);
const numParam = (v: unknown): number | null => {
  if (typeof v !== "string" || v.trim() === "") return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

const effectivePrice = sql`case when ${auctionsTable.bidCount} = 0 then ${auctionsTable.startingPrice} else ${auctionsTable.currentBid} end`;

// GET /auctions - filterable listing (also used by the admin dashboard)
// Query: status, q, make, model, location, fuelType, yearMin, yearMax, priceMin, priceMax, sort, page, limit
router.get("/auctions", optionalAuth, async (req, res): Promise<void> => {
  try {
    await syncAuctionStatuses();
    const q = req.query;
    const page = Math.max(1, Math.floor(numParam(q.page) ?? 1));
    const limit = Math.min(100, Math.max(1, Math.floor(numParam(q.limit) ?? 20)));
    const conds: SQL[] = [eq(carsTable.status, "approved")];

    const statuses = listParam(q.status).filter((s) => ["upcoming", "live", "ended"].includes(s)) as Array<"upcoming" | "live" | "ended">;
    if (statuses.length) conds.push(inArray(auctionsTable.status, statuses));
    const makes = listParam(q.make).map((m) => m.toLowerCase());
    if (makes.length) conds.push(inArray(sql`lower(${carsTable.brand})`, makes));
    if (typeof q.model === "string" && q.model.trim()) conds.push(ilike(carsTable.model, `%${q.model.trim().replace(/[%_]/g, "")}%`));
    if (typeof q.location === "string" && q.location.trim()) conds.push(ilike(carsTable.location, `%${q.location.trim().replace(/[%_]/g, "")}%`));
    const fuels = listParam(q.fuelType).filter((f) => ["petrol", "diesel", "electric", "hybrid", "cng"].includes(f)) as any[];
    if (fuels.length) conds.push(inArray(carsTable.fuelType, fuels));
    const yMin = numParam(q.yearMin), yMax = numParam(q.yearMax);
    if (yMin != null) conds.push(gte(carsTable.year, yMin));
    if (yMax != null) conds.push(lte(carsTable.year, yMax));
    const pMin = numParam(q.priceMin), pMax = numParam(q.priceMax);
    if (pMin != null) conds.push(sql`${effectivePrice} >= ${pMin}`);
    if (pMax != null) conds.push(sql`${effectivePrice} <= ${pMax}`);
    if (typeof q.q === "string" && q.q.trim()) {
      const like = `%${q.q.trim().replace(/[%_]/g, "")}%`;
      conds.push(or(ilike(carsTable.brand, like), ilike(carsTable.model, like), ilike(carsTable.location, like), ilike(carsTable.color, like), sql`(${carsTable.brand} || ' ' || ${carsTable.model}) ilike ${like}`)!);
    }
    const where = and(...conds);

    const onlyEnded = statuses.length === 1 && statuses[0] === "ended";
    const orderBy =
      q.sort === "newest" ? [desc(auctionsTable.createdAt)]
      : q.sort === "price_high" ? [desc(effectivePrice)]
      : q.sort === "price_low" ? [asc(effectivePrice)]
      : q.sort === "most_bids" ? [desc(auctionsTable.bidCount)]
      : onlyEnded ? [desc(auctionsTable.endTime)]
      : [sql`(${auctionsTable.status} = 'ended')`, asc(auctionsTable.endTime)];

    const rows = await db
      .select({ auction: auctionsTable, car: carsTable })
      .from(auctionsTable)
      .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId))
      .where(where)
      .orderBy(...orderBy)
      .limit(limit)
      .offset((page - 1) * limit);
    const [{ count }] = await db
      .select({ count: sql<number>`count(*)::int` })
      .from(auctionsTable)
      .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId))
      .where(where);

    const admin = isAdmin(req);
    res.json({ auctions: rows.map((r) => serializeAuction(r.auction, r.car, { admin })), total: count, page, limit });
  } catch (err) {
    req.log.error({ err }, "List auctions error");
    res.status(500).json({ error: "Internal server error" });
  }
});

router.get("/auctions/live", async (req, res): Promise<void> => {
  await syncAuctionStatuses();
  const rows = await db.select({ auction: auctionsTable, car: carsTable }).from(auctionsTable)
    .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId)).where(eq(auctionsTable.status, "live")).orderBy(asc(auctionsTable.endTime));
  res.json(rows.map((r) => serializeAuction(r.auction, r.car)));
});

router.get("/auctions/upcoming", async (req, res): Promise<void> => {
  await syncAuctionStatuses();
  const rows = await db.select({ auction: auctionsTable, car: carsTable }).from(auctionsTable)
    .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId)).where(eq(auctionsTable.status, "upcoming")).orderBy(asc(auctionsTable.startTime));
  res.json(rows.map((r) => serializeAuction(r.auction, r.car)));
});

// POST /auctions (admin) - create auction
router.post("/auctions", requireAdmin, async (req, res): Promise<void> => {
  const parsed = CreateAuctionBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const d = parsed.data;
  if (new Date(d.endTime) <= new Date(d.startTime)) {
    res.status(400).json({ error: "End time must be after start time" });
    return;
  }
  const [auction] = await db.insert(auctionsTable).values({
    carId: d.carId,
    sellerId: (req as any).userId,
    startingPrice: String(d.startingPrice),
    reservePrice: String(d.reservePrice),
    ...(d.bidIncrement != null ? { bidIncrement: String(d.bidIncrement) } : {}),
    currentBid: "0",
    startTime: new Date(d.startTime),
    endTime: new Date(d.endTime),
  }).returning();
  res.status(201).json(serializeAuction(auction, null, { admin: true }));
});

async function bidList(auctionId: number, admin: boolean, limit: number) {
  const bids = await db
    .select({ id: bidsTable.id, auctionId: bidsTable.auctionId, bidderId: bidsTable.bidderId, amount: bidsTable.amount, createdAt: bidsTable.createdAt, bidderName: usersTable.name })
    .from(bidsTable)
    .leftJoin(usersTable, eq(bidsTable.bidderId, usersTable.id))
    .where(eq(bidsTable.auctionId, auctionId))
    .orderBy(desc(bidsTable.createdAt), desc(bidsTable.id))
    .limit(limit);
  return bids.map((b) => ({
    id: b.id, auctionId: b.auctionId, bidderId: b.bidderId, amount: Number(b.amount), createdAt: b.createdAt,
    bidderLabel: `Bidder #${String(b.bidderId).padStart(4, "0")}`, // bidder identities are not public
    ...(admin ? { bidderName: b.bidderName } : {}),
  }));
}

// GET /auctions/:id - detail
router.get("/auctions/:id", optionalAuth, async (req, res): Promise<void> => {
  const id = parseId(req.params.id);
  if (!id) { res.status(400).json({ error: "Invalid auction id" }); return; }
  await syncAuctionStatuses();
  const [row] = await db.select({ auction: auctionsTable, car: carsTable }).from(auctionsTable)
    .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId)).where(eq(auctionsTable.id, id)).limit(1);
  if (!row) { res.status(404).json({ error: "Auction not found" }); return; }
  const admin = isAdmin(req);
  res.json({ ...serializeAuction(row.auction, row.car, { admin }), recentBids: await bidList(id, admin, 30) });
});

// PATCH /auctions/:id (admin)
router.patch("/auctions/:id", requireAdmin, async (req, res): Promise<void> => {
  const params = UpdateAuctionParams.safeParse(req.params);
  const body = UpdateAuctionBody.safeParse(req.body);
  if (!params.success || !body.success) {
    res.status(400).json({ error: (!params.success ? params.error : (body as any).error).message });
    return;
  }
  const updates: any = { ...body.data };
  if (body.data.startTime) updates.startTime = new Date(body.data.startTime);
  if (body.data.endTime) updates.endTime = new Date(body.data.endTime);
  const [auction] = await db.update(auctionsTable).set(updates).where(eq(auctionsTable.id, params.data.id)).returning();
  if (!auction) { res.status(404).json({ error: "Auction not found" }); return; }
  res.json(serializeAuction(auction, null, { admin: true }));
});

// DELETE /auctions/:id (admin)
router.delete("/auctions/:id", requireAdmin, async (req, res): Promise<void> => {
  const params = DeleteAuctionParams.safeParse(req.params);
  if (!params.success) { res.status(400).json({ error: params.error.message }); return; }
  await db.transaction(async (tx) => {
    await tx.delete(bidsTable).where(eq(bidsTable.auctionId, params.data.id));
    await tx.delete(auctionsTable).where(eq(auctionsTable.id, params.data.id));
  });
  res.sendStatus(204);
});

// GET /auctions/:id/bids - bid history
router.get("/auctions/:id/bids", optionalAuth, async (req, res): Promise<void> => {
  const id = parseId(req.params.id);
  if (!id) { res.status(400).json({ error: "Invalid auction id" }); return; }
  res.json(await bidList(id, isAdmin(req), 50));
});

class BidError extends Error {
  constructor(public status: number, message: string, public code: string) { super(message); }
}

// POST /auctions/:id/bids - the server is the only authority on whether a bid is accepted.
router.post("/auctions/:id/bids", requireAuth, async (req, res): Promise<void> => {
  const id = parseId(req.params.id);
  if (!id) { res.status(400).json({ error: "Invalid auction id", code: "validation" }); return; }
  const amount = (req.body ?? {}).amount;
  if (typeof amount !== "number" || !Number.isFinite(amount) || !Number.isInteger(amount) || amount <= 0 || amount > MAX_BID) {
    res.status(400).json({ error: "Enter a valid whole-rupee bid amount.", code: "invalid_amount" });
    return;
  }
  const bidderId: number = (req as any).userId;

  try {
    const result = await db.transaction(async (tx) => {
      // Row lock: concurrent bids on one auction are applied strictly one after another.
      const [auction] = await tx.select().from(auctionsTable).where(eq(auctionsTable.id, id)).for("update");
      if (!auction) throw new BidError(404, "Auction not found.", "not_found");
      const now = new Date();
      const status = derivedStatus(auction, now);
      if (status === "upcoming") throw new BidError(400, "This auction has not started yet.", "not_started");
      if (status === "ended") throw new BidError(400, "This auction has ended.", "ended");
      if (auction.sellerId === bidderId) throw new BidError(403, "You cannot bid on your own listing.", "own_listing");
      if (auction.highestBidderId === bidderId) throw new BidError(409, "You are already the highest bidder.", "already_highest");
      const min = minNextBid(auction);
      if (amount < min) throw new BidError(400, `Minimum bid is ${min.toLocaleString("en-IN")}.`, "below_minimum");
      if (auction.bidCount > 0 && (amount - Number(auction.currentBid)) % 1 !== 0) throw new BidError(400, "Invalid amount.", "invalid_amount");

      const [bid] = await tx.insert(bidsTable).values({ auctionId: id, bidderId, amount: String(amount) }).returning();
      const [updated] = await tx.update(auctionsTable).set({
        currentBid: String(amount),
        bidCount: sql`${auctionsTable.bidCount} + 1`,
        highestBidderId: bidderId,
        status: "live",
      }).where(eq(auctionsTable.id, id)).returning();
      return { bid, updated, previousBidder: auction.highestBidderId };
    });

    if (result.previousBidder && result.previousBidder !== bidderId) {
      await createNotification(result.previousBidder, "outbid", "You have been outbid",
        `Someone placed a higher bid of ₹${amount.toLocaleString("en-IN")}. Place a new bid to stay in the auction.`, id);
    }
    broadcastBid(id, {
      id: result.bid.id, bidderId, bidderName: null, amount, currentBid: amount,
      bidCount: result.updated.bidCount, createdAt: result.bid.createdAt,
    });
    const [car] = await db.select().from(carsTable).where(eq(carsTable.id, result.updated.carId)).limit(1);
    res.status(201).json({
      bid: { id: result.bid.id, auctionId: id, bidderId, amount, createdAt: result.bid.createdAt },
      auction: serializeAuction(result.updated, car),
    });
  } catch (err) {
    if (err instanceof BidError) {
      res.status(err.status).json({ error: err.message, code: err.code });
      return;
    }
    req.log.error({ err }, "Place bid error");
    res.status(500).json({ error: "Could not place bid. Please try again.", code: "server_error" });
  }
});

export default router;
