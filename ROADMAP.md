# TrackIt — Product & Engineering Roadmap

> Last updated: 2026-05-04  
> Owner: Shrinandan  
> Stack: Android (Kotlin + Jetpack Compose) + Hono backend (Cloudflare Workers + MongoDB)

---

## Vision

TrackIt is an SMS-first expense tracker for India. The strategic bet is:
1. Auto-capture expenses from bank/UPI SMS (no manual entry)
2. Add group split features (Splitwise-style) with UPI-native settlement
3. Expand to iPhone via Kotlin Multiplatform (KMP)

The UPI settlement loop already lives in every Indian's phone (GPay/PhonePe). TrackIt doesn't need to build payments — it just needs to build the social graph around them.

---

## Phase Overview

| Phase | Focus | Status |
|---|---|---|
| 1 | Fix foundations (auth, sync, user identity) | **TODO** |
| 2 | Group splits — Android only | TODO |
| 3 | KMP migration (shared business logic) | TODO |
| 4 | iOS app (SwiftUI + KMP) | TODO |

---

## Phase 1 — Fix Foundations (Weeks 1–8)

> Nothing else can be built correctly without these. Social features on broken auth = data corruption.

### 1.1 Google Sign-In Auth ✅ DONE (2026-05-04)
**Decision:** Replaced Twilio OTP with Google Sign-In (Firebase Auth). Free, no per-SMS cost, works on iOS too.

**What was built:**
- `trackit-api/src/auth.ts` — Firebase RS256 token verifier using Web Crypto API + Cloudflare Cache API for JWKS caching. Zero external deps.
- `trackit-api/src/index.ts` — Removed OTP routes, removed `SESSIONS` KV and `JWT_SECRET`. All routes now verify Firebase ID tokens. Added `POST /api/auth/profile` to bootstrap user record on first login.
- `trackit-api/wrangler.jsonc` — Removed `kv_namespaces` (SESSIONS), added `FIREBASE_PROJECT_ID` var.
- Android: Firebase BOM 33.1.0 + `firebase-auth-ktx` + `play-services-auth` 21.2.0
- `AuthRepository` + `AuthRepositoryImpl` — Google Sign-In → Firebase credential → profile upsert
- `AuthInterceptor` — attaches Firebase ID token to every Retrofit request
- `LoginScreen` + `LoginViewModel` — Google Sign-In UI with loading/error states
- `NavGraph` — added `Screen.Login`, start destination logic: Login → Onboarding → Home
- `MainActivity` — auth state check gates Permission dialog

**Manual steps still required by developer:** See `FIREBASE_SETUP.md`
- Place `google-services.json` in `app/`
- Set `FIREBASE_PROJECT_ID` in `wrangler.jsonc`

---

### 1.2 User Profiles ✅ DONE (2026-05-04)
**Current state:** `userId` is `user_${btoa(phone).substring(0, 10)}` — not a real identity.

**What to do:**

Backend — new `users` collection in MongoDB:
```
{
  _id: userId,
  phone: string,
  name: string,
  upiId: string?,       // critical for settlement deep links
  avatarUrl: string?,
  createdAt: number,
  updatedAt: number
}
```

New API routes:
- `GET /api/me` — fetch own profile
- `PUT /api/me` — update name, upiId, avatarUrl
- `GET /api/users/search?phone=` — find users by phone (hashed lookup for privacy)

Android — new screens:
- `ProfileScreen` — edit name + UPI ID, shown after onboarding and in settings
- `ProfileViewModel` — calls `/api/me`
- Add `UserProfile` domain model + `ProfileRepository` interface + `ProfileRepositoryImpl`

**Acceptance criteria:** User can set display name and UPI ID. Name appears across the app instead of phone number.

---

### 1.3 Sync Conflict Resolution
**File:** `trackit-api/src/index.ts` — `POST /api/sync`  
**File:** `app/.../worker/SyncWorker.kt`

**Current state:** `bulkUpsert` blindly overwrites with `$set`. If two devices touch the same expense, last write wins with no awareness. No retry logic on the Android side for partial failures.

**What to do:**
- Add `updatedAt` timestamp to every expense mutation (already partially there via `syncedAt`)
- Upsert strategy: only update if incoming `updatedAt > existing updatedAt` (add to MongoDB filter)
- SyncWorker: implement exponential backoff — currently no retry logic
- SyncWorker: after sync, update `isSynced = true` on local Room records that succeeded
- Add `SyncStateStore` properly — it exists but isn't wired to real sync status

**Acceptance criteria:** Edit an expense on device A while offline. Sync from device B. Merge reflects latest `updatedAt`. No data loss.

---

### 1.4 Onboarding Completion Gate
**File:** `app/.../presentation/onboarding/OnboardingScreen.kt`

**Current state:** Onboarding exists but there's no step that captures the user's name or UPI ID. The profile is never set up.

**What to do:**
- Add a "Set up your profile" step in onboarding (name + UPI ID, optional but prompted)
- After profile step, call `PUT /api/me` to persist
- Gate `Screen.Home` on onboarding completion AND profile having at least a name

---

