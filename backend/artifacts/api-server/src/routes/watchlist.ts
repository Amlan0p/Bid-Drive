import { Router, type IRouter } from "express";
import { desc, eq, and } from "drizzle-orm";
import { db, watchlistTable, auctionsTable, carsTable } from "@workspace/db";
import { requireAuth } from "../middlewares/requireAuth";
import { serializeAuction, syncAuctionStatuses } from "../lib/auctions";

const router: IRouter = Router();
const parseId = (raw: unknown): number | null => {
  const n = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isInteger(n) && n > 0 && n < 2_147_483_647 ? n : null;
};

// GET /watchlist - always scoped to the authenticated user
router.get("/watchlist", requireAuth, async (req, res): Promise<void> => {
  await syncAuctionStatuses();
  const userId: number = (req as any).userId;
  const rows = await db
    .select({ item: watchlistTable, auction: auctionsTable, car: carsTable })
    .from(watchlistTable)
    .innerJoin(auctionsTable, eq(auctionsTable.id, watchlistTable.auctionId))
    .innerJoin(carsTable, eq(carsTable.id, auctionsTable.carId))
    .where(eq(watchlistTable.userId, userId))
    .orderBy(desc(watchlistTable.createdAt));
  res.json(rows.map((r) => ({ ...r.item, auction: serializeAuction(r.auction, r.car) })));
});

// POST /watchlist/:auctionId - idempotent (unique(user, auction) + onConflictDoNothing)
router.post("/watchlist/:auctionId", requireAuth, async (req, res): Promise<void> => {
  const auctionId = parseId(req.params.auctionId);
  if (!auctionId) { res.status(400).json({ error: "Invalid auction id" }); return; }
  const [auction] = await db.select({ id: auctionsTable.id }).from(auctionsTable).where(eq(auctionsTable.id, auctionId)).limit(1);
  if (!auction) { res.status(404).json({ error: "Auction not found" }); return; }
  const userId: number = (req as any).userId;
  await db.insert(watchlistTable).values({ userId, auctionId }).onConflictDoNothing();
  const [item] = await db.select().from(watchlistTable).where(and(eq(watchlistTable.userId, userId), eq(watchlistTable.auctionId, auctionId)));
  res.status(201).json(item);
});

// DELETE /watchlist/:auctionId
router.delete("/watchlist/:auctionId", requireAuth, async (req, res): Promise<void> => {
  const auctionId = parseId(req.params.auctionId);
  if (!auctionId) { res.status(400).json({ error: "Invalid auction id" }); return; }
  const userId: number = (req as any).userId;
  await db.delete(watchlistTable).where(and(eq(watchlistTable.userId, userId), eq(watchlistTable.auctionId, auctionId)));
  res.sendStatus(204);
});

export default router;
