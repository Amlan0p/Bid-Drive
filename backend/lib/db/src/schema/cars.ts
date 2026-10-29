import { pgTable, text, serial, timestamp, integer, pgEnum } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const fuelTypeEnum = pgEnum("fuel_type", ["petrol", "diesel", "electric", "hybrid", "cng"]);
export const transmissionEnum = pgEnum("transmission", ["manual", "automatic", "cvt"]);
export const carStatusEnum = pgEnum("car_status", ["pending", "approved", "rejected"]);

export const carsTable = pgTable("cars", {
  id: serial("id").primaryKey(),
  sellerId: integer("seller_id").notNull(),
  brand: text("brand").notNull(),
  model: text("model").notNull(),
  year: integer("year").notNull(),
  fuelType: fuelTypeEnum("fuel_type").notNull(),
  transmission: transmissionEnum("transmission").notNull(),
  mileage: integer("mileage").notNull(),
  location: text("location").notNull(),
  color: text("color"),
  registrationNumber: text("registration_number"),
  damageReport: text("damage_report"),
  inspectionReport: text("inspection_report"),
  status: carStatusEnum("status").notNull().default("pending"),
  rejectionReason: text("rejection_reason"),
  images: text("images").array().notNull().default([]),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertCarSchema = createInsertSchema(carsTable).omit({ id: true, createdAt: true, updatedAt: true });
export type InsertCar = z.infer<typeof insertCarSchema>;
export type Car = typeof carsTable.$inferSelect;