## Phase 2 — Group Splits, Android Only (Weeks 9–16)

> Validate the social feature before investing in iOS. Build the minimum that creates the debt-notification viral loop.

### 2.1 Backend: Groups, Splits, Settlements

**New MongoDB collections:**

```
groups: {
  _id: string,
  name: string,
  emoji: string?,
  createdBy: userId,
  members: [{ userId, name, phone, upiId }],  // denormalised for offline display
  createdAt: number
}

splits: {
  _id: string,
  groupId: string,
  description: string,
  totalAmount: number,
  currency: "INR",
  paidBy: userId,
  participants: [{ userId, amount, isPaid: false }],
  expenseId: string?,    // links to an existing tracked expense if SMS-detected
  createdAt: number
}

settlements: {
  _id: string,
  groupId: string,
  fromUserId: string,
  toUserId: string,
  amount: number,
  status: "pending" | "confirmed",
  createdAt: number,
  confirmedAt: number?
}
```

**New API routes to add to `trackit-api/src/index.ts`:**
```
POST   /api/groups              — create group
GET    /api/groups              — list user's groups
GET    /api/groups/:id          — group detail + members
POST   /api/groups/:id/members  — add member by phone
POST   /api/groups/:id/splits   — add a split expense
GET    /api/groups/:id/splits   — list splits
GET    /api/groups/:id/balances — computed: who owes whom (simplify debts algorithm)
POST   /api/groups/:id/settle   — record a settlement
PUT    /api/settlements/:id/confirm — counterparty confirms payment received
```

**Balance simplification algorithm** (implement in backend, not client):
- Collect all net balances per member
- Use greedy min-transactions algorithm (sort creditors/debtors, match largest)
- Return as `[{ from, to, amount }]` array

---

### 2.2 Android: Domain + Data Layer

New domain models:
```kotlin
// domain/model/Group.kt
data class Group(val id: String, val name: String, val emoji: String?, val members: List<GroupMember>, val createdAt: Long)

// domain/model/GroupMember.kt  
data class GroupMember(val userId: String, val name: String, val phone: String, val upiId: String?)

// domain/model/Split.kt
data class Split(val id: String, val groupId: String, val description: String, val totalAmount: Double, val paidBy: String, val participants: List<SplitParticipant>, val expenseId: String?, val createdAt: Long)

// domain/model/SplitParticipant.kt
data class SplitParticipant(val userId: String, val amount: Double, val isPaid: Boolean)

// domain/model/Balance.kt
data class Balance(val fromUserId: String, val toUserId: String, val amount: Double)
```

New Room entities: `GroupEntity`, `SplitEntity` (for offline cache)

New use cases:
- `CreateGroupUseCase`
- `AddSplitUseCase`
- `GetGroupBalancesUseCase`
- `SettleDebtUseCase`
- `LinkExpenseToSplitUseCase` — when user adds a split, optionally link to an SMS-detected expense

New repositories: `GroupRepository`, `SplitRepository`

---

### 2.3 Android: UI Screens

New screens to build (in `presentation/`):

**`GroupListScreen`**
- Shows all groups user is in
- Each group card: name, member count, "you owe X" or "owed X" summary
- FAB to create new group

**`GroupDetailScreen`**
- Member avatars + balances
- Split history list
- "Settle up" button per balance (see 2.4)
- FAB to add expense to group

**`AddSplitScreen`**
- Amount, description, who paid, split type (equal / custom)
- Member checkboxes
- Optional: "link to a recent SMS expense" — shows last 5 unlinked expenses as suggestions

**`BalanceSummaryScreen`**
- "You owe" section + "You are owed" section
- Simplified debt view (fewest transactions)

Add to `NavGraph.kt`:
```kotlin
data object GroupList   : Screen("groups")
data object GroupDetail : Screen("groups/{groupId}") { fun createRoute(id: String) = "groups/$id" }
data object AddSplit    : Screen("groups/{groupId}/add_split")
```

Add "Groups" tab to `TrackItBottomNav` (currently has Home, History, Analytics, Settings — add Groups between History and Analytics).

---

### 2.4 UPI Settlement Deep Link (The Killer Feature)

When a user taps "Settle up" against a balance:
1. Look up the creditor's `upiId` from their profile
2. Launch a UPI intent with pre-filled amount:

```kotlin
// In SettleUpScreen or BalanceSummaryScreen
val upiIntent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse(
        "upi://pay?pa=${creditor.upiId}&pn=${creditor.name}&am=${amount}&cu=INR&tn=TrackIt+settlement"
    )
}
startActivity(Intent.createChooser(upiIntent, "Pay via"))
```

This opens GPay / PhonePe / Paytm with amount and recipient pre-filled. User just taps confirm. After returning to app, prompt "Did you complete the payment?" → if yes, call `POST /api/settlements/:id/confirm`.

**This is the feature that makes TrackIt better than Splitwise for India.**

---

### 2.5 Viral Loop — "You Owe" Share Link

When a non-TrackIt user is added to a group:
- Generate a shareable link: `https://trackit.app/join/{groupId}?invited_by={userId}`
- Tapping the link opens a web view (or deep link to Play Store) showing what they owe
- This is the primary growth mechanic — the debtor installs the app

