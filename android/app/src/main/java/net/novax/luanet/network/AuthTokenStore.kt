package net.novax.luanet.network

import android.content.Context
import java.util.UUID

class AuthTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("novax_auth", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
            val created = UUID.randomUUID().toString()
            preferences.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    fun bearerToken(): String? = preferences.getString(KEY_BEARER_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun requireBearerToken(): String = bearerToken() ?: error("Sign in before using external links")

    fun updateBearerToken(value: String) {
        preferences.edit().putString(KEY_BEARER_TOKEN, value.trim()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_BEARER_TOKEN).apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BEARER_TOKEN = "bearer_token"
    }
}
