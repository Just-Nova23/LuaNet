package net.novax.luanet.runtime

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.novax.luanet.monetization.AdvertisementGate
import net.novax.luanet.network.ControlPlaneClient
import net.novax.luanet.network.TunnelLease
import java.time.Instant
import java.util.UUID

class PublicTunnelController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val api: ControlPlaneClient,
    private val ads: AdvertisementGate,
    private val frp: FrpSupervisor,
    private val deviceId: String = UUID.randomUUID().toString(),
) {
    private var active: TunnelLease? = null
    private var expiryJob: Job? = null

    suspend fun start(profileId: String, localPort: Int, premium: Boolean, onExpired: () -> Unit): TunnelLease {
        check(active == null) { "A public tunnel is already active" }
        val hold = api.createHold(deviceId, profileId)
        if (!premium) ads.showBeforePublicStart(activity)
        val lease = api.activateHold(hold.id)
        frp.start(FrpLeaseConfiguration(
            lease.id, lease.sessionToken, lease.frpServerHost, lease.frpServerPort, localPort, lease.publicPort,
        ))
        active = lease
        if (!premium) {
            expiryJob = scope.launch {
                val remaining = Instant.parse(lease.expiresAt).toEpochMilli() - System.currentTimeMillis()
                delay(remaining.coerceAtLeast(0))
                stop()
                onExpired()
            }
        }
        return lease
    }

    suspend fun stop() {
        expiryJob?.cancel()
        expiryJob = null
        frp.stop()
        active?.let { runCatching { api.release(it.id) } }
        active = null
    }
}

