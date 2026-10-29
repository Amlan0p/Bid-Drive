import { Router } from "express";
import { requireAuth } from "../middlewares/requireAuth";
import { db, bidsTable, usersTable, auctionsTable, carsTable } from "@workspace/db";
import { and, desc, eq, inArray, sql } from "drizzle-orm";
import { serializeAuction, syncAuctionStatuses } from "../lib/auctions";

const router = Router();

const publicUser = (u: typeof usersTable.$inferSelect) => ({
  id: u.id, email: u.email, name: u.name, phone: u.phone, avatarUrl: u.avatarUrl,
  role: u.role, status: u.status, isDemo: u.isDemo, createdAt: u.createdAt,
});

// GET /me (and /users/me alias) - the authenticated user's own profile
const getMe = (req: any, res: any) => res.json(publicUser(req.dbUser));
router.get("/me", requireAuth, getMe);
router.get("/users/me", requireAuth, getMe);

// PATCH /users/me - name and phone only. Email, role and status can never be changed here.
router.patch("/users/me", requireAuth, async (req, res) => {
  const { name, phone } = req.body ?? {};
  const updates: Partial<typeof usersTable.$inferInsert> = {};
  if (name !== undefined) {
    const n = typeof name === "string" ? name.trim() : "";
    if (n.length < 2 || n.length > 80) {
      res.status(400).json({ error: "Name must be 2-80 characters.", code: "validation" });
      return;
    }
    updates.name = n;
  }
  if (phone !== undefined) {
    if (phone === null || (typeof phone === "string" && phone.trim() === "")) updates.phone = null;
    else if (typeof phone === "string" && /^[+0-9 ()-]{7,20}$/.test(phone.trim())) updates.phone = phone.trim();
    else {
      res.status(400).json({ error: "Please enter a valid phone number.", code: "validation" });
      return;
    }
  }
  if (Object.keys(updates).length === 0) {
    res.status(400).json({ error: "Nothing to update.", code: "validation" });
    return;
  }
  const [updated] = await db.update(usersTable).set(updates).where(eq(usersTable.id, (req as any).userId)).returning();
  res.json(publicUser(updated));
});

// GET /bids/my - one entry per auction the user has bid on, with a server-derived state.
router.get("/bids/my", requireAuth, async (req, res) => {
  try {
    const userId: number = (req as any).userId;
    await syncAuctionStatuses();
    const bids = await db.select().from(bidsTable).where(eq(bidsTable.bidderId, userId)).orderBy(desc(bidsTable.createdAt));
    const byAuction = new Map<number, { highest: number; count: number; lastBidAt: Date }>();
    for (const b of bids) {
      const amt = Number(b.amount);
      const cur = byAuction.get(b.auctionId);
      if (!cur) byAuction.set(b.auctionId, { highest: amt, count: 1, lastBidAt: b.createdAt });
      else { cur.highest = Math.max(cur.highest, amt); cur.count += 1; }
    }
    if (byAuction.size === 0) { res.json([]); return; }
    const rows = await db
      .select({ auction: auctionsTable, car: carsTable })
      .from(auctionsTable)
      .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId))
      .where(inArray(auctionsTable.id, [...byAuction.keys()]));
    const out = rows.map(({ auction, car }) => {
      const mine = byAuction.get(auction.id)!;
      const a = serializeAuction(auction, car);
      let state: "leading" | "outbid" | "won" | "lost" | "ended_reserve_not_met";
      if (a.status === "ended") {
        if (a.winnerId === userId) state = "won";
        else if (a.winnerId == null && auction.highestBidderId === userId) state = "ended_reserve_not_met";
        else state = "lost";
      } else state = auction.highestBidderId === userId ? "leading" : "outbid";
      return {
        auction: a, myHighestBid: mine.highest, myBidCount: mine.count, lastBidAt: mine.lastBidAt, state,
        // Payments are intentionally not implemented yet.
        payment: state === "won" ? { status: "not_available", message: "Payment integration coming soon." } : null,
      };
    }).sort((x, y) => +new Date(y.lastBidAt) - +new Date(x.lastBidAt));
    res.json(out);
  } catch (err) {
    req.log.error({ err }, "List my bids error");
    res.status(500).json({ error: "Internal server error" });
  }
});

export default router;
