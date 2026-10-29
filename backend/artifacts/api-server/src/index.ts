import { createServer } from "http";
import { Server as SocketIOServer } from "socket.io";
import app from "./app";
import { logger } from "./lib/logger";
import { setupSocketIO } from "./lib/socketio";
import { ensureSeed } from "./lib/demo";
import { syncAuctionStatuses } from "./lib/auctions";

const rawPort = process.env["PORT"];

if (!rawPort) {
  throw new Error(
    "PORT environment variable is required but was not provided.",
  );
}

const port = Number(rawPort);

if (Number.isNaN(port) || port <= 0) {
  throw new Error(`Invalid PORT value: "${rawPort}"`);
}

const httpServer = createServer(app);

const io = new SocketIOServer(httpServer, {
  cors: {
    origin: (process.env.CORS_ORIGINS ?? "").split(",").map((o) => o.trim()).filter(Boolean),
    methods: ["GET", "POST"],
  },
  path: "/api/socket.io",
});

setupSocketIO(io);

httpServer.listen(port, async (err?: Error) => {
  if (err) {
    logger.error({ err }, "Error listening on port");
    process.exit(1);
  }
  logger.info({ port }, "Server listening with Socket.IO");

  try {
    await ensureSeed();
    logger.info("Seed check complete");
  } catch (seedErr) {
    logger.error({ err: seedErr }, "Seed failed");
  }
});

// Close auctions and pick winners even when nobody is polling.
setInterval(() => void syncAuctionStatuses(), 15_000).unref();
