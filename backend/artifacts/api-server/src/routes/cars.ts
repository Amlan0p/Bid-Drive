import { Router, type IRouter } from "express";
import { eq, and, sql } from "drizzle-orm";
import { db, carsTable } from "@workspace/db";
import { requireAuth, requireAdmin } from "../middlewares/requireAuth";
import {
  ListCarsQueryParams,
  GetCarParams,
  CreateCarBody,
  UpdateCarParams,
  UpdateCarBody,
  ApproveCarParams,
  RejectCarParams,
  RejectCarBody,
} from "@workspace/api-zod";

const router: IRouter = Router();

// GET /cars — list car listings
router.get("/cars", async (req, res): Promise<void> => {
  const query = ListCarsQueryParams.safeParse(req.query);
  const page = query.success ? (query.data.page ?? 1) : 1;
  const limit = query.success ? (query.data.limit ?? 20) : 20;
  const offset = (page - 1) * limit;

  const conditions = [];
  if (query.success && query.data.status) conditions.push(eq(carsTable.status, query.data.status));
  else conditions.push(eq(carsTable.status, "approved"));
  if (query.success && query.data.brand) conditions.push(eq(carsTable.brand, query.data.brand));
  if (query.success && query.data.fuelType) conditions.push(eq(carsTable.fuelType, query.data.fuelType as any));
  if (query.success && query.data.transmission) conditions.push(eq(carsTable.transmission, query.data.transmission as any));
  if (query.success && query.data.location) conditions.push(eq(carsTable.location, query.data.location));

  const cars = await db
    .select()
    .from(carsTable)
    .where(conditions.length > 0 ? and(...conditions) : undefined)
    .limit(limit)
    .offset(offset)
    .orderBy(carsTable.createdAt);

  const [{ count }] = await db
    .select({ count: sql<number>`count(*)::int` })
    .from(carsTable)
    .where(conditions.length > 0 ? and(...conditions) : undefined);

  res.json({ cars, total: count, page, limit });
});

// POST /cars — create listing
router.post("/cars", requireAuth, async (req, res): Promise<void> => {
  const parsed = CreateCarBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const sellerId = (req as any).userId;
  const [car] = await db
    .insert(carsTable)
    .values({ ...parsed.data, sellerId, images: parsed.data.images ?? [] })
    .returning();
  res.status(201).json(car);
});

// GET /cars/my — user's own listings
router.get("/cars/my", requireAuth, async (req, res): Promise<void> => {
  const userId = (req as any).userId;
  const cars = await db.select().from(carsTable).where(eq(carsTable.sellerId, userId)).orderBy(carsTable.createdAt);
  res.json(cars);
});

// GET /cars/:id
router.get("/cars/:id", async (req, res): Promise<void> => {
  const params = GetCarParams.safeParse(req.params);
  if (!params.success) {
    res.status(400).json({ error: params.error.message });
    return;
  }
  const car = await db.select().from(carsTable).where(eq(carsTable.id, params.data.id)).then(r => r[0]);
  if (!car) {
    res.status(404).json({ error: "Car not found" });
    return;
  }
  res.json(car);
});

// PATCH /cars/:id
router.patch("/cars/:id", requireAuth, async (req, res): Promise<void> => {
  const params = UpdateCarParams.safeParse(req.params);
  if (!params.success) {
    res.status(400).json({ error: params.error.message });
    return;
  }
  const body = UpdateCarBody.safeParse(req.body);
  if (!body.success) {
    res.status(400).json({ error: body.error.message });
    return;
  }
  const existing = await db.select().from(carsTable).where(eq(carsTable.id, params.data.id)).then(r => r[0]);
  if (!existing) {
    res.status(404).json({ error: "Car not found" });
    return;
  }
  const me = (req as any).dbUser;
  if (existing.sellerId !== me.id && me.role !== "admin") {
    res.status(403).json({ error: "You can only edit your own listings" });
    return;
  }
  // Never let a client change ownership or moderation status through this endpoint.
  const { status: _s, sellerId: _o, rejectionReason: _r, ...safe } = body.data as any;
  const [car] = await db.update(carsTable).set(safe).where(eq(carsTable.id, params.data.id)).returning();
  res.json(car);
});

// DELETE /cars/:id
router.delete("/cars/:id", requireAuth, async (req, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  const existing = Number.isFinite(id) ? await db.select().from(carsTable).where(eq(carsTable.id, id)).then(r => r[0]) : undefined;
  if (!existing) {
    res.status(404).json({ error: "Car not found" });
    return;
  }
  const me = (req as any).dbUser;
  if (existing.sellerId !== me.id && me.role !== "admin") {
    res.status(403).json({ error: "You can only delete your own listings" });
    return;
  }
  await db.delete(carsTable).where(eq(carsTable.id, id));
  res.sendStatus(204);
});

// POST /cars/:id/approve (admin)
router.post("/cars/:id/approve", requireAdmin, async (req, res): Promise<void> => {
  const params = ApproveCarParams.safeParse(req.params);
  if (!params.success) {
    res.status(400).json({ error: params.error.message });
    return;
  }
  const [car] = await db
    .update(carsTable)
    .set({ status: "approved", rejectionReason: null })
    .where(eq(carsTable.id, params.data.id))
    .returning();
  if (!car) {
    res.status(404).json({ error: "Car not found" });
    return;
  }
  res.json(car);
});

// POST /cars/:id/reject (admin)
router.post("/cars/:id/reject", requireAdmin, async (req, res): Promise<void> => {
  const params = RejectCarParams.safeParse(req.params);
  if (!params.success) {
    res.status(400).json({ error: params.error.message });
    return;
  }
  const body = RejectCarBody.safeParse(req.body);
  if (!body.success) {
    res.status(400).json({ error: body.error.message });
    return;
  }
  const [car] = await db
    .update(carsTable)
    .set({ status: "rejected", rejectionReason: body.data.reason })
    .where(eq(carsTable.id, params.data.id))
    .returning();
  if (!car) {
    res.status(404).json({ error: "Car not found" });
    return;
  }
  res.json(car);
});

export default router;
