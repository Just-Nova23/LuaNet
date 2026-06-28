/* SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright 2026 NovaX Hosting contributors
 */

#include "luanet_engine.h"

#include "irrlichttypes_bloated.h"
#include "porting.h"
#include "chat_interface.h"
#include "util/numeric.h"
#include "util/string.h"

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
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
std::mutex g_io_mutex;
std::mutex g_log_line_mutex;
std::mutex g_chat_mutex;
std::atomic<bool> g_running{false};
std::atomic<bool> g_log_tail_running{false};
std::atomic<bool> g_announced_ready{false};
int g_log_read = -1;
int g_log_write = -1;
int g_input_read = -1;
int g_input_write = -1;
ChatInterface *g_admin_chat = nullptr;
std::string g_admin_nick = "LuaNet";

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

std::string trim_copy(const std::string &text)
{
	size_t start = 0;
	while (start < text.size() && std::isspace(static_cast<unsigned char>(text[start])))
		++start;
	size_t end = text.size();
	while (end > start && std::isspace(static_cast<unsigned char>(text[end - 1])))
		--end;
	return text.substr(start, end - start);
}

std::string player_name_before(const std::string &line, size_t marker)
{
	size_t start = 0;
	const auto action_prefix = line.rfind("]:", marker);
	if (action_prefix != std::string::npos) {
		start = action_prefix + 2;
	} else {
		const auto space = line.rfind(' ', marker > 0 ? marker - 1 : 0);
		start = space == std::string::npos ? 0 : space + 1;
	}
	std::string name = trim_copy(line.substr(start, marker - start));
	const auto address = name.find(" [");
	if (address != std::string::npos)
		name = trim_copy(name.substr(0, address));
	return name;
}

bool write_all(int fd, const std::string &text)
{
	size_t written = 0;
	while (written < text.size()) {
		const ssize_t result = ::write(fd, text.data() + written, text.size() - written);
		if (result <= 0)
			return false;
		written += static_cast<size_t>(result);
	}
	return true;
}

void handle_log_line(std::string line)
{
	while (!line.empty() && (line.back() == '\n' || line.back() == '\r'))
		line.pop_back();
	if (line.empty())
		return;
	{
		static std::string last_line;
		static std::chrono::steady_clock::time_point last_at;
		const auto now = std::chrono::steady_clock::now();
		std::lock_guard<std::mutex> lock(g_log_line_mutex);
		if (line == last_line && now - last_at < std::chrono::milliseconds(800))
			return;
		last_line = line;
		last_at = now;
	}
	__android_log_print(ANDROID_LOG_INFO, "LuaNetEngine", "%s", line.c_str());
	callback("emitLog", "(ILjava/lang/String;)V", line);
	if ((line.find("listening on") != std::string::npos ||
			line.find("Server for gameid") != std::string::npos)) {
		bool expected = false;
		if (g_announced_ready.compare_exchange_strong(expected, true))
			ready();
	}
	auto joins = line.find(" joins game");
	if (joins != std::string::npos) {
		const std::string player = player_name_before(line, joins);
		if (!player.empty())
			callback("emitPlayerJoined", "(Ljava/lang/String;)V", player);
	}
	auto leaves = line.find(" leaves game");
	if (leaves != std::string::npos) {
		const std::string player = player_name_before(line, leaves);
		if (!player.empty())
			callback("emitPlayerLeft", "(Ljava/lang/String;)V", player);
	}
	const auto list = line.find("List of players:");
	if (list != std::string::npos) {
		std::string players = line.substr(list + std::strlen("List of players:"));
		size_t cursor = 0;
		while (cursor < players.size()) {
			size_t comma = players.find(',', cursor);
			std::string player = trim_copy(players.substr(cursor, comma == std::string::npos ? comma : comma - cursor));
			if (!player.empty())
				callback("emitPlayerJoined", "(Ljava/lang/String;)V", player);
			if (comma == std::string::npos)
				break;
			cursor = comma + 1;
		}
	}
}

void consume_pipe_logs()
{
	FILE *stream = fdopen(g_log_read, "r");
	if (!stream)
		return;
	char *buffer = nullptr;
	size_t capacity = 0;
	while (getline(&buffer, &capacity, stream) >= 0) {
		handle_log_line(buffer);
	}
	free(buffer);
	fclose(stream);
}

