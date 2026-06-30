# Architecture

## Trust boundaries

The Android device owns all worlds and content. NovaX only authenticates users,
allocates public UDP ports, verifies subscriptions, and relays encrypted tunnel
traffic. FRPS never receives filesystem access to a world.

```text
Luanti client --UDP--> NovaX FRPS --TLS/FRP--> Android frpc --UDP--> Luanti engine
                              ^
                              |
                     Go control-plane + PostgreSQL
```

`luanet-api.novaxhosting.com` is HTTP behind Cloudflare.
`tunnel.luanet.novaxhosting.com:7443` and
`play.luanet.novaxhosting.com:30000-30127/udp` are DNS-only.

## Android runtime

The Compose application owns profile configuration, package installation,
backups, auth, ads, and billing. A foreground orchestrator binds up to five
engine-slot services in separate Android processes. Each slot loads exactly one
versioned headless Luanti library and communicates through a stable Binder/JNI
contract. The orchestrator supervises and terminates the FRP child process with the
foreground service.

The app stores each profile below its own app-scoped directory. Games and mods
are copied into the profile so backups are self-contained and changes cannot
leak between servers.

## Control plane

Firebase ID tokens authenticate public API calls. A tunnel hold reserves a port
briefly while the app displays an interstitial. Activation converts the hold
into a lease and returns a short-lived FRP credential. The FRP HTTP plugin
rejects unknown sessions, non-UDP proxies, ports outside the assigned lease, and
expired sessions.

Free leases expire after four hours. Premium leases are rolling leases extended
while the Google Play entitlement is valid. A released port remains reserved
for ten minutes before returning to the pool.

## Data minimization

PostgreSQL stores Firebase UID, installation identifier, entitlement state,
lease/port state, and security audit timestamps.
Player names, chat, packet contents, worlds, games, and mods are never accepted
by the API. Expired audit records are deleted after 30 days.
