import { pgTable, text, serial, timestamp, pgEnum, boolean, uniqueIndex } from "drizzle-orm/pg-core";
import { sql } from "drizzle-orm";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const userRoleEnum = pgEnum("user_role", ["dealer", "customer", "admin"]);
export const userStatusEnum = pgEnum("user_status", ["active", "banned"]);

export const usersTable = pgTable("users", {
  id: serial("id").primaryKey(),
  // Legacy identifier from the original project; now a unique local identifier (e.g. "local_<uuid>").
  clerkId: text("clerk_id").notNull().unique(),
  // bcrypt hash. NULL for non-loginable accounts (seed sellers / seed bidders).
  passwordHash: text("password_hash"),
  isDemo: boolean("is_demo").notNull().default(false),
  email: text("email").notNull(),
  name: text("name").notNull(),
  phone: text("phone"),
  avatarUrl: text("avatar_url"),
  role: userRoleEnum("role").notNull().default("customer"),
  status: userStatusEnum("status").notNull().default("active"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
}, (t) => [uniqueIndex("users_email_lower_idx").on(sql`lower(${t.email})`)]);

export const insertUserSchema = createInsertSchema(usersTable).omit({ id: true, createdAt: true, updatedAt: true });
export type InsertUser = z.infer<typeof insertUserSchema>;
export type User = typeof usersTable.$inferSelect;
