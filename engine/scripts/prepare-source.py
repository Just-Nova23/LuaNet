#!/usr/bin/env python3
"""Turn Luanti's dedicated-server executable target into an Android shared library."""

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--bridge", type=Path, required=True)
    parser.add_argument("--library", required=True)
    args = parser.parse_args()

    cmake = args.source / "src" / "CMakeLists.txt"
    text = cmake.read_text()
    pattern = r"add_executable\(\$\{PROJECT_NAME\}server([^\n]*)\)"
    text, replacements = re.subn(
        pattern,
        r"add_library(${PROJECT_NAME}server SHARED\1)",
        text,
        count=1,
    )
    if replacements != 1:
        raise SystemExit("Unsupported Luanti server target layout")
    marker = "endif(BUILD_SERVER)"
    injection = f"""
\ttarget_sources(${{PROJECT_NAME}}server PRIVATE \"{args.bridge.as_posix()}\")
\ttarget_include_directories(${{PROJECT_NAME}}server PRIVATE \"{(args.bridge.parent.parent / 'include').as_posix()}\")
\tif(ANDROID)
\t\ttarget_link_libraries(${{PROJECT_NAME}}server log)
\tendif()
\tset_target_properties(${{PROJECT_NAME}}server PROPERTIES OUTPUT_NAME \"{args.library}\")
"""
    if marker not in text:
        raise SystemExit("Unsupported Luanti BUILD_SERVER block")
    text = text.replace(
        "\tlist(APPEND common_SRCS porting_android.cpp)",
        "\t# LuaNet headless Android server provides porting_android replacements in the JNI bridge.",
    )
    text = text.replace(
        "\tset(PLATFORM_LIBS -lpthread ${CMAKE_DL_LIBS})",
        "\tif(ANDROID)\n"
        "\t\tset(PLATFORM_LIBS ${CMAKE_DL_LIBS})\n"
        "\telse()\n"
        "\t\tset(PLATFORM_LIBS -lpthread ${CMAKE_DL_LIBS})\n"
        "\tendif()",
    )
    text = re.sub(
        r"\n\t\tCreateLegacyAlias\(minetestserver_alias[^\n]*\)",
        "\n\t\t# LuaNet builds the server as an Android shared library, not a legacy executable alias.",
        text,
    )
    cmake.write_text(text.replace(marker, injection + marker, 1))

    android_header = args.source / "src" / "porting_android.h"
    if android_header.exists():
        header = android_header.read_text()
        header = re.sub(
            r"\n#ifndef SERVER\s*\n(float getDisplayDensity\(\);\s*\nv2u32 getDisplaySize\(\);)\s*\n#endif\s*\n",
            r"\n\1\n",
            header,
            count=1,
        )
        android_header.write_text(header)

    vector2d_header = args.source / "src" / "irr_v2d.h"
    if vector2d_header.exists():
        vector2d = vector2d_header.read_text()
        if "LuaNet vector2d hash" not in vector2d:
            vector2d = vector2d.replace(
                "#include <vector2d.h>\n",
                "#include <vector2d.h>\n\n#include <functional>\n",
                1,
            )
            vector2d += """

// LuaNet vector2d hash: libc++ does not provide std::hash for Irrlicht vectors.
namespace std {
template <>
struct hash<irr::core::vector2d<irr::s16>> {
	size_t operator()(const irr::core::vector2d<irr::s16> &value) const noexcept
	{
		return static_cast<size_t>(value.X) ^
				(static_cast<size_t>(value.Y) << 16);
	}
};
}
"""
            vector2d_header.write_text(vector2d)

    vector3d_header = args.source / "src" / "irr_v3d.h"
    if vector3d_header.exists():
        vector3d = vector3d_header.read_text()
        if "LuaNet vector3d hash" not in vector3d:
            vector3d = vector3d.replace(
                "#include <vector3d.h>\n",
                "#include <vector3d.h>\n\n#include <functional>\n",
                1,
            )
            vector3d += """

// LuaNet vector3d hash: libc++ does not provide std::hash for Irrlicht vectors.
namespace std {
template <>
struct hash<irr::core::vector3d<irr::s16>> {
	size_t operator()(const irr::core::vector3d<irr::s16> &value) const noexcept
	{
		return static_cast<size_t>(value.X) ^
				(static_cast<size_t>(value.Y) << 16) ^
				(static_cast<size_t>(value.Z) << 32);
	}
};
}
"""
            vector3d_header.write_text(vector3d)

    porting_cpp = args.source / "src" / "porting.cpp"
    if porting_cpp.exists():
        porting = porting_cpp.read_text()
        linux_branch = "#elif defined(__linux__) || defined(__FreeBSD__) || defined(__NetBSD__) || defined(__DragonFly__)"
        if "extern bool setSystemPaths();" not in porting and linux_branch in porting:
            porting = porting.replace(
                linux_branch,
                "#elif defined(__ANDROID__)\n\n"
                "extern bool setSystemPaths(); // defined by LuaNet's headless Android bridge\n\n"
                f"{linux_branch}",
                1,
            )
            porting_cpp.write_text(porting)

    server_header = args.source / "src" / "server.h"
    if server_header.exists():
        server = server_header.read_text()
        server = server.replace(
            "return std::hash<v3s16>()(p.first) ^ p.second;",
            "return static_cast<size_t>(p.first.X) ^\n"
            "\t\t\t\t\t(static_cast<size_t>(p.first.Y) << 16) ^\n"
            "\t\t\t\t\t(static_cast<size_t>(p.first.Z) << 32) ^\n"
            "\t\t\t\t\tstatic_cast<size_t>(p.second);",
        )
        server_header.write_text(server)


if __name__ == "__main__":
    main()
