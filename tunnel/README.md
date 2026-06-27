# FRPC Android artifact

LuaNet runs the public tunnel with NovaX FRPS 0.69. Android packages the FRP client as
`libfrpc.so` so it is extracted into `applicationInfo.nativeLibraryDir` and can be launched
with `ProcessBuilder`.

Requirements: Go 1.25+, Git.

```bash
tunnel/scripts/build-frpc-android.sh
FRPC_ARTIFACT=tunnel/build/android-arm64/libfrpc.so engine/scripts/sync-android-artifacts.sh
```

The script fetches the official `fatedier/frp` tag `v0.69.0`, verifies the dereferenced tag
commit, and cross-compiles `./cmd/frpc` for `GOOS=android GOARCH=arm64`.
