package net.novax.luanet.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.data.importer.ImportKind

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LuaNetApplication).container
    private val repository = container.servers
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

    fun updateAutoOff(profileId: String, enabled: Boolean, minutes: Int) {
        viewModelScope.launch { repository.updateAutoOff(profileId, enabled, minutes) }
    }

    fun importArchive(profileId: String, uri: Uri, kind: ImportKind, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                val result = repository.importArchive(profileId, uri, kind)
                "Imported ${result.kind.name.lowercase()} (${result.bytesWritten / 1024} KiB)"
            })
        }
    }

    fun createBackup(profileId: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                val profile = requireNotNull(repository.profile(profileId)) { "Server profile not found" }
                require(profile.state.name in setOf("STOPPED", "CRASHED")) { "Stop the server before creating a backup" }
                val backup = container.backups.create(
                    profileId, repository.profileDirectory(profileId), "Manual backup", automatic = false,
                )
                "Backup created (${backup.sizeBytes / 1024} KiB)"
            })
        }
    }
}
