# BidDrive — native Android vehicle-auction app + API

```
biddrive/
├── android/   Kotlin + Jetpack Compose app (open this folder in Android Studio)
└── backend/   Node/Express/TypeScript + PostgreSQL (Drizzle) API, deployable to Render
```

> **Read first — what was and wasn't verified.** The backend was installed, type-checked, built and exercised
> against a real PostgreSQL (see *Backend test results*). The Android project was **not compiled** in the
> environment that produced it (no Android SDK / Maven access there). All 20 Kotlin files parse cleanly, and the pure
> logic (`util/Format.kt`) was compiled and run, but Gradle sync / first build on your machine is the first real
> compile of the UI. Expect to fix, at most, a few small API/import nits. See *Known limitations*.

## 1. Run the backend locally
Requirements: Node 20+/22, pnpm 10 (`npm i -g pnpm@10`), PostgreSQL 14+.
```bash
cd backend
cp .env.example .env           # fill DATABASE_URL and JWT_SECRET (>=32 chars)
pnpm install
set -a; . ./.env; set +a
pnpm --filter @workspace/db push          # create/migrate tables (drizzle-kit push)
pnpm --filter @workspace/api-server build
pnpm --filter @workspace/api-server start # http://localhost:8080/api/healthz
```
On start the server seeds a sample catalogue + the demo account (idempotent). Manual control:
```bash
pnpm --filter @workspace/api-server seed:demo    # ensure demo user, seed if nothing is open
pnpm --filter @workspace/api-server reset:demo   # wipe + recreate demo auctions/bids/watchlist with fresh timings
```
(run them with the same env vars exported.)

## 2. Deploy the backend on Render (full-project GitHub repository)
1. Upload/push the **entire `biddrive/` project** to one GitHub repository. The repository root should contain `render.yaml`, `android/`, and `backend/`.
2. In Render, choose **New → Blueprint** and select that repository. Render reads the root-level `render.yaml` and deploys the service with `rootDir: backend`; the Android folder stays in GitHub but is not built by Render.
3. The Blueprint creates the PostgreSQL database and web service and generates `JWT_SECRET`. Review the resources and approve deployment.
4. Build runs `pnpm --filter @workspace/db push` (migrations) then builds the API. Health check: `/api/healthz`.
5. Your API base URL will be `https://<service>.onrender.com/api/`. Use the actual URL Render assigns; no URL is hard-coded in the project.
6. Free instances can sleep; the first request may take a while. If deployment fails, inspect the Render build/runtime logs before changing settings.

The original `backend/render.yaml` is retained for deployments where `backend/` itself is the repository root. When using the full-project repository, use the root-level `render.yaml`.

Environment variables: see `backend/.env.example` (`DATABASE_URL`, `JWT_SECRET`, `ENABLE_DEMO`, `SEED_DEMO_DATA`,
`ADMIN_SECRET` (optional, web admin only), `CORS_ORIGINS`, `DATABASE_SSL`).

## 3. Build the Android app
Requirements: Android Studio (Ladybug or newer) with JDK 17, Android SDK 35.
1. Open the `android/` folder. Let Gradle sync (wrapper = Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01).
2. **Backend URL** (must end with `/api/`):
   * **Default (debug and release):** the deployed Render API `https://biddrive-api.onrender.com/api/` is preconfigured, so the app can reach it from an emulator or a real phone.
   * To use another backend, set `android/local.properties` → `biddrive.apiBaseUrl=https://<service>.onrender.com/api/` or build with `-PbiddriveApiBaseUrl=https://...`.
   * Release builds **require https** and fail the build if you pass an http URL. The deployed Render URL is configured by default; it never silently falls back to fake data.
3. Run (▶) or `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. Release APK: `./gradlew assembleRelease -PbiddriveApiBaseUrl=https://<service>.onrender.com/api/`, then sign it
   (Build ▸ Generate Signed Bundle/APK, or add a `signingConfig`). The release build is unsigned by default; no keystore is included.
5. Unit tests: `./gradlew testDebugUnitTest`.

