import { db, notificationsTable, bidsTable, usersTable } from "@workspace/db";
import { eq, desc, ne } from "drizzle-orm";
import { logger } from "./logger";

export async function notifyOutbid(auctionId: number, newBidderId: number, newAmount: number): Promise<void> {
  try {
    // Find the previous highest bidder (excluding new bidder)
    const previousBids = await db
      .select({ bidderId: bidsTable.bidderId })
      .from(bidsTable)
      .where(eq(bidsTable.auctionId, auctionId))
      .orderBy(desc(bidsTable.createdAt))
      .limit(10);

    const prevBidder = previousBids.find(b => b.bidderId !== newBidderId);
    if (!prevBidder) return;

    await db.insert(notificationsTable).values({
      userId: prevBidder.bidderId,
      type: "outbid",
      title: "You have been outbid",
      message: `Someone placed a higher bid of ₹${newAmount.toLocaleString()}. Place a new bid to stay in the auction.`,
      auctionId,
    });
  } catch (err) {
    logger.error({ err }, "Failed to create outbid notification");
  }
}

export async function createNotification(
  userId: number,
  type: "outbid" | "auction_won" | "auction_ending" | "auction_started" | "payment_success" | "payment_failed" | "listing_approved" | "listing_rejected" | "system",
  title: string,
  message: string,
  auctionId?: number,
): Promise<void> {
  try {
    await db.insert(notificationsTable).values({ userId, type, title, message, auctionId });
  } catch (err) {
    logger.error({ err }, "Failed to create notification");
  }
}
