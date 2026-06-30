# NovaX deployment

The production topology keeps the REST API behind Cloudflare while FRP control and Luanti
UDP traffic remain DNS-only. FRPS uses host networking because it must bind a dynamic UDP
range; PostgreSQL is reachable only on the private Compose network.

## Host prerequisites

- HPE host address: `192.168.100.11`.
- Router forwards TCP `7443` and UDP `30000-30127` to the HPE host.
- `luanet-api.novaxhosting.com` is proxied by Cloudflare to the HTTPS reverse proxy.
- `tunnel.luanet.novaxhosting.com` and `play.luanet.novaxhosting.com` are DNS-only A/AAAA records.
- A publicly trusted certificate for `tunnel.luanet.novaxhosting.com` is installed as
  `secrets/frps-fullchain.pem` and `secrets/frps-private-key.pem`.
- Firebase service-account JSON is installed as `secrets/firebase-service-account.json`.

Do not use a Cloudflare Origin CA certificate for FRPS: Android FRPC must trust the issuing
chain directly. Standard Cloudflare proxying does not carry this arbitrary TCP/UDP traffic;
doing so would require [Cloudflare Spectrum](https://developers.cloudflare.com/spectrum/),
which LuaNet does not depend on.

## Start and verify

```bash
cp .env.example .env
# Fill .env and place secret files, then:
docker compose pull
docker compose build api
docker compose up -d
curl --fail http://127.0.0.1:9080/healthz
docker compose logs --tail=100 api frps
```

`frps.toml` limits clients to `30000-30127`, one proxy per control connection, TLS-only
control traffic, and the lease-authorizer plugin. The plugin accepts only the leased UDP
port and rewrites every accepted proxy to a server-side `10MB` bandwidth limit.

The first 100 allocations are active capacity. The control plane intentionally leaves 28
ports for ten-minute grace periods and operations. FRPS exposes metrics only on host-local
`127.0.0.1:7500/metrics`.

## Firewall

Allow inbound TCP 7443 and UDP 30000-30127. Keep TCP 9080, 7500, and PostgreSQL private.
Rate-limit TCP connection attempts to 7443 at the host firewall, but do not apply a low
packet-per-second limit to game UDP because it would break normal Luanti traffic.

Back up `data/postgres`, encrypted secret material, DNS configuration, and certificate
renewal state. Worlds are never present on NovaX and therefore are not part of this backup.
