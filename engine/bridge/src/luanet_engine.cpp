/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 NovaX Hosting contributors
 */

#include "luanet_engine.h"

#include "irrlichttypes_bloated.h"
#include "porting.h"
#include "util/numeric.h"

#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

extern int main(int argc, char *argv[]);

struct android_app;
extern "C" void android_main(android_app *) {}

namespace porting {
void osSpecificInit() {}
void initAndroid() {}
void cleanupAndroid() {}

std::string getLanguageAndroid()
{
	const char *language = std::getenv("LANG");
	return language && *language ? language : "C";
}

bool setSystemPaths()
{
	const char *user = std::getenv("LUANTI_USER_PATH");
	if (!user || !*user)
		user = std::getenv("MINETEST_USER_PATH");
	if (!user || !*user)
		user = ".";
	path_user = user;
	path_share = path_user;
	path_cache = path_user + "/cache";
	return true;
}

void initializePathsAndroid()
{
	setSystemPaths();
}

void copyAssets() {}
void openURIAndroid(const char *) {}
void openURIAndroid(const std::string &) {}
void openURLAndroid(const std::string &) {}
void shareFileAndroid(const std::string &) {}
void setPlayingNowNotification(bool) {}
void showInputDialog(const std::string &, const std::string &, const std::string &, int) {}
#ifdef LUANET_ANDROID_DIALOG_ENUM
AndroidDialogType getLastInputDialogType() { return TEXT_INPUT; }
AndroidDialogState getInputDialogState() { return DIALOG_CANCELED; }
int getInputDialogSelection() { return -1; }
#else
int getInputDialogState() { return 0; }
#endif
std::string getInputDialogValue() { return {}; }
void showTextInputDialog(const std::string &, const std::string &, int) {}
void showComboBoxDialog(const std::string *, s32, s32) {}
std::string getInputDialogMessage() { return {}; }
bool hasPhysicalKeyboardAndroid() { return false; }
float getDisplayDensity() { return 1.0f; }
v2u32 getDisplaySize() { return v2u32(1280, 720); }
}

