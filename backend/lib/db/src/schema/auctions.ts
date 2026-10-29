import { pgTable, text, serial, timestamp, integer, numeric, pgEnum, boolean, index } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const auctionStatusEnum = pgEnum("auction_status", ["upcoming", "live", "ended"]);

export const auctionsTable = pgTable("auctions", {
  id: serial("id").primaryKey(),
  carId: integer("car_id").notNull(),
  sellerId: integer("seller_id").notNull(),
  startingPrice: numeric("starting_price", { precision: 12, scale: 2 }).notNull(),
  reservePrice: numeric("reserve_price", { precision: 12, scale: 2 }).notNull(),
  currentBid: numeric("current_bid", { precision: 12, scale: 2 }).notNull().default("0"),
  bidIncrement: numeric("bid_increment", { precision: 12, scale: 2 }).notNull().default("500"),
  bidCount: integer("bid_count").notNull().default(0),
  status: auctionStatusEnum("status").notNull().default("upcoming"),
  highestBidderId: integer("highest_bidder_id"),
  isDemo: boolean("is_demo").notNull().default(false),
  winnerId: integer("winner_id"),
  winningBid: numeric("winning_bid", { precision: 12, scale: 2 }),
  startTime: timestamp("start_time", { withTimezone: true }).notNull(),
  endTime: timestamp("end_time", { withTimezone: true }).notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => [index("auctions_status_end_idx").on(t.status, t.endTime)]);

export const insertAuctionSchema = createInsertSchema(auctionsTable).omit({ id: true, createdAt: true, updatedAt: true });
export type InsertAuction = z.infer<typeof insertAuctionSchema>;
export type Auction = typeof auctionsTable.$inferSelect;
