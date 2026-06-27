#!/usr/bin/env python3
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
repo = root.parent
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
android_catalog = repo / "android/app/src/main/java/net/novax/luanet/domain/EngineCatalog.kt"
if android_catalog.exists():
    text = android_catalog.read_text()
    releases = re.findall(
        r'EngineRelease\("([^"]+)",\s*\d+,\s*\d+,\s*"([^"]+)",\s*([^)]+)\)',
        text,
    )
    assert len(releases) == len(engines), "Android EngineCatalog release count diverges"
    by_version = {item["version"]: item for item in engines}
    for version, library, module in releases:
        assert version in by_version, f"Android declares unknown engine {version}"
        expected = by_version[version]
        assert library == expected["library"], f"Android library mismatch for {version}: {library} != {expected['library']}"
        if expected["base"]:
            assert module.strip() == "null", f"Base engine {version} must stay in the base APK"
gradle_build = repo / "android/app/build.gradle.kts"
if gradle_build.exists():
    text = gradle_build.read_text()
    expected_libraries = {f'lib{item["library"]}.so' for item in engines}
    declared_libraries = set(re.findall(r'"(libluanet_engine_[^"]+\.so)"', text))
    missing = sorted(expected_libraries - declared_libraries)
    assert not missing, f"Gradle release native check misses: {', '.join(missing)}"
    assert '"libfrpc.so"' in text, "Gradle release native check must require FRP client"
print(f"validated {len(engines)} pinned Luanti engines")
