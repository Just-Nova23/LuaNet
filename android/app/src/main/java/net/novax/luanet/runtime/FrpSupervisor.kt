package net.novax.luanet.runtime

import android.content.Context
import java.io.File

data class FrpLeaseConfiguration(
    val leaseId: String,
    val sessionToken: String,
    val serverHost: String,
    val serverPort: Int,
    val localPort: Int,
    val remotePort: Int,
)

class FrpSupervisor(private val context: Context) {
    private var process: Process? = null
    private var config: File? = null

    @Synchronized fun start(lease: FrpLeaseConfiguration) {
        check(process?.isAlive != true) { "FRP is already running" }
        val executable = File(context.applicationInfo.nativeLibraryDir, "libfrpc.so")
        require(executable.canExecute()) { "Bundled FRP client is unavailable" }
        val file = File(context.cacheDir, "frpc-${lease.leaseId}.toml")
        file.writeText(render(lease))
        config = file
        process = ProcessBuilder(executable.absolutePath, "-c", file.absolutePath)
            .redirectErrorStream(true)
            .start()
    }

    @Synchronized fun stop() {
        process?.destroy()
        if (process?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) == false) process?.destroyForcibly()
        process = null
        config?.delete()
        config = null
    }

    private fun render(value: FrpLeaseConfiguration) = """
        user = "${safe(value.leaseId)}"
        serverAddr = "${safe(value.serverHost)}"
        serverPort = ${value.serverPort}
        loginFailExit = false
        transport.tls.enable = true
        transport.wireProtocol = "v2"
        metadatas.session_token = "${safe(value.sessionToken)}"
        log.to = "console"
        log.level = "warn"

        [[proxies]]
        name = "luanti"
        type = "udp"
        localIP = "127.0.0.1"
        localPort = ${value.localPort}
        remotePort = ${value.remotePort}
        transport.bandwidthLimit = "10MB"
        transport.bandwidthLimitMode = "server"
    """.trimIndent()

    private fun safe(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9._:-]+"))) { "Unsafe FRP configuration value" }
        return value
    }
}

