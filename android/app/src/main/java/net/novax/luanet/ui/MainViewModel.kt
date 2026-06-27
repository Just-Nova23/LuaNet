package net.novax.luanet.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.content.ContentPackage
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LuaNetApplication).container
    private val repository = container.servers
    val profiles = repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _content = MutableStateFlow(ContentBrowserState())
    val content: StateFlow<ContentBrowserState> = _content.asStateFlow()

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

    fun searchContent(profileId: String, type: String, query: String) {
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        _content.value = ContentBrowserState(profileId, type, query, loading = true)
        viewModelScope.launch {
            _content.value = runCatching {
                ContentBrowserState(profileId, type, query,
                    items = container.contentDb.search(type, query, profile.engineVersion, profile.gameKey), loading = false)
            }.getOrElse { ContentBrowserState(profileId, type, query, error = it.message ?: "ContentDB request failed") }
        }
    }

    fun installContent(profileId: String, item: ContentPackage, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                val installed = linkedSetOf<String>()
                installWithDependencies(profileId, item, installed)
                "Installed ${item.title}${if (installed.size > 1) " with ${installed.size - 1} dependencies" else ""}"
            })
        }
    }

    private suspend fun installWithDependencies(profileId: String, item: ContentPackage, visited: MutableSet<String>) {
        check(visited.add(item.key)) { "Cyclic ContentDB dependency involving ${item.key}" }
        container.contentDb.hardDependencies(item.key).forEach { dependency ->
            var packageItem: ContentPackage? = null
            for (key in dependency.candidates.take(12)) {
                val candidate = try { container.contentDb.packageByKey(key) } catch (_: Exception) { null }
                if (candidate?.type == "mod" || candidate?.type == "modpack") {
                    packageItem = candidate
                    break
                }
            }
            // A dependency with game-only candidates is supplied by the installed game.
            if (packageItem != null && packageItem.key !in visited) {
                installWithDependencies(profileId, packageItem, visited)
            }
        }
        val (kind, allowedKinds) = when (item.type) {
            "game" -> ImportKind.GAME to setOf(ImportKind.GAME)
            // ContentDB exposes modpacks through the mod channel; validate the downloaded archive and keep the real kind.
            "mod" -> ImportKind.MOD to setOf(ImportKind.MOD, ImportKind.MODPACK)
            "modpack" -> ImportKind.MODPACK to setOf(ImportKind.MODPACK)
            else -> error("Unsupported ContentDB type ${item.type}")
        }
        val archive = File(getApplication<Application>().cacheDir, "${item.name}.zip")
        try {
            container.contentDb.download(item, archive)
            repository.importContentDbArchive(
                profileId = profileId,
                source = Uri.fromFile(archive),
                defaultKind = kind,
                allowedKinds = allowedKinds,
                packageKey = item.key,
                title = item.title,
                releaseId = item.release,
                compatible = item.compatible,
            )
        } finally {
            archive.delete()
        }
    }
}

data class ContentBrowserState(
    val profileId: String? = null,
    val type: String = "game",
    val query: String = "",
    val items: List<ContentPackage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
