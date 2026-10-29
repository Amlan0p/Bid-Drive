import { db, auctionsTable } from "@workspace/db";
import { sql } from "drizzle-orm";
import { createNotification } from "./notifications";
import { broadcastAuctionStatus } from "./socketio";
import { logger } from "./logger";

type AuctionRow = typeof auctionsTable.$inferSelect;

/** Status derived from the clock, so responses are right even if the periodic sync has not run yet. */
export function derivedStatus(a: Pick<AuctionRow, "status" | "startTime" | "endTime">, now = new Date()) {
  if (a.status === "ended" || a.endTime <= now) return "ended" as const;
  if (a.startTime <= now) return "live" as const;
  return "upcoming" as const;
}

export function minNextBid(a: Pick<AuctionRow, "bidCount" | "startingPrice" | "currentBid" | "bidIncrement">): number {
  return a.bidCount === 0 ? Number(a.startingPrice) : Number(a.currentBid) + Number(a.bidIncrement);
}

export function serializeAuction(a: AuctionRow, car?: unknown, opts: { admin?: boolean } = {}) {
  const now = new Date();
  const status = derivedStatus(a, now);
  const current = Number(a.currentBid);
  const out: Record<string, unknown> = {
    id: a.id,
    carId: a.carId,
    sellerId: a.sellerId,
    startingPrice: Number(a.startingPrice),
    currentBid: current,
    bidIncrement: Number(a.bidIncrement),
    bidCount: a.bidCount,
    minNextBid: minNextBid(a),
    // The reserve itself stays private; clients only learn whether it has been met.
    reserveMet: a.bidCount > 0 && current >= Number(a.reservePrice),
    status,
    highestBidderId: a.highestBidderId,
    winnerId: a.winnerId,
    winningBid: a.winningBid != null ? Number(a.winningBid) : null,
    startTime: a.startTime,
    endTime: a.endTime,
    createdAt: a.createdAt,
    updatedAt: a.updatedAt,
    serverTime: now.toISOString(),
    car: car ?? null,
  };
  if (opts.admin) out.reservePrice = Number(a.reservePrice);
  return out;
}

/**
 * Moves auctions through upcoming -> live -> ended and picks winners.
 * A winner exists only if the reserve price was met. Safe to call often and from several instances.
 */
export async function syncAuctionStatuses(): Promise<void> {
  try {
    await db.execute(sql`
      update auctions set status = 'live', updated_at = now()
      where status = 'upcoming' and start_time <= now() and end_time > now()`);
    const res = await db.execute(sql`
      update auctions set
        status = 'ended',
        winner_id = case when highest_bidder_id is not null and current_bid >= reserve_price then highest_bidder_id end,
        winning_bid = case when highest_bidder_id is not null and current_bid >= reserve_price then current_bid end,
        updated_at = now()
      where status <> 'ended' and end_time <= now()
      returning id, winner_id, winning_bid`);
    for (const row of res.rows as Array<{ id: number; winner_id: number | null; winning_bid: string | null }>) {
      broadcastAuctionStatus(row.id, "ended", row.winner_id, row.winning_bid != null ? Number(row.winning_bid) : null);
      if (row.winner_id) {
        await createNotification(
          row.winner_id, "auction_won", "You won an auction",
          "Congratulations! You had the winning bid. Payment integration is coming soon - no payment is due in the app yet.", row.id,
        );
      }
    }
  } catch (err) {
    logger.error({ err }, "syncAuctionStatuses failed");
  }
}
