import { Router, type IRouter } from "express";
import { eq, sql, gte, desc } from "drizzle-orm";
import { db, usersTable, carsTable, auctionsTable, bidsTable, paymentsTable } from "@workspace/db";
import { requireAdmin } from "../middlewares/requireAdmin";
import { GetAuctionAnalyticsQueryParams, GetRevenueAnalyticsQueryParams } from "@workspace/api-zod";

const router: IRouter = Router();

// GET /analytics/dashboard — dashboard stats
router.get("/analytics/dashboard", requireAdmin, async (_req, res): Promise<void> => {
  const [
    usersResult,
    carsResult,
    auctionsResult,
    liveResult,
    bidsResult,
    revenueResult,
    pendingResult,
    recentAuctions,
  ] = await Promise.all([
    db.select({ count: sql<number>`count(*)::int` }).from(usersTable),
    db.select({ count: sql<number>`count(*)::int` }).from(carsTable),
    db.select({ count: sql<number>`count(*)::int` }).from(auctionsTable),
    db.select({ count: sql<number>`count(*)::int` }).from(auctionsTable).where(eq(auctionsTable.status, "live")),
    db.select({ count: sql<number>`count(*)::int` }).from(bidsTable),
    db.select({ total: sql<number>`coalesce(sum(amount::numeric), 0)::float` }).from(paymentsTable).where(eq(paymentsTable.status, "completed")),
    db.select({ count: sql<number>`count(*)::int` }).from(carsTable).where(eq(carsTable.status, "pending")),
    db.select().from(auctionsTable).orderBy(desc(auctionsTable.createdAt)).limit(5),
  ]);

  res.json({
    totalUsers: usersResult[0].count,
    totalCars: carsResult[0].count,
    totalAuctions: auctionsResult[0].count,
    liveAuctions: liveResult[0].count,
    totalRevenue: revenueResult[0].total,
    totalBids: bidsResult[0].count,
    pendingListings: pendingResult[0].count,
    recentAuctions: recentAuctions.map(a => ({
      ...a,
      startingPrice: Number(a.startingPrice),
      reservePrice: Number(a.reservePrice),
      currentBid: Number(a.currentBid),
      bidIncrement: Number(a.bidIncrement),
      winningBid: a.winningBid != null ? Number(a.winningBid) : null,
    })),
  });
});

// GET /analytics/auctions — auction analytics
router.get("/analytics/auctions", requireAdmin, async (req, res): Promise<void> => {
  const query = GetAuctionAnalyticsQueryParams.safeParse(req.query);
  const days = query.success ? (query.data.days ?? 30) : 30;
  const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000);

  const [upcomingCount, liveCount, endedCount, bidsOverTimeRaw, topAuctions] = await Promise.all([
    db.select({ count: sql<number>`count(*)::int` }).from(auctionsTable).where(eq(auctionsTable.status, "upcoming")),
    db.select({ count: sql<number>`count(*)::int` }).from(auctionsTable).where(eq(auctionsTable.status, "live")),
    db.select({ count: sql<number>`count(*)::int` }).from(auctionsTable).where(eq(auctionsTable.status, "ended")),
    db.execute(sql`
      SELECT DATE(created_at AT TIME ZONE 'UTC') as date, COUNT(*)::int as count
      FROM bids
      WHERE created_at >= ${since.toISOString()}
      GROUP BY DATE(created_at AT TIME ZONE 'UTC')
      ORDER BY date ASC
    `),
    db.select().from(auctionsTable).orderBy(desc(auctionsTable.bidCount)).limit(5),
  ]);

  res.json({
    statusBreakdown: {
      upcoming: upcomingCount[0].count,
      live: liveCount[0].count,
      ended: endedCount[0].count,
    },
    bidsOverTime: (bidsOverTimeRaw.rows as any[]).map(r => ({
      date: r.date instanceof Date ? r.date.toISOString().split("T")[0] : String(r.date),
      count: Number(r.count),
    })),
    topAuctions: topAuctions.map(a => ({
      ...a,
      startingPrice: Number(a.startingPrice),
      reservePrice: Number(a.reservePrice),
      currentBid: Number(a.currentBid),
      bidIncrement: Number(a.bidIncrement),
      winningBid: a.winningBid != null ? Number(a.winningBid) : null,
    })),
  });
});

// GET /analytics/revenue — revenue analytics
router.get("/analytics/revenue", requireAdmin, async (req, res): Promise<void> => {
  const query = GetRevenueAnalyticsQueryParams.safeParse(req.query);
  const days = query.success ? (query.data.days ?? 30) : 30;
  const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000);

  const [totalRevResult, revenueOverTimeRaw, pendingCount, completedCount, failedCount, refundedCount] = await Promise.all([
    db.select({ total: sql<number>`coalesce(sum(amount::numeric), 0)::float` }).from(paymentsTable).where(eq(paymentsTable.status, "completed")),
    db.execute(sql`
      SELECT DATE(created_at AT TIME ZONE 'UTC') as date, COALESCE(SUM(amount::numeric), 0)::float as amount
      FROM payments
      WHERE created_at >= ${since.toISOString()} AND status = 'completed'
      GROUP BY DATE(created_at AT TIME ZONE 'UTC')
      ORDER BY date ASC
    `),
    db.select({ count: sql<number>`count(*)::int` }).from(paymentsTable).where(eq(paymentsTable.status, "pending")),
    db.select({ count: sql<number>`count(*)::int` }).from(paymentsTable).where(eq(paymentsTable.status, "completed")),
    db.select({ count: sql<number>`count(*)::int` }).from(paymentsTable).where(eq(paymentsTable.status, "failed")),
    db.select({ count: sql<number>`count(*)::int` }).from(paymentsTable).where(eq(paymentsTable.status, "refunded")),
  ]);

  res.json({
    totalRevenue: totalRevResult[0].total,
    revenueOverTime: (revenueOverTimeRaw.rows as any[]).map(r => ({
      date: r.date instanceof Date ? r.date.toISOString().split("T")[0] : String(r.date),
      amount: Number(r.amount),
    })),
    paymentStatusBreakdown: {
      pending: pendingCount[0].count,
      completed: completedCount[0].count,
      failed: failedCount[0].count,
      refunded: refundedCount[0].count,
    },
  });
});

export default router;
