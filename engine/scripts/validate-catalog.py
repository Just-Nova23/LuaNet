#!/usr/bin/env python3
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
catalog = json.loads((root / "catalog.json").read_text())
engines = catalog["engines"]
assert len(engines) == 17
assert sum(bool(item["base"]) for item in engines) == 1
assert engines[-1]["version"] == "5.16.1" and engines[-1]["base"]
assert len({item["version"] for item in engines}) == len(engines)
assert len({item["library"] for item in engines}) == len(engines)
for item in engines:
    assert re.fullmatch(r"[0-9a-f]{40}", item["commit"])
    assert item["tag"] == item["version"]
print(f"validated {len(engines)} pinned Luanti engines")
