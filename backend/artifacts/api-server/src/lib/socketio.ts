import { type Server } from "socket.io";
import { logger } from "./logger";

export function setupSocketIO(io: Server): void {
  io.on("connection", (socket) => {
    logger.info({ socketId: socket.id }, "Client connected");

    // Join an auction room to receive live bid updates
    socket.on("join_auction", (auctionId: number) => {
      const room = `auction:${auctionId}`;
      socket.join(room);
      logger.info({ socketId: socket.id, auctionId }, "Joined auction room");
    });

    socket.on("leave_auction", (auctionId: number) => {
      const room = `auction:${auctionId}`;
      socket.leave(room);
    });

    socket.on("disconnect", () => {
      logger.info({ socketId: socket.id }, "Client disconnected");
    });
  });

  // Export broadcast helper — routes can use this to push bid updates
  (global as any).__socketIO = io;
}

export function broadcastBid(auctionId: number, bid: {
  id: number;
  bidderId: number;
  bidderName: string | null;
  amount: number;
  currentBid: number;
  bidCount: number;
  createdAt: Date;
}): void {
  const io: Server | undefined = (global as any).__socketIO;
  if (!io) return;
  io.to(`auction:${auctionId}`).emit("new_bid", { auctionId, ...bid });
}

export function broadcastAuctionStatus(auctionId: number, status: string, winnerId?: number | null, winningBid?: number | null): void {
  const io: Server | undefined = (global as any).__socketIO;
  if (!io) return;
  io.to(`auction:${auctionId}`).emit("auction_status", { auctionId, status, winnerId, winningBid });
}
