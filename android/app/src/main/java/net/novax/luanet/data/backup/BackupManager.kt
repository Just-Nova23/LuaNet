package net.novax.luanet.data.backup

import net.novax.luanet.data.db.BackupEntity
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.data.db.LuaNetDao
import net.novax.luanet.data.db.ServerConfigSettingEntity
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.PackageSource
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val root: File, private val dao: LuaNetDao) {
    suspend fun create(profileId: String, profileDirectory: File, reason: String, automatic: Boolean): BackupEntity {
        require(profileDirectory.isDirectory) { "Profile directory is missing" }
        val profile = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        val packages = dao.packages(profileId)
        val settings = dao.configSettings(profileId)
        val directory = File(root, profileId).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val output = File(directory, "$id.zip")
        val temporary = File(directory, "$id.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                zip.putText(PROFILE_METADATA, profileProperties(profile))
                zip.putText(PACKAGE_METADATA, packageProperties(packages))
                zip.putText(CONFIG_METADATA, configProperties(settings))
                profileDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(profileDirectory).invariantSeparatorsPath
                    if (relative in RESERVED_ENTRIES) return@forEach
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(temporary.renameTo(output)) { "Unable to finalize backup" }
            val backup = BackupEntity(id, profileId, output.name, automatic, reason, System.currentTimeMillis(), output.length())
            dao.insertBackup(backup)
            if (automatic) retainThree(profileId)
            return backup
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    suspend fun restore(profileId: String, backupId: String, profileDirectory: File): BackupEntity {
        val backup = requireNotNull(dao.backup(profileId, backupId)) { "Backup not found" }
        val archive = File(root, "$profileId/${backup.fileName}")
        require(archive.isFile) { "Backup archive is missing" }
        val current = requireNotNull(dao.profile(profileId)) { "Server profile not found" }
        val metadata = archive.propertiesEntry(PROFILE_METADATA)
        val packageMetadata = archive.propertiesEntry(PACKAGE_METADATA)
        val configMetadata = archive.propertiesEntry(CONFIG_METADATA)
        val restoredPackages = packageMetadata.toPackages(profileId)
        val restoredSettings = configMetadata.toConfigSettings(profileId)
        val parent = requireNotNull(profileDirectory.parentFile) { "Invalid profile directory" }.apply { mkdirs() }
        val restoreDirectory = File(parent, "${profileDirectory.name}.restore-$backupId").apply { deleteRecursively() }
        val previousDirectory = File(parent, "${profileDirectory.name}.before-restore-$backupId").apply { deleteRecursively() }
        try {
            extractProfileArchive(archive, restoreDirectory)
            if (profileDirectory.exists()) {
                check(profileDirectory.renameTo(previousDirectory)) { "Unable to move current profile before restore" }
            }
            check(restoreDirectory.renameTo(profileDirectory)) { "Unable to activate restored profile" }
            previousDirectory.deleteRecursively()
            dao.updateProfile(metadata.toProfile(current).copy(
                state = ServerState.STOPPED,
                localPort = null,
                publicEnabled = false,
                publicHost = null,
                publicPort = null,
                updatedAt = System.currentTimeMillis(),
            ))
            dao.deletePackages(profileId)
            restoredPackages.forEach { dao.upsertPackage(it) }
            dao.deleteConfigSettings(profileId)
            restoredSettings.forEach { dao.upsertConfigSetting(it) }
            return backup
        } catch (error: Throwable) {
            restoreDirectory.deleteRecursively()
            if (!profileDirectory.exists() && previousDirectory.exists()) {
                previousDirectory.renameTo(profileDirectory)
            }
            throw error
        }
    }

    suspend fun delete(profileId: String, backupId: String): Boolean {
        val backup = dao.backup(profileId, backupId) ?: return false
        File(root, "$profileId/${backup.fileName}").delete()
        dao.deleteBackup(backup)
        return true
    }

    private suspend fun retainThree(profileId: String) {
        dao.automaticBackups(profileId).drop(3).forEach { old ->
            File(root, "$profileId/${old.fileName}").delete()
            dao.deleteBackup(old)
        }
    }

    private fun extractProfileArchive(archive: File, destination: File) {
        val canonicalRoot = destination.apply { mkdirs() }.canonicalFile
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (name in RESERVED_ENTRIES) {
                    zip.closeEntry()
                    continue
                }
                val parts = name.split('/').filter { it.isNotBlank() }
                require(parts.isNotEmpty() && parts.none { it == ".." }) { "Unsafe backup entry: ${entry.name}" }
                val target = File(destination, name).canonicalFile
                require(target.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Backup entry escapes profile directory: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun ZipOutputStream.putText(name: String, properties: Properties) {
        putNextEntry(ZipEntry(name))
        val writer = StringWriter()
        properties.store(writer, null)
        write(writer.toString().toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun File.propertiesEntry(name: String): Properties {
        val text = ZipFile(this).use { zip ->
            zip.getEntry(name)?.let { entry ->
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.orEmpty()
        }
        return Properties().apply {
            if (text.isNotBlank()) load(StringReader(text))
        }
    }

    private fun profileProperties(profile: ServerProfileEntity) = Properties().apply {
        setProperty("name", profile.name)
        setProperty("engineVersion", profile.engineVersion)
        setProperty("gameKey", profile.gameKey.orEmpty())
        setProperty("mapgen", profile.mapgen)
        setProperty("maxPlayers", profile.maxPlayers.toString())
        setProperty("creative", profile.creative.toString())
        setProperty("damage", profile.damage.toString())
        setProperty("pvp", profile.pvp.toString())
        setProperty("accessMode", profile.accessMode.name)
        setProperty("autoOffEnabled", profile.autoOffEnabled.toString())
        setProperty("autoOffMinutes", profile.autoOffMinutes.toString())
        setProperty("serverDescription", profile.serverDescription)
        setProperty("motd", profile.motd)
        setProperty("announceServer", profile.announceServer.toString())
        setProperty("defaultPrivileges", profile.defaultPrivileges)
        setProperty("disallowEmptyPassword", profile.disallowEmptyPassword.toString())
        setProperty("enableRollback", profile.enableRollback.toString())
        setProperty("timeSpeed", profile.timeSpeed.toString())
        setProperty("activeBlockRange", profile.activeBlockRange.toString())
        setProperty("maxBlockSendDistance", profile.maxBlockSendDistance.toString())
        setProperty("maxBlockGenerateDistance", profile.maxBlockGenerateDistance.toString())
        setProperty("dedicatedServerStepMs", profile.dedicatedServerStepMs.toString())
        setProperty("maxObjectsPerBlock", profile.maxObjectsPerBlock.toString())
        setProperty("itemEntityTtl", profile.itemEntityTtl.toString())
        setProperty("maxPacketsPerIteration", profile.maxPacketsPerIteration.toString())
        setProperty("mapgenLimit", profile.mapgenLimit.toString())
    }

    private fun packageProperties(packages: List<InstalledPackageEntity>) = Properties().apply {
        setProperty("count", packages.size.toString())
        packages.forEachIndexed { index, item ->
            setProperty("package.$index.id", item.id)
            setProperty("package.$index.packageKey", item.packageKey)
            setProperty("package.$index.title", item.title)
            setProperty("package.$index.type", item.type.name)
            setProperty("package.$index.source", item.source.name)
            setProperty("package.$index.releaseId", item.releaseId?.toString().orEmpty())
            setProperty("package.$index.compatible", item.compatible.toString())
            setProperty("package.$index.enabled", item.enabled.toString())
            setProperty("package.$index.installedAt", item.installedAt.toString())
        }
    }

    private fun configProperties(settings: List<ServerConfigSettingEntity>) = Properties().apply {
        setProperty("count", settings.size.toString())
        settings.forEachIndexed { index, item ->
            setProperty("setting.$index.key", item.key)
            setProperty("setting.$index.value", item.value)
            setProperty("setting.$index.updatedAt", item.updatedAt.toString())
        }
    }

    private fun Properties.toProfile(current: ServerProfileEntity) = current.copy(
        name = getProperty("name", current.name),
        engineVersion = getProperty("engineVersion", current.engineVersion),
        gameKey = getProperty("gameKey")?.takeIf { it.isNotBlank() } ?: current.gameKey,
        mapgen = getProperty("mapgen", current.mapgen),
        maxPlayers = int("maxPlayers", current.maxPlayers),
        creative = bool("creative", current.creative),
        damage = bool("damage", current.damage),
        pvp = bool("pvp", current.pvp),
        accessMode = enum("accessMode", current.accessMode),
        autoOffEnabled = bool("autoOffEnabled", current.autoOffEnabled),
        autoOffMinutes = int("autoOffMinutes", current.autoOffMinutes),
        serverDescription = getProperty("serverDescription", current.serverDescription),
        motd = getProperty("motd", current.motd),
        announceServer = bool("announceServer", current.announceServer),
        defaultPrivileges = getProperty("defaultPrivileges", current.defaultPrivileges),
        disallowEmptyPassword = bool("disallowEmptyPassword", current.disallowEmptyPassword),
        enableRollback = bool("enableRollback", current.enableRollback),
        timeSpeed = int("timeSpeed", current.timeSpeed),
        activeBlockRange = int("activeBlockRange", current.activeBlockRange),
        maxBlockSendDistance = int("maxBlockSendDistance", current.maxBlockSendDistance),
        maxBlockGenerateDistance = int("maxBlockGenerateDistance", current.maxBlockGenerateDistance),
        dedicatedServerStepMs = int("dedicatedServerStepMs", current.dedicatedServerStepMs),
        maxObjectsPerBlock = int("maxObjectsPerBlock", current.maxObjectsPerBlock),
        itemEntityTtl = int("itemEntityTtl", current.itemEntityTtl),
        maxPacketsPerIteration = int("maxPacketsPerIteration", current.maxPacketsPerIteration),
        mapgenLimit = int("mapgenLimit", current.mapgenLimit),
    )

    private fun Properties.toPackages(profileId: String): List<InstalledPackageEntity> =
        (0 until int("count", 0)).mapNotNull { index ->
            runCatching {
                InstalledPackageEntity(
                    id = getProperty("package.$index.id").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
                    profileId = profileId,
                    packageKey = requireNotNull(getProperty("package.$index.packageKey")),
                    title = getProperty("package.$index.title", getProperty("package.$index.packageKey")),
                    type = enum("package.$index.type", PackageType.MOD),
                    source = enum("package.$index.source", PackageSource.MANUAL_ZIP),
                    releaseId = getProperty("package.$index.releaseId")?.toLongOrNull(),
                    compatible = bool("package.$index.compatible", true),
                    enabled = bool("package.$index.enabled", true),
                    installedAt = long("package.$index.installedAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }

    private fun Properties.toConfigSettings(profileId: String): List<ServerConfigSettingEntity> =
        (0 until int("count", 0)).mapNotNull { index ->
            val key = getProperty("setting.$index.key") ?: return@mapNotNull null
            ServerConfigSettingEntity(
                profileId = profileId,
                key = key,
                value = getProperty("setting.$index.value", ""),
                updatedAt = long("setting.$index.updatedAt", System.currentTimeMillis()),
            )
        }

    private fun Properties.int(key: String, default: Int): Int = getProperty(key)?.toIntOrNull() ?: default
    private fun Properties.long(key: String, default: Long): Long = getProperty(key)?.toLongOrNull() ?: default
    private fun Properties.bool(key: String, default: Boolean): Boolean = getProperty(key)?.toBooleanStrictOrNull() ?: default
    private inline fun <reified T : Enum<T>> Properties.enum(key: String, default: T): T =
        runCatching { enumValueOf<T>(getProperty(key) ?: return default) }.getOrDefault(default)

    companion object {
        private const val PROFILE_METADATA = ".luanet-profile.properties"
        private const val PACKAGE_METADATA = ".luanet-packages.properties"
        private const val CONFIG_METADATA = ".luanet-config.properties"
        private val RESERVED_ENTRIES = setOf(PROFILE_METADATA, PACKAGE_METADATA, CONFIG_METADATA)
    }
}
