#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/scripts/validate-catalog.py"
python3 - "$ROOT/catalog.json" <<'PY' | while read -r version; do
import json, sys
for item in json.load(open(sys.argv[1]))["engines"]:
    print(item["version"])
PY
  "$ROOT/scripts/build-version.sh" "$version"
done
