package net.novax.luanet.runtime

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

abstract class EngineSlotService : Service(), NativeEngineBridge.Listener {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var callback: Messenger? = null
    private var bridge: NativeEngineBridge? = null
    private var profileId: String? = null
    private val messenger = Messenger(IncomingHandler())

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        bridge?.requestStop()
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
        val configuration = runCatching { Json.decodeFromString<EngineConfiguration>(configurationJson) }.getOrElse { error ->
            onLog(3, error.message ?: error.javaClass.simpleName)
            sendState("CRASHED")
            running.set(false)
            stopSelf()
            return
        }
        profileId = configuration.profileId
        sendState("STARTING")
        executor.execute {
            try {
                val native = NativeEngineBridge(this).also { bridge = it }
                native.load(configuration.libraryName)
                val exitCode = native.run(Json.encodeToString(configuration))
                sendState(if (exitCode == 0) "STOPPED" else "CRASHED")
            } catch (error: Throwable) {
                onLog(3, error.message ?: error.javaClass.simpleName)
                sendState("CRASHED")
            } finally {
                running.set(false)
                bridge = null
                stopSelf()
            }
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
    override fun onReady() = sendState("RUNNING")
}

class EngineSlot0Service : EngineSlotService()
class EngineSlot1Service : EngineSlotService()
class EngineSlot2Service : EngineSlotService()
class EngineSlot3Service : EngineSlotService()
class EngineSlot4Service : EngineSlotService()
