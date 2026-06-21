package net.novax.luanet.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.data.db.LuaNetDao
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.importer.ImportResult
import net.novax.luanet.data.importer.SafeZipImporter
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.PackageSource
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class ServerRepository(
    private val context: Context,
    private val dao: LuaNetDao,
    private val importer: SafeZipImporter = SafeZipImporter(),
) {
    val profiles: Flow<List<ServerProfileEntity>> = dao.observeProfiles()

    suspend fun create(
        name: String,
        engineVersion: String,
        maxPlayers: Int,
        creative: Boolean,
        damage: Boolean,
        pvp: Boolean,
    ): ServerProfileEntity {
        require(name.isNotBlank()) { "Server name is required" }
        require(EngineCatalog.find(engineVersion) != null) { "Unknown engine version" }
        require(maxPlayers in 1..100) { "Max players must be between 1 and 100" }
        val now = System.currentTimeMillis()
        val profile = ServerProfileEntity(
            id = UUID.randomUUID().toString(), name = name.trim(), engineVersion = engineVersion,
            gameKey = null, mapgen = "v7", maxPlayers = maxPlayers, creative = creative,
            damage = damage, pvp = pvp, accessMode = AccessMode.OPEN, state = ServerState.STOPPED,
            localPort = null, publicEnabled = false, publicHost = null, publicPort = null,
            autoOffEnabled = false, autoOffMinutes = 15,
            createdAt = now, updatedAt = now,
        )
        profileDirectory(profile.id).mkdirs()
        dao.insertProfile(profile)
        return profile
    }

    suspend fun profile(id: String) = dao.profile(id)
    suspend fun activeProfiles() = dao.activeProfiles()
    suspend fun updateRuntime(id: String, state: ServerState, port: Int?) = dao.updateRuntime(id, state, port, System.currentTimeMillis())

    suspend fun updateAutoOff(id: String, enabled: Boolean, minutes: Int) {
        require(minutes in 1..1_440) { "Auto off must be between 1 minute and 24 hours" }
        val profile = requireNotNull(dao.profile(id)) { "Server profile not found" }
        dao.updateProfile(profile.copy(
            autoOffEnabled = enabled,
            autoOffMinutes = minutes,
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun importArchive(profileId: String, source: Uri, expected: ImportKind): ImportResult = withContext(Dispatchers.IO) {
        val profile = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        require(profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)) { "Stop the server before importing content" }
        val displayName = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "${expected.name.lowercase()}.zip"
        val archive = File(context.cacheDir, "import-${UUID.randomUUID()}.zip")
        val root = profileDirectory(profileId)
        val incoming = File(root, ".incoming-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                archive.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ARCHIVE_BYTES) { "ZIP archive exceeds 512 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Unable to read selected document")
            val imported = importer.import(archive, incoming, expected)
            val identifier = safeIdentifier(displayName.substringBeforeLast('.'))
            val destination = when (expected) {
                ImportKind.WORLD -> File(root, "world")
                ImportKind.GAME -> File(root, "games/$identifier")
                ImportKind.MOD, ImportKind.MODPACK -> File(root, "mods/$identifier")
            }
            require(!destination.exists()) { "${expected.name.lowercase()} '$identifier' is already installed" }
            destination.parentFile?.mkdirs()
            Files.move(incoming.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            if (expected != ImportKind.WORLD) {
                val type = when (expected) {
                    ImportKind.GAME -> PackageType.GAME
                    ImportKind.MOD -> PackageType.MOD
                    ImportKind.MODPACK -> PackageType.MODPACK
                    else -> error("unreachable")
                }
                dao.upsertPackage(InstalledPackageEntity(
                    id = UUID.randomUUID().toString(), profileId = profileId,
                    packageKey = "manual/$identifier", title = displayName.substringBeforeLast('.'),
                    type = type, source = PackageSource.MANUAL_ZIP, releaseId = null,
                    compatible = true, enabled = true, installedAt = System.currentTimeMillis(),
                ))
                if (expected == ImportKind.GAME && profile.gameKey == null) {
                    dao.updateProfile(profile.copy(gameKey = "manual/$identifier", updatedAt = System.currentTimeMillis()))
                }
            }
            imported.copy(destination = destination)
        } finally {
            archive.delete()
            incoming.deleteRecursively()
        }
    }

    suspend fun upgradeEngine(profile: ServerProfileEntity, target: String): ServerProfileEntity {
        require(EngineCatalog.canUpgrade(profile.engineVersion, target)) { "Engine downgrade is not supported" }
        val updated = profile.copy(engineVersion = target, updatedAt = System.currentTimeMillis())
        dao.updateProfile(updated)
        return updated
    }

    fun profileDirectory(id: String): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "servers/$id")
    fun backupDirectory(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")

    private fun safeIdentifier(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(64).ifBlank { "content-${UUID.randomUUID().toString().take(8)}" }

    companion object { private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024 }
}
