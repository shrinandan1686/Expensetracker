# TrackIt Privacy Policy

_Last updated: 1 September 2026_

TrackIt is a personal expense tracker distributed as an open-source APK. This
document describes exactly what it reads, what leaves your device, and what you can
delete. The code that does all of this is in this repository — every claim below is
checkable against it.

---

## Who runs TrackIt

There is no company behind TrackIt and no analytics business model. It is a personal
project, self-hosted by whoever deploys it. If you installed a build published from
this repository, the backend is a Cloudflare Worker and a MongoDB database operated
by the person who gave you the APK.

---

## What TrackIt reads on your device

### SMS messages (`READ_SMS`, `RECEIVE_SMS`)

This is the permission that matters most, so to be specific:

- TrackIt reads SMS messages to find bank and UPI transaction alerts and turn them
  into expense records. This is the app's core function.
- Parsing happens **entirely on your device**, in `SmsParser.kt`. Messages are not
  uploaded for processing.
- Only messages that parse as a transaction produce a record. OTPs and promotional
  messages are explicitly discarded.
- The **raw text of a matched transaction SMS is stored** in the local database
  (`raw_sms`) so you can see where an expense came from, and it **is included when
  an expense syncs to the server**. If you do not want bank SMS text on the server,
  do not enable sync.
- TrackIt never reads, stores or transmits messages that are not matched as
  transactions.

### Location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`)

Optional. When granted, an approximate location is attached to an expense you add
manually, so you can remember where you spent. Denying it does not affect anything
else. Location is stored with the expense and syncs with it.

### Notifications, boot, battery optimisation

Used to show budget alerts and weekly summaries, to re-schedule background sync
after a restart, and to ask for a battery-optimisation exemption so SMS detection
keeps working. None of these read personal data.

---

## What leaves your device

Only if you sign in. Signed out, TrackIt is entirely local.

| Data | Where it goes | Why |
|---|---|---|
| Google account name, email, profile photo URL | Firebase Auth + the TrackIt backend | Identifies your account |
| Expenses: amount, merchant, category, account label, notes, timestamps, raw SMS text, location | TrackIt backend (MongoDB) | Sync and restore across devices |
| Budgets | TrackIt backend | Sync |
| Group names, members, splits, settlements, UPI ID | TrackIt backend | Shared with the other members of that group |
| Your UPI ID | Visible to other members of groups you join | So they can pay you back |

**Authentication** is Google Sign-In via Firebase. TrackIt never sees or stores your
Google password. The backend verifies your identity from a signed token; it holds no
credentials of yours.

**Payments:** TrackIt never processes money. "Settle up" builds a `upi://pay` link
and hands it to your UPI app. No payment details pass through TrackIt.

---

## What TrackIt does not do

- No advertising, ad SDKs or ad identifiers.
- No analytics or crash-reporting SDK.
- No selling, renting or sharing of your data with third parties.
- No tracking across other apps or websites.
- No access to contacts, camera, microphone, photos, call logs or files.

The only third parties involved are the infrastructure the app runs on: Google
(Firebase Auth), Cloudflare (the API), and MongoDB Atlas (the database).

---

## Retention and deletion

Expenses stay until you delete them. Deleting one removes it from your device and,
on the next sync, from the server.

**Deleting your account** — Settings → Account → Delete Account — permanently
erases your profile, every expense and every budget, both locally and on the server,
and deletes your Firebase user. It cannot be undone.

One deliberate exception: **shared group splits and settlements are kept**, and you
are removed from those groups instead. Deleting them would silently rewrite other
members' balances and make money genuinely owed disappear from their side of the
ledger. Your name remains visible to those members in the split history.

You can export everything as CSV at any time from Settings → Export Data.

---

## Security

- All traffic to the backend uses HTTPS. Release builds refuse cleartext outright.
- Every API request is authenticated with a short-lived Firebase ID token, verified
  against Google's public keys on the server.
- The API enforces per-user authorization: you can only read and write your own
  records, and group data only for groups you are a member of.
- Your local database is excluded from Android cloud backups and device-to-device
  transfer, so your transaction history is not copied into a Google backup.
- The local database is **not encrypted at rest** beyond Android's own full-disk
  encryption. On a rooted or compromised device, another process with root could
  read it.

No system is perfect. If you find a security problem, please open an issue in this
repository.

---

## Children

TrackIt is not directed at children under 13 and does not knowingly collect their
data.

---

## Changes

Material changes will be noted in this file and in the release notes. The git
history of this document is the changelog.

---

## Contact

Open an issue at https://github.com/shrinandan1686/Expensetracker/issues
