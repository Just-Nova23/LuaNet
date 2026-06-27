#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
OUT="$ROOT/build/android-jni/arm64-v8a"

rm -rf "$OUT"
mkdir -p "$OUT"

found=0
for artifact_dir in "$ROOT"/build/artifacts/*; do
  [[ -d "$artifact_dir" ]] || continue
  while IFS= read -r library; do
    install -m 0644 "$library" "$OUT/"
    found=$((found + 1))
  done < <(find "$artifact_dir" -maxdepth 1 -type f -name 'lib*.so' ! -name 'libfrpc.so' | sort)
done

if [[ -n "${FRPC_ARTIFACT:-}" ]]; then
  install -m 0755 "$FRPC_ARTIFACT" "$OUT/libfrpc.so"
elif [[ -f "$REPO/tunnel/build/android-arm64/libfrpc.so" ]]; then
  install -m 0755 "$REPO/tunnel/build/android-arm64/libfrpc.so" "$OUT/libfrpc.so"
else
  echo "warning: libfrpc.so not found; release packaging will fail until FRPC_ARTIFACT is provided" >&2
fi

echo "synced $found engine libraries to $OUT"
