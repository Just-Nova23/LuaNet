# LuaNet legal/compliance audit

Last reviewed: 1 July 2026

This is an engineering compliance checklist, not a legal opinion. Final production launch
should still be reviewed by the Play Console account owner or counsel.

## Implemented

- App package ID is stable: `net.novax.luanet`.
- LAN hosting works without account and without ads.
- Public access and Premium require Firebase-backed account authentication.
- In-app account deletion exists in Account > Delete NovaX account.
- Backend account deletion removes tunnel allocations, entitlement records, and Firebase Auth
  user records.
- Security/anti-abuse retention is limited to 30 days.
- Worlds, chat, player names, console logs, and installed content are not uploaded to NovaX.
- UMP consent is requested before AdMob use.
- AdMob is not used for LAN hosting.
- Teen/13+ positioning is documented; LuaNet is not directed to children.
- Code licensing is documented: original LuaNet Apache-2.0; Luanti-derived/native engine
  work under LGPL-2.1-or-later; frp under Apache-2.0.
- Play release uses AAB, Play App Signing, and a private upload key.
- Public website now has Privacy, Terms, Account deletion, Cookie notice, and Licenses pages.

## Play Console values

- Category: Tools.
- Pricing: Free.
- Target audience: 13+, 16-17, 18+; not designed for children.
- Ads: Yes.
- In-app purchases/subscriptions: Yes.
- Data encrypted in transit: Yes.
- Data deletion: Yes, in-app and web.
- Privacy policy URL: `https://luanet.novaxhosting.com/privacy`.
- Account deletion URL: `https://luanet.novaxhosting.com/delete-account`.

## Remaining before public production

- Confirm the public legal name and jurisdiction of the Play Console developer/controller.
- Confirm `luanet@novaxhosting.com` mailbox exists, is monitored, and can process deletion
  requests.
- Complete Play Data safety exactly as documented in `docs/play-data-safety.md`.
- Complete Play content rating questionnaire.
- Complete foreground service declaration for user-started local Luanti server hosting.
- Verify AdMob UMP consent form in EEA/UK test geography.
- Verify Play Billing sandbox for `luanet_premium_monthly` and `luanet_premium_yearly`.
- Verify NovaX server-side purchase verification has Play Developer API permissions.
- Keep Luanti/frp/source notices accessible in-app and from the website.
- If Crashlytics or any analytics are enabled later, update privacy policy, Data safety, and
  consent disclosures before release.

## Not covered

No static checklist can guarantee compliance with every law in every country. LuaNet should
not be launched in jurisdictions that require local registration, age gates, tax handling,
consumer-law language, or hosting-provider obligations that NovaX has not reviewed.
