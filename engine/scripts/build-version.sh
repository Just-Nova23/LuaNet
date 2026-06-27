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
IRRLICHT_REPOSITORY="https://github.com/minetest/irrlicht.git"
IRRLICHT_TAG="1.9.0mt0"
IRRLICHT_COMMIT="03ad637114ffb255f5b9d36c204072e8d263022f"
IRRLICHT_CACHE="$ROOT/.cache/irrlichtmt.git"
IRRLICHT_ROOT="$ROOT/build/legacy-irrlicht/$IRRLICHT_COMMIT"
IRRLICHT_STUB_DIR="$ROOT/build/legacy-irrlicht/android-arm64"
IRRLICHT_STUB_LIB="$IRRLICHT_STUB_DIR/libIrrlicht.a"
if [[ ! -d "$IRRLICHT_ROOT/include" ]]; then
  if [[ ! -d "$IRRLICHT_CACHE" ]]; then
    git init --bare "$IRRLICHT_CACHE"
    git -C "$IRRLICHT_CACHE" remote add origin "$IRRLICHT_REPOSITORY"
  fi
  git -C "$IRRLICHT_CACHE" remote set-url origin "$IRRLICHT_REPOSITORY"
  git -C "$IRRLICHT_CACHE" fetch --force --depth 1 origin "refs/tags/$IRRLICHT_TAG:refs/tags/$IRRLICHT_TAG"
  IRRLICHT_ACTUAL="$(git -C "$IRRLICHT_CACHE" rev-parse "refs/tags/$IRRLICHT_TAG^{}")"
  if [[ "$IRRLICHT_ACTUAL" != "$IRRLICHT_COMMIT" ]]; then
    echo "Irrlicht header verification failed: expected $IRRLICHT_COMMIT, got $IRRLICHT_ACTUAL" >&2
    exit 67
  fi
  IRRLICHT_SOURCE="$(mktemp -d "$WORK_ROOT/irrlicht.XXXXXX")"
  git clone --quiet --no-checkout "$IRRLICHT_CACHE" "$IRRLICHT_SOURCE"
  git -C "$IRRLICHT_SOURCE" checkout -q --detach "$IRRLICHT_COMMIT"
  mkdir -p "$IRRLICHT_ROOT"
  cp -a "$IRRLICHT_SOURCE/include" "$IRRLICHT_ROOT/"
  rm -rf "$IRRLICHT_SOURCE"
fi
if [[ ! -f "$IRRLICHT_STUB_LIB" ]]; then
  TOOLCHAIN_BIN="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d -print -quit)/bin"
  mkdir -p "$IRRLICHT_STUB_DIR"
  printf 'void luanet_irrlicht_stub(void) {}\n' > "$IRRLICHT_STUB_DIR/irrlicht_stub.c"
  "$TOOLCHAIN_BIN/aarch64-linux-android29-clang" -c "$IRRLICHT_STUB_DIR/irrlicht_stub.c" -o "$IRRLICHT_STUB_DIR/irrlicht_stub.o"
  "$TOOLCHAIN_BIN/llvm-ar" rcs "$IRRLICHT_STUB_LIB" "$IRRLICHT_STUB_DIR/irrlicht_stub.o"
fi

