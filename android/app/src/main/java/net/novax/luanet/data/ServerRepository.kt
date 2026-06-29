package net.novax.luanet.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.data.db.LuaNetDao
import net.novax.luanet.data.db.ServerConfigSettingEntity
import net.novax.luanet.data.db.ServerPlayerEntity
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.importer.ImportResult
import net.novax.luanet.data.importer.SafeZipImporter
import net.novax.luanet.data.importer.UnsafeArchiveException
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.PackageSource
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class ArchiveCopyProgress(val bytesRead: Long, val totalBytes: Long?)

data class ServerProfileSettingsUpdate(
    val profileId: String,
    val name: String,
    val engineVersion: String,
    val gameKey: String?,
    val mapgen: String,
    val maxPlayers: Int,
    val creative: Boolean,
    val damage: Boolean,
    val pvp: Boolean,
    val autoOffEnabled: Boolean,
    val autoOffMinutes: Int,
    val serverDescription: String,
    val motd: String,
    val announceServer: Boolean,
    val defaultPrivileges: String,
    val disallowEmptyPassword: Boolean,
    val enableRollback: Boolean,
    val timeSpeed: Int,
    val activeBlockRange: Int,
    val maxBlockSendDistance: Int,
    val maxBlockGenerateDistance: Int,
    val dedicatedServerStepMs: Int,
    val maxObjectsPerBlock: Int,
    val itemEntityTtl: Int,
    val maxPacketsPerIteration: Int,
    val mapgenLimit: Int,
)

data class ServerModSetting(
    val key: String,
    val title: String,
    val type: String,
    val defaultValue: String,
    val value: String,
    val source: String,
    val description: String,
)

