/* SPDX-License-Identifier: LGPL-2.1-or-later */
#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_run(JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_requestStop(JNIEnv *, jobject);

JNIEXPORT void JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_submitCommand(JNIEnv *, jobject, jstring);

#ifdef __cplusplus
}
#endif