Application ID / package: `app.biddrive.android` (the suggested `in.biddrive.app` can't be a Kotlin package because `in` is a reserved word).

## 4. Demo account
| | |
|---|---|
| Email | `demo@biddrive.app` |
| Password | `Demo#Drive2026` |

The **Demo Login** button calls `POST /api/auth/demo`, a real server-issued session for this non-admin account
(stored as a bcrypt hash). It has live bids (one leading, one outbid), a won auction, a lost auction and a watchlist.
Disable with `ENABLE_DEMO=false`. Reset with `reset:demo`. Seed bidders/sellers have no password and cannot log in.

## 5. Architecture (Android)
`ui/*` (Compose screens + ViewModels, StateFlow) → `data/Repositories.kt` → `data/Api.kt` (Retrofit + OkHttp + kotlinx-serialization)
→ REST API. `SessionStore` keeps the server-issued JWT in app-private DataStore; any 401 on an authenticated call clears it and
returns the user to Sign In. Dependency container: `BidDriveApp.kt` (no DI framework). Room is intentionally **not** used —
there is no offline requirement; Explore/Home/My Bids/Watchlist always reflect the server.

**Welcome page + themes.** First launch (signed out) opens `ui/auth/WelcomeScreen.kt`; "Create Account" / "Sign In" lead to the auth screens and the page is not shown again
(flag in `SettingsStore`). The app ships a **white (light) theme by default** plus the original dark theme; switch under Profile → Appearance (Light / Dark / System).
Colours live in `ui/theme/Theme.kt` as two `Palette`s resolved through `Bd.*`, so every screen follows the active theme. The light palette uses a deeper amber (`#B86E00`) so amber text/buttons stay legible on white.

Screens → files: Home `ui/home`, Explore `ui/explore`, Details `ui/details`, Bid Confirmation `ui/bid`, My Bids `ui/mybids`,
Search & Filters `ui/search`, Watchlist `ui/watchlist`, Sign In/Up `ui/auth`, Profile `ui/profile`, nav `ui/navigation`.

### Updates are polling, not real-time
Auction Details polls every **5 s**, Home/Explore/Watchlist every 30 s, My Bids every 15 s — only while the screen is visible.
The backend also has Socket.IO (used by the web dashboard); the Android app does **not** use it.

## 6. API summary (`/api`)
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` `/auth/login` `/auth/demo` | – | bcrypt, JWT (30 d), rate-limited |
| GET/PATCH | `/me` , `/users/me` | user | name + phone only; email/role immutable |
| GET | `/auctions` | – | filters: `status,q,make,model,location,fuelType,yearMin,yearMax,priceMin,priceMax,sort,page,limit` |
| GET | `/auctions/:id` | – | includes `recentBids` (bidders anonymised) |
| POST | `/auctions/:id/bids` | user | `{amount}` — transactional (row lock), see rules below |
| GET | `/bids/my` | user | per-auction state: `leading / outbid / won / lost / ended_reserve_not_met` |
| GET/POST/DELETE | `/watchlist`, `/watchlist/:auctionId` | user | scoped to the caller, idempotent |
| POST | `/payments/intent` | user | **501 – “Payment integration coming soon”** |
| GET | `/healthz` | – | health check |

Bid rules (server-enforced): auction exists, is live (clock-checked, not just a flag), amount is a positive whole number
≥ `startingPrice` (first bid) or `currentBid + increment`, bidder isn't the seller, bidder isn't already highest.
Winners are set only if the reserve was met. A row lock (`SELECT … FOR UPDATE`) serialises concurrent bids.

## 7. Payments — intentionally not implemented
No gateway SDK, no payment fields, no fake IDs/confirmations. Won auctions show *“Payment integration coming soon”*.
Future hook: `backend/artifacts/api-server/src/routes/payments.ts` (`POST /payments/intent` — create the provider intent;
mark a payment complete **only** from a verified provider webhook), and the `payment` object already returned by `/bids/my`.

## 8. What I changed in your original backend (it was unsafe)
* Auth token was the **user ID** and `/auth/sign-in` logged in (or created) anyone from an **email alone** → replaced by bcrypt + signed JWT; old route removed.
* Hard-coded default admin secret → admin sign-in disabled unless `ADMIN_SECRET` is set; demo user can never be admin.
* Anyone could `PATCH /auctions/:id` → admin-only. Cars: owner/admin check, status/seller not client-editable.
* Bidding had no transaction, no end-time check, N+1 queries → rewritten (locking, validation, derived status, winners at close).
* Mock payment intents (`pi_mock…`) → removed (501). Clerk dependencies/proxy removed. Password hashes never serialised.
* Added unique case-insensitive email index, indexes on bids/auctions, `/bids/my`, filtered `/auctions`.
The Expo mobile app and its Clerk code were **not** carried over (replaced by the native app). The web admin dashboard is kept but untested here.

## 9. Design inputs — what I could and couldn't use
All 10 HTML files and the archive were read. Deliberately **not** built because they contradict your rules or have no backend:
OTP/phone/WhatsApp-SMS verification, Face ID, Google/Apple sign-in, KYC, escrow/buyer-premium/GST fee tables, payment & wallet actions,
“Buying power”, push-notification toggles. Vehicle photos in the HTML point to external Google URLs that could not be downloaded;
the app uses the 10 vehicle images already bundled in your backend (`/api/cars/images/*`). Your instruction document was **cut off
at section 13 (vehicle images and assets)** — anything after that point (sections 14+) was not visible to me, so please re-send it if it contained further requirements.

### Welcome page — what changed vs. the design
Kept: hero, "Own The Apex.", stats strip, scrolling feature cards, Create Account / Sign In. Changed because the original claims aren't true of this app:
"₹180+ Cr GMV / 99.8% provenance / 12 ms latency" → real live/upcoming/vehicle counts from the API (hidden if the server is unreachable);
"100% escrow / RBI settlement / 210-point inspection / SEBI-VCC" → removed (no payments or inspections exist in the app); "Explore as Guest" → removed (the API requires sign-in);
hero photo → bundled `welcome_hero.jpg` (your Porsche 911 asset) instead of an external URL.

## 10. Known limitations
* Android code is uncompiled (see top). Versions used are a known-good set but Android Studio may suggest upgrades.
* Fonts (Space Grotesk, JetBrains Mono, Geist; OFL) are bundled as variable TTFs, weights applied via `FontVariation` (API 26+).
* The launcher icon is derived from the icon in your Expo project.
* Token is stored in app-private DataStore (not hardware-backed encryption).
* Rate limiting is in-memory (single instance).
