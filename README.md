# BidDrive — Android Vehicle Auction App

BidDrive is a native Android application for exploring vehicle auctions, viewing vehicle details, placing bids, and managing a personal watchlist. It uses **Kotlin + Jetpack Compose** on Android and a **Node.js/TypeScript API with PostgreSQL** on the backend.

[![Backend Health](https://img.shields.io/badge/API-health%20check-brightgreen)](https://biddrive-api.onrender.com/api/healthz) [![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/) [![Backend](https://img.shields.io/badge/backend-Render-46E3B7)](https://render.com/)

## 📲 Download the Android app

**[Download the latest BidDrive APK](https://github.com/Amlan0p/Bid-Drive/releases/latest)**

1. Open the latest release and download the `.apk` file from **Assets**.
2. Open the downloaded APK on your Android device.
3. If Android prompts you, allow installation from the browser or file manager you used to download it.
4. Complete the installation and open BidDrive.

> Install APKs only from a source you trust. BidDrive is distributed through GitHub Releases and is not necessarily available on Google Play.

## ✨ Features

- Native Android UI built with Kotlin and Jetpack Compose.
- Welcome screen, account registration, and sign-in.
- Browse and search vehicle auction listings.
- View vehicle and auction details.
- Place bids with server-side validation.
- View personal bid statuses, including leading, outbid, won, and lost where applicable.
- Save auctions to a personal watchlist.
- Light, dark, and system theme options.
- Backend-connected data rather than relying on a local-only catalogue.

**Availability note:** Online payments are not implemented; the API currently reports payment integration as coming soon. Auction updates use periodic polling in the Android app, not a real-time socket connection.

## 🧱 Technology stack

| Component | Technology |
|---|---|
| Android app | Kotlin, Jetpack Compose, ViewModels, StateFlow |
| Networking | Retrofit, OkHttp, Kotlin serialization |
| Session storage | Android DataStore |
| Backend | Node.js, Express, TypeScript |
| Database | PostgreSQL with Drizzle ORM/tooling |
| Hosting | Render |

## 🌐 Live backend

- **API base URL:** `https://biddrive-api.onrender.com/api/`
- **Health check:** [`https://biddrive-api.onrender.com/api/healthz`](https://biddrive-api.onrender.com/api/healthz)

The health-check endpoint should return a successful status when the backend is available. Render free services may sleep when idle, so the first request after inactivity can take longer.

## 📁 Repository structure

```text
Bid-Drive/
├── android/          # Native Android app (Kotlin + Jetpack Compose)
├── backend/          # API server, database schema, and backend packages
├── render.yaml       # Root-level Render Blueprint for the full repository
├── README.md
└── .gitignore
```

Render uses `rootDir: backend` in the root-level Blueprint to deploy the API. The Android app is kept in the same GitHub repository but is not built by Render.

## 🛠️ Run the backend locally

### Requirements

- Node.js 20 or 22
- pnpm 10
- PostgreSQL 14 or newer

### Setup

```bash
cd backend
cp .env.example .env
```

Edit `backend/.env` and provide the required database connection string and a strong `JWT_SECRET`. Do not commit `.env` or real secrets to GitHub.

Then run:

```bash
npm install -g pnpm@10
pnpm install
set -a
. ./.env
set +a
pnpm --filter @workspace/db push
pnpm --filter @workspace/api-server build
pnpm --filter @workspace/api-server start
```

The local API health endpoint is expected at `http://localhost:8080/api/healthz`.

## 📱 Build the Android app

1. Install Android Studio and the required Android SDK/JDK versions.
2. Open the `android/` folder in Android Studio and allow Gradle sync to finish.
3. Configure the backend base URL for your build. For the deployed API, use:

   ```text
   https://biddrive-api.onrender.com/api/
   ```

   The URL must end in `/api/`. The project supports configuring it through `android/local.properties` using `biddrive.apiBaseUrl=...` or through the Gradle property `-PbiddriveApiBaseUrl=...`.
4. Build and test the app on an emulator or physical Android device.
5. For distribution, create a signed release APK in Android Studio (**Build → Generate Signed Bundle / APK**) and upload it to a GitHub Release.

Keep signing keystores and passwords private. Do not commit them to the repository.

## 🚀 Deploy the backend to Render

The repository includes a root-level `render.yaml` Blueprint for deploying the backend and its PostgreSQL database.

1. Push the complete repository to GitHub, with `render.yaml`, `android/`, and `backend/` at the repository root.
2. In Render, select **New → Blueprint** and choose this repository and the intended branch.
3. Review the resources and environment variables shown by Render, then approve deployment.
4. Wait for the build and deployment to finish. Check the service logs if anything fails.
5. Verify the health endpoint: `https://biddrive-api.onrender.com/api/healthz`.

Do not create a second database or change the existing Render service unnecessarily if the current deployment is already healthy. A GitHub push can trigger an automatic redeploy because the service is connected to the repository.

## 🔐 Security notes

- Never commit API keys, database credentials, JWT secrets, signing keys, or passwords.
- Use HTTPS for production API traffic.
- Keep authentication and bid validation on the server; do not treat client-side checks as authoritative.
- The demo-login feature is controlled by the backend's `ENABLE_DEMO` setting. If enabled, use only the demo account documented by the project/backend configuration and never use demo credentials for a real account.

## 🧭 Current limitations

- Online payment processing is not implemented.
- Android auction refresh is periodic polling rather than live socket updates.
- Render free-tier services can take time to wake after inactivity.
- Social sign-in, identity verification, escrow, and other features not implemented in the current app should not be assumed available.

## 📄 License

No license has been specified yet. Unless a license is added to this repository, reuse and redistribution are not automatically permitted.

---

Built as a native Android vehicle-auction project, with its backend deployed separately on Render.
