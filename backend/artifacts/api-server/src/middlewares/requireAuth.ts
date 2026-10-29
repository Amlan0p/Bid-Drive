import { type Request, type Response, type NextFunction } from "express";
import { db, usersTable } from "@workspace/db";
import { eq } from "drizzle-orm";
import { verifyToken } from "../lib/security";

async function loadUser(req: Request) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) return null;
  const userId = verifyToken(header.slice(7).trim());
  if (!userId) return null;
  const [user] = await db.select().from(usersTable).where(eq(usersTable.id, userId)).limit(1);
  return user ?? null;
}

export async function requireAuth(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const user = await loadUser(req);
    if (!user) {
      res.status(401).json({ error: "Session expired or invalid. Please sign in again.", code: "unauthorized" });
      return;
    }
    if (user.status === "banned") {
      res.status(403).json({ error: "This account has been suspended.", code: "banned" });
      return;
    }
    (req as any).userId = user.id;
    (req as any).dbUser = user;
    next();
  } catch (err) {
    req.log?.error({ err }, "Auth middleware error");
    res.status(500).json({ error: "Internal server error" });
  }
}

/** Attaches the user when a valid token is present; never rejects. */
export async function optionalAuth(req: Request, _res: Response, next: NextFunction): Promise<void> {
  try {
    const user = await loadUser(req);
    if (user && user.status !== "banned") {
      (req as any).userId = user.id;
      (req as any).dbUser = user;
    }
  } catch {
    /* anonymous */
  }
  next();
}

export async function requireAdmin(req: Request, res: Response, next: NextFunction): Promise<void> {
  await requireAuth(req, res, () => {
    const user = (req as any).dbUser;
    if (!user || user.role !== "admin") {
      res.status(403).json({ error: "Admin access required", code: "forbidden" });
      return;
    }
    next();
  });
}
