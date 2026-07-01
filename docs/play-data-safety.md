# LuaNet Play Data safety draft

Use this to complete Play Console. Keep it consistent with the public privacy policy.

## Collection and sharing

LuaNet collects user data only for account, public tunnel, billing, ads, security, and app
functionality purposes. Data is encrypted in transit.

LuaNet does not sell user data. Data may be processed by Firebase, Google Play Billing,
AdMob/UMP, GitHub sign-in, ContentDB, Cloudflare, and NovaX infrastructure when the related
feature is used.

## Data types to disclose

### Personal info

- Email address: collected for Firebase email/password account and account contact.
- User IDs: collected as Firebase UID and provider identifiers for authentication, limits,
  billing entitlement, and account deletion.

Purpose: app functionality, account management, fraud prevention/security, purchases.

### Financial info

- Purchase history / purchase token: collected for Premium subscription verification.

Purpose: purchases, entitlement, fraud prevention/security.

### App activity

- App interactions / feature usage: tunnel hold/lease creation, active tunnel count,
  aggregate public access usage.

Purpose: app functionality, analytics in aggregate only, fraud prevention/security.

### App info and performance

- Crash logs only if the user opts in later. If Crashlytics is disabled for the release,
  answer as not collected.

Purpose: diagnostics, if enabled.

### Device or other IDs

- App-generated device/installation identifier.
- Advertising ID may be processed by AdMob for Free public-start ads.

Purpose: app functionality, account limits, advertising, fraud prevention/security.

## Data not collected by NovaX

Do not disclose these as server-collected by NovaX unless the implementation changes:

- world files;
- chat messages;
- player names;
- console logs;
- installed mods/games;
- local server settings;
- local backups.

## Deletion answers

- Provides account deletion in app: Yes.
- Provides account deletion web link: Yes.
- Web URL: `https://luanet.novaxhosting.com/delete-account.html`.
- Data deleted: account, tunnel allocations, entitlements, Firebase Auth user.
- Data retained: security/anti-abuse logs up to 30 days, and provider-side records under
  Google/Firebase/AdMob/Play retention rules.

## Security answers

- Data encrypted in transit: Yes.
- Users can request deletion: Yes.
- Independent security review: No, unless a review is actually completed.
