package net.novax.luanet.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.novax.luanet.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun interface AuthTokenProvider { suspend fun token(): String }

@Serializable data class TunnelHold(
    val id: String,
    val port: Int,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable data class TunnelLease(
    val id: String,
    val tier: String,
    @SerialName("public_host") val publicHost: String,
    @SerialName("public_port") val publicPort: Int,
    @SerialName("frp_server_host") val frpServerHost: String,
    @SerialName("frp_server_port") val frpServerPort: Int,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable data class EntitlementResponse(
    val tier: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

class ControlPlaneClient(
    private val tokens: AuthTokenProvider,
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = BuildConfig.CONTROL_PLANE_URL.trimEnd('/'),
) {
    suspend fun createHold(deviceId: String, profileId: String): TunnelHold = post(
        "/v1/tunnel-holds", HoldRequest(deviceId, profileId)
    )

    suspend fun activateHold(holdId: String): TunnelLease = post(
        "/v1/tunnel-leases", ActivateRequest(holdId)
    )

    suspend fun entitlement(): EntitlementResponse = get("/v1/entitlement")

    suspend fun release(leaseId: String) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$baseUrl/v1/tunnel-leases/$leaseId").delete().build()).close()
    }

    suspend fun verifyPurchase(token: String): EntitlementResponse = post(
        "/v1/billing/google/verify", PurchaseRequest(token)
    )

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$baseUrl/v1/account").delete().build()).close()
    }

    private suspend inline fun <reified RequestType, reified ResponseType> post(path: String, body: RequestType): ResponseType =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(baseUrl + path)
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE)).build()
            execute(request).use { response -> json.decodeFromString(response.body?.string() ?: error("Empty response")) }
        }

    private suspend inline fun <reified ResponseType> get(path: String): ResponseType = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(baseUrl + path).get().build()).use { response ->
            json.decodeFromString(response.body?.string() ?: error("Empty response"))
        }
    }

    private suspend fun execute(unsigned: Request): okhttp3.Response {
        val signed = unsigned.newBuilder().header("Authorization", "Bearer ${tokens.token()}")
            .header("User-Agent", "LuaNet/${BuildConfig.VERSION_NAME}").build()
        val response = http.newCall(signed).execute()
        if (!response.isSuccessful) {
            val message = response.body?.string()?.trim().orEmpty()
            response.close()
            error("Control plane ${response.code}: $message")
        }
        return response
    }

    @Serializable private data class HoldRequest(@SerialName("device_id") val deviceId: String, @SerialName("profile_id") val profileId: String)
    @Serializable private data class ActivateRequest(@SerialName("hold_id") val holdId: String)
    @Serializable private data class PurchaseRequest(@SerialName("purchase_token") val purchaseToken: String)

    companion object { private val JSON_MEDIA_TYPE = "application/json".toMediaType() }
}