namespace {
JavaVM *g_vm = nullptr;
jobject g_bridge = nullptr;
std::mutex g_mutex;
std::atomic<bool> g_running{false};
int g_log_read = -1;
int g_log_write = -1;

std::string field(const std::string &json, const char *name)
{
	const std::string marker = std::string("\"") + name + "\":";
	auto at = json.find(marker);
	if (at == std::string::npos)
		return {};
	at += marker.size();
	while (at < json.size() && json[at] == ' ')
		++at;
	if (at >= json.size() || json[at] != '"') {
		auto end = json.find_first_of(",}", at);
		return json.substr(at, end - at);
	}
	std::string result;
	for (++at; at < json.size(); ++at) {
		if (json[at] == '"')
			break;
		if (json[at] == '\\' && at + 1 < json.size()) {
			const char escaped = json[++at];
			result += escaped == 'n' ? '\n' : escaped == 't' ? '\t' : escaped;
		} else {
			result += json[at];
		}
	}
	return result;
}

void callback(const char *method, const char *signature, const std::string &text, int level = 1)
{
	JNIEnv *env = nullptr;
	bool attached = false;
	if (!g_vm || g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
		if (!g_vm || g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK)
			return;
		attached = true;
	}
	std::lock_guard<std::mutex> lock(g_mutex);
	if (g_bridge) {
		jclass type = env->GetObjectClass(g_bridge);
		jmethodID id = env->GetMethodID(type, method, signature);
		if (id) {
			jstring value = env->NewStringUTF(text.c_str());
			if (std::strcmp(signature, "(ILjava/lang/String;)V") == 0)
				env->CallVoidMethod(g_bridge, id, level, value);
			else
				env->CallVoidMethod(g_bridge, id, value);
			env->DeleteLocalRef(value);
		}
		env->DeleteLocalRef(type);
	}
	if (attached)
		g_vm->DetachCurrentThread();
}

void ready()
{
	JNIEnv *env = nullptr;
	bool attached = false;
	if (!g_vm || g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
		if (!g_vm || g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK)
			return;
		attached = true;
	}
	std::lock_guard<std::mutex> lock(g_mutex);
	if (g_bridge) {
		jclass type = env->GetObjectClass(g_bridge);
		jmethodID id = env->GetMethodID(type, "emitReady", "()V");
		if (id)
			env->CallVoidMethod(g_bridge, id);
		env->DeleteLocalRef(type);
	}
	if (attached)
		g_vm->DetachCurrentThread();
}

void consume_logs()
{
	FILE *stream = fdopen(g_log_read, "r");
	if (!stream)
		return;
	char *buffer = nullptr;
	size_t capacity = 0;
	bool announced = false;
	while (getline(&buffer, &capacity, stream) >= 0) {
		std::string line(buffer);
		while (!line.empty() && (line.back() == '\n' || line.back() == '\r'))
			line.pop_back();
		__android_log_print(ANDROID_LOG_INFO, "LuaNetEngine", "%s", line.c_str());
		callback("emitLog", "(ILjava/lang/String;)V", line);
		if (!announced && (line.find("listening on") != std::string::npos ||
				line.find("Server for gameid") != std::string::npos)) {
			announced = true;
			ready();
		}
		auto joins = line.find(" joins game");
		if (joins != std::string::npos) {
			auto start = line.rfind(' ', joins > 0 ? joins - 1 : 0);
			callback("emitPlayerJoined", "(Ljava/lang/String;)V", line.substr(start + 1, joins - start - 1));
		}
		auto leaves = line.find(" leaves game");
		if (leaves != std::string::npos) {
			auto start = line.rfind(' ', leaves > 0 ? leaves - 1 : 0);
			callback("emitPlayerLeft", "(Ljava/lang/String;)V", line.substr(start + 1, leaves - start - 1));
		}
	}
	free(buffer);
	fclose(stream);
}

void set_path(const char *modern, const char *legacy, const std::string &value)
{
	setenv(modern, value.c_str(), 1);
	setenv(legacy, value.c_str(), 1);
}
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
	g_vm = vm;
	return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_run(JNIEnv *env, jobject instance, jstring configuration)
{
	if (g_running.exchange(true))
		return 70;
	const char *raw = env->GetStringUTFChars(configuration, nullptr);
	const std::string json(raw ? raw : "");
	env->ReleaseStringUTFChars(configuration, raw);
	{
		std::lock_guard<std::mutex> lock(g_mutex);
		g_bridge = env->NewGlobalRef(instance);
	}

	const std::string root = field(json, "profilePath");
	const std::string world = field(json, "worldPath");
	const std::string config = field(json, "configPath");
	const std::string port = field(json, "localPort");
	const std::string game = field(json, "gameId");
	set_path("LUANTI_USER_PATH", "MINETEST_USER_PATH", root);
	set_path("LUANTI_GAME_PATH", "MINETEST_GAME_PATH", root + "/games");
	set_path("LUANTI_MOD_PATH", "MINETEST_MOD_PATH", root + "/mods");

	int descriptors[2];
	if (pipe(descriptors) != 0) {
		g_running = false;
		return 71;
	}
	g_log_read = descriptors[0];
	g_log_write = descriptors[1];
	const int old_out = dup(STDOUT_FILENO);
	const int old_err = dup(STDERR_FILENO);
	dup2(g_log_write, STDOUT_FILENO);
	dup2(g_log_write, STDERR_FILENO);
	std::thread reader(consume_logs);

	std::vector<std::string> values{"luanetserver", "--world", world, "--config", config,
		"--port", port, "--logfile", ""};
	if (!game.empty() && game != "null") {
		values.emplace_back("--gameid");
		values.emplace_back(game);
	}
	std::vector<char *> arguments;
	for (auto &value : values)
		arguments.push_back(const_cast<char *>(value.c_str()));
	const int result = main(static_cast<int>(arguments.size()), arguments.data());

	fflush(stdout);
	fflush(stderr);
	dup2(old_out, STDOUT_FILENO);
	dup2(old_err, STDERR_FILENO);
	close(old_out);
	close(old_err);
	close(g_log_write);
	g_log_write = -1;
	reader.join();
	{
		std::lock_guard<std::mutex> lock(g_mutex);
		env->DeleteGlobalRef(g_bridge);
		g_bridge = nullptr;
	}
	g_running = false;
	return result;
}

JNIEXPORT void JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_requestStop(JNIEnv *, jobject)
{
	if (g_running)
		*porting::signal_handler_killstatus() = 1;
}

JNIEXPORT void JNICALL
Java_net_novax_luanet_runtime_NativeEngineBridge_submitCommand(JNIEnv *env, jobject, jstring command)
{
	const char *raw = env->GetStringUTFChars(command, nullptr);
	callback("emitLog", "(ILjava/lang/String;)V",
		std::string("Console command queued for LuaNet runtime mod: ") + (raw ? raw : ""), 1);
	env->ReleaseStringUTFChars(command, raw);
}
