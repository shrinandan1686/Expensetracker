# Firebase Setup Guide

One-time manual steps required before the app will build and run.
Everything else in this repo is already implemented.

---

## Step 1 — Create a Firebase Project

1. Go to https://console.firebase.google.com
2. Click **Add project** → name it `trackit` (or anything you like)
3. Disable Google Analytics if you don't need it → **Create project**

---

## Step 2 — Enable Google Sign-In

1. In your Firebase project: **Authentication → Sign-in method**
2. Click **Google** → toggle **Enable** → set your support email → **Save**

---

## Step 3 — Add the Android App

1. In the Firebase console home: click **Add app → Android**
2. **Android package name:** `com.trackit.expense`
3. **App nickname:** TrackIt (optional)
4. **SHA-1 fingerprint:** Required for Google Sign-In. Get it by running:
   ```bash
   # Debug keystore (for development):
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   Copy the `SHA1:` line and paste it in the Firebase console.
5. Click **Register app**
6. **Download `google-services.json`** and place it at:
   ```
   Expensetracker/app/google-services.json
   ```
   (Same directory as `app/build.gradle.kts`)

   > This file is **gitignored on purpose** — it carries your project's API key,
   > OAuth client IDs and signing-certificate hash. `app/google-services.json.template`
   > shows the expected shape; each developer generates their own from the console.

---

## Step 4 — Get Your Firebase Project ID

1. In Firebase console: **Project settings (gear icon) → General**
2. Copy the **Project ID** (looks like `trackit-a1b2c` or whatever you named it)

---

## Step 5 — Update the Backend Config

Open `trackit-api/wrangler.jsonc` and replace the placeholder:
```jsonc
"FIREBASE_PROJECT_ID": "your-firebase-project-id"  // ← replace this
```

For deployed Workers (production), set it as a secret instead:
```bash
cd trackit-api
npx wrangler secret put FIREBASE_PROJECT_ID
# Enter your project ID when prompted
```

Also set your MongoDB connection string if you haven't already:
```bash
npx wrangler secret put MONGODB_URI
```

For local development, copy `trackit-api/.dev.vars.example` to `trackit-api/.dev.vars`
and put the connection string there. `.dev.vars` is gitignored — never commit it.

---

## Step 6 — Regenerate Worker TypeScript Types (optional)

After changing `wrangler.jsonc`, regenerate the type definitions:
```bash
cd trackit-api
npm run cf-typegen
```

---

## Verification Checklist

- [ ] `app/google-services.json` exists
- [ ] Google Sign-In is enabled in Firebase Authentication
- [ ] SHA-1 of your debug keystore is registered in Firebase
- [ ] `FIREBASE_PROJECT_ID` is set in `wrangler.jsonc` (local dev) and as a Wrangler secret (production)
- [ ] `MONGODB_URI` is in `trackit-api/.dev.vars` (local dev) and a Wrangler secret (production)
- [ ] `trackit.api.baseUrl` is set in `local.properties` if you're running on a physical device
- [ ] App builds without errors (`./gradlew assembleDebug`)
- [ ] Tapping "Continue with Google" shows the account picker
- [ ] After sign-in, the app navigates to Onboarding (first time) or Home (returning user)

---

## Troubleshooting

**"Sign in failed" immediately after tapping the button:**
→ SHA-1 fingerprint is missing or wrong in Firebase console. Re-add it (Step 3).

**Build error: `R.string.default_web_client_id` not found:**
→ `google-services.json` is missing from `app/` directory.

**Backend returns 401 after sign-in:**
→ `FIREBASE_PROJECT_ID` in `wrangler.jsonc` doesn't match the project that issued the token.

**Build error: `com.google.android.gms.base.R.drawable.googleg_standard_color_18` not found:**
→ `play-services-auth` dependency is not synced. Run Gradle sync in Android Studio.
