# LuaNet control plane

The control plane allocates UDP ports and validates FRP sessions. It does not
proxy HTTP game traffic or accept world/player data.

## Local development

Run PostgreSQL and apply `migrations/*.sql`, then start the API with the values
from `.env.example`. When `FIREBASE_PROJECT_ID` is empty, development requests
use `Authorization: Bearer dev:<uid>`.

```bash
curl -X POST http://localhost:8080/v1/tunnel-holds \
  -H 'Authorization: Bearer dev:alice' \
  -H 'Content-Type: application/json' \
  -d '{"device_id":"phone-1","profile_id":"world-1"}'
```

Production refuses to start without a Firebase project. Google Play purchase
verification is enabled only when application-default credentials are present.
Account deletion first revokes all LuaNet allocations and entitlements, then removes the
Firebase Auth user.

## FRP plugin

Configure FRPS to call `POST /internal/frp-plugin` for `Login`, `NewProxy`,
`Ping`, `NewWorkConn`, and `CloseProxy`. The client must set its FRP `user` to
the lease ID and send `session_token` in user metadata. `NewProxy` is accepted
only when it requests the exact assigned UDP port. FRP 0.69 sends `NewProxy` fields at the
top level of `content`; the plugin also accepts the older nested shape for test/backward
compatibility and forces the bandwidth limit into server mode.
