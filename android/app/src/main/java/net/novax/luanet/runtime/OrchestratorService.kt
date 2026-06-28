package net.novax.luanet.runtime

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.R
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.EntitlementPolicy
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState
import net.novax.luanet.network.TunnelLease
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class OrchestratorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = mutableMapOf<String, BoundEngine>()
    private val tunnels = mutableMapOf<String, BoundTunnel>()
    private val logcatTails = mutableMapOf<String, Job>()
    private val recentLogLines = mutableMapOf<String, Pair<String, Long>>()
    private val callback = Messenger(CallbackHandler())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val entitlement by lazy { EntitlementStore(this) }

    private val repository get() = (application as LuaNetApplication).container.servers
    private val controlPlane get() = (application as LuaNetApplication).container.controlPlane

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Starting LuaNet"))
        acquireLocks()
        mainHandler.postDelayed(idleCheck, 60_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        when (intent?.action) {
            ACTION_START -> if (profileId != null) scope.launch { startProfile(profileId) }
            ACTION_STOP -> if (profileId != null) stopProfile(profileId)
            ACTION_COMMAND -> if (profileId != null) sendCommand(profileId, intent.getStringExtra(EXTRA_COMMAND).orEmpty())
            ACTION_PUBLIC_START -> if (profileId != null) startPublicTunnel(profileId, intent.getStringExtra(EXTRA_LEASE).orEmpty())
            ACTION_PUBLIC_STOP -> if (profileId != null) stopPublicTunnel(profileId)
            ACTION_STOP_ALL -> sessions.keys.toList().forEach(::stopProfile)
        }
        if (intent?.action == ACTION_COMMAND && sessions.isEmpty()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tunnels.keys.toList().forEach { stopPublicTunnel(it) }
        sessions.keys.toList().forEach(::stopProfile)
        mainHandler.removeCallbacks(idleCheck)
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startProfile(profileId: String) {
        if (sessions.containsKey(profileId)) return
        val profile = repository.profile(profileId) ?: return
        val limits = EntitlementPolicy.forTier(entitlement.current())
        val limit = limits.activeServers
        if (sessions.size >= limit) {
            repository.updateRuntime(profileId, ServerState.CRASHED, null)
            RuntimeRegistry.update(profileId) { RuntimeSnapshot(profileId, "FREE_LIMIT", -1, 0) }
            return
        }
        val slot = (0 until 5).firstOrNull { candidate -> sessions.values.none { it.slot == candidate } } ?: return
        val port = 30_000 + slot
        val release = EngineCatalog.find(profile.engineVersion) ?: return
        if (!hasBundledLibrary(release.libraryName)) {
            val text = "Engine ${profile.engineVersion} is not bundled in this APK. Build native artifacts and sync them before starting this server."
            repository.updateRuntime(profileId, ServerState.CRASHED, null)
            RuntimeRegistry.update(profileId) { RuntimeSnapshot(profileId, "CRASHED", -1, 0, logs = listOf(text)) }
            return
        }
        val root = repository.profileDirectory(profile.id).apply { mkdirs() }
        runCatching { ensureRuntimeAssets(root, profile.engineVersion) }.onFailure { error ->
            val text = "Engine ${profile.engineVersion} runtime assets are missing or invalid: ${error.message}"
            repository.updateRuntime(profileId, ServerState.CRASHED, null)
            RuntimeRegistry.update(profileId) { RuntimeSnapshot(profileId, "CRASHED", -1, 0, logs = listOf(text)) }
            return
        }
        val world = File(root, "world").apply { mkdirs() }
        val packages = repository.packages(profile.id)
        writeWorldConfig(world, profile.gameKey, packages, File(root, "mods"))
        val config = File(root, "minetest.conf")
        writeConfig(config, profile.name, port, profile.maxPlayers, profile.creative, profile.damage, profile.pvp)
        val engineConfiguration = EngineConfiguration(
            profile.id, profile.engineVersion, release.libraryName, root.absolutePath,
            world.absolutePath, config.absolutePath, port, profile.gameKey?.substringAfter('/'),
            "__luanet_console_${profile.id.take(8)}",
        )
        repository.updateRuntime(profile.id, ServerState.STARTING, port)
        RuntimeRegistry.update(profile.id) {
            RuntimeSnapshot(
                profile.id,
                "STARTING",
                slot,
                port,
                logs = listOf(
                    "Preparing Luanti ${profile.engineVersion} runtime",
                    "Starting isolated engine slot $slot on UDP port $port",
                ),
            )
        }
        bindEngine(profile.id, slot, engineConfiguration)
    }

    private fun hasBundledLibrary(libraryName: String): Boolean {
        val filename = "lib$libraryName.so"
        val apkPaths = buildList {
            add(applicationInfo.sourceDir)
            applicationInfo.splitSourceDirs?.let(::addAll)
        }
        return apkPaths.any { path ->
            runCatching {
                ZipFile(path).use { zip ->
                    zip.entries().asSequence().any { entry ->
                        !entry.isDirectory && entry.name.startsWith("lib/") && entry.name.endsWith("/$filename")
                    }
                }
            }.getOrDefault(false)
        }
    }

    private fun ensureRuntimeAssets(root: File, engineVersion: String) {
        val marker = File(root, ".luanet-runtime-version")
        val builtinInit = File(root, "builtin/init.lua")
        val installedVersion = runCatching { marker.readText().trim() }.getOrNull()
        if (builtinInit.isFile && installedVersion == engineVersion) return
        File(root, "builtin").deleteRecursively()
        extractRuntimeAsset(root, "engine-runtime/$engineVersion/builtin.zip")
        require(builtinInit.isFile) { "builtin/init.lua was not installed" }
        marker.writeText(engineVersion)
    }

    private fun extractRuntimeAsset(root: File, assetPath: String) {
        val canonicalRoot = root.canonicalFile
        var totalBytes = 0L
        assets.open(assetPath).use { input ->
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val parts = name.split('/').filter { it.isNotBlank() }
                    require(parts.isNotEmpty() && parts.first() == "builtin" && parts.none { it == ".." }) {
                        "Unsafe runtime asset entry: ${entry.name}"
                    }
                    val target = File(root, name).canonicalFile
                    require(target.path.startsWith(canonicalRoot.path + File.separator)) {
                        "Runtime asset escapes profile directory: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                require(totalBytes <= MAX_RUNTIME_ASSET_BYTES) { "Runtime assets exceed ${MAX_RUNTIME_ASSET_BYTES / (1024 * 1024)} MiB" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun bindEngine(profileId: String, slot: Int, configuration: EngineConfiguration) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val messenger = Messenger(binder)
                sessions[profileId] = BoundEngine(slot, messenger, this, System.currentTimeMillis(), System.currentTimeMillis())
                EngineProtocol.send(messenger, EngineProtocol.START, callback) {
                    putString(EngineProtocol.KEY_CONFIG, Json.encodeToString(configuration))
                    putString(EngineProtocol.KEY_PROFILE_ID, profileId)
                }
                startLogcatTail(profileId, slot)
                updateNotification()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                engineEnded(profileId, "CRASHED")
            }
        }
        bindService(Intent(this, slotClass(slot)), connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
    }

    private fun stopProfile(profileId: String) {
        val engine = sessions[profileId] ?: return
        stopPublicTunnel(profileId)
        EngineProtocol.send(engine.messenger, EngineProtocol.STOP, callback)
        scope.launch { repository.updateRuntime(profileId, ServerState.STOPPING, 30_000 + engine.slot) }
    }

    private fun sendCommand(profileId: String, raw: String) {
        val command = raw.trim().removePrefix("/").trim()
        if (command.isBlank()) return
        val engine = sessions[profileId] ?: return
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(logs = (previous.logs + "> /$command").takeLast(2_000))
        }
        EngineProtocol.send(engine.messenger, EngineProtocol.COMMAND, callback) {
            putString(EngineProtocol.KEY_PROFILE_ID, profileId)
            putString(EngineProtocol.KEY_TEXT, command)
        }
    }

    private fun engineEnded(profileId: String, state: String) {
        stopPublicTunnel(profileId)
        logcatTails.remove(profileId)?.cancel()
        val engine = sessions.remove(profileId)
        if (engine != null) runCatching { unbindService(engine.connection) }
        val serverState = runCatching { ServerState.valueOf(state) }.getOrDefault(ServerState.CRASHED)
        scope.launch { repository.updateRuntime(profileId, serverState, null) }
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(
                state = state,
                localPort = 0,
                logs = (previous.logs + "State: ${state.lowercase().replaceFirstChar(Char::uppercase)}").takeLast(2_000),
            )
        }
        updateNotification()
        if (sessions.isEmpty()) stopSelf()
    }

    private fun startPublicTunnel(profileId: String, leaseJson: String) {
        val snapshot = RuntimeRegistry.sessions.value[profileId] ?: return
        if (snapshot.localPort <= 0 || snapshot.state !in setOf("STARTING", "RUNNING")) return
        if (tunnels.containsKey(profileId)) return
        val lease = runCatching { Json.decodeFromString<TunnelLease>(leaseJson) }.getOrElse { error ->
            RuntimeRegistry.update(profileId) { previous ->
                previous?.copy(logs = (previous.logs + "Public tunnel failed: ${error.message}").takeLast(2_000))
            }
            return
        }
        val frp = FrpSupervisor(this)
        runCatching {
            frp.start(FrpLeaseConfiguration(
                leaseId = lease.id,
                sessionToken = lease.sessionToken,
                serverHost = lease.frpServerHost,
                serverPort = lease.frpServerPort,
                localPort = snapshot.localPort,
                remotePort = lease.publicPort,
            ))
        }.onFailure { error ->
            scope.launch { runCatching { controlPlane.release(lease.id) } }
            RuntimeRegistry.update(profileId) { previous ->
                previous?.copy(logs = (previous.logs + "Public tunnel failed: ${error.message}").takeLast(2_000))
            }
            return
        }
        val expiry = scope.launch {
            val delayMs = Instant.parse(lease.expiresAt).toEpochMilli() - System.currentTimeMillis()
            delay(delayMs.coerceAtLeast(0))
            stopPublicTunnel(profileId)
        }
        tunnels[profileId] = BoundTunnel(frp, lease.id, expiry)
        scope.launch { repository.updatePublic(profileId, true, lease.publicHost, lease.publicPort) }
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(
                publicHost = lease.publicHost,
                publicPort = lease.publicPort,
                publicExpiresAt = lease.expiresAt,
                logs = (previous.logs + "Public tunnel active at ${lease.publicHost}:${lease.publicPort}").takeLast(2_000),
            )
        }
        updateNotification()
    }

    private fun stopPublicTunnel(profileId: String) {
        val tunnel = tunnels.remove(profileId)
        if (tunnel == null) {
            scope.launch { repository.updatePublic(profileId, false, null, null) }
            RuntimeRegistry.update(profileId) { previous ->
                previous?.copy(publicHost = null, publicPort = null, publicExpiresAt = null)
            }
            return
        }
        tunnel.expiry.cancel()
        tunnel.frp.stop()
        scope.launch {
            runCatching { controlPlane.release(tunnel.leaseId) }
            repository.updatePublic(profileId, false, null, null)
        }
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(publicHost = null, publicPort = null, publicExpiresAt = null)
        }
        updateNotification()
    }

    private inner class CallbackHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val profileId = message.data.getString(EngineProtocol.KEY_PROFILE_ID)
                ?: sessions.entries.firstOrNull { it.value.messenger == message.replyTo }?.key
                ?: message.data.getString(EXTRA_PROFILE_ID)
                ?: sessions.keys.singleOrNull()
                ?: return
            when (message.what) {
                EngineProtocol.STATE -> {
                    val state = message.data.getString(EngineProtocol.KEY_STATE).orEmpty()
                    if (state == "STOPPED" || state == "CRASHED") engineEnded(profileId, state)
                    else updateEngineState(profileId, state)
                }
                EngineProtocol.LOG -> recordEngineLine(profileId, message.data.getString(EngineProtocol.KEY_TEXT).orEmpty())
                EngineProtocol.PLAYER_JOIN -> playerJoined(profileId, message.data.getString(EngineProtocol.KEY_TEXT).orEmpty())
                EngineProtocol.PLAYER_LEAVE -> playerLeft(profileId, message.data.getString(EngineProtocol.KEY_TEXT).orEmpty())
            }
        }
    }

    private fun updateEngineState(profileId: String, state: String) {
        val engine = sessions[profileId]
        val port = engine?.let { 30_000 + it.slot }
            ?: RuntimeRegistry.sessions.value[profileId]?.localPort?.takeIf { it > 0 }
            ?: 0
        RuntimeRegistry.update(profileId) { previous ->
            val logs = if (previous?.state == state) {
                previous.logs
            } else {
                (previous?.logs.orEmpty() + "State: ${state.lowercase().replaceFirstChar(Char::uppercase)}").takeLast(2_000)
            }
            previous?.copy(state = state, localPort = port, logs = logs) ?: RuntimeSnapshot(
                profileId = profileId,
                state = state,
                slot = engine?.slot ?: -1,
                localPort = port,
                logs = logs,
            )
        }
        val persistedState = runCatching { ServerState.valueOf(state) }.getOrNull()
        if (persistedState != null) {
            scope.launch { repository.updateRuntime(profileId, persistedState, port.takeIf { it > 0 }) }
        }
    }

    private fun appendLog(profileId: String, line: String) {
        val normalized = line.trimEnd()
        if (normalized.isBlank()) return
        val now = System.currentTimeMillis()
        val duplicate = recentLogLines[profileId]?.let { (previousLine, previousAt) ->
            previousLine == normalized && now - previousAt < 800L
        } == true
        if (duplicate) return
        recentLogLines[profileId] = normalized to now
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(logs = (previous.logs + normalized).takeLast(2_000)) ?: RuntimeSnapshot(
                profileId = profileId,
                state = "STARTING",
                slot = sessions[profileId]?.slot ?: -1,
                localPort = sessions[profileId]?.let { 30_000 + it.slot } ?: 0,
                logs = listOf(normalized),
            )
        }
    }

    private fun recordEngineLine(profileId: String, rawLine: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { recordEngineLine(profileId, rawLine) }
            return
        }
        val line = rawLine.stripAndroidLogPrefix().trimEnd()
        appendLog(profileId, line)
        if (line.contains("listening on", ignoreCase = true) || line.contains("Server for gameid", ignoreCase = true)) {
            updateEngineState(profileId, "RUNNING")
        }
        line.playerNameBefore(" joins game")?.let { playerJoined(profileId, it) }
        line.playerNameBefore(" leaves game")?.let { playerLeft(profileId, it) }
        val players = line.substringAfter("List of players:", missingDelimiterValue = "")
        if (players.isNotBlank()) {
            players.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { playerJoined(profileId, it) }
        }
    }

    private fun playerJoined(profileId: String, rawName: String) {
        val name = rawName.cleanPlayerName()
        if (name.isBlank()) return
        sessions[profileId]?.emptySince = null
        RuntimeRegistry.update(profileId) { previous ->
            previous?.copy(players = previous.players + name)
        }
    }

    private fun playerLeft(profileId: String, rawName: String) {
        val name = rawName.cleanPlayerName()
        if (name.isBlank()) return
        RuntimeRegistry.update(profileId) { previous ->
            val remaining = previous?.players.orEmpty() - name
            if (remaining.isEmpty()) sessions[profileId]?.emptySince = System.currentTimeMillis()
            previous?.copy(players = remaining)
        }
    }

    private fun startLogcatTail(profileId: String, slot: Int) {
        logcatTails.remove(profileId)?.cancel()
        logcatTails[profileId] = scope.launch {
            val pid = waitForEnginePid(slot)
            if (pid == null) {
                recordEngineLine(profileId, "Logcat console tail unavailable: engine process :engine$slot was not visible.")
                return@launch
            }
            val process = runCatching {
                ProcessBuilder(
                    "logcat",
                    "-v", "time",
                    "-T", "1",
                    "--pid=$pid",
                    "Luanti:I",
                    "Minetest:I",
                    "*:S",
                ).redirectErrorStream(true).start()
            }.getOrElse { error ->
                recordEngineLine(profileId, "Logcat console tail unavailable: ${error.message ?: error.javaClass.simpleName}")
                return@launch
            }
            currentCoroutineContext()[Job]?.invokeOnCompletion { process.destroy() }
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        recordEngineLine(profileId, line)
                    }
                }
            }
            process.destroy()
        }
    }

    private suspend fun waitForEnginePid(slot: Int): Int? {
        repeat(40) {
            enginePid(slot)?.let { return it }
            delay(250)
        }
        return enginePid(slot)
    }

    private fun enginePid(slot: Int): Int? {
        val expected = "$packageName:engine$slot"
        return getSystemService(ActivityManager::class.java)
            .runningAppProcesses
            ?.firstOrNull { process -> process.processName == expected }
            ?.pid
    }

    private fun writeConfig(file: File, name: String, port: Int, maxPlayers: Int, creative: Boolean, damage: Boolean, pvp: Boolean) {
        file.writeText("""
            port = $port
            bind_address = 0.0.0.0
            server_name = ${name.replace("\n", " ")}
            max_users = $maxPlayers
            creative_mode = $creative
            enable_damage = $damage
            enable_pvp = $pvp
            server_announce = false
            secure.enable_security = true
            secure.trusted_mods =
            secure.http_mods =
            disallow_empty_password = true
        """.trimIndent())
    }

    private fun writeWorldConfig(world: File, gameKey: String?, packages: List<InstalledPackageEntity>, modsRoot: File) {
        val existing = File(world, "world.mt")
        val preserved = linkedMapOf<String, String>()
        if (existing.isFile) {
            existing.readLines().forEach { line ->
                val index = line.indexOf('=')
                if (index > 0) {
                    preserved[line.substring(0, index).trim()] = line.substring(index + 1).trim()
                }
            }
        }
        preserved["backend"] = preserved["backend"] ?: "sqlite3"
        gameKey?.substringAfter('/')?.takeIf { it.isNotBlank() }?.let { preserved["gameid"] = it }
        preserved.keys.filter { it.startsWith("load_mod_") }.toList().forEach { preserved.remove(it) }
        enabledModNames(packages, modsRoot).forEach { modName ->
            preserved["load_mod_$modName"] = "true"
        }
        existing.writeText(preserved.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key = $value" })
    }

    private fun enabledModNames(packages: List<InstalledPackageEntity>, modsRoot: File): List<String> {
        val names = linkedSetOf<String>()
        packages.filter { it.enabled && (it.type == PackageType.MOD || it.type == PackageType.MODPACK) }.forEach { item ->
            val folderName = item.packageKey.substringAfter('/').safeFolderName()
            val directory = File(modsRoot, folderName)
            if (item.type == PackageType.MODPACK && directory.isDirectory) {
                directory.listFiles()?.filter { it.isDirectory }?.forEach { child ->
                    val detected = modName(child)
                    if (detected != null) names += detected
                }
            } else {
                names += modName(directory) ?: folderName.safeModName()
            }
        }
        return names.toList()
    }

    private fun modName(directory: File): String? {
        val conf = File(directory, "mod.conf")
        if (conf.isFile) {
            conf.readLines().firstOrNull { it.trimStart().startsWith("name") }?.let { line ->
                val value = line.substringAfter('=', "").trim().safeModName()
                if (value.isNotBlank()) return value
            }
        }
        return directory.name.safeModName().takeIf { it.isNotBlank() }
    }

    private fun String.safeModName(): String = filter { it.isLetterOrDigit() || it == '_' }.take(80)
    private fun String.safeFolderName(): String = filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(80)

    private fun slotClass(slot: Int) = arrayOf(
        EngineSlot0Service::class.java, EngineSlot1Service::class.java, EngineSlot2Service::class.java,
        EngineSlot3Service::class.java, EngineSlot4Service::class.java,
    )[slot]

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_luanet).setContentTitle("LuaNet").setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true).build()

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification("${sessions.size} server(s), ${tunnels.size} public tunnel(s) active"),
        )
    }

    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LuaNet:servers").apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LuaNet:tunnel").apply { acquire() }
    }

    private val idleCheck = object : Runnable {
        override fun run() {
            val activeSessions = sessions.toMap()
            scope.launch {
                val now = System.currentTimeMillis()
                activeSessions.forEach { (profileId, engine) ->
                    val profile = repository.profile(profileId) ?: return@forEach
                    if (!profile.autoOffEnabled) return@forEach
                    val idleFor = engine.emptySince?.let(now::minus) ?: return@forEach
                    if (idleFor >= profile.autoOffMinutes * 60_000L) {
                        mainHandler.post { stopProfile(profileId) }
                    }
                }
            }
            mainHandler.postDelayed(this, 60_000)
        }
    }

    private data class BoundEngine(
        val slot: Int,
        val messenger: Messenger,
        val connection: ServiceConnection,
        val startedAt: Long,
        var emptySince: Long?,
    )

    private data class BoundTunnel(
        val frp: FrpSupervisor,
        val leaseId: String,
        val expiry: Job,
    )

    companion object {
        private const val CHANNEL_ID = "hosted_servers"
        private const val NOTIFICATION_ID = 100
        private const val MAX_RUNTIME_ASSET_BYTES = 32L * 1024L * 1024L
        const val EXTRA_PROFILE_ID = "profile_id"
        const val ACTION_START = "net.novax.luanet.START"
        const val ACTION_STOP = "net.novax.luanet.STOP"
        const val ACTION_COMMAND = "net.novax.luanet.COMMAND"
        const val ACTION_PUBLIC_START = "net.novax.luanet.PUBLIC_START"
        const val ACTION_PUBLIC_STOP = "net.novax.luanet.PUBLIC_STOP"
        const val ACTION_STOP_ALL = "net.novax.luanet.STOP_ALL"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_LEASE = "lease"

        fun start(context: Context, profileId: String) = ContextCompat.startForegroundService(
            context, Intent(context, OrchestratorService::class.java).setAction(ACTION_START).putExtra(EXTRA_PROFILE_ID, profileId)
        )
        fun stop(context: Context, profileId: String) = ContextCompat.startForegroundService(
            context, Intent(context, OrchestratorService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_PROFILE_ID, profileId)
        )
        fun command(context: Context, profileId: String, command: String) = ContextCompat.startForegroundService(
            context, Intent(context, OrchestratorService::class.java)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_COMMAND, command)
        )
        fun startPublic(context: Context, profileId: String, lease: TunnelLease) = ContextCompat.startForegroundService(
            context, Intent(context, OrchestratorService::class.java)
                .setAction(ACTION_PUBLIC_START)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_LEASE, Json.encodeToString(lease))
        )
        fun stopPublic(context: Context, profileId: String) = ContextCompat.startForegroundService(
            context, Intent(context, OrchestratorService::class.java)
                .setAction(ACTION_PUBLIC_STOP)
                .putExtra(EXTRA_PROFILE_ID, profileId)
        )
    }
}

private fun String.stripAndroidLogPrefix(): String {
    val close = indexOf("): ")
    return if (close >= 0) substring(close + 3) else this
}

private fun String.playerNameBefore(marker: String): String? {
    val markerAt = indexOf(marker).takeIf { it >= 0 } ?: return null
    val actionPrefix = lastIndexOf("]:", startIndex = markerAt)
    val start = if (actionPrefix >= 0) actionPrefix + 2 else lastIndexOf(' ', startIndex = (markerAt - 1).coerceAtLeast(0)) + 1
    return substring(start.coerceAtLeast(0), markerAt)
        .substringBefore(" [")
        .cleanPlayerName()
        .takeIf { it.isNotBlank() }
}

private fun String.cleanPlayerName(): String =
    trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