void drain_log_file(const std::string &path, std::streamoff &offset)
{
	std::ifstream input(path);
	if (!input)
		return;
	input.seekg(0, std::ios::end);
	const std::streamoff end = input.tellg();
	if (end < 0)
		return;
	if (offset > end)
		offset = 0;
	input.seekg(offset, std::ios::beg);
	std::string line;
	while (std::getline(input, line))
		handle_log_line(line);
	input.clear();
	input.seekg(0, std::ios::end);
	const std::streamoff next = input.tellg();
	if (next >= 0)
		offset = next;
}

void tail_log_file(const std::string &path)
{
	std::streamoff offset = 0;
	while (g_log_tail_running) {
		drain_log_file(path, offset);
		std::this_thread::sleep_for(std::chrono::milliseconds(250));
	}
	drain_log_file(path, offset);
}

void set_path(const char *modern, const char *legacy, const std::string &value)
{
	setenv(modern, value.c_str(), 1);
	setenv(legacy, value.c_str(), 1);
}
}

extern "C" void luanet_set_admin_chat_interface(ChatInterface *iface, const char *nick)
{
	std::lock_guard<std::mutex> lock(g_chat_mutex);
	g_admin_chat = iface;
	g_admin_nick = nick && *nick ? nick : "LuaNet";
	if (g_admin_chat)
		g_admin_chat->command_queue.push_back(new ChatEventNick(CET_NICK_ADD, g_admin_nick));
}

extern "C" void luanet_clear_admin_chat_interface(ChatInterface *iface)
{
	std::lock_guard<std::mutex> lock(g_chat_mutex);
	if (!iface || iface == g_admin_chat)
		g_admin_chat = nullptr;
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
	const std::string log = root + "/server.log";
	set_path("LUANTI_USER_PATH", "MINETEST_USER_PATH", root);
	set_path("LUANTI_GAME_PATH", "MINETEST_GAME_PATH", root + "/games");
	set_path("LUANTI_MOD_PATH", "MINETEST_MOD_PATH", root + "/mods");
	std::remove(log.c_str());
	g_announced_ready = false;

	int log_descriptors[2];
	if (pipe(log_descriptors) != 0) {
		g_running = false;
		return 71;
	}
	int input_descriptors[2];
	if (pipe(input_descriptors) != 0) {
		close(log_descriptors[0]);
		close(log_descriptors[1]);
		g_running = false;
		return 72;
	}
	g_log_read = log_descriptors[0];
	g_log_write = log_descriptors[1];
	g_input_read = input_descriptors[0];
	g_input_write = input_descriptors[1];
	const int old_in = dup(STDIN_FILENO);
	const int old_out = dup(STDOUT_FILENO);
	const int old_err = dup(STDERR_FILENO);
	dup2(g_input_read, STDIN_FILENO);
	dup2(g_log_write, STDOUT_FILENO);
	dup2(g_log_write, STDERR_FILENO);
	std::thread pipe_reader(consume_pipe_logs);
	g_log_tail_running = true;
	std::thread file_reader(tail_log_file, log);

	std::vector<std::string> values{"luanetserver", "--world", world, "--config", config,
		"--port", port, "--logfile", log};
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
	g_log_tail_running = false;
	if (file_reader.joinable())
		file_reader.join();
	dup2(old_in, STDIN_FILENO);
	dup2(old_out, STDOUT_FILENO);
	dup2(old_err, STDERR_FILENO);
	close(old_in);
	close(old_out);
	close(old_err);
	{
		std::lock_guard<std::mutex> lock(g_io_mutex);
		close(g_input_write);
		g_input_write = -1;
	}
	luanet_clear_admin_chat_interface(nullptr);
	close(g_input_read);
	g_input_read = -1;
	close(g_log_write);
	g_log_write = -1;
	pipe_reader.join();
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
	const std::string text = raw ? raw : "";
	env->ReleaseStringUTFChars(command, raw);
	if (text.empty())
		return;
	std::string chat_command = text;
	if (chat_command.front() != '/')
		chat_command.insert(chat_command.begin(), '/');
	bool sent = false;
	{
		std::lock_guard<std::mutex> lock(g_chat_mutex);
		if (g_running && g_admin_chat) {
			g_admin_chat->command_queue.push_back(
				new ChatEventChat(g_admin_nick, utf8_to_wide(chat_command)));
			sent = true;
		}
	}
	{
		std::lock_guard<std::mutex> lock(g_io_mutex);
		if (!sent && g_running && g_input_write >= 0)
			sent = write_all(g_input_write, chat_command + "\n");
	}
	if (!sent) {
		callback("emitLog", "(ILjava/lang/String;)V",
			std::string("Console command failed because Luanti admin interface is not available: ") + chat_command, 3);
	}
}