**Backend:** Add `GET /api/groups/:id/public-summary?token=` (no auth, token scoped to group, short TTL)

---

## Phase 3 — Kotlin Multiplatform Migration (Weeks 17–22)

> Don't change any behavior. Just make the business logic portable. Low risk.

### What to migrate to KMP shared module

Create a new Gradle module: `shared/`

**Move these packages to `shared/`:**
- `domain/model/` — all data classes (pure Kotlin, zero Android deps)
- `domain/repository/` — interfaces only
- `domain/usecase/` — all use cases (pure Kotlin + coroutines)
- `sms/SmsParser.kt` — pure regex, no Android dependencies (uses `java.util.regex`)
- `sms/ParsedTransaction.kt`

**Keep Android-only in `app/`:**
- `data/local/` — Room (use SQLDelight for KMP version)
- `data/remote/` — Retrofit (use Ktor for KMP version)
- `presentation/` — Compose (stays Android-only in Phase 3)
- `sms/SmsReceiver.kt` — Android BroadcastReceiver
- `worker/` — WorkManager
- `widget/` — Glance

**Tools:** `kotlin.multiplatform` plugin, `kotlinx.coroutines` multiplatform, `kotlinx.serialization` (replace Gson)

---

## Phase 4 — iOS App (Weeks 23–32)

### What iOS gets
- All split features (the main value prop)
- Manual expense entry
- Bank email parsing (forward-to-address flow as substitute for SMS)
- Home screen widget (WidgetKit)
- UPI deep links work on iOS too (GPay iOS app handles `upi://` scheme)

### What iOS doesn't get (and that's OK)
- Auto SMS detection — impossible, Apple doesn't allow it
- SMS inbox bulk import

### Tech approach
- SwiftUI for all iOS UI
- KMP `shared/` module compiled to XCFramework, imported into Xcode project
- `ViewModels` in Swift (`@Observable` + `async/await`) calling KMP use cases via `@MainActor`
- SQLDelight for shared DB schema, Room for Android / SQLDelight iOS driver
- Ktor for shared networking, Retrofit kept for Android if migration is too risky

### iOS-specific additions
- `SFSafariViewController` for onboarding email setup
- `UNUserNotificationCenter` for push notifications
- `NSContactsStore` for friend discovery (same as Android contacts permission)

---

## Technical Debt to Fix in Parallel

These are not features but will cause production incidents if ignored:

| Debt | File | Priority |
|---|---|---|
| Overlay banner commented out in `MainActivity` | `MainActivity.kt:107–123` | Low (per user request, leave commented) |
| `userId` derived from base64 of phone — collision risk for long phone numbers | `trackit-api/src/index.ts:81` | Fix in Phase 1.2 |
| No retry / backoff in `SyncWorker` | `worker/SyncWorker.kt` | Fix in Phase 1.3 |
| OTP `console.log` in production | `trackit-api/src/index.ts:65` | Fix in Phase 1.1 (blocker) |
| `LocationHelper` + location fields on `Expense` — feature not wired to any UI | `util/LocationHelper.kt` | Wire in AddExpenseScreen or remove |
| No error state handling in most ViewModels — UI silently does nothing on failure | various ViewModels | Fix as you touch each screen |

---

## Decisions & Constraints Log

- **No Flutter/React Native.** KMP preserves the existing Kotlin investment and avoids a full rewrite.
- **No custom payments.** UPI deep links to existing apps. TrackIt is never in the money flow.
- **India-first.** INR only for now. Multi-currency complicates split math significantly — defer.
- **Overlay permission banner left commented** in `MainActivity.kt` — was removed per earlier decision, do not re-enable without discussion.
- **MongoDB stays.** Don't migrate to PostgreSQL/PlanetScale despite it being better for relational split data — the migration cost outweighs the benefit at this stage. Use application-level joins.
- **Cloudflare Workers stays.** Add Durable Objects for WebSocket real-time if needed in Phase 2.

---

## Session Handoff Notes

If picking this up in a new session:
1. Read this file first
2. Check `PHASE_STATUS` below for current state
3. The backend is at `trackit-api/` — Hono + TypeScript
4. The Android app is at `app/src/main/java/com/trackit/expense/`
5. No iOS code exists yet — don't look for it

### PHASE_STATUS
```
Phase 1.1 — Google Sign-In   [x] Done 2026-05-04
Phase 1.2 — User profiles    [x] Done 2026-05-04
Phase 1.3 — Sync conflicts   [x] Done 2026-05-04
Phase 1.4 — Onboarding gate  [x] Done 2026-05-04
Phase 2.1 — Backend splits   [x] Done 2026-05-04
Phase 2.2 — Android domain   [x] Done 2026-05-04
Phase 2.3 — Android UI       [x] Done 2026-05-04
Phase 2.4 — UPI deep links   [x] Done 2026-05-04
Phase 2.5 — Viral share link [ ] Not started
Phase 3   — KMP migration    [ ] Not started
Phase 4   — iOS app          [ ] Not started
```
