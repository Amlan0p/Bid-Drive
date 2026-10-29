import { db, usersTable, carsTable, auctionsTable, bidsTable, watchlistTable, notificationsTable } from "@workspace/db";
import { and, eq, inArray, sql } from "drizzle-orm";
import { hashPassword } from "./security";
import { logger } from "./logger";

export const DEMO_EMAIL = "demo@biddrive.app";
// Documented, non-sensitive demo password. Only its bcrypt hash is stored.
export const DEMO_PASSWORD = "Demo#Drive2026";

export const demoEnabled = () => process.env.ENABLE_DEMO !== "false";

const IMG = (f: string) => [`/api/cars/images/${f}`];
const CARS = [
  { brand: "Toyota", model: "Camry", year: 2022, color: "Pearl White", fuelType: "petrol", transmission: "automatic", mileage: 18000, registrationNumber: "MH-01-AB-1234", location: "Mumbai", images: IMG("toyota-camry.png") },
  { brand: "Honda", model: "City", year: 2023, color: "Midnight Black", fuelType: "petrol", transmission: "automatic", mileage: 8500, registrationNumber: "DL-04-CD-5678", location: "Delhi", images: IMG("honda-city.png") },
  { brand: "BMW", model: "5 Series", year: 2021, color: "Alpine White", fuelType: "petrol", transmission: "automatic", mileage: 35000, registrationNumber: "KA-03-EF-9012", location: "Bengaluru", images: IMG("bmw-5series.png") },
  { brand: "Mercedes-Benz", model: "E-Class", year: 2022, color: "Obsidian Black", fuelType: "diesel", transmission: "automatic", mileage: 22000, registrationNumber: "TN-09-GH-3456", location: "Chennai", images: IMG("mercedes-eclass.png") },
  { brand: "Mahindra", model: "XUV700", year: 2023, color: "Deep Forest Green", fuelType: "diesel", transmission: "automatic", mileage: 12000, registrationNumber: "GJ-01-IJ-7890", location: "Ahmedabad", images: IMG("mahindra-xuv700.png") },
  { brand: "Hyundai", model: "Creta", year: 2023, color: "Titan Grey", fuelType: "petrol", transmission: "automatic", mileage: 6000, registrationNumber: "PB-10-KL-2345", location: "Chandigarh", images: IMG("hyundai-creta.png") },
  { brand: "Tata", model: "Nexon EV", year: 2023, color: "Intensi-Teal", fuelType: "electric", transmission: "automatic", mileage: 15000, registrationNumber: "MH-04-MN-6789", location: "Pune", images: IMG("tata-nexon-ev.png") },
  { brand: "Porsche", model: "911 Carrera", year: 2021, color: "Guards Red", fuelType: "petrol", transmission: "automatic", mileage: 28000, registrationNumber: "KA-05-OP-1111", location: "Bengaluru", images: IMG("porsche-911.png") },
  { brand: "Audi", model: "Q7", year: 2022, color: "Mythos Black", fuelType: "diesel", transmission: "automatic", mileage: 18500, registrationNumber: "DL-07-QR-2222", location: "Delhi", images: IMG("audi-q7.png") },
  { brand: "Volkswagen", model: "Tiguan", year: 2022, color: "Reflex Silver", fuelType: "petrol", transmission: "automatic", mileage: 24000, registrationNumber: "WB-02-ST-3333", location: "Kolkata", images: IMG("vw-tiguan.png") },
] as const;

const H = 3600_000;
const SELLER_CLERK = "seed_seller";

async function upsertSeedUser(clerkId: string, email: string, name: string, role: "dealer" | "customer") {
  const [u] = await db.insert(usersTable).values({ clerkId, email, name, role }).onConflictDoNothing().returning();
  if (u) return u;
  const [existing] = await db.select().from(usersTable).where(eq(usersTable.clerkId, clerkId));
  return existing;
}

/** Creates (or refreshes) the demo account. Never grants admin; password stored only as a bcrypt hash. */
export async function ensureDemoUser() {
  const [existing] = await db.select().from(usersTable).where(sql`lower(${usersTable.email}) = ${DEMO_EMAIL}`).limit(1);
  if (existing) {
    if (!existing.isDemo) throw new Error("Demo email is taken by a non-demo account");
    if (existing.role === "admin" || existing.status === "banned" || !existing.passwordHash) {
      const [fixed] = await db.update(usersTable).set({ role: "customer", status: "active", passwordHash: await hashPassword(DEMO_PASSWORD) })
        .where(eq(usersTable.id, existing.id)).returning();
      return fixed;
    }
    return existing;
  }
  const [created] = await db.insert(usersTable).values({
    clerkId: "demo_user", email: DEMO_EMAIL, name: "Demo Bidder", role: "customer", isDemo: true,
    passwordHash: await hashPassword(DEMO_PASSWORD),
  }).returning();
  return created;
}