class ServerRepository(
    private val context: Context,
    private val dao: LuaNetDao,
    private val importer: SafeZipImporter = SafeZipImporter(),
) {
    val profiles: Flow<List<ServerProfileEntity>> = dao.observeProfiles()

    suspend fun create(
        name: String,
        engineVersion: String,
        mapgen: String,
        maxPlayers: Int,
        creative: Boolean,
        damage: Boolean,
        pvp: Boolean,
    ): ServerProfileEntity {
        require(name.isNotBlank()) { "Server name is required" }
        require(EngineCatalog.find(engineVersion) != null) { "Unknown engine version" }
        require(mapgen in MAPGENS) { "Unknown map generator" }
        require(maxPlayers in 1..100) { "Max players must be between 1 and 100" }
        val now = System.currentTimeMillis()
        val profile = ServerProfileEntity(
            id = UUID.randomUUID().toString(), name = name.trim(), engineVersion = engineVersion,
            gameKey = null, mapgen = mapgen, maxPlayers = maxPlayers, creative = creative,
            damage = damage, pvp = pvp, accessMode = AccessMode.OPEN, state = ServerState.STOPPED,
            localPort = null, publicEnabled = false, publicHost = null, publicPort = null,
            autoOffEnabled = false, autoOffMinutes = 15,
            serverDescription = "", motd = "", announceServer = false,
            defaultPrivileges = "interact,shout", disallowEmptyPassword = true,
            enableRollback = false, timeSpeed = 72, activeBlockRange = 3,
            maxBlockSendDistance = 10, maxBlockGenerateDistance = 6,
            dedicatedServerStepMs = 100, maxObjectsPerBlock = 64,
            itemEntityTtl = 900, maxPacketsPerIteration = 1024, mapgenLimit = 31_000,
            createdAt = now, updatedAt = now,
        )
        profileDirectory(profile.id).mkdirs()
        dao.insertProfile(profile)
        return profile
    }

    suspend fun profile(id: String) = dao.profile(id)
    suspend fun activeProfiles() = dao.activeProfiles()
    fun observePackages(profileId: String) = dao.observePackages(profileId)
    suspend fun packages(profileId: String) = dao.packages(profileId)
    fun observePlayers(profileId: String) = dao.observePlayers(profileId)
    suspend fun configSettings(profileId: String) = dao.configSettings(profileId)
    fun observeConfigSettings(profileId: String) = dao.observeConfigSettings(profileId)
    fun observeModSettings(profileId: String): Flow<List<ServerModSetting>> =
        combine(dao.observeConfigSettings(profileId), dao.observePackages(profileId)) { saved, packages ->
            modSettingDefinitions(profileId, packages, saved.associate { it.key to it.value })
        }

    fun observeGameSettings(profileId: String): Flow<List<ServerModSetting>> =
        combine(dao.observeConfigSettings(profileId), dao.observePackages(profileId)) { saved, packages ->
            gameSettingDefinitions(profileId, packages, saved.associate { it.key to it.value })
        }
    suspend fun updateRuntime(id: String, state: ServerState, port: Int?) = dao.updateRuntime(id, state, port, System.currentTimeMillis())
    suspend fun updatePublic(id: String, enabled: Boolean, host: String?, port: Int?) =
        dao.updatePublic(id, enabled, host, port, System.currentTimeMillis())

    suspend fun recoverAbandonedRuntimeStates(liveProfileIds: Set<String> = emptySet()): Int {
        val abandoned = activeProfiles().filterNot { it.id in liveProfileIds }
        if (abandoned.isEmpty()) return 0
        val now = System.currentTimeMillis()
        abandoned.forEach { profile ->
            dao.updateRuntime(profile.id, ServerState.CRASHED, null, now)
            dao.updatePublic(profile.id, false, null, null, now)
        }
        return abandoned.size
    }

    suspend fun updateAutoOff(id: String, enabled: Boolean, minutes: Int) {
        require(minutes in 1..1_440) { "Auto off must be between 1 minute and 24 hours" }
        val profile = requireNotNull(dao.profile(id)) { "Server profile not found" }
        dao.updateProfile(profile.copy(
            autoOffEnabled = enabled,
            autoOffMinutes = minutes,
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun updateServerSettings(update: ServerProfileSettingsUpdate): ServerProfileEntity {
        val profile = requireNotNull(dao.profile(update.profileId)) { "Server profile not found" }
        require(profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)) { "Stop the server before changing server settings" }
        require(update.name.isNotBlank()) { "Server name is required" }
        require(EngineCatalog.find(update.engineVersion) != null) { "Unknown engine version" }
        require(EngineCatalog.canUpgrade(profile.engineVersion, update.engineVersion)) { "Engine downgrade is not supported" }
        require(update.mapgen in MAPGENS) { "Unknown map generator" }
        require(update.maxPlayers in 1..100) { "Max players must be between 1 and 100" }
        require(update.autoOffMinutes in 1..1_440) { "Auto off must be between 1 minute and 24 hours" }
        require(update.timeSpeed in 0..2_400) { "Time speed must be 0 to 2400" }
        require(update.activeBlockRange in 1..10) { "Active block range must be 1 to 10" }
        require(update.maxBlockSendDistance in 1..64) { "Send distance must be 1 to 64" }
        require(update.maxBlockGenerateDistance in 1..64) { "Generate distance must be 1 to 64" }
        require(update.dedicatedServerStepMs in 20..1_000) { "Server step must be 20 to 1000 ms" }
        require(update.maxObjectsPerBlock in 1..256) { "Max objects per block must be 1 to 256" }
        require(update.itemEntityTtl in -1..86_400) { "Dropped item TTL must be -1 to 86400 seconds" }
        require(update.maxPacketsPerIteration in 64..16_384) { "Packets per iteration must be 64 to 16384" }
        require(update.mapgenLimit in 100..31_000) { "Mapgen limit must be 100 to 31000" }
        if (update.gameKey != null) {
            require(packages(update.profileId).any { it.type == PackageType.GAME && it.packageKey == update.gameKey }) {
                "Selected game is not installed in this server profile"
            }
        }
        val updated = profile.copy(
            name = update.name.trim(),
            engineVersion = update.engineVersion,
            gameKey = update.gameKey,
            mapgen = update.mapgen,
            maxPlayers = update.maxPlayers,
            creative = update.creative,
            damage = update.damage,
            pvp = update.pvp,
            autoOffEnabled = update.autoOffEnabled,
            autoOffMinutes = update.autoOffMinutes,
            serverDescription = update.serverDescription.trim().take(240),
            motd = update.motd.trim().take(240),
            announceServer = update.announceServer,
            defaultPrivileges = update.defaultPrivileges.toPrivilegeList(),
            disallowEmptyPassword = update.disallowEmptyPassword,
            enableRollback = update.enableRollback,
            timeSpeed = update.timeSpeed,
            activeBlockRange = update.activeBlockRange,
            maxBlockSendDistance = update.maxBlockSendDistance,
            maxBlockGenerateDistance = update.maxBlockGenerateDistance,
            dedicatedServerStepMs = update.dedicatedServerStepMs,
            maxObjectsPerBlock = update.maxObjectsPerBlock,
            itemEntityTtl = update.itemEntityTtl,
            maxPacketsPerIteration = update.maxPacketsPerIteration,
            mapgenLimit = update.mapgenLimit,
            updatedAt = System.currentTimeMillis(),
        )
        dao.updateProfile(updated)
        return updated
    }

    suspend fun markPlayerOnline(profileId: String, rawName: String) {
        val name = rawName.safePlayerName()
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = dao.player(profileId, name)
        dao.upsertPlayer(ServerPlayerEntity(
            profileId = profileId,
            name = name,
            firstSeenAt = existing?.firstSeenAt ?: now,
            lastSeenAt = now,
            online = true,
            banned = existing?.banned ?: false,
            admin = existing?.admin ?: false,
        ))
    }

    suspend fun markPlayerOffline(profileId: String, rawName: String) {
        val name = rawName.safePlayerName()
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = dao.player(profileId, name) ?: return
        dao.upsertPlayer(existing.copy(lastSeenAt = now, online = false))
    }

    suspend fun markAllPlayersOffline(profileId: String) = dao.markAllPlayersOffline(profileId, System.currentTimeMillis())
    suspend fun markPlayerBanned(profileId: String, rawName: String, banned: Boolean) = dao.updatePlayerBanned(profileId, rawName.safePlayerName(), banned)
    suspend fun markPlayerAdmin(profileId: String, rawName: String, admin: Boolean) = dao.updatePlayerAdmin(profileId, rawName.safePlayerName(), admin)

    suspend fun setPlayerAdminOffline(profileId: String, rawName: String, admin: Boolean): String = withContext(Dispatchers.IO) {
        val name = rawName.safePlayerName()
        require(name.isNotBlank()) { "Invalid player name" }
        val profile = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        require(profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)) {
            "Stop the server before editing offline privileges directly"
        }
        updateAuthSqliteAdmin(profile, name, admin)
        dao.updatePlayerAdmin(profileId, name, admin)
        if (admin) "$name is now admin. Restart or start the server to use the updated privileges." else "$name admin privileges removed."
    }

    suspend fun unbanPlayerOffline(profileId: String, rawName: String): String = withContext(Dispatchers.IO) {
        val name = rawName.safePlayerName()
        require(name.isNotBlank()) { "Invalid player name" }
        val profile = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        require(profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)) {
            "Stop the server before editing offline bans directly"
        }
        val removed = removePlayerFromIpBan(profile.id, name)
        dao.updatePlayerBanned(profileId, name, false)
        if (removed > 0) "Removed $removed ban entr${if (removed == 1) "y" else "ies"} for $name." else "No saved ban entry existed for $name."
    }

    suspend fun saveModSetting(profileId: String, key: String, value: String) {
        saveConfigSetting(profileId, key, value)
    }

    suspend fun saveConfigSetting(profileId: String, key: String, value: String) {
        require(key.matches(SETTING_KEY)) { "Invalid setting key" }
        require(value.length <= 512) { "Setting value is too long" }
        dao.upsertConfigSetting(ServerConfigSettingEntity(profileId, key, value.trim(), System.currentTimeMillis()))
    }

    suspend fun importArchive(
        profileId: String,
        source: Uri,
        expected: ImportKind,
        onProgress: (ArchiveCopyProgress) -> Unit = {},
    ): ImportResult =
        importArchiveInternal(
            profileId = profileId,
            source = source,
            defaultKind = expected,
            allowedKinds = setOf(expected),
            metadata = null,
            onProgress = onProgress,
        )

    suspend fun importContentDbArchive(
        profileId: String,
        source: Uri,
        defaultKind: ImportKind,
        allowedKinds: Set<ImportKind>,
        packageKey: String,
        title: String,
        releaseId: Long?,
        compatible: Boolean,
    ): ImportResult = importArchiveInternal(
        profileId = profileId,
        source = source,
        defaultKind = defaultKind,
        allowedKinds = allowedKinds,
        metadata = PackageMetadata(
            packageKey = packageKey,
            title = title,
            source = PackageSource.CONTENT_DB,
            releaseId = releaseId,
            compatible = compatible,
        ),
        onProgress = {},
    )

    private suspend fun importArchiveInternal(
        profileId: String,
        source: Uri,
        defaultKind: ImportKind,
        allowedKinds: Set<ImportKind>,
        metadata: PackageMetadata?,
        onProgress: (ArchiveCopyProgress) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        val profile = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        require(profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)) { "Stop the server before importing content" }
        val sourceInfo = archiveSourceInfo(source, defaultKind)
        val archive = File(context.cacheDir, "import-${UUID.randomUUID()}.zip")
        val root = profileDirectory(profileId)
        val incoming = File(root, ".incoming-${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                archive.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    onProgress(ArchiveCopyProgress(bytesRead = 0, totalBytes = sourceInfo.sizeBytes))
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ARCHIVE_BYTES) { "ZIP archive exceeds 512 MB" }
                        output.write(buffer, 0, read)
                        onProgress(ArchiveCopyProgress(bytesRead = total, totalBytes = sourceInfo.sizeBytes))
                    }
                }
            } ?: error("Unable to read selected document")
            val imported = importer.import(archive, incoming)
            if (imported.kind !in allowedKinds) {
                throw UnsafeArchiveException("Expected ${allowedKinds.joinToString(" or ") { it.name.lowercase() }} archive, found ${imported.kind.name.lowercase()}")
            }
            val identifier = safeIdentifier(sourceInfo.displayName.substringBeforeLast('.'))
            val destination = when (imported.kind) {
                ImportKind.WORLD -> File(root, "world")
                ImportKind.GAME -> File(root, "games/$identifier")
                ImportKind.MOD, ImportKind.MODPACK -> File(root, "mods/$identifier")
            }
            require(!destination.exists()) { "${imported.kind.name.lowercase()} '$identifier' is already installed" }
            destination.parentFile?.mkdirs()
            Files.move(incoming.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            if (imported.kind != ImportKind.WORLD) {
                val type = when (imported.kind) {
                    ImportKind.GAME -> PackageType.GAME
                    ImportKind.MOD -> PackageType.MOD
                    ImportKind.MODPACK -> PackageType.MODPACK
                    else -> error("unreachable")
                }
                dao.upsertPackage(InstalledPackageEntity(
                    id = UUID.randomUUID().toString(), profileId = profileId,
                    packageKey = metadata?.packageKey ?: "manual/$identifier",
                    title = metadata?.title ?: sourceInfo.displayName.substringBeforeLast('.'),
                    type = type,
                    source = metadata?.source ?: PackageSource.MANUAL_ZIP,
                    releaseId = metadata?.releaseId,
                    compatible = metadata?.compatible ?: true,
                    enabled = true, installedAt = System.currentTimeMillis(),
                ))
                if (imported.kind == ImportKind.GAME && profile.gameKey == null) {
                    dao.updateProfile(profile.copy(gameKey = metadata?.packageKey ?: "manual/$identifier", updatedAt = System.currentTimeMillis()))
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

    private fun archiveSourceInfo(source: Uri, defaultKind: ImportKind): ArchiveSourceInfo {
        var displayName: String? = null
        var sizeBytes: Long? = null
        context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                }
            }
        return ArchiveSourceInfo(
            displayName = displayName
                ?: source.lastPathSegment?.substringAfterLast('/')
                ?: "${defaultKind.name.lowercase()}.zip",
            sizeBytes = sizeBytes,
        )
    }

    private fun safeIdentifier(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(64).ifBlank { "content-${UUID.randomUUID().toString().take(8)}" }

    private fun modSettingDefinitions(
        profileId: String,
        packages: List<InstalledPackageEntity>,
        saved: Map<String, String>,
    ): List<ServerModSetting> {
        val modsRoot = File(profileDirectory(profileId), "mods")
        return packages
            .filter { it.enabled && (it.type == PackageType.MOD || it.type == PackageType.MODPACK) }
            .flatMap { item ->
                val root = File(modsRoot, item.packageKey.substringAfter('/').safeFolderName())
                if (item.type == PackageType.MODPACK) {
                    root.listFiles()?.filter { it.isDirectory }.orEmpty().flatMap { child ->
                        parseSettingTypes(File(child, "settingtypes.txt"), item.title, saved)
                    }
                } else {
                    parseSettingTypes(File(root, "settingtypes.txt"), item.title, saved)
                }
            }
            .distinctBy { it.key }
            .sortedWith(compareBy<ServerModSetting> { it.source.lowercase() }.thenBy { it.title.lowercase() })
    }

    private fun gameSettingDefinitions(
        profileId: String,
        packages: List<InstalledPackageEntity>,
        saved: Map<String, String>,
    ): List<ServerModSetting> {
        val gamesRoot = File(profileDirectory(profileId), "games")
        return packages
            .filter { it.enabled && it.type == PackageType.GAME }
            .flatMap { item ->
                val root = File(gamesRoot, item.packageKey.substringAfter('/').safeFolderName())
                buildList {
                    addAll(parseSettingTypes(File(root, "settingtypes.txt"), item.title, saved))
                    File(root, "mods").listFiles()?.filter { it.isDirectory }.orEmpty().forEach { child ->
                        addAll(parseSettingTypes(File(child, "settingtypes.txt"), "${item.title} / ${child.name}", saved))
                    }
                }
            }
            .distinctBy { it.key }
            .sortedWith(compareBy<ServerModSetting> { it.source.lowercase() }.thenBy { it.title.lowercase() })
    }

    private fun updateAuthSqliteAdmin(profile: ServerProfileEntity, name: String, admin: Boolean) {
        val authFile = File(profileDirectory(profile.id), "world/auth.sqlite")
        require(authFile.isFile) {
            "Luanti auth.sqlite does not exist yet. Start this server once and let the player join before editing offline privileges."
        }
        val database = SQLiteDatabase.openDatabase(authFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.beginTransaction()
            val playerId = requireNotNull(authId(database, name)) {
                "Player $name has no Luanti auth record. They must join once before offline privileges can be edited."
            }
            database.delete("user_privileges", "id = ?", arrayOf(playerId.toString()))
            val privileges = if (admin) adminPrivilegesFor(profile.id, database) else profile.defaultPrivileges.privilegeSet()
            privileges.forEach { privilege ->
                database.insertWithOnConflict(
                    "user_privileges",
                    null,
                    ContentValues().apply {
                        put("id", playerId)
                        put("privilege", privilege)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            runCatching { database.endTransaction() }
            database.close()
        }
    }

    private fun adminPrivilegesFor(profileId: String, database: SQLiteDatabase): Set<String> {
        val consoleAdmin = "ln_admin_${profileId.take(8).filter { it.isLetterOrDigit() }}"
        val consoleAdminId = authId(database, consoleAdmin)
        val copied = consoleAdminId?.let { privilegesFor(database, it) }.orEmpty()
        return (copied + DEFAULT_ADMIN_PRIVILEGES).filterTo(linkedSetOf()) { it.matches(SETTING_KEY) }
    }

    private fun authId(database: SQLiteDatabase, name: String): Long? {
        database.rawQuery("SELECT id FROM auth WHERE name = ?", arrayOf(name)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun privilegesFor(database: SQLiteDatabase, id: Long): Set<String> {
        val privileges = linkedSetOf<String>()
        database.rawQuery("SELECT privilege FROM user_privileges WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf { it.matches(SETTING_KEY) }?.let(privileges::add)
            }
        }
        return privileges
    }

    private fun removePlayerFromIpBan(profileId: String, name: String): Int {
        val ipBan = File(profileDirectory(profileId), "world/ipban.txt")
        if (!ipBan.isFile) return 0
        val before = ipBan.readLines()
        val after = before.filterNot { line ->
            val parts = line.split('|', limit = 2)
            parts.getOrNull(1)?.trim() == name
        }
        if (after.size != before.size) {
            ipBan.writeText(after.joinToString(separator = "\n", postfix = if (after.isEmpty()) "" else "\n"))
        }
        return before.size - after.size
    }

    private fun parseSettingTypes(file: File, source: String, saved: Map<String, String>): List<ServerModSetting> {
        if (!file.isFile) return emptyList()
        val result = mutableListOf<ServerModSetting>()
        val description = mutableListOf<String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> description.clear()
                line.startsWith("#") -> description += line.removePrefix("#").trim()
                else -> {
                    SETTING_LINE.matchEntire(line)?.let { match ->
                        val key = match.groupValues[1]
                        val title = match.groupValues[2].ifBlank { key }
                        val type = match.groupValues[3].lowercase()
                        val defaultValue = match.groupValues[4]
                        if (key.matches(SETTING_KEY) && type in SUPPORTED_SETTING_TYPES) {
                            result += ServerModSetting(
                                key = key,
                                title = title,
                                type = type,
                                defaultValue = defaultValue,
                                value = saved[key] ?: defaultValue,
                                source = source,
                                description = description.joinToString(" ").take(240),
                            )
                        }
                    }
                    description.clear()
                }
            }
        }
        return result
    }

    private fun String.safePlayerName(): String = filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
    private fun String.safeFolderName(): String = filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(80)
    private fun String.privilegeSet(): Set<String> = split(',')
        .map { it.trim().filter { char -> char.isLetterOrDigit() || char == '_' } }
        .filterTo(linkedSetOf()) { it.isNotBlank() }
    private fun String.toPrivilegeList(): String = split(',')
        .map { it.trim().filter { char -> char.isLetterOrDigit() || char == '_' } }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
        .ifBlank { "interact,shout" }

    private data class PackageMetadata(
        val packageKey: String,
        val title: String,
        val source: PackageSource,
        val releaseId: Long?,
        val compatible: Boolean,
    )

    private data class ArchiveSourceInfo(val displayName: String, val sizeBytes: Long?)

    companion object {
        private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        val MAPGENS = listOf("v7", "valleys", "carpathian", "flat", "fractal", "singlenode", "v6")
        private val SETTING_KEY = Regex("[A-Za-z0-9_.:-]{1,120}")
        private val SETTING_LINE = Regex("""^([A-Za-z0-9_.:-]+)\s*(?:\(([^)]*)\))?\s+([A-Za-z]+)(?:\s+(\S+))?.*$""")
        private val SUPPORTED_SETTING_TYPES = setOf("bool", "boolean", "int", "float", "string", "enum", "path")
        private val DEFAULT_ADMIN_PRIVILEGES = setOf(
            "interact", "shout", "privs", "basic_privs", "server", "ban", "kick", "teleport",
            "bring", "fast", "fly", "noclip", "give", "settime", "rollback", "debug",
            "password", "protection_bypass",
        )
    }
}
