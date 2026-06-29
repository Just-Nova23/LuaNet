package net.novax.luanet.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import net.novax.luanet.BuildConfig
import kotlin.coroutines.resume

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

class AdMobAdvertisementGate(context: Context) : AdvertisementGate {
    init {
        MobileAds.initialize(context)
    }

    override suspend fun showBeforePublicStart(activity: Activity) {
        requestConsent(activity)
        showInterstitial(activity)
    }

    private suspend fun requestConsent(activity: Activity) = suspendCancellableCoroutine { continuation ->
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            },
            {
                if (continuation.isActive) continuation.resume(Unit)
            },
        )
    }

    private suspend fun showInterstitial(activity: Activity) = suspendCancellableCoroutine { continuation ->
        val unitId = BuildConfig.ADMOB_PUBLIC_INTERSTITIAL_ID
        if (unitId.isBlank()) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        InterstitialAd.load(
            activity,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            },
        )
    }
}

class PlayBillingGateway(context: Context) : BillingGateway, PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private var pendingPurchase: CompletableDeferred<String>? = null
    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun purchaseMonthly(activity: Activity): String = purchase(activity, MONTHLY_PRODUCT)

    override suspend fun purchaseYearly(activity: Activity): String = purchase(activity, YEARLY_PRODUCT)

    override suspend fun restorePurchaseTokens(): List<String> {
        connect()
        val result = suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases.map { it.purchaseToken }.distinct())
                } else {
                    continuation.resume(emptyList())
                }
            }
        }
        return result
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val pending = pendingPurchase ?: return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases.orEmpty().firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchase == null) {
                    pending.completeExceptionally(IllegalStateException("No completed Play purchase returned"))
                } else {
                    pending.complete(purchase.purchaseToken)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> pending.completeExceptionally(CancellationException("Purchase cancelled"))
            else -> pending.completeExceptionally(IllegalStateException("Play Billing ${result.responseCode}: ${result.debugMessage}"))
        }
        pendingPurchase = null
    }

    private suspend fun purchase(activity: Activity, productId: String): String {
        connect()
        check(pendingPurchase == null) { "A purchase is already in progress" }
        val details = queryProduct(productId)
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: error("No subscription offer configured for $productId")
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        val deferred = CompletableDeferred<String>()
        pendingPurchase = deferred
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            error("Play Billing ${result.responseCode}: ${result.debugMessage}")
        }
        val token = deferred.await()
        acknowledge(token)
        return token
    }

    private suspend fun queryProduct(productId: String): ProductDetails {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(products)
                    .build(),
            ) { result, productDetails ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails.productDetailsList.firstOrNull()?.let {
                        continuation.resume(it)
                    } ?: continuation.resumeWith(Result.failure(IllegalStateException("Subscription $productId is not available")))
                } else {
                    continuation.resumeWith(Result.failure(IllegalStateException("Play Billing ${result.responseCode}: ${result.debugMessage}")))
                }
            }
        }
    }

    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine { continuation ->
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
        ) {
            continuation.resume(Unit)
        }
    }

    private suspend fun connect() {
        if (client.isReady) return
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWith(Result.failure(IllegalStateException("Play Billing ${result.responseCode}: ${result.debugMessage}")))
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private class CancellationException(message: String) : RuntimeException(message)

    companion object {
        const val MONTHLY_PRODUCT = "luanet_premium_monthly"
        const val YEARLY_PRODUCT = "luanet_premium_yearly"
    }
}
