import { Router, type IRouter } from "express";
import { eq, sql, and } from "drizzle-orm";
import { db, paymentsTable, auctionsTable } from "@workspace/db";
import { requireAuth, requireAdmin } from "../middlewares/requireAuth";
import { CreatePaymentIntentBody, ListPaymentsQueryParams } from "@workspace/api-zod";
import { createNotification } from "../lib/notifications";

const router: IRouter = Router();

function serialize(p: any) {
  return { ...p, amount: Number(p.amount) };
}

// POST /payments/intent - payments are intentionally NOT implemented yet.
// Future provider hook: create the provider's payment intent here for a won auction, store its id on
// paymentsTable.stripePaymentIntentId (rename as needed) and mark "completed" ONLY from a verified provider webhook.
router.post("/payments/intent", requireAuth, (_req, res): void => {
  res.status(501).json({ error: "Payment integration coming soon.", code: "payments_unavailable" });
});

// GET /payments/my — user's transactions
router.get("/payments/my", requireAuth, async (req, res): Promise<void> => {
  const userId = (req as any).userId;
  const payments = await db
    .select()
    .from(paymentsTable)
    .where(eq(paymentsTable.buyerId, userId))
    .orderBy(paymentsTable.createdAt);
  res.json(payments.map(serialize));
});

// GET /payments — admin list
router.get("/payments", requireAdmin, async (req, res): Promise<void> => {
  const query = ListPaymentsQueryParams.safeParse(req.query);
  const page = query.success ? (query.data.page ?? 1) : 1;
  const limit = query.success ? (query.data.limit ?? 20) : 20;
  const offset = (page - 1) * limit;

  const conditions = [];
  if (query.success && query.data.status) conditions.push(eq(paymentsTable.status, query.data.status));

  const payments = await db
    .select()
    .from(paymentsTable)
    .where(conditions.length > 0 ? and(...conditions) : undefined)
    .limit(limit)
    .offset(offset)
    .orderBy(paymentsTable.createdAt);

  const [{ count }] = await db
    .select({ count: sql<number>`count(*)::int` })
    .from(paymentsTable)
    .where(conditions.length > 0 ? and(...conditions) : undefined);

  res.json({ payments: payments.map(serialize), total: count, page, limit });
});

export default router;
