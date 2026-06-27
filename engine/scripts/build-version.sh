#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <version>" >&2
  exit 64
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an installed Android NDK}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$1"
readarray -t META < <(python3 - "$ROOT/catalog.json" "$VERSION" <<'PY'
import json, sys
catalog = json.load(open(sys.argv[1]))
item = next((x for x in catalog["engines"] if x["version"] == sys.argv[2]), None)
if not item:
    raise SystemExit(f"unsupported engine {sys.argv[2]}")
print(catalog["repository"])
print(item["tag"])
print(item["commit"])
print(item["library"])
PY
)
REPOSITORY="${META[0]}"
TAG="${META[1]}"
COMMIT="${META[2]}"
LIBRARY="${META[3]}"
CACHE="$ROOT/.cache/luanti.git"
WORK_ROOT="$ROOT/build/work"
OUTPUT="$ROOT/build/artifacts/$VERSION"

if [[ ! -d "$CACHE" ]]; then
  git init --bare "$CACHE"
  git -C "$CACHE" remote add origin "$REPOSITORY"
fi
git -C "$CACHE" remote set-url origin "$REPOSITORY"
for attempt in 1 2 3; do
  if git -C "$CACHE" fetch --force --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG"; then
    break
  fi
  if [[ "$attempt" == 3 ]]; then
    echo "failed to fetch Luanti $TAG after $attempt attempts" >&2
    exit 66
  fi
  sleep "$((attempt * 5))"
done
ACTUAL="$(git -C "$CACHE" rev-parse "refs/tags/$TAG^{}")"
if [[ "$ACTUAL" != "$COMMIT" ]]; then
  echo "source verification failed: expected $COMMIT, got $ACTUAL" >&2
  exit 65
fi

mkdir -p "$WORK_ROOT"
SOURCE="$(mktemp -d "$WORK_ROOT/${VERSION}.XXXXXX")"
trap 'rm -rf "$SOURCE"' EXIT
git clone --quiet --no-checkout "$CACHE" "$SOURCE"
git -C "$SOURCE" checkout -q --detach "$COMMIT"
git -C "$SOURCE" submodule update --init --depth 1
BUILD="$SOURCE/.luanet-build"

python3 "$ROOT/scripts/prepare-source.py" "$SOURCE" \
  --bridge "$ROOT/bridge/src/luanet_engine.cpp" --library "$LIBRARY"

DEPS="$SOURCE/android/native/deps"
if [[ ! -d "$DEPS/arm64-v8a" ]]; then
  mkdir -p "$DEPS"
  DEPS_URL="https://github.com/luanti-org/luanti_android_deps/releases/download/latest/deps-lite.zip"
  curl --fail --location --retry 3 "$DEPS_URL" --output "$SOURCE/deps-lite.zip"
  unzip -q "$SOURCE/deps-lite.zip" -d "$DEPS"
fi
ABI_DEPS="$DEPS/arm64-v8a"
SQLITE_DIR="$ABI_DEPS/SQLite"

cmake -S "$SOURCE" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DSQLITE3_INCLUDE_DIR="$SQLITE_DIR/include" -DSQLITE3_LIBRARY="$SQLITE_DIR/libsqlite3.a" \
  -DSQLite3_INCLUDE_DIR="$SQLITE_DIR/include" -DSQLite3_LIBRARY="$SQLITE_DIR/libsqlite3.a" \
  -DBUILD_CLIENT=OFF -DBUILD_SERVER=ON -DBUILD_UNITTESTS=OFF -DBUILD_BENCHMARKS=OFF \
  -DENABLE_CURSES=OFF -DENABLE_CURL=ON -DENABLE_FREETYPE=OFF -DENABLE_GETTEXT=OFF -DENABLE_SOUND=OFF \
  -DENABLE_POSTGRESQL=OFF -DENABLE_LEVELDB=OFF -DENABLE_REDIS=OFF \
  -DENABLE_PROMETHEUS=OFF -DENABLE_SPATIAL=OFF -DENABLE_UPDATE_CHECKER=OFF
cmake --build "$BUILD"

ARTIFACT="$(find "$BUILD" -name "lib${LIBRARY}.so" -print -quit)"
test -n "$ARTIFACT"
mkdir -p "$OUTPUT"
install -m 0644 "$ARTIFACT" "$OUTPUT/lib${LIBRARY}.so"
install -m 0644 "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so "$OUTPUT/" 2>/dev/null || true
sha256sum "$OUTPUT"/*.so > "$OUTPUT/SHA256SUMS"
echo "built $OUTPUT/lib${LIBRARY}.so"
