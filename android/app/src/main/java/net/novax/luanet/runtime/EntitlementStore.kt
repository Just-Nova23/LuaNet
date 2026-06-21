package net.novax.luanet.runtime

import android.content.Context
import net.novax.luanet.domain.SubscriptionTier

class EntitlementStore(context: Context) {
    private val preferences = context.getSharedPreferences("entitlement", Context.MODE_PRIVATE)

    fun current(now: Long = System.currentTimeMillis()): SubscriptionTier {
        val expiresAt = preferences.getLong("expires_at", 0)
        return if (preferences.getString("tier", "free") == "premium" && expiresAt > now) {
            SubscriptionTier.PREMIUM
        } else SubscriptionTier.FREE
    }

    fun update(tier: SubscriptionTier, expiresAt: Long) {
        preferences.edit().putString("tier", tier.name.lowercase()).putLong("expires_at", expiresAt).apply()
    }
}
