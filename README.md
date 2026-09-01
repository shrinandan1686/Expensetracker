# TrackIt

An SMS-first expense tracker for India. TrackIt automatically parses bank and UPI SMS messages into expense records, syncs them to the cloud, and lets you split bills with friends — all without manual data entry.

---

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [Tech Stack](#tech-stack)
5. [Backend API](#backend-api)
6. [Android App](#android-app)
7. [Local Development Setup](#local-development-setup)
8. [Environment Variables](#environment-variables)
9. [Database Schema](#database-schema)
10. [Roadmap](#roadmap)

---

## Features

### Expense Tracking
- **Automatic SMS parsing** — reads bank/UPI SMSes via `SmsReceiver`; no manual entry needed
- **Bulk SMS import** — retroactively parse inbox on first install
- **Manual add/edit** — full expense form with category, merchant, date, notes
- **Expense detail** — deep-linkable (`trackit://expense_detail/{id}`)
- **Unlogged expenses** — inbox of SMS-detected spends awaiting confirmation

### Sync & Budgets
- **Cloud sync** — WorkManager-backed `SyncWorker` pushes local expenses to MongoDB; conflict resolution uses `updatedAt` timestamps (last-write-wins per device, newer wins across devices)
- **Monthly budgets** — set category budgets; `BudgetAlertWorker` fires notifications near threshold

### Group Splits
- **Groups** — create named groups with emoji; add members by name/phone/UPI ID
- **Splits** — add shared expenses; equal or custom participant amounts
- **Balance simplification** — greedy min-transactions algorithm runs server-side; minimizes the number of payments needed to settle a group
- **UPI settle-up** — one tap launches `upi://pay?pa=<upiId>&am=<amount>` which GPay/PhonePe picks up with pre-filled details. TrackIt never handles money.
- **Settlement tracking** — pending → confirmed two-step flow; balances update on confirmation

### Analytics & Reports
- Monthly category breakdown with charts
- Category totals use case aggregates Room data locally; `/api/analytics/summary` aggregates server-side via MongoDB `$group` pipeline

### Home Screen Widget
- Glance-based widget showing today's spend and month-to-date total
- Auto-refreshes via `WidgetRefreshWorker`

### Onboarding & Auth
- Google Sign-In via Firebase Auth (no OTP, no phone number required)
- 3-page onboarding: welcome → permissions → profile (name + UPI ID)
- Profile editable later from Settings → My Profile

---

## Architecture

TrackIt follows **Clean Architecture** with a unidirectional data flow:

```
SMS / User Input
      │
      ▼
 Presentation (Compose + ViewModel)
      │  StateFlow / UI events
      ▼
  Use Cases (domain layer, pure Kotlin)
      │
      ▼
 Repository (interface, injected by Hilt)
      │
   ┌──┴──────────────────┐
   │                     │
   ▼                     ▼
Room (local)        Retrofit (remote)
  SQLite           Cloudflare Workers
                   + MongoDB Atlas
```

**Key principles:**
- ViewModels hold `StateFlow<UiState>` — single source of truth for the UI
- Use cases are thin orchestrators; business logic lives in domain models
- Repositories own local-first strategy: read from Room, sync to API in background
- No shared mutable state between screens; navigation is purely composable

---

## Project Structure

```
Expensetracker/
├── app/                         Android application
│   └── src/main/java/com/trackit/expense/
│       ├── MainActivity.kt
│       ├── TrackItApp.kt         Hilt + WorkManager application class
│       ├── di/                   Hilt modules
│       │   ├── AuthModule.kt
│       │   ├── DatabaseModule.kt
│       │   ├── NetworkModule.kt
│       │   └── RepositoryModule.kt
│       ├── domain/
│       │   ├── model/            Pure Kotlin data classes (no Android deps)
│       │   ├── repository/       Repository interfaces
│       │   └── usecase/          One class per use case
│       ├── data/
│       │   ├── local/
│       │   │   ├── dao/          Room DAOs
│       │   │   ├── db/           TrackItDatabase + migrations
│       │   │   └── entity/       Room entities
│       │   └── remote/
│       │       ├── api/          Retrofit service interface
│       │       ├── dto/          Request/response data classes
│       │       └── interceptor/  AuthInterceptor (attaches Firebase token)
│       ├── data/repository/      Repository implementations
│       ├── presentation/
│       │   ├── navigation/       NavGraph, Screen, BottomNavItem
│       │   ├── login/            Google Sign-In
│       │   ├── onboarding/       3-page first-run flow
│       │   ├── home/             Dashboard
│       │   ├── add/              Add/edit expense
│       │   ├── history/          Expense list + unlogged
│       │   ├── detail/           Expense detail
│       │   ├── analytics/        Category breakdown charts
│       │   ├── groups/           Group list, group detail, add split
│       │   ├── profile/          Edit name + UPI ID
│       │   ├── settings/         App settings
│       │   ├── components/       Shared composables
│       │   └── theme/            Material 3 theme, colors, typography
│       ├── sms/                  SMS parsing (stateless regex) + BroadcastReceiver
│       ├── worker/               WorkManager workers
│       ├── widget/               Glance home screen widget
│       └── util/                 Helpers: permissions, export, notifications
│
└── trackit-api/                 Backend (Cloudflare Workers)
    ├── src/
    │   ├── index.ts             All API routes
    │   ├── auth.ts              Firebase JWT verification
    │   └── db.ts                MongoDB native driver wrapper
    ├── wrangler.jsonc           Cloudflare Workers config
    ├── package.json
    └── .dev.vars                Local secrets (gitignored)
```

---

## Tech Stack

### Android
| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (KSP) |
| Local DB | Room v7 |
| Networking | Retrofit + OkHttp |
| Background work | WorkManager |
| Home widget | Glance |
| Preferences | DataStore |
| Auth | Firebase Authentication (Google Sign-In) |
| Serialization | Gson |
| Testing | JUnit4, MockK, Turbine, Google Truth |

### Backend
| Layer | Technology |
|---|---|
| Runtime | Cloudflare Workers |
| Framework | Hono (TypeScript) |
| Database | MongoDB Atlas |
| DB driver | `mongodb@^7.2.0` (native, via `nodejs_compat`) |
| Auth | Firebase RS256 JWT via Web Crypto API (no Admin SDK) |

---

## Backend API

Base URL (local): `http://192.168.x.x:8787`  
Base URL (production): deployed via `npx wrangler deploy`

All `/api/*` routes require `Authorization: Bearer <firebase-id-token>`.

### Auth & Profile

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/profile` | Upsert user record on first login (`$setOnInsert` for `createdAt`) |
| `GET` | `/api/me` | Get current user profile |
| `PUT` | `/api/me` | Update name and/or UPI ID |

### Expenses

| Method | Path | Query params | Description |
|---|---|---|---|
| `GET` | `/api/expenses` | `month`, `page`, `limit` | List expenses (sorted by `transactionAt` desc) |
| `POST` | `/api/expenses` | — | Create a single expense |
| `POST` | `/api/sync` | — | Bulk upsert with conflict resolution (`updatedAt` wins) |

### Groups & Splits

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/groups` | Create group (creator auto-added as first member) |
| `GET` | `/api/groups` | List groups the authenticated user belongs to |
| `GET` | `/api/groups/:id` | Get a single group |
| `POST` | `/api/groups/:id/members` | Add a member to a group |
| `POST` | `/api/groups/:id/splits` | Record a shared expense split |
| `GET` | `/api/groups/:id/splits` | List all splits in a group |
| `GET` | `/api/groups/:id/balances` | Get simplified balance list (min-transactions algorithm) |
| `POST` | `/api/groups/:id/settle` | Record a pending settlement |
| `PUT` | `/api/settlements/:id/confirm` | Confirm a settlement (only the payee can confirm) |

**Balance simplification** — The `/balances` endpoint runs a greedy min-transactions algorithm:
1. Compute net balance per member across all splits and confirmed settlements
2. Separate into creditors (net > 0) and debtors (net < 0)
3. Greedily match largest creditor with largest debtor until all balances are zero
4. Returns a flat list of `{ from, to, amount }` transfer pairs

### Analytics & Budgets

| Method | Path | Query params | Description |
|---|---|---|---|
| `GET` | `/api/analytics/summary` | `month` (required) | Total spend + per-category breakdown via MongoDB `$group` |
| `GET` | `/api/budgets` | `month` | Get budget for a month |
| `POST` | `/api/budgets` | — | Upsert budget for a month |

---

## Android App

### Navigation

Bottom nav: **Home → Expenses → Groups → Analytics → Settings**

Deep links (registered in `AndroidManifest.xml`):
```
trackit://add_expense?amount=&merchant=&account=
trackit://expense_detail/{expenseId}
trackit://unlogged
trackit://reports
```

UPI settle-up intent (external):
```
upi://pay?pa={upiId}&pn={name}&am={amount}&cu=INR&tn=TrackIt+settlement
```

### SMS Parsing

`SmsParser` is a stateless regex engine. It handles:
- HDFC, ICICI, SBI, Axis, Kotak debit messages
- UPI debit/credit confirmations
- Credit card spend notifications

Parsed fields: `amount`, `merchant`, `account`, `transactionAt`, `type` (debit/credit).

`SmsReceiver` receives `SMS_RECEIVED` broadcasts, passes each message body through `SmsParser`, and stores results in `PendingExpenseStore` for the user to confirm in the Unlogged Expenses screen.

### WorkManager Workers

| Worker | Trigger | Purpose |
|---|---|---|
| `SyncWorker` | Periodic (15 min) | Push unsynced local expenses to `/api/sync` |
| `BudgetAlertWorker` | Periodic (daily) | Fire notification if spend > 80% of monthly budget |
| `WeeklySummaryWorker` | Weekly | Send week-in-review notification |
| `UnloggedReminderWorker` | Periodic | Remind user if unlogged SMS expenses accumulate |
| `WidgetRefreshWorker` | Periodic (30 min) | Refresh Glance home screen widget data |

---

## Local Development Setup

### Prerequisites
- Android Studio Hedgehog or later
- Node.js 20+
- `npx wrangler` (included via `npm install`)
- MongoDB Atlas account with a free-tier cluster
- Firebase project with Google Sign-In enabled

### 1. Clone the repo

```bash
git clone <repo-url>
cd Expensetracker
```

### 2. Firebase setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `com.trackit.expense`
3. Download `google-services.json` and place it at `app/google-services.json`
   — this file is **gitignored**; it carries your API key, OAuth client IDs and
   signing-cert hash. `app/google-services.json.template` shows the shape.
4. Enable **Google Sign-In** under Authentication → Sign-in methods
5. Note your **Project ID** (e.g. `trackit-a1b2c`)

### 3. Backend setup

```bash
cd trackit-api
npm install
```

Copy the example env file and fill in your connection string (`.dev.vars` is
gitignored — never commit it):

```bash
cp .dev.vars.example .dev.vars
```

```
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority&appName=<AppName>
```

> **Important:** URL-encode special characters in your password. `#` → `%23`, `@` → `%40`.

Edit `wrangler.jsonc` — set your Firebase Project ID:

```jsonc
"vars": {
  "MONGODB_DATABASE": "trackit",
  "FIREBASE_PROJECT_ID": "your-firebase-project-id"
}
```

**MongoDB Atlas Network Access:** Add your IP address (or `0.0.0.0/0` for development) under Database → Network Access.

Start the dev server:

```bash
npx wrangler dev --ip 0.0.0.0
```

> `--ip 0.0.0.0` is required to accept connections from a physical Android device on the same LAN.  
> **macOS users:** If the phone cannot connect, allow `node` inbound connections via System Settings → Privacy & Security → Firewall → Firewall Options, or temporarily: `sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setglobalstate off`

Server starts on `http://0.0.0.0:8787`. Find your Mac's LAN IP with `ipconfig getifaddr en0`.

### 4. Android setup

Open `Expensetracker/` in Android Studio.

Point the app at your backend by adding a line to `local.properties` (gitignored,
so your address never lands in a commit):

```properties
# Physical device on the same LAN as your dev machine:
trackit.api.baseUrl=http://192.168.x.x:8787/

# Emulator (this is the default if the property is absent):
# trackit.api.baseUrl=http://10.0.2.2:8787/

# Deployed backend:
# trackit.api.baseUrl=https://<your-worker>.workers.dev/
```

Gradle reads it into `BuildConfig.API_BASE_URL`, which `NetworkModule` consumes.
Cleartext HTTP is permitted in **debug builds only** — release builds enforce
Android's default HTTPS-only policy, so a deployed Worker must be reached over TLS.

Build and run on a physical device (SMS reading requires a real device; emulators do not receive real SMSes).

**Required permissions** (requested at runtime):
- `RECEIVE_SMS` — live SMS interception
- `READ_SMS` — bulk inbox import
- `POST_NOTIFICATIONS` — budget alerts and weekly summaries

---

## Environment Variables

### Backend (`wrangler.jsonc` vars — non-secret)

| Variable | Example | Description |
|---|---|---|
| `MONGODB_DATABASE` | `trackit` | MongoDB database name |
| `FIREBASE_PROJECT_ID` | `trackit-a1b2c` | Firebase project ID for JWT verification |

### Backend (`.dev.vars` — secrets, never commit)

| Variable | Description |
|---|---|
| `MONGODB_URI` | Full MongoDB Atlas connection string (URL-encode special chars in password) |

### Android (`local.properties` — machine-local, never commit)

| Property | Default | Description |
|---|---|---|
| `trackit.api.baseUrl` | `http://10.0.2.2:8787/` | Backend base URL, injected as `BuildConfig.API_BASE_URL` |
| `sdk.dir` | — | Android SDK path (written by Android Studio) |

### Production secrets

Set via Wrangler:

```bash
npx wrangler secret put MONGODB_URI
```

---

## Database Schema

### MongoDB Collections

**`users`**
```
{ _id: firebaseUid, name, email, photoUrl, upiId, createdAt, updatedAt }
```

**`expenses`**
```
{ id, userId, amount, category, merchant, month, transactionAt, notes, updatedAt, syncedAt }
```

**`budgets`**
```
{ userId, month, total, categories: { [category]: amount }, updatedAt }
```

**`groups`**
```
{ _id: uuid, name, emoji, createdBy: userId, members: [{ userId, name, phone, upiId }], createdAt }
```

**`splits`**
```
{ _id: uuid, groupId, description, totalAmount, currency, paidBy: userId,
  participants: [{ userId, amount }], expenseId, createdAt }
```

**`settlements`**
```
{ _id: uuid, groupId, fromUserId, toUserId, amount, status: 'pending'|'confirmed',
  createdAt, confirmedAt }
```

### Room (SQLite) — v7

| Table | Key columns |
|---|---|
| `expenses` | `id`, `userId`, `amount`, `category`, `merchant`, `month`, `transactionAt`, `updatedAt` |
| `budgets` | `userId`, `month`, `totalBudget`, `categoryBudgetsJson` |
| `groups` | `id`, `name`, `emoji`, `createdBy`, `membersJson`, `createdAt` |
| `splits` | `id`, `groupId`, `description`, `totalAmount`, `paidBy`, `participantsJson`, `createdAt` |

`membersJson` and `participantsJson` store serialized JSON arrays (via Gson) since Room does not support nested objects.

---

## Building a Release

TrackIt is distributed as a **signed APK via GitHub Releases**, not through the Play
Store. It needs `READ_SMS` / `RECEIVE_SMS` to parse bank messages, and automatic
expense tracking is not one of Google's approved use cases for those restricted
permissions, so a Play submission would be rejected on policy rather than quality.

### One-time: create a signing key

```bash
keytool -genkeypair -v -keystore trackit-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias trackit
```

> **Back this file up outside the repo.** Android only allows an update if it is
> signed with the same key. Lose the keystore and no existing install can ever be
> updated again — users would have to uninstall (losing local data) and reinstall.
> `*.jks` is gitignored.

### Configure the build

Add to `local.properties` (untracked):

```properties
trackit.api.baseUrl.release=https://<your-worker>.workers.dev/
trackit.keystore.path=/absolute/path/to/trackit-release.jks
trackit.keystore.password=...
trackit.key.alias=trackit
trackit.key.password=...
```

```bash
./gradlew :app:assembleRelease
```

`verifyReleaseConfig` runs first and **fails the build** if the release URL is
missing, is not `https://`, or has no trailing slash. Without that check a release
would silently inherit the debug default of `http://10.0.2.2:8787/` — unreachable
off an emulator and blocked outright by the HTTPS-only policy release builds get,
so every request would fail with no visible cause.

### Automated releases

Pushing a `v*` tag runs `.github/workflows/release.yml`, which builds the APK,
verifies it is actually signed with `apksigner`, and attaches it to the GitHub
release. It needs these repository secrets:

| Secret | What it holds |
|---|---|
| `GOOGLE_SERVICES_JSON` | Contents of `app/google-services.json` (gitignored) |
| `RELEASE_KEYSTORE_BASE64` | `base64 -i trackit-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias (`trackit`) |
| `RELEASE_KEY_PASSWORD` | Key password |
| `TRACKIT_API_BASEURL_RELEASE` | Deployed backend URL, `https://…/` |

---

## CI & Secret Scanning

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

| Job | What it does |
|---|---|
| **backend** | `tsc --noEmit` + `vitest run` in `trackit-api/` |
| **android** | `:app:testDebugUnitTest` + `:app:lintDebug`, with test and lint reports uploaded as artifacts |
| **secrets** | gitleaks over the full history, using `.gitleaks.toml` |

The Android job writes `app/google-services.json.template` into place before
building. The Google Services Gradle plugin only needs well-formed JSON whose
`package_name` matches the `applicationId` — enough to compile and run unit
tests, and not enough to reach a real Firebase project.

### Local guard

A pre-commit hook refuses to commit `app/google-services.json` or any staged diff
that looks like a credential. Enable it once per clone:

```bash
git config core.hooksPath .githooks
```

It uses [gitleaks](https://github.com/gitleaks/gitleaks) when installed
(`brew install gitleaks`) and falls back to a pattern scan otherwise, so the
guard still works on a machine without it.

---

## Privacy

[PRIVACY.md](PRIVACY.md) is the user-facing policy, linked from Settings → Privacy
Policy in the app. The short version: SMS parsing happens entirely on-device, only
matched transactions become records, nothing is sold or shared with third parties,
and Settings → Account → Delete Account erases everything.

One deliberate exception worth knowing about: account deletion keeps shared group
splits and settlements and removes you from those groups instead. Deleting them
would rewrite other members' balances and make money genuinely owed vanish from
their ledger.

---

## Security Model

### What proves identity
Every `/api/*` route requires an `Authorization: Bearer <Firebase ID token>` header.
`src/auth.ts` verifies the RS256 signature against Google's JWKS endpoint using Web
Crypto — no Firebase Admin SDK, no service-account key to leak. It checks algorithm,
`kid`, signature, expiry, `iat` skew, issuer and audience before trusting the `sub`
claim as the user id. Public keys are cached through Cloudflare's Cache API, which
honours Google's `Cache-Control: max-age=21600`, so keys rotate on their own.

### What proves authorization
Authentication proves *who* is calling; it says nothing about *what* they may touch.
So every route derives ownership server-side and never from the request body:

- **User-scoped collections** (`expenses`, `budgets`, `users`) filter on the token's
  uid. `userId` is spread *last* into every written document, and `_id`/`userId` are
  stripped from incoming payloads, so a client cannot claim another user's records.
- **Group-scoped routes** all pass through `requireMembership()`, which loads the
  group with `{ _id: groupId, 'members.userId': callerUid }`. A non-member gets a
  404 — the same response as a group that does not exist, so the endpoint does not
  confirm which group ids are real.
- **Cross-member writes are validated against the member list**: a split's `paidBy`
  and every participant, and a settlement's `toUserId`, must belong to that group.
  Otherwise a caller could assign a debt to an unrelated user.
- **Settlement confirmation** is filtered on `{ _id, toUserId: callerUid }` — only
  the person owed money can confirm they were paid.

### Input handling
- Request-body fields that reach a Mongo filter are rejected unless scalar, so a
  value like `{"$ne": null}` cannot be smuggled in as a query operator.
- Amounts must be finite and positive; page size is clamped (200 max) and the sync
  batch is capped (500 expenses) so no client can ask for or push an unbounded set.
- `app.onError` logs unexpected failures server-side and returns a flat
  `{ error: 'Internal server error' }` — no stack traces or driver messages.

### On-device
- The Firebase ID token is attached by `AuthInterceptor` and **redacted from OkHttp
  logs**; body-level logging is compiled out of release builds entirely.
- `allowBackup="false"` plus `data_extraction_rules.xml` keep the Room database of
  parsed transactions out of Google cloud backups and device-transfer bundles.
- `SmsReceiver` is guarded by `android:permission="android.permission.BROADCAST_SMS"`,
  so only the system — not a third-party app — can deliver SMS intents to it.
- Parsed SMS content never leaves the device beyond the user's own backend; TrackIt
  never touches money, it only builds a `upi://pay` intent for the user's UPI app.

### Rate limiting
Two Cloudflare rate-limit bindings sit in front of every `/api/*` route:

| Binding | Key | Budget | Why |
|---|---|---|---|
| `IP_RATE_LIMIT` | `CF-Connecting-IP` | 300 / min | Checked **before** token verification, so a flood of junk tokens cannot burn CPU on RS256 signature checks |
| `USER_RATE_LIMIT` | Firebase uid | 120 / min | Checked **after** verification, so one compromised account cannot exhaust the Worker or hammer MongoDB for everyone else |

Both fail *open*: if a limiter is unavailable the request proceeds rather than the
API going dark. Order matters and is covered by tests — an over-limit IP gets a
429 with no `Authorization` header present, while an invalid token still gets a
401 even when the user limiter would have denied it, so an unauthenticated caller
can never consume someone else's quota.

### Known limitations
- **Group membership is invite-by-uid.** There is no invite-acceptance step yet, so
  a member can add another uid to a group without that user consenting.
- **No audit trail** on split edits or settlement confirmations.
- **Rate limits are per-datacenter**, not global — Cloudflare's rate limiting API
  counts within a colo, so the effective ceiling is higher than the configured
  number for a geographically distributed caller.

---

## Roadmap

| Phase | Status | Description |
|---|---|---|
| 1 — Foundations | ✅ Complete | Firebase auth, user profiles, sync conflict resolution, onboarding |
| 2 — Group Splits | 🔄 In progress | Backend + Android groups/splits/settlements (2.1–2.4 done; 2.5 share links pending) |
| 3 — KMP Migration | Planned | Move domain + use cases to Kotlin Multiplatform shared module |
| 4 — iOS | Planned | SwiftUI app using KMP shared module; email-based import instead of SMS |

### Phase 2.5 — Viral Share Links (next up)
Allow a group creator to generate a join link. Non-users who open the link see a web preview of the group and a prompt to install TrackIt. Existing users deep-link directly into the group.

### Phase 3 — KMP
Migrate `domain/model/`, `domain/repository/`, `domain/usecase/`, and `SmsParser` to a shared Kotlin Multiplatform module. Android app continues to use Room + Compose. iOS app (Phase 4) reuses the shared module with SwiftUI.

### Phase 4 — iOS
- SwiftUI UI layer
- KMP shared domain/use-case/repository interfaces
- No SMS (iOS restriction) — email forwarding flow as substitute
- UPI deep links work on iOS via GPay/PhonePe iOS apps
