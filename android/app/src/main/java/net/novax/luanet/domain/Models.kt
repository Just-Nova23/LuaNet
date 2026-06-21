package net.novax.luanet.domain

enum class ServerState { STOPPED, STARTING, RUNNING, STOPPING, CRASHED }
enum class AccessMode { OPEN, ALLOWLIST }
enum class PackageType { GAME, MOD, MODPACK }
enum class PackageSource { CONTENT_DB, MANUAL_ZIP }
enum class SubscriptionTier { FREE, PREMIUM }

data class RuntimeLimits(
    val activeServers: Int,
    val publicSessionMillis: Long?,
    val idleTimeoutMillis: Long?,
    val showAds: Boolean,
)

object EntitlementPolicy {
    val free = RuntimeLimits(
        activeServers = 1,
        publicSessionMillis = 4 * 60 * 60 * 1000L,
        idleTimeoutMillis = 15 * 60 * 1000L,
        showAds = true,
    )
    val premium = RuntimeLimits(
        activeServers = 5,
        publicSessionMillis = null,
        idleTimeoutMillis = null,
        showAds = false,
    )

    fun forTier(tier: SubscriptionTier) = if (tier == SubscriptionTier.PREMIUM) premium else free
}

