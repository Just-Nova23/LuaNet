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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.novax.luanet.LuaNetApplication
import net.novax.luanet.data.ArchiveCopyProgress
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.SubscriptionTier
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.content.ContentHomeSection
import net.novax.luanet.data.content.ContentPackage
import net.novax.luanet.data.content.ContentPackageDetail
import net.novax.luanet.data.content.DownloadProgress
import net.novax.luanet.runtime.EntitlementStore
import net.novax.luanet.runtime.OrchestratorService
import java.io.File
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LuaNetApplication).container
    private val repository = container.servers
    private val entitlementStore = EntitlementStore(application)
    val profiles = repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _content = MutableStateFlow(ContentBrowserState())
    val content: StateFlow<ContentBrowserState> = _content.asStateFlow()
    private val _contentDetails = MutableStateFlow<Map<String, ContentDetailState>>(emptyMap())
    val contentDetails: StateFlow<Map<String, ContentDetailState>> = _contentDetails.asStateFlow()
    private val _account = MutableStateFlow(AccountState(
        tokenConfigured = container.authTokens.bearerToken() != null,
        tier = entitlementStore.current(),
    ))
    val account: StateFlow<AccountState> = _account.asStateFlow()

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

    fun installedPackages(profileId: String) = repository.observePackages(profileId)

    fun importArchive(profileId: String, uri: Uri, kind: ImportKind, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            setOperation(profileId, "Manual ZIP", "Reading selected ${kind.name.lowercase()} archive", indeterminate = true)
            val result = runCatching {
                val imported = repository.importArchive(profileId, uri, kind) { progress ->
                    setArchiveProgress(profileId, kind, progress)
                }
                setOperation(profileId, "Manual ZIP", "Installing ${kind.name.lowercase()} archive", indeterminate = true)
                "Imported ${imported.kind.name.lowercase()} (${imported.bytesWritten / 1024} KiB)"
            }
            _content.update { it.copy(operation = null) }
            onResult(result)
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

    fun saveAccountToken(token: String) {
        if (token.isBlank()) container.authTokens.clear() else container.authTokens.updateBearerToken(token)
        _account.value = _account.value.copy(tokenConfigured = container.authTokens.bearerToken() != null)
    }

    fun syncEntitlement(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                val response = container.controlPlane.entitlement()
                val tier = if (response.tier == "premium") SubscriptionTier.PREMIUM else SubscriptionTier.FREE
                val visibleExpiry = response.expiresAt.takeIf { tier == SubscriptionTier.PREMIUM }
                val expiresAt = visibleExpiry?.let { Instant.parse(it).toEpochMilli() } ?: 0L
                entitlementStore.update(tier, expiresAt)
                _account.value = _account.value.copy(tokenConfigured = true, tier = tier, expiresAt = visibleExpiry)
                "NovaX entitlement: ${response.tier}"
            })
        }
    }

    fun startPublicTunnel(profileId: String, localPort: Int, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                require(localPort > 0) { "Start the local server before opening a public tunnel" }
                require(container.authTokens.bearerToken() != null) { "Set a NovaX account token before starting a public tunnel" }
                val entitlement = runCatching { container.controlPlane.entitlement() }.getOrNull()
                if (entitlement != null) {
                    val tier = if (entitlement.tier == "premium") SubscriptionTier.PREMIUM else SubscriptionTier.FREE
                    val visibleExpiry = entitlement.expiresAt.takeIf { tier == SubscriptionTier.PREMIUM }
                    entitlementStore.update(tier, visibleExpiry?.let { Instant.parse(it).toEpochMilli() } ?: 0L)
                    _account.value = _account.value.copy(tokenConfigured = true, tier = tier, expiresAt = visibleExpiry)
                }
                val hold = container.controlPlane.createHold(container.authTokens.deviceId, profileId)
                val lease = container.controlPlane.activateHold(hold.id)
                OrchestratorService.startPublic(getApplication(), profileId, lease)
                "Public tunnel requested: ${lease.publicHost}:${lease.publicPort}"
            })
        }
    }

    fun stopPublicTunnel(profileId: String, onResult: (Result<String>) -> Unit) {
        OrchestratorService.stopPublic(getApplication(), profileId)
        onResult(Result.success("Public tunnel stopping"))
    }

    fun searchContent(profileId: String, type: String, query: String) {
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        if (query.isBlank()) {
            loadContentHome(profileId)
            return
        }
        _content.value = ContentBrowserState(
            profileId = profileId,
            type = type,
            query = query,
            loading = true,
            operation = _content.value.operation,
        )
        viewModelScope.launch {
            _content.value = runCatching {
                ContentBrowserState(profileId, type, query,
                    items = container.contentDb.search(type, query, profile.engineVersion, profile.gameKey),
                    loading = false,
                    operation = _content.value.operation,
                )
            }.getOrElse {
                ContentBrowserState(
                    profileId = profileId,
                    type = type,
                    query = query,
                    error = it.message ?: "ContentDB request failed",
                    operation = _content.value.operation,
                )
            }
        }
    }

    fun loadContentHome(profileId: String) {
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        _content.value = ContentBrowserState(
            profileId = profileId,
            type = _content.value.type,
            query = "",
            loading = true,
            operation = _content.value.operation,
        )
        viewModelScope.launch {
            _content.value = runCatching {
                ContentBrowserState(
                    profileId = profileId,
                    query = "",
                    sections = container.contentDb.home(profile.engineVersion, profile.gameKey),
                    loading = false,
                    operation = _content.value.operation,
                )
            }.getOrElse {
                ContentBrowserState(
                    profileId = profileId,
                    query = "",
                    error = it.message ?: "ContentDB request failed",
                    operation = _content.value.operation,
                )
            }
        }
    }

    fun loadContentDetail(packageKey: String) {
        val current = _contentDetails.value[packageKey]
        if (current?.loading == true || current?.detail != null) return
        _contentDetails.update { it + (packageKey to ContentDetailState(loading = true)) }
        viewModelScope.launch {
            val state = runCatching {
                ContentDetailState(detail = container.contentDb.details(packageKey))
            }.getOrElse {
                ContentDetailState(error = it.message ?: "ContentDB detail request failed")
            }
            _contentDetails.update { it + (packageKey to state) }
        }
    }

    fun installContent(profileId: String, item: ContentPackage, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            setOperation(profileId, item.title, "Preparing install", packageKey = item.key, indeterminate = true)
            val result = runCatching {
                val installed = linkedSetOf<String>()
                installWithDependencies(profileId, item, installed)
                "Installed ${item.title}${if (installed.size > 1) " with ${installed.size - 1} dependencies" else ""}"
            }
            _content.update { it.copy(operation = null) }
            onResult(result)
        }
    }

    private suspend fun installWithDependencies(profileId: String, item: ContentPackage, visited: MutableSet<String>) {
        check(visited.add(item.key)) { "Cyclic ContentDB dependency involving ${item.key}" }
        setOperation(profileId, item.title, "Resolving dependencies", packageKey = item.key, indeterminate = true)
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
            container.contentDb.download(item, archive) { progress ->
                setDownloadProgress(profileId, item, progress)
            }
            setOperation(profileId, item.title, "Installing archive", packageKey = item.key, indeterminate = true)
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

    private fun setDownloadProgress(profileId: String, item: ContentPackage, progress: DownloadProgress) {
        setOperation(
            profileId = profileId,
            title = item.title,
            phase = "Downloading ${item.author}/${item.name}",
            packageKey = item.key,
            bytesRead = progress.bytesRead,
            totalBytes = progress.totalBytes,
        )
    }

    private fun setArchiveProgress(profileId: String, kind: ImportKind, progress: ArchiveCopyProgress) {
        setOperation(
            profileId = profileId,
            title = "Manual ZIP",
            phase = "Copying ${kind.name.lowercase()} archive",
            bytesRead = progress.bytesRead,
            totalBytes = progress.totalBytes,
        )
    }

    private fun setOperation(
        profileId: String,
        title: String,
        phase: String,
        packageKey: String? = null,
        bytesRead: Long = 0,
        totalBytes: Long? = null,
        indeterminate: Boolean = false,
    ) {
        _content.update { current ->
            current.copy(
                operation = ContentOperationState(
                    profileId = profileId,
                    packageKey = packageKey,
                    title = title,
                    phase = phase,
                    bytesRead = bytesRead,
                    totalBytes = totalBytes,
                    indeterminate = indeterminate,
                ),
            )
        }
    }
}

data class AccountState(
    val tokenConfigured: Boolean = false,
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val expiresAt: String? = null,
)

data class ContentBrowserState(
    val profileId: String? = null,
    val type: String = "game",
    val query: String = "",
    val items: List<ContentPackage> = emptyList(),
    val sections: List<ContentHomeSection> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val operation: ContentOperationState? = null,
)

data class ContentDetailState(
    val loading: Boolean = false,
    val detail: ContentPackageDetail? = null,
    val error: String? = null,
)

data class ContentOperationState(
    val profileId: String,
    val packageKey: String? = null,
    val title: String,
    val phase: String,
    val bytesRead: Long = 0,
    val totalBytes: Long? = null,
    val indeterminate: Boolean = false,
)
