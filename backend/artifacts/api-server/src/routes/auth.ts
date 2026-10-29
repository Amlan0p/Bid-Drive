import { Router } from "express";
import { db, usersTable } from "@workspace/db";
import { eq, sql } from "drizzle-orm";
import { randomUUID } from "node:crypto";
import {
  DUMMY_HASH, EMAIL_RE, allow, hashPassword, safeEqual, signToken, validatePassword, verifyPassword,
} from "../lib/security";
import { DEMO_EMAIL, demoEnabled, ensureDemoUser } from "../lib/demo";

const router = Router();

type UserRow = typeof usersTable.$inferSelect;
const publicUser = (u: UserRow) => ({
  id: u.id, email: u.email, name: u.name, phone: u.phone, avatarUrl: u.avatarUrl,
  role: u.role, status: u.status, isDemo: u.isDemo, createdAt: u.createdAt,
});
const session = (u: UserRow) => ({ token: signToken(u.id), user: publicUser(u) });
const clientKey = (req: any, extra = "") => `${req.ip}|${extra}`;

// POST /auth/register
router.post("/auth/register", async (req, res) => {
  if (!allow(clientKey(req, "register"), 10, 60 * 60 * 1000)) {
    res.status(429).json({ error: "Too many sign-up attempts. Try again later.", code: "rate_limited" });
    return;
  }
  const { name, email, password, phone } = req.body ?? {};
  const cleanName = typeof name === "string" ? name.trim() : "";
  const cleanEmail = typeof email === "string" ? email.trim().toLowerCase() : "";
  if (cleanName.length < 2 || cleanName.length > 80) {
    res.status(400).json({ error: "Please enter your full name (2-80 characters).", code: "validation" });
    return;
  }
  if (!EMAIL_RE.test(cleanEmail) || cleanEmail.length > 254) {
    res.status(400).json({ error: "Please enter a valid email address.", code: "validation" });
    return;
  }
  const pwError = validatePassword(password);
  if (pwError) {
    res.status(400).json({ error: pwError, code: "validation" });
    return;
  }
  const cleanPhone = typeof phone === "string" && phone.trim() ? phone.trim().slice(0, 20) : null;
  if (cleanPhone && !/^[+0-9 ()-]{7,20}$/.test(cleanPhone)) {
    res.status(400).json({ error: "Please enter a valid phone number.", code: "validation" });
    return;
  }
  try {
    const [existing] = await db.select({ id: usersTable.id }).from(usersTable).where(sql`lower(${usersTable.email}) = ${cleanEmail}`).limit(1);
    if (existing) {
      res.status(409).json({ error: "An account with this email already exists.", code: "email_taken" });
      return;
    }
    const [user] = await db.insert(usersTable).values({
      clerkId: `local_${randomUUID()}`,
      email: cleanEmail,
      name: cleanName,
      phone: cleanPhone,
      passwordHash: await hashPassword(password),
      role: "customer",
    }).returning();
    res.status(201).json(session(user));
  } catch (err: any) {
    if (err?.code === "23505" || err?.cause?.code === "23505") {
      res.status(409).json({ error: "An account with this email already exists.", code: "email_taken" });
      return;
    }
    req.log.error({ err }, "Register error");
    res.status(500).json({ error: "Internal server error" });
  }
});

// POST /auth/login
router.post("/auth/login", async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== "string" || typeof password !== "string" || !email || !password) {
    res.status(400).json({ error: "Email and password are required.", code: "validation" });
    return;
  }
  const cleanEmail = email.trim().toLowerCase();
  if (!allow(clientKey(req, cleanEmail), 10, 15 * 60 * 1000)) {
    res.status(429).json({ error: "Too many attempts. Please wait a few minutes.", code: "rate_limited" });
    return;
  }
  try {
    const [user] = await db.select().from(usersTable).where(sql`lower(${usersTable.email}) = ${cleanEmail}`).limit(1);
    const ok = await verifyPassword(password, user?.passwordHash ?? DUMMY_HASH);
    if (!user || !user.passwordHash || !ok) {
      res.status(401).json({ error: "Incorrect email or password.", code: "invalid_credentials" });
      return;
    }
    if (user.status === "banned") {
      res.status(403).json({ error: "This account has been suspended.", code: "banned" });
      return;
    }
    res.json(session(user));
  } catch (err) {
    req.log.error({ err }, "Login error");
    res.status(500).json({ error: "Internal server error" });
  }
});

// POST /auth/demo - signs in the seeded, non-admin demo account (a real session, issued by the server).
router.post("/auth/demo", async (req, res) => {
  if (!demoEnabled()) {
    res.status(404).json({ error: "Demo login is disabled on this server.", code: "demo_disabled" });
    return;
  }
  if (!allow(clientKey(req, "demo"), 30, 15 * 60 * 1000)) {
    res.status(429).json({ error: "Too many attempts. Please wait a few minutes.", code: "rate_limited" });
    return;
  }
  try {
    const user = await ensureDemoUser();
    res.json(session(user));
  } catch (err) {
    req.log.error({ err }, "Demo login error");
    res.status(500).json({ error: "Internal server error" });
  }
});

// GET /auth/demo-info - lets clients show the documented demo account.
router.get("/auth/demo-info", (_req, res) => {
  res.json({ enabled: demoEnabled(), email: demoEnabled() ? DEMO_EMAIL : null });
});

// POST /auth/admin-sign-in - used by the web admin dashboard only. Requires ADMIN_SECRET to be configured.
router.post("/auth/admin-sign-in", async (req, res) => {
  const expected = process.env.ADMIN_SECRET;
  if (!expected || expected.length < 16) {
    res.status(503).json({ message: "Admin sign-in is not configured on this server.", error: "Admin sign-in is not configured." });
    return;
  }
  const { email, adminSecret } = req.body ?? {};
  if (typeof email !== "string" || typeof adminSecret !== "string") {
    res.status(400).json({ message: "Email and admin secret are required", error: "Email and admin secret are required" });
    return;
  }
  if (!allow(clientKey(req, "admin"), 5, 15 * 60 * 1000)) {
    res.status(429).json({ message: "Too many attempts", error: "Too many attempts" });
    return;
  }
  if (!safeEqual(adminSecret, expected)) {
    res.status(401).json({ message: "Invalid credentials", error: "Invalid credentials" });
    return;
  }
  const cleanEmail = email.trim().toLowerCase();
  try {
    let [user] = await db.select().from(usersTable).where(sql`lower(${usersTable.email}) = ${cleanEmail}`).limit(1);
    if (!user) {
      const admins = await db.select({ id: usersTable.id }).from(usersTable).where(eq(usersTable.role, "admin")).limit(1);
      if (admins.length > 0) {
        res.status(401).json({ message: "Invalid credentials", error: "Invalid credentials" });
        return;
      }
      [user] = await db.insert(usersTable).values({
        clerkId: `local_${randomUUID()}`, email: cleanEmail, name: cleanEmail.split("@")[0], role: "admin",
      }).returning();
    }
    if (user.role !== "admin" || user.isDemo) {
      res.status(403).json({ message: "Admin access required", error: "Admin access required" });
      return;
    }
    if (user.status === "banned") {
      res.status(403).json({ message: "Account banned", error: "Account banned" });
      return;
    }
    res.json(session(user));
  } catch (err) {
    req.log.error({ err }, "Admin sign-in error");
    res.status(500).json({ error: "Internal server error" });
  }
});

export default router;
