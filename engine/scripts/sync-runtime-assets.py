#!/usr/bin/env python3
"""Bundle Luanti builtin runtime assets for Android server profiles.

The Android bridge sets Luanti's share path to each server profile directory.
That keeps profiles isolated, but it also means every profile needs the engine
`builtin/` Lua runtime next to its world data before the native server starts.

This script downloads official Luanti source archives and writes compact
`builtin.zip` assets consumed by OrchestratorService.
"""

from __future__ import annotations

import subprocess
import tarfile
import tempfile
import zipfile
from pathlib import Path


VERSIONS = [
    "5.0.1",
    "5.1.1",
    "5.2.0",
    "5.3.0",
    "5.4.1",
    "5.5.1",
    "5.6.1",
    "5.7.0",
    "5.8.0",
    "5.9.1",
    "5.10.0",
    "5.11.0",
    "5.12.0",
    "5.13.0",
    "5.14.0",
    "5.15.2",
    "5.16.1",
]

SOURCE_URLS = [
    "https://github.com/luanti-org/luanti/archive/refs/tags/{version}.tar.gz",
    "https://github.com/minetest/minetest/archive/refs/tags/{version}.tar.gz",
]


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def download(version: str, cache_dir: Path) -> Path:
    archive = cache_dir / f"{version}.tar.gz"
    if archive.is_file() and archive.stat().st_size > 1024:
        return archive

    for template in SOURCE_URLS:
        url = template.format(version=version)
        print(f"{version}: download {url}", flush=True)
        result = subprocess.run(
            ["curl", "-fL", "--retry", "3", "--connect-timeout", "20", "-o", str(archive), url],
            check=False,
        )
        if result.returncode == 0 and archive.is_file() and archive.stat().st_size > 1024:
            return archive

    raise RuntimeError(f"Unable to download Luanti source archive for {version}")


def write_builtin_zip(version: str, archive: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "r:gz") as tar, zipfile.ZipFile(
        destination,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
    ) as zip_out:
        files = 0
        for member in tar:
            parts = Path(member.name).parts
            if len(parts) < 2 or parts[1] != "builtin":
                continue
            archive_name = "/".join(parts[1:])
            if member.isdir():
                zip_out.writestr(zipfile.ZipInfo(archive_name.rstrip("/") + "/"), b"")
                continue
            if not member.isfile():
                continue
            source = tar.extractfile(member)
            if source is None:
                continue
            info = zipfile.ZipInfo(archive_name)
            info.external_attr = 0o644 << 16
            zip_out.writestr(info, source.read())
            files += 1

    if files == 0:
        raise RuntimeError(f"No builtin files found in {archive}")
    print(f"{version}: wrote {destination} ({destination.stat().st_size} bytes)", flush=True)


def main() -> None:
    output_root = repo_root() / "android/app/src/main/assets/engine-runtime"
    with tempfile.TemporaryDirectory(prefix="luanet-runtime-source-") as temporary:
        cache_dir = Path(temporary)
        for version in VERSIONS:
            archive = download(version, cache_dir)
            write_builtin_zip(version, archive, output_root / version / "builtin.zip")


if __name__ == "__main__":
    main()