/** Deletes demo auctions (and their bids / watchlist / notifications) and recreates them with fresh timings. */
export async function resetDemoData() {
  const demo = await ensureDemoUser();
  const seller = await upsertSeedUser(SELLER_CLERK, "seller@seed.biddrive.app", "BidDrive Auto Seller", "dealer");
  const b1 = await upsertSeedUser("seed_bidder_1", "bidder1@seed.biddrive.app", "Seed Bidder 1", "customer");
  const b2 = await upsertSeedUser("seed_bidder_2", "bidder2@seed.biddrive.app", "Seed Bidder 2", "customer");
  const b3 = await upsertSeedUser("seed_bidder_3", "bidder3@seed.biddrive.app", "Seed Bidder 3", "customer");

  await db.transaction(async (tx) => {
    const old = await tx.select({ id: auctionsTable.id }).from(auctionsTable).where(eq(auctionsTable.isDemo, true));
    const ids = old.map((o) => o.id);
    if (ids.length) {
      await tx.delete(bidsTable).where(inArray(bidsTable.auctionId, ids));
      await tx.delete(watchlistTable).where(inArray(watchlistTable.auctionId, ids));
      await tx.delete(notificationsTable).where(inArray(notificationsTable.auctionId, ids));
      await tx.delete(auctionsTable).where(inArray(auctionsTable.id, ids));
    }
    await tx.delete(carsTable).where(eq(carsTable.sellerId, seller.id));
    await tx.delete(notificationsTable).where(eq(notificationsTable.userId, demo.id));

    const cars = await tx.insert(carsTable).values(CARS.map((c) => ({ ...c, images: [...c.images], status: "approved" as const, sellerId: seller.id }))).returning();
    const now = Date.now();
    // [carIdx, start, reserve, increment, startOffsetH, endOffsetH, bids: [bidder, amount, minutesAgo][]]
    type B = [typeof demo, number, number];
    const plan: Array<[number, number, number, number, number, number, B[]]> = [
      [0, 1_500_000, 1_800_000, 25_000, -2, 30, [[b1, 1_525_000, 90], [b2, 1_550_000, 60], [demo, 1_575_000, 20]]],
      [1, 1_200_000, 1_500_000, 15_000, -1, 20, [[demo, 1_215_000, 80], [b1, 1_230_000, 50], [b3, 1_245_000, 12]]],
      [2, 4_500_000, 5_500_000, 75_000, -3, 5, [[b2, 4_575_000, 150], [b3, 4_650_000, 70], [b1, 4_725_000, 15]]],
      [3, 5_200_000, 6_000_000, 75_000, -4, 52, [[b1, 5_275_000, 200]]],
      [4, 2_100_000, 2_500_000, 25_000, 24, 48, []],
      [5, 1_400_000, 1_700_000, 20_000, 12, 36, []],
      [6, 1_600_000, 2_000_000, 20_000, 6, 30, []],
      [7, 9_500_000, 9_000_000, 100_000, -72, -24, [[b2, 9_600_000, 3000], [demo, 9_700_000, 2500], [b1, 9_800_000, 1600]]],
      [8, 7_500_000, 7_600_000, 75_000, -48, -12, [[b2, 7_575_000, 2400], [demo, 7_650_000, 800]]],
      [9, 3_200_000, 3_800_000, 50_000, -36, -6, [[b3, 3_250_000, 1800], [b1, 3_300_000, 700]]],
    ];
    const auctionIds: number[] = [];
    for (const [ci, start, reserve, inc, so, eo, bids] of plan) {
      const last = bids[bids.length - 1];
      const ended = eo < 0;
      const metReserve = !!last && last[1] >= reserve;
      const [a] = await tx.insert(auctionsTable).values({
        carId: cars[ci].id, sellerId: seller.id, isDemo: true,
        startingPrice: String(start), reservePrice: String(reserve), bidIncrement: String(inc),
        currentBid: String(last ? last[1] : 0), bidCount: bids.length, highestBidderId: last ? last[0].id : null,
        status: ended ? "ended" : so > 0 ? "upcoming" : "live",
        winnerId: ended && metReserve ? last[0].id : null, winningBid: ended && metReserve ? String(last[1]) : null,
        startTime: new Date(now + so * H), endTime: new Date(now + eo * H),
      }).returning();
      auctionIds.push(a.id);
      if (bids.length) {
        await tx.insert(bidsTable).values(bids.map(([u, amt, minsAgo]) => ({
          auctionId: a.id, bidderId: u.id, amount: String(amt), createdAt: new Date(now - minsAgo * 60_000),
        })));
      }
    }
    // Demo watchlist: one live (BMW), one upcoming, one ended
    await tx.insert(watchlistTable).values([auctionIds[2], auctionIds[5], auctionIds[7]].map((auctionId) => ({ userId: demo.id, auctionId }))).onConflictDoNothing();
    await tx.insert(notificationsTable).values({
      userId: demo.id, type: "outbid", title: "You have been outbid",
      message: "Someone placed a higher bid on the Honda City.", auctionId: auctionIds[1],
    });
  });
  logger.info("Demo data reset");
}

/** Called at startup: seed on first run, and re-time the demo catalogue once it has all ended. */
export async function ensureSeed() {
  if (process.env.SEED_DEMO_DATA === "false") return;
  await ensureDemoUser();
  const [row] = await db.select({ n: sql<number>`count(*)::int` }).from(auctionsTable)
    .where(and(eq(auctionsTable.isDemo, true), sql`${auctionsTable.endTime} > now()`));
  if (row.n === 0) await resetDemoData();
}