ZSTD_ARGS=()
if grep -q 'find_package(Zstd' "$SOURCE/src/CMakeLists.txt"; then
  TOOLCHAIN_BIN="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d -print -quit)/bin"
  ZSTD_REPOSITORY="https://github.com/facebook/zstd.git"
  ZSTD_TAG="v1.5.7"
  ZSTD_COMMIT="f8745da6ff1ad1e7bab384bd1f9d742439278e99"
  ZSTD_CACHE="$ROOT/.cache/zstd.git"
  ZSTD_ROOT="$ROOT/build/android-deps/zstd-$ZSTD_COMMIT-arm64"
  ZSTD_LIBRARY="$ZSTD_ROOT/lib/libzstd.a"
  if [[ ! -f "$ZSTD_LIBRARY" ]]; then
    if [[ ! -d "$ZSTD_CACHE" ]]; then
      git init --bare "$ZSTD_CACHE"
      git -C "$ZSTD_CACHE" remote add origin "$ZSTD_REPOSITORY"
    fi
    git -C "$ZSTD_CACHE" remote set-url origin "$ZSTD_REPOSITORY"
    git -C "$ZSTD_CACHE" fetch --force --depth 1 origin "refs/tags/$ZSTD_TAG:refs/tags/$ZSTD_TAG"
    ZSTD_ACTUAL="$(git -C "$ZSTD_CACHE" rev-parse "refs/tags/$ZSTD_TAG^{}")"
    if [[ "$ZSTD_ACTUAL" != "$ZSTD_COMMIT" ]]; then
      echo "Zstd verification failed: expected $ZSTD_COMMIT, got $ZSTD_ACTUAL" >&2
      exit 68
    fi
    ZSTD_SOURCE="$(mktemp -d "$WORK_ROOT/zstd.XXXXXX")"
    git clone --quiet --no-checkout "$ZSTD_CACHE" "$ZSTD_SOURCE"
    git -C "$ZSTD_SOURCE" checkout -q --detach "$ZSTD_COMMIT"
    make -s -C "$ZSTD_SOURCE/lib" -j"$(nproc)" libzstd.a \
      CC="$TOOLCHAIN_BIN/aarch64-linux-android29-clang" \
      AR="$TOOLCHAIN_BIN/llvm-ar" \
      RANLIB="$TOOLCHAIN_BIN/llvm-ranlib" \
      CFLAGS="-fPIC -O2"
    mkdir -p "$ZSTD_ROOT/include" "$ZSTD_ROOT/lib"
    install -m 0644 "$ZSTD_SOURCE/lib/libzstd.a" "$ZSTD_LIBRARY"
    install -m 0644 "$ZSTD_SOURCE/lib/zstd.h" "$ZSTD_ROOT/include/"
    install -m 0644 "$ZSTD_SOURCE/lib/zdict.h" "$ZSTD_ROOT/include/"
    install -m 0644 "$ZSTD_SOURCE/lib/zstd_errors.h" "$ZSTD_ROOT/include/"
    rm -rf "$ZSTD_SOURCE"
  fi
  ZSTD_ARGS=(-DZSTD_INCLUDE_DIR="$ZSTD_ROOT/include" -DZSTD_LIBRARY="$ZSTD_LIBRARY")
fi

cmake -S "$SOURCE" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DIRRLICHT_INCLUDE_DIR="$IRRLICHT_ROOT/include" -DIRRLICHT_LIBRARY="$IRRLICHT_STUB_LIB" \
  -DSQLITE3_INCLUDE_DIR="$SQLITE_DIR/include" -DSQLITE3_LIBRARY="$SQLITE_DIR/libsqlite3.a" \
  -DSQLite3_INCLUDE_DIR="$SQLITE_DIR/include" -DSQLite3_LIBRARY="$SQLITE_DIR/libsqlite3.a" \
  -DBUILD_CLIENT=OFF -DBUILD_SERVER=ON -DBUILD_UNITTESTS=OFF -DBUILD_BENCHMARKS=OFF \
  -DENABLE_CURSES=OFF -DENABLE_CURL=ON -DENABLE_FREETYPE=OFF -DENABLE_GETTEXT=OFF -DENABLE_SOUND=OFF \
  -DENABLE_POSTGRESQL=OFF -DENABLE_LEVELDB=OFF -DENABLE_REDIS=OFF \
  -DENABLE_PROMETHEUS=OFF -DENABLE_SPATIAL=OFF -DENABLE_UPDATE_CHECKER=OFF \
  "${ZSTD_ARGS[@]}"
cmake --build "$BUILD"

ARTIFACT="$(find "$BUILD" -name "lib${LIBRARY}.so" -print -quit)"
test -n "$ARTIFACT"
mkdir -p "$OUTPUT"
install -m 0644 "$ARTIFACT" "$OUTPUT/lib${LIBRARY}.so"
install -m 0644 "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"/*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so "$OUTPUT/" 2>/dev/null || true
sha256sum "$OUTPUT"/*.so > "$OUTPUT/SHA256SUMS"
echo "built $OUTPUT/lib${LIBRARY}.so"
