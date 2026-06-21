package net.novax.luanet.monetization

import android.app.Activity

fun interface AdvertisementGate {
    /** Returns after an interstitial closes. Ad load failure must not block hosting. */
    suspend fun showBeforePublicStart(activity: Activity)
}

interface BillingGateway {
    suspend fun purchaseMonthly(activity: Activity): String
    suspend fun purchaseYearly(activity: Activity): String
    suspend fun restorePurchaseTokens(): List<String>
}

class NoAdvertisement : AdvertisementGate {
    override suspend fun showBeforePublicStart(activity: Activity) = Unit
}

