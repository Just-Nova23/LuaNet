#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRP_VERSION="${FRP_VERSION:-v0.69.0}"
FRP_COMMIT="${FRP_COMMIT:-c8c1e5116cdeb0f8edf51aab946917c6ce9dae14}"
REPOSITORY="${FRP_REPOSITORY:-https://github.com/fatedier/frp.git}"
CACHE="$ROOT/.cache/frp.git"
WORK_ROOT="$ROOT/build/work"
OUTPUT="$ROOT/build/android-arm64"
FRPC_GOOS="${FRPC_GOOS:-linux}"
FRPC_GOARCH="${FRPC_GOARCH:-arm64}"

if ! command -v go >/dev/null 2>&1; then
  echo "Go is required to build frpc" >&2
  exit 66
fi

if [[ ! -d "$CACHE" ]]; then
  git init --bare "$CACHE"
fi
git -C "$CACHE" fetch --force --depth 1 "$REPOSITORY" "refs/tags/$FRP_VERSION:refs/tags/$FRP_VERSION"
ACTUAL="$(git -C "$CACHE" rev-parse "refs/tags/$FRP_VERSION^{}")"
if [[ "$ACTUAL" != "$FRP_COMMIT" ]]; then
  echo "source verification failed: expected $FRP_COMMIT, got $ACTUAL" >&2
  exit 65
fi

mkdir -p "$WORK_ROOT"
SOURCE="$(mktemp -d "$WORK_ROOT/frp.XXXXXX")"
trap 'rm -rf "$SOURCE"' EXIT
git clone --quiet --no-checkout "$CACHE" "$SOURCE"
git -C "$SOURCE" checkout -q --detach "$FRP_COMMIT"
mkdir -p "$SOURCE/web/frpc/dist"
printf '<!doctype html><title>LuaNet frpc</title>\n' > "$SOURCE/web/frpc/dist/index.html"

mkdir -p "$OUTPUT"
(
  cd "$SOURCE"
  env CGO_ENABLED=0 GOOS="$FRPC_GOOS" GOARCH="$FRPC_GOARCH" \
    go build -trimpath -ldflags="-s -w -buildid=" -o "$OUTPUT/libfrpc.so" ./cmd/frpc
)
chmod 0755 "$OUTPUT/libfrpc.so"
sha256sum "$OUTPUT/libfrpc.so" > "$OUTPUT/SHA256SUMS"
echo "built $OUTPUT/libfrpc.so"
