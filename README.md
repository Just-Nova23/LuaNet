# LuaNet

LuaNet is an Android app for hosting Luanti servers directly on a phone.

The world, mods, players and console stay on the device. LAN hosting works
without an account. When a server needs to be reachable from outside the local
network, LuaNet can open an external UDP link through the LuaNet control plane.

## What works

- Native Android app written in Kotlin and Jetpack Compose.
- Server profiles with their own world, game, mods, settings and backups.
- Luanti engine management with the latest engine in the base app and older
  engines installable on demand.
- ContentDB browsing, unified search, package details, screenshots and install.
- ZIP import for worlds, games, mods and modpacks.
- Live console, player list, moderation actions and offline auth database edits.
- Manual backups with restore/delete support, including profile configuration.
- Optional Auto off timer per server.
- Account, Premium, AdMob and public-link plumbing for Play builds.

## Repository layout

- `android/` — Android app (`net.novax.luanet`).
- `control-plane/` — Go API and tunnel lease service.
- `engine/` — Luanti engine catalog, bridge and build tooling.
- `infra/` — deployment templates for the control plane.
- `docs/` — architecture, release and compliance notes.

## Development

Build the debug APK:

```bash
cd android
./gradlew :app:assembleDebug
```

Build a release app bundle:

```bash
cd android
./gradlew :app:bundleRelease
```

Install on a connected emulator or device:

```bash
cd android
./gradlew :app:installDebug
```

Production builds require the normal Android/Firebase/Play configuration files
and signing properties. Do not commit private keys, Firebase admin credentials,
Play API keys or server deployment secrets.

## Privacy boundary

LuaNet does not upload worlds, chat logs, mod files or player names to the
control plane. Public-link infrastructure stores only the account/device and
lease metadata needed to operate the service and prevent abuse.

## License

Original LuaNet code is Apache-2.0. Luanti-derived engine and bridge code keeps
the upstream LGPL-2.1-or-later licensing. See [NOTICE](NOTICE).
