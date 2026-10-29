// Usage: pnpm --filter @workspace/api-server seed:demo        (idempotent: ensures demo user, seeds if nothing is open)
//        pnpm --filter @workspace/api-server reset:demo       (wipes demo auctions/bids/watchlist and recreates them)
import { ensureDemoUser, ensureSeed, resetDemoData, DEMO_EMAIL, DEMO_PASSWORD } from "../lib/demo";
import { pool } from "@workspace/db";

const reset = process.argv.includes("--reset");
(async () => {
  if (reset) await resetDemoData();
  else { await ensureDemoUser(); await ensureSeed(); }
  console.log(`Demo account ready: ${DEMO_EMAIL} / ${DEMO_PASSWORD}`);
  await pool.end();
})().catch((e) => { console.error(e); process.exit(1); });
