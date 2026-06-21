# Luanti engine matrix

This directory pins and builds the 17 ARM64 dedicated-server engines supported by LuaNet.
`5.16.1` is the Play base engine; older engines are inputs for on-demand feature modules.
The universal beta may package all generated libraries.

## Reproducible build

Requirements: Python 3, Git, CMake, Ninja, curl, unzip, JDK 17, and an Android NDK exposed
as `ANDROID_NDK_HOME`.

```bash
python3 scripts/validate-catalog.py
scripts/build-version.sh 5.16.1
# or, after validating one version on the current NDK:
scripts/build-all.sh
```

The script fetches a tag, verifies the resolved 40-character commit, converts only the
dedicated-server CMake target into a shared Android library, disables unavailable server
backends, and emits checksummed files below `build/artifacts/<version>`.

Luanti changed its Android dependencies and build layout over this long version range.
The CI smoke matrix is the authority for compatibility; version-specific transformations
belong in `scripts/prepare-source.py` and must remain LGPL-2.1-or-later.

The bridge runs inside one of five isolated Android processes, maps the profile paths to
both old `MINETEST_*` and current `LUANTI_*` environment variables, streams logs and player
events to Kotlin, and requests a graceful stop through Luanti's kill-status interface.
Console execution still requires the LuaNet runtime server mod; the JNI method never grants
trusted-mod access or disables Luanti's security sandbox.

