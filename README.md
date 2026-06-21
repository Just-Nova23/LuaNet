# LuaNet

LuaNet turns an Android phone into a Luanti server host. Worlds run locally on
the phone; an authenticated NovaX relay exposes them over UDP when public
access is enabled.

## Repository layout

- `android/` — Kotlin/Compose Android application (`net.novax.luanet`).
- `control-plane/` — Go API, tunnel lease allocator, billing boundary, and FRP plugin.
- `engine/` — version catalog, LGPL native bridge, patches, and reproducible Luanti builds.
- `infra/` — Docker Compose and NovaX deployment configuration.
- `docs/` — architecture, privacy, release, and operating documentation.

## Product rules

- Android 10+ and ARM64 only.
- Luanti 5.0 through 5.16, one maintained patch release per minor.
- LAN hosting works offline without an account or ads.
- Free public sessions last four hours and stop after 15 idle minutes.
- Premium allows five active sessions per account and removes ads and timeouts.
- No world, chat, mod, or player data is uploaded to the control plane.

## Development status

The project is under active construction. Infrastructure files are safe
defaults and do not contain production credentials. Do not expose FRPS until
Moldtelecom forwarding, DNS, TLS, and load testing are complete.

## License

Original LuaNet code is Apache-2.0. Code derived from or linked into Luanti is
LGPL-2.1-or-later and is kept under `engine/`. See [NOTICE](NOTICE).

