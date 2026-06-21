package net.novax.luanet.runtime

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import kotlinx.serialization.Serializable

object EngineProtocol {
    const val START = 1
    const val STOP = 2
    const val COMMAND = 3
    const val STATE = 10
    const val LOG = 11
    const val PLAYER_JOIN = 12
    const val PLAYER_LEAVE = 13

    const val KEY_CONFIG = "config"
    const val KEY_TEXT = "text"
    const val KEY_STATE = "state"

    fun send(target: Messenger, what: Int, replyTo: Messenger? = null, values: Bundle.() -> Unit = {}) {
        target.send(Message.obtain(null, what).apply {
            this.replyTo = replyTo
            data = Bundle().apply(values)
        })
    }
}

@Serializable
data class EngineConfiguration(
    val profileId: String,
    val engineVersion: String,
    val libraryName: String,
    val profilePath: String,
    val worldPath: String,
    val configPath: String,
    val localPort: Int,
    val gameId: String?,
    val consoleAdmin: String,
)

