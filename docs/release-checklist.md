# Public beta release gate

The beta is not releasable until every item below is evidenced in the release record.

- Moldtelecom forwards TCP 7443 and UDP 30000-30127 to `192.168.100.11`.
- API is Cloudflare-proxied; tunnel and play records are DNS-only.
- FRPS presents a publicly trusted certificate and refuses non-TLS control connections.
- `luanet@novaxhosting.com` exists and is monitored.
- All 17 engine sources match `engine/catalog.json`; builds and smoke tests pass on ARM64.
- One world can be joined from the same phone, LAN, mobile data, and NovaX.
- Five different engines run simultaneously without process-state collision.
- 100 concurrent UDP tunnels pass uniqueness and cross-traffic tests; 28 ports stay reserved.
- Free four-hour expiry, optional Auto off, port grace, entitlement loss, and simulated clocks pass.
- Malicious ZIP, cyclic dependency, incompatible ContentDB package, backup restore, and engine upgrade pass.
- Firebase deletion, UMP consent, AdMob test ads, Play Billing sandbox, and Data Safety are verified.
- Screen-off, Wi-Fi/mobile handover, memory pressure, low battery, and critical thermal stop pass on real devices.
- LGPL corresponding sources/notices and this privacy policy are linked from the app listing.

