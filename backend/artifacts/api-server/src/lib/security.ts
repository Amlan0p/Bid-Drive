import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { randomBytes, timingSafeEqual } from "node:crypto";
import { logger } from "./logger";

const BCRYPT_COST = 12;

function resolveJwtSecret(): string {
  const configured = process.env.JWT_SECRET;
  if (configured && configured.length >= 32) return configured;
  if (process.env.NODE_ENV === "production") {
    throw new Error("JWT_SECRET must be set to a random string of at least 32 characters in production.");
  }
  logger.warn("JWT_SECRET not set - using a temporary random secret (sessions reset on every restart). Dev only.");
  return randomBytes(48).toString("hex");
}

const JWT_SECRET = resolveJwtSecret();
const JWT_ISSUER = "biddrive-api";
const TOKEN_TTL = "30d";

export function signToken(userId: number): string {
  return jwt.sign({}, JWT_SECRET, { subject: String(userId), issuer: JWT_ISSUER, expiresIn: TOKEN_TTL });
}

/** Returns the user id, or null if the token is missing, malformed, expired or forged. */
export function verifyToken(token: string): number | null {
  try {
    const payload = jwt.verify(token, JWT_SECRET, { issuer: JWT_ISSUER }) as jwt.JwtPayload;
    const id = Number(payload.sub);
    return Number.isInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

export const hashPassword = (plain: string) => bcrypt.hash(plain, BCRYPT_COST);
export const verifyPassword = (plain: string, hash: string) => bcrypt.compare(plain, hash);
// A real bcrypt hash used to equalise timing when the account does not exist.
export const DUMMY_HASH = bcrypt.hashSync("not-a-real-password", BCRYPT_COST);

export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

/** Tiny in-memory fixed-window limiter. Good enough for a single Render instance. */
const buckets = new Map<string, { count: number; resetAt: number }>();
export function allow(key: string, max: number, windowMs: number): boolean {
  const now = Date.now();
  if (buckets.size > 5000) for (const [k, v] of buckets) if (v.resetAt < now) buckets.delete(k);
  const b = buckets.get(key);
  if (!b || b.resetAt < now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    return true;
  }
  b.count += 1;
  return b.count <= max;
}

export function validatePassword(pw: unknown): string | null {
  if (typeof pw !== "string") return "Password is required";
  if (pw.length < 8) return "Password must be at least 8 characters";
  if (pw.length > 128) return "Password is too long";
  if (!/[A-Za-z]/.test(pw) || !/[0-9]/.test(pw)) return "Password must contain a letter and a number";
  return null;
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
