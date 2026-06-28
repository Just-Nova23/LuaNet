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
    android_header = args.source / "src" / "porting_android.h"
    modern_android_dialogs = (
        android_header.exists()
        and "enum AndroidDialogState" in android_header.read_text()
    )
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
\tif({"ON" if modern_android_dialogs else "OFF"})
\t\ttarget_compile_definitions(${{PROJECT_NAME}}server PRIVATE LUANET_ANDROID_DIALOG_ENUM)
\tendif()
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
        "\tset(common_SRCS ${common_SRCS} porting_android.cpp)",
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

    main_cpp = args.source / "src" / "main.cpp"
    if main_cpp.exists():
        main = main_cpp.read_text()
        if "luanet_set_admin_chat_interface" not in main:
            main = main.replace(
                '#include "chat_interface.h"\n',
                '#include "chat_interface.h"\n\n'
                'extern "C" void luanet_set_admin_chat_interface(ChatInterface *iface, const char *nick);\n'
                'extern "C" void luanet_clear_admin_chat_interface(ChatInterface *iface);\n',
                1,
            )
        no_curses_server = (
            "\t\ttry {\n"
            "\t\t\t// Create server\n"
            "\t\t\tServer server(game_params.world_path, game_params.game_spec, false,\n"
            "\t\t\t\tbind_addr, true);\n"
            "\t\t\tserver.start();\n\n"
            "\t\t\t// Run server\n"
            "\t\t\tvolatile auto &kill = *porting::signal_handler_killstatus();\n"
            "\t\t\tdedicated_server_loop(server, kill);\n\n"
            "\t\t} catch (const ModError &e) {"
        )
        luanet_server = (
            "\t\tChatInterface luanet_iface;\n"
            "\t\tstd::string luanet_admin_nick = g_settings->get(\"name\");\n"
            "\t\tif (!is_valid_player_name(luanet_admin_nick))\n"
            "\t\t\tluanet_admin_nick = \"LuaNet\";\n"
            "\t\ttry {\n"
            "\t\t\t// Create server\n"
            "\t\t\tServer server(game_params.world_path, game_params.game_spec, false,\n"
            "\t\t\t\tbind_addr, true, &luanet_iface);\n"
            "\t\t\tluanet_set_admin_chat_interface(&luanet_iface, luanet_admin_nick.c_str());\n"
            "\t\t\tserver.start();\n\n"
            "\t\t\t// Run server\n"
            "\t\t\tvolatile auto &kill = *porting::signal_handler_killstatus();\n"
            "\t\t\tdedicated_server_loop(server, kill);\n"
            "\t\t\tluanet_clear_admin_chat_interface(&luanet_iface);\n\n"
            "\t\t} catch (const ModError &e) {"
        )
        legacy_no_curses_server = (
            "\t\ttry {\n"
            "\t\t\t// Create server\n"
            "\t\t\tServer server(game_params.world_path, game_params.game_spec, false,\n"
            "\t\t\t\tbind_addr, true);\n"
            "\t\t\tserver.init();\n"
            "\t\t\tserver.start();\n\n"
            "\t\t\t// Run server\n"
            "\t\t\tbool &kill = *porting::signal_handler_killstatus();\n"
            "\t\t\tdedicated_server_loop(server, kill);\n\n"
            "\t\t} catch (const ModError &e) {"
        )
        legacy_luanet_server = (
            "\t\tChatInterface luanet_iface;\n"
            "\t\tstd::string luanet_admin_nick = g_settings->get(\"name\");\n"
            "\t\tif (!is_valid_player_name(luanet_admin_nick))\n"
            "\t\t\tluanet_admin_nick = \"LuaNet\";\n"
            "\t\ttry {\n"
            "\t\t\t// Create server\n"
            "\t\t\tServer server(game_params.world_path, game_params.game_spec, false,\n"
            "\t\t\t\tbind_addr, true, &luanet_iface);\n"
            "\t\t\tluanet_set_admin_chat_interface(&luanet_iface, luanet_admin_nick.c_str());\n"
            "\t\t\tserver.init();\n"
            "\t\t\tserver.start();\n\n"
            "\t\t\t// Run server\n"
            "\t\t\tbool &kill = *porting::signal_handler_killstatus();\n"
            "\t\t\tdedicated_server_loop(server, kill);\n"
            "\t\t\tluanet_clear_admin_chat_interface(&luanet_iface);\n\n"
            "\t\t} catch (const ModError &e) {"
        )
        if no_curses_server in main:
            main = main.replace(no_curses_server, luanet_server, 1)
        elif legacy_no_curses_server in main:
            main = main.replace(legacy_no_curses_server, legacy_luanet_server, 1)
        else:
            # Older releases use the same structure but whitespace can differ.
            main = re.sub(
                r"\t\ttry \{\n"
                r"\t\t\t// Create server\n"
                r"\t\t\tServer server\(game_params\.world_path, game_params\.game_spec, false,\n"
                r"\t\t\t\tbind_addr, true\);\n"
                r"\t\t\tserver\.start\(\);\n\n"
                r"\t\t\t// Run server\n"
                r"\t\t\tvolatile auto &kill = \*porting::signal_handler_killstatus\(\);\n"
                r"\t\t\tdedicated_server_loop\(server, kill\);\n\n"
                r"\t\t\} catch \(const ModError &e\) \{",
                luanet_server,
                main,
                count=1,
            )
        if "luanet_set_admin_chat_interface(&luanet_iface" not in main:
            raise SystemExit("Unsupported Luanti dedicated server loop layout")
        main_cpp.write_text(main)

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
struct hash<::v2s16> {
	size_t operator()(const ::v2s16 &value) const noexcept
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
struct hash<::v3s16> {
	size_t operator()(const ::v3s16 &value) const noexcept
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
