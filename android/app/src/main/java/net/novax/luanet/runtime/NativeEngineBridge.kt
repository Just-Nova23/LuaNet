package net.novax.luanet.runtime

import androidx.annotation.Keep

@Keep
class NativeEngineBridge(private val listener: Listener) {
    interface Listener {
        fun onLog(level: Int, line: String)
        fun onPlayerJoined(name: String)
        fun onPlayerLeft(name: String)
        fun onReady()
    }

    fun load(libraryName: String) = System.loadLibrary(libraryName)

    external fun run(configurationJson: String): Int
    external fun requestStop()
    external fun submitCommand(command: String)

    @Keep private fun emitLog(level: Int, line: String) = listener.onLog(level, line)
    @Keep private fun emitPlayerJoined(name: String) = listener.onPlayerJoined(name)
    @Keep private fun emitPlayerLeft(name: String) = listener.onPlayerLeft(name)
    @Keep private fun emitReady() = listener.onReady()
}

