package net.novax.luanet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.domain.EngineCatalog

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LuaNetApplication).container.servers
    val profiles = repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(
        name: String,
        engineVersion: String = EngineCatalog.latest.version,
        maxPlayers: Int = 8,
        creative: Boolean = false,
        damage: Boolean = true,
        pvp: Boolean = true,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val profile = repository.create(name, engineVersion, maxPlayers, creative, damage, pvp)
            onCreated(profile.id)
        }
    }
}

