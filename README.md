# TrackIt

**An expense tracker that doesn't ask you to track expenses.**

TrackIt reads the bank and UPI SMS your phone already receives, turns them into a
categorised ledger without a single tap, and lets you split bills with friends that
settle over UPI — in the app they already pay with.

Android · Kotlin/Compose · Cloudflare Workers · MongoDB · [Product roadmap](ROADMAP.md) · [Privacy policy](PRIVACY.md)

> **Status:** feature-complete on Android and verified on a physical device
> (227 unit tests, 41 instrumented tests, 0 lint errors). The backend runs locally
> but **is not yet deployed**, so there is no public production instance. See
> [What's built — and what isn't](#whats-built--and-what-isnt) for the honest state.

---

## Contents

**Product**
[The problem](#the-problem) ·
[Who it's for](#who-its-for) ·
[The solution](#the-solution) ·
[The strategic bet](#the-strategic-bet) ·
[Decisions and trade-offs](#decisions-and-trade-offs) ·
[What's built](#whats-built--and-what-isnt) ·
[How I'd measure it](#how-id-measure-it) ·
[Constraints that shaped the product](#constraints-that-shaped-the-product) ·
[Roadmap](#roadmap)

**Engineering**
[Architecture](#architecture) ·
[Project structure](#project-structure) ·
[Tech stack](#tech-stack) ·
[Backend API](#backend-api) ·
[Android app](#android-app) ·
[Local development](#local-development-setup) ·
[Database schema](#database-schema) ·
[Building a release](#building-a-release) ·
[CI](#ci--secret-scanning) ·
[Security model](#security-model)

---

## The Problem

**Every expense tracker asks the user to do the one thing they will not do: log the
expense.**

Manual entry has to happen at the exact moment someone is spending money — at a
counter, in a cab, splitting a dinner bill. That is the worst possible moment to ask
for thirty seconds of data entry. So people log diligently for a week, miss a few
days, and the ledger becomes wrong. Once it's wrong it's useless, and once it's
useless they stop opening the app. The failure isn't motivation; it's that the
product put work in the wrong place.

Meanwhile, the data already exists. In India, UPI is the default way money moves,
and effectively every transaction generates an SMS from the bank within seconds —
amount, merchant, account, timestamp. It is a complete, timestamped transaction
feed sitting unused in the messages app — structured enough to parse, and already
delivered to the one device the user always has.

The adjacent problem is settling up. Splitting costs with flatmates or on a trip
means one person fronts the money and everyone forgets who owes what. Existing
split apps track the debt but **stop at the ledger** — you still leave the app,
open a payments app, retype the amount, and come back to mark it paid. The friction
is at the last step, which is exactly where a debt goes unpaid.

### Why this space is open

SMS-based expense tracking isn't a new idea in India — Walnut and Money View both
built on it years ago. What happened to that category is the interesting part:
those products were acquired or pivoted toward lending, where the money is, and in
2019 Google sharply restricted SMS and call-log permissions on Play, removing the
capture mechanism for everyone who wanted to stay listed.

So the approach was abandoned for two reasons, and neither was that it stopped
working for users. It was abandoned because tracking expenses monetises badly
compared to lending, and because the distribution channel closed.

That leaves a genuine gap, and it defines the terms for anything filling it: it has
to work outside the Play Store, and it has to be cheap enough to run without a
lending business attached. Both are design constraints TrackIt accepts up front —
see [Constraints that shaped the product](#constraints-that-shaped-the-product).

**What none of them did was close the loop to payment.** UPI arrived after that
generation of apps, and it is the piece that turns a ledger into a settlement: the
transaction feed and the payment rail now both sit on the same phone, unconnected.

---

## Who It's For

Urban Indian smartphone users, roughly 22–35, who:

- pay for almost everything by UPI, across two or three accounts and cards
- have tried a budgeting app and abandoned it within a month
- regularly share costs — flatmates, couples, trips, group dinners
- already have GPay or PhonePe installed and use it without thinking

The second and third bullets are the interesting ones together: this user has
already *tried and rejected* manual tracking, and has an ongoing social reason to
need a shared ledger. They are not looking for another budgeting app. They are
looking to not have to think about it.

---

## The Solution

Three things, in priority order.

### 1. Capture with zero user effort

An SMS receiver reads incoming bank and UPI messages, parses amount, merchant,
account and timestamp on-device, infers a category, and writes the expense. The
user does nothing. First install also offers a bulk import of the existing inbox,
so the app is useful with history from minute one rather than empty for a month.

Parsed expenses land in an **Unlogged** inbox rather than silently into the ledger.
Review is a one-tap confirm, and duplicate detection catches the same transaction
arriving twice. The design principle: the app does the work and asks the user to
approve, never the reverse.

### 2. Splits that end in an actual payment

Create a group, add a shared expense, split it equally or by custom amounts.
Balances are simplified server-side with a greedy min-transactions algorithm, so a
group of five settles in the fewest possible payments instead of a web of small
IOUs.

Settling is one tap: TrackIt builds a `upi://pay` intent pre-filled with the payee's
UPI ID and the exact amount, and hands it to GPay or PhonePe. The user confirms in
the app they already trust. **TrackIt never touches the money** — it holds no float,
processes no payment, and needs no licence to operate.

### 3. Local-first, so it's instant and works offline

Room is the source of truth. Every screen reads local data, so the UI is immediate
and the app works fully offline — including SMS capture, which is exactly when
connectivity is least reliable. Sync is a background reconciliation, not a
prerequisite for the app functioning.

---

## The Strategic Bet

> Payments in India are a solved, regulated, winner-take-all market. The **ledger on
> top of them is not.**

Building payments would mean licences, capital, compliance, and competing with
PhonePe, Google and Paytm. TrackIt deliberately doesn't. UPI already moved the
money and the bank already sent the receipt — the unclaimed ground is the
*record* of what happened and the *social graph* around who owes whom.

That reframes the moat. It isn't technology; it's the accumulated ledger and the
group relationships inside it. A user with two years of categorised history and
four active groups has something they can't get by installing a competitor. The
SMS parser is the wedge that makes accumulating that history free for them.

It also makes the business viable at a hobby scale: no payment infrastructure means
no per-transaction cost, which is why this runs on free tiers.

---

## Decisions and Trade-offs

Every one of these closed off something real. Documenting the cost is the point.

| Decision | Why | What I gave up |
|---|---|---|
| **Parse SMS on-device, never server-side** | Bank SMS is the most sensitive data the app touches. Parsing locally means messages never leave the phone, and it costs nothing to run. | No central improvement loop. A bank that changes its SMS format breaks parsing until the next app release, and I can't see or fix failures across users. |
| **Google Sign-In, not phone OTP** | Free at any scale, no per-SMS cost, and works unchanged when iOS arrives. | Lost the phone number as the natural identity for splits — which is why adding a group member is still clumsier than it should be. Excludes users without a Google account. |
| **Never handle money; deep-link to UPI** | No licence, no compliance burden, no float, no per-transaction cost. Users pay in an app they already trust more than mine. | Can't confirm a payment automatically. Settlement needs a two-step "mark paid → recipient confirms" flow, which is more taps and can be gamed by an impatient user. |
| **Local-first with Room as source of truth** | Instant UI and full offline capability, which SMS capture genuinely needs. | Real distributed-sync problems: conflict resolution, tombstones for deletes, and a pull path — none of which a server-authoritative design would need. |
| **Ship as a signed APK, not on Play** | SMS permissions are Play-restricted and automatic expense tracking isn't an approved use case. A submission would be rejected on policy, not quality. | No organic discovery, no automatic updates, and users must allow install from unknown sources. This is the single biggest growth constraint. |
| **Free-tier infrastructure only** | $0 running cost keeps the project alive indefinitely without a business model. | MongoDB M0 has no automated backups — unacceptable for financial data long-term. Rate limits are per-datacenter, not global. |
| **Simplify debts server-side** | One implementation, consistent across every client, and the client stays thin — which matters for the planned iOS app. | Balances need a network round-trip, so the one screen where users are most impatient is the one that can show a spinner. |
| **Review queue instead of silent auto-add** | A wrong auto-captured expense corrupts the ledger, and a corrupted ledger is the exact failure mode that kills manual trackers too. | Reintroduces a small amount of user work — the thing the product exists to remove. Justified only because confirming is one tap and batched. |

---

## What's Built — and What Isn't

### Working and verified

| Area | What it does |
|---|---|
| **SMS capture** | `SmsReceiver` parses incoming bank/UPI SMS on-device; bulk inbox import on first run; duplicate detection; OTP and promotional messages explicitly discarded |
| **Ledger** | Manual add/edit, categories, merchant, notes, deep-linkable detail (`trackit://expense_detail/{id}`), CSV export |
| **Budgets** | Monthly overall and per-category budgets; `BudgetAlertWorker` notifies near threshold; weekly summary |
| **Groups and splits** | Groups with members, equal or custom splits, server-side min-transaction balance simplification, `upi://pay` settle-up, pending → confirmed settlement tracking |
| **Sync** | Bidirectional. Push of local changes plus an incremental pull keyed on an `updated_at` watermark, so a reinstall restores history and a second device sees the data. Soft-delete tombstones propagate deletions. |
| **Analytics** | Monthly category breakdown, daily totals, charts; server-side aggregation via MongoDB `$group` |
| **Widget** | Glance home-screen widget with today's and month-to-date spend |
| **Auth and account** | Google Sign-In via Firebase; onboarding; profile with UPI ID; **full account deletion** that erases server and local data |

### Not done, and why

- **The backend is not deployed.** It runs under `wrangler dev` against a local
  MongoDB connection. There is no public URL, so there is no production instance
  and no real users yet. Deploying is a decision to start operating a service, not
  just a command to run.
- **Group membership has no invite acceptance.** A member can add another user's ID
  to a group without that person agreeing. Fine for a trusted circle, wrong for
  anything public — and the fix is blocked on not having phone-number identity.
- **No audit trail** on split edits or settlement confirmations, so a disputed
  balance can't be reconstructed.
- **The local database is not encrypted at rest** beyond Android's own full-disk
  encryption.
- **`targetSdk` is deliberately still 34.** Play's floor doesn't apply to direct
  APK distribution, and moving to 35+ enforces edge-to-edge layout and changes
  foreground-service rules. That's a tested change, not a version bump.
- **No iOS, no share links, no KMP migration** — see [Roadmap](#roadmap).

---

## How I'd Measure It

The entire thesis is "capture without effort", so the metrics have to test that
specifically rather than reporting generic engagement.

**The one number that matters**

> **Auto-capture rate** — auto-captured expenses ÷ total expenses recorded.
> If this isn't high, TrackIt is just a manual tracker with extra permissions, and
> the whole premise is wrong.

**Activation funnel** — where the thesis is won or lost, in the first session

| Step | What it tests |
|---|---|
| Install → SMS permission granted | Whether the value proposition justifies a scary permission |
| Permission → ≥1 expense auto-captured | Whether the parser works on this user's bank |
| Bulk import → ≥30 days of history present | Whether the app is useful immediately instead of empty |
| First session → first confirmed expense | Whether review-and-confirm is understood |

**Parser quality** — the product's core competence, and its main failure mode

- Confirm-without-edit rate on unlogged expenses (precision proxy: did we parse it right?)
- Discard rate (false positives: did we surface something that wasn't a transaction?)
- Unparsed-SMS sampling by bank sender, to find formats the parser misses

**Retention** — the metric manual trackers fail

- D7 / D30 weekly-active, segmented by whether SMS permission was granted. If
  granted and denied cohorts retain the same, automatic capture isn't the driver.

**Social loop** — the compounding part

- Groups created per active user; median group size
- Share of settlements completed through the UPI deep link vs abandoned
- Time from balance created to settled

**Guardrails** — things that would mean I'm winning the metric and losing the user

- Notification opt-out rate (budget alerts becoming noise)
- Battery-optimisation exemption revoked (the app feeling too costly to keep)
- Sync failure rate and unresolved conflicts

---

## Constraints That Shaped the Product

**A policy constraint became a distribution strategy.** `READ_SMS` and
`RECEIVE_SMS` are restricted permissions on Google Play, and the approved use cases
are things like being the default SMS handler or a backup tool. Automatic expense
tracking is not on the list. A Play submission would be rejected regardless of code
quality.

That left three options: drop SMS auto-capture and pass review, submit and argue for
an exception, or distribute outside Play. The first destroys the only reason the
product exists. The second is a coin flip that costs weeks. So TrackIt ships as a
signed APK from GitHub Releases and accepts that it will never have organic install
growth.

That single constraint cascades into everything else: no store discovery means no
growth loop from search, which is why the group-invite share link matters more than
it otherwise would — a friend inviting you is the only distribution channel left.

**Free tier as a hard requirement.** With no revenue, the running cost has to be
zero, which is why the architecture avoids anything with per-request pricing and
why the app never touches money. The honest cost is MongoDB M0's lack of automated
backups, which is fine for a small trusted group and unacceptable at scale.

---

# Engineering

Everything above is the product case. Everything below is how it is actually built,
tested and shipped.

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

### Room (SQLite) — v8

Migrations live in `DatabaseMigrations.ALL` and cover every consecutive version
pair; `MigrationTest` runs the full 3 → 8 chain on a device and asserts data
survives. Schemas are exported to `app/schemas/` so a migration can be diffed
against what Room actually expects. Destructive fallback is debug-only — in release
a missing migration fails at open time rather than silently erasing history.

| Table | Key columns |
|---|---|
| `expenses` | `id`, `userId`, `amount`, `category`, `merchant`, `month`, `transactionAt`, `updatedAt`, `isDeleted` |
| `budgets` | `userId`, `month`, `totalBudget`, `categoryBudgetsJson` |
| `groups` | `id`, `name`, `emoji`, `createdBy`, `membersJson`, `createdAt` |
| `splits` | `id`, `groupId`, `description`, `totalAmount`, `paidBy`, `participantsJson`, `createdAt` |

`membersJson` and `participantsJson` store serialized JSON arrays (via Gson) since Room does not support nested objects.

`isDeleted` is a soft-delete tombstone: the row survives locally so the deletion can
be pushed to the server, every read query filters it out, and it is purged once the
server acknowledges it. Hard-deleting meant the server never learned about the
deletion and the expense reappeared on the next pull.

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

### Accepted findings

`.gitleaksignore` holds fingerprints of findings a human has reviewed and accepted,
with the reasoning inline. It currently has one entry: the Firebase Android API key
in commit `28fda89`, which is restricted in Google Cloud Console to this app's
package name and signing certificate and so cannot be used elsewhere.

Entries are fingerprint-scoped, not pattern-scoped — a *different* Google API key
committed tomorrow still fails the scan. Add an entry only when the finding is
genuinely not exploitable, never to turn CI green.

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

Sequenced by dependency, not by appeal. Each phase exists because the next one is
wrong to build without it.

| Phase | Status | Why it comes here |
|---|---|---|
| **1 — Foundations** | ✅ Complete | Auth, identity and sync correctness. Social features built on broken sync produce corrupted shared balances, which is worse than having no social features. |
| **2 — Group splits** | ✅ Android complete | The retention and distribution engine. Solo tracking is a habit that decays; a shared ledger has other people asking about it. |
| **2.5 — Invite share links** | Next | With no app store, a friend's invite is the *only* distribution channel. This is the growth work. |
| **3 — KMP migration** | Planned | Move domain, use cases and the SMS parser to a shared module. Pure enablement for Phase 4 — no user-visible change, so it's only worth doing immediately before iOS. |
| **4 — iOS** | Planned | Doubles the addressable group size. Groups break when one member can't join, so iOS absence caps the social feature, not just the user count. |

### Phase 2.5 — Invite share links (next up)

A group creator generates a join link. A non-user opening it sees a web preview of
the group and a prompt to install; an existing user deep-links straight into the
group. This also fixes the invite-acceptance gap — joining via a link is consent,
which the current add-by-user-ID flow lacks.

### Phase 3 — Kotlin Multiplatform

Migrate `domain/model/`, `domain/repository/`, `domain/usecase/` and `SmsParser`
into a shared module. Android keeps Room and Compose. Deliberately deferred: it
buys nothing for Android users on its own, and doing it early means maintaining a
multiplatform build for months before anything consumes it.

### Phase 4 — iOS

SwiftUI on top of the KMP shared module. **iOS cannot read SMS** — there is no
equivalent API — so the capture wedge doesn't transfer. The substitute is email
forwarding of bank alerts, which is meaningfully worse, and worth being honest
about: on iOS, TrackIt is a splits app with manual entry rather than a zero-effort
tracker. UPI deep links work normally, since GPay and PhonePe both ship iOS apps.

---

## Product Documentation

- **[ROADMAP.md](ROADMAP.md)** — full phase breakdown with decisions log and status
- **[PRIVACY.md](PRIVACY.md)** — user-facing privacy policy
- **[FIREBASE_SETUP.md](FIREBASE_SETUP.md)** — one-time Firebase configuration

---

## License

[MIT](LICENSE)
