package net.novax.luanet.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeSnapshot(
    val profileId: String,
    val state: String,
    val slot: Int,
    val localPort: Int,
    val players: Set<String> = emptySet(),
    val logs: List<String> = emptyList(),
    val publicHost: String? = null,
    val publicPort: Int? = null,
    val publicExpiresAt: String? = null,
)

object RuntimeRegistry {
    private val mutable = MutableStateFlow<Map<String, RuntimeSnapshot>>(emptyMap())
    val sessions: StateFlow<Map<String, RuntimeSnapshot>> = mutable.asStateFlow()

    fun update(profileId: String, transform: (RuntimeSnapshot?) -> RuntimeSnapshot?) {
        val copy = mutable.value.toMutableMap()
        val updated = transform(copy[profileId])
        if (updated == null) copy.remove(profileId) else copy[profileId] = updated
        mutable.value = copy
    }
}
