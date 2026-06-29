package net.novax.luanet.runtime

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlin.concurrent.thread
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

abstract class EngineSlotService : Service(), NativeEngineBridge.Listener {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val processExitScheduled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: Messenger? = null
    private var bridge: NativeEngineBridge? = null
    private var profileId: String? = null
    private val messenger = Messenger(IncomingHandler())

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        bridge?.requestStop()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            callback = message.replyTo ?: callback
            when (message.what) {
                EngineProtocol.START -> {
                    profileId = message.data.getString(EngineProtocol.KEY_PROFILE_ID)
                    start(message.data.getString(EngineProtocol.KEY_CONFIG).orEmpty())
                }
                EngineProtocol.STOP -> bridge?.requestStop()
                EngineProtocol.COMMAND -> bridge?.submitCommand(message.data.getString(EngineProtocol.KEY_TEXT).orEmpty())
                else -> super.handleMessage(message)
            }
        }
    }

    private fun start(configurationJson: String) {
        if (!running.compareAndSet(false, true)) return
        ready.set(false)
        val configuration = runCatching { Json.decodeFromString<EngineConfiguration>(configurationJson) }.getOrElse { error ->
            onLog(3, error.message ?: error.javaClass.simpleName)
            sendState("CRASHED")
            running.set(false)
            stopSelf()
            return
        }
        profileId = configuration.profileId
        sendState("STARTING")
        onLog(1, "Starting Luanti ${configuration.engineVersion} on local UDP port ${configuration.localPort}")
        executor.execute {
            try {
                val native = NativeEngineBridge(this).also { bridge = it }
                native.load(configuration.libraryName)
                mainHandler.postDelayed({
                    if (running.get() && ready.compareAndSet(false, true)) {
                        onLog(1, "Engine process is alive; marking server running while Luanti continues loading.")
                        sendState("RUNNING")
                    }
                }, READY_FALLBACK_MS)
                val exitCode = native.run(Json.encodeToString(configuration))
                sendState(if (exitCode == 0) "STOPPED" else "CRASHED")
            } catch (error: Throwable) {
                onLog(3, error.message ?: error.javaClass.simpleName)
                sendState("CRASHED")
            } finally {
                running.set(false)
                bridge = null
                mainHandler.removeCallbacksAndMessages(null)
                stopSelf()
                scheduleProcessExitAfterCleanNativeReturn()
            }
        }
    }

    private fun scheduleProcessExitAfterCleanNativeReturn() {
        if (!processExitScheduled.compareAndSet(false, true)) return
        thread(name = "LuaNetEngineProcessExit", isDaemon = false) {
            runCatching { Thread.sleep(PROCESS_EXIT_DELAY_MS) }
            exitProcess(0)
        }
    }

    private fun sendState(state: String) = send(EngineProtocol.STATE) { putString(EngineProtocol.KEY_STATE, state) }
    private fun send(what: Int, values: android.os.Bundle.() -> Unit) {
        callback?.let {
            EngineProtocol.send(it, what) {
                putString(EngineProtocol.KEY_PROFILE_ID, profileId)
                values()
            }
        }
    }

    override fun onLog(level: Int, line: String) = send(EngineProtocol.LOG) {
        putInt("level", level)
        putString(EngineProtocol.KEY_TEXT, line.take(8_192))
    }
    override fun onPlayerJoined(name: String) = send(EngineProtocol.PLAYER_JOIN) { putString(EngineProtocol.KEY_TEXT, name) }
    override fun onPlayerLeft(name: String) = send(EngineProtocol.PLAYER_LEAVE) { putString(EngineProtocol.KEY_TEXT, name) }
    override fun onReady() {
        if (ready.compareAndSet(false, true)) {
            onLog(1, "Luanti reported server ready")
        }
        sendState("RUNNING")
    }

    companion object {
        private const val READY_FALLBACK_MS = 8_000L
        private const val PROCESS_EXIT_DELAY_MS = 250L
    }
}

class EngineSlot0Service : EngineSlotService()
class EngineSlot1Service : EngineSlotService()
class EngineSlot2Service : EngineSlotService()
class EngineSlot3Service : EngineSlotService()
class EngineSlot4Service : EngineSlotService()
