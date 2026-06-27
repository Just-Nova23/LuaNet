package net.novax.luanet.runtime

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.R
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.EntitlementPolicy
import net.novax.luanet.domain.ServerState
import net.novax.luanet.network.TunnelLease
import java.io.File
import java.time.Instant

class OrchestratorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = mutableMapOf<String, BoundEngine>()
    private val tunnels = mutableMapOf<String, BoundTunnel>()
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
        val library = File(applicationInfo.nativeLibraryDir, "lib${release.libraryName}.so")
        if (!library.isFile) {
            val text = "Engine ${profile.engineVersion} is not bundled in this APK. Build native artifacts and sync them before starting this server."
            repository.updateRuntime(profileId, ServerState.CRASHED, null)
            RuntimeRegistry.update(profileId) { RuntimeSnapshot(profileId, "CRASHED", -1, 0, logs = listOf(text)) }
            return
        }
        val root = repository.profileDirectory(profile.id).apply { mkdirs() }
        val world = File(root, "world").apply { mkdirs() }
        val config = File(root, "minetest.conf")
        writeConfig(config, profile.name, port, profile.maxPlayers, profile.creative, profile.damage, profile.pvp)
        val engineConfiguration = EngineConfiguration(
            profile.id, profile.engineVersion, release.libraryName, root.absolutePath,
            world.absolutePath, config.absolutePath, port, profile.gameKey?.substringAfter('/'),
            "__luanet_console_${profile.id.take(8)}",
        )
        repository.updateRuntime(profile.id, ServerState.STARTING, port)
        RuntimeRegistry.update(profile.id) { RuntimeSnapshot(profile.id, "STARTING", slot, port) }
        bindEngine(profile.id, slot, engineConfiguration)
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
        val engine = sessions.remove(profileId)
        if (engine != null) runCatching { unbindService(engine.connection) }
        val serverState = runCatching { ServerState.valueOf(state) }.getOrDefault(ServerState.CRASHED)
        scope.launch { repository.updateRuntime(profileId, serverState, null) }
        RuntimeRegistry.update(profileId) { previous -> previous?.copy(state = state, localPort = 0) }
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
                    else RuntimeRegistry.update(profileId) { it?.copy(state = state) }
                }
                EngineProtocol.LOG -> RuntimeRegistry.update(profileId) { previous ->
                    previous?.copy(logs = (previous.logs + message.data.getString(EngineProtocol.KEY_TEXT).orEmpty()).takeLast(2_000))
                }
                EngineProtocol.PLAYER_JOIN -> RuntimeRegistry.update(profileId) { previous ->
                    sessions[profileId]?.emptySince = null
                    previous?.copy(players = previous.players + message.data.getString(EngineProtocol.KEY_TEXT).orEmpty())
                }
                EngineProtocol.PLAYER_LEAVE -> RuntimeRegistry.update(profileId) { previous ->
                    val remaining = previous?.players.orEmpty() - message.data.getString(EngineProtocol.KEY_TEXT).orEmpty()
                    if (remaining.isEmpty()) sessions[profileId]?.emptySince = System.currentTimeMillis()
                    previous?.copy(players = remaining)
                }
            }
        }
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
