# LuaNet Play Console release checklist

## App setup

- App name: `LuaNet`
- Package name: `net.novax.luanet`
- App type: App
- Pricing: Free
- Category: Tools
- Target audience: 13+, not designed for children
- Play App Signing: enabled
- Automatic Play protection: enabled

## Monetization

Create two auto-renewing subscription products. The product IDs must match the app and backend:

| Product ID | User-facing plan | Price |
| --- | --- | --- |
| `luanet_premium_monthly` | LuaNet Premium Monthly | EUR 1.99/month |
| `luanet_premium_yearly` | LuaNet Premium Yearly | EUR 19.10/year |

Each subscription needs an active base plan and offer so Billing Library can obtain a subscription offer token.

## AdMob

LuaNet needs two different AdMob identifiers:

- App ID format: `ca-app-pub-...~...`
- Interstitial ad unit format: `ca-app-pub-.../...`

Do not use the app ID as the interstitial ad unit. Release builds intentionally fail when the interstitial ID is missing, test-only, or has the wrong `~` format.

## First internal testing release

Upload the signed release bundle from:

`android/app/build/outputs/bundle/release/app-release.aab`

After the first upload, open Play Console > Release > Setup > App integrity. Copy the
App signing key certificate SHA-1 and SHA-256 fingerprints into the Firebase Android app
settings for package `net.novax.luanet`. Firebase Google sign-in must trust the Play app
signing certificate, not only the local upload certificate.

Suggested release notes:

```text
Initial LuaNet internal testing build.

- Local Luanti server hosting on Android.
- Multiple engine versions bundled in the app.
- ContentDB browsing and downloads.
- Firebase account login.
- NovaX public access integration.
- Ad-supported Free public access flow and Premium purchase flow.
```

## Before production

- Verify Firebase Google/GitHub/email login.
- Verify AdMob consent and interstitial on a real device.
- Verify Play Billing sandbox purchases for both subscription products.
- Verify NovaX server-side purchase verification.
- Verify public tunnel start/stop and lease expiry.
- Complete Play Data Safety, Ads, Content rating, and Foreground service declarations.
