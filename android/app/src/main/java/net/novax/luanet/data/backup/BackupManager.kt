package net.novax.luanet.data.backup

import net.novax.luanet.data.db.BackupEntity
import net.novax.luanet.data.db.LuaNetDao
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManager(private val root: File, private val dao: LuaNetDao) {
    suspend fun create(profileId: String, profileDirectory: File, reason: String, automatic: Boolean): BackupEntity {
        require(profileDirectory.isDirectory) { "Profile directory is missing" }
        val directory = File(root, profileId).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val output = File(directory, "$id.zip")
        val temporary = File(directory, "$id.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                profileDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(profileDirectory).invariantSeparatorsPath
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

    private suspend fun retainThree(profileId: String) {
        dao.automaticBackups(profileId).drop(3).forEach { old ->
            File(root, "$profileId/${old.fileName}").delete()
            dao.deleteBackup(old)
        }
    }
}

