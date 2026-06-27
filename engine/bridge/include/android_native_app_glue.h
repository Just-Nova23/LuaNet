/* SPDX-License-Identifier: LGPL-2.1-or-later
 * LuaNet headless Android compatibility shim.
 *
 * Older Luanti/Minetest Android headers include android_native_app_glue.h even
 * for dedicated-server builds. LuaNet does not use the native-activity glue in
 * headless mode, so a forward declaration is enough for those declarations.
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

struct android_app;

#ifdef __cplusplus
}
#endif
