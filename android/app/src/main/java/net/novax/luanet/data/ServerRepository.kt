package net.novax.luanet.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import net.novax.luanet.data.db.LuaNetDao
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.ServerState
import java.io.File
import java.util.UUID

class ServerRepository(private val context: Context, private val dao: LuaNetDao) {
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
            createdAt = now, updatedAt = now,
        )
        profileDirectory(profile.id).mkdirs()
        dao.insertProfile(profile)
        return profile
    }

    suspend fun profile(id: String) = dao.profile(id)
    suspend fun activeProfiles() = dao.activeProfiles()
    suspend fun updateRuntime(id: String, state: ServerState, port: Int?) = dao.updateRuntime(id, state, port, System.currentTimeMillis())

    suspend fun upgradeEngine(profile: ServerProfileEntity, target: String): ServerProfileEntity {
        require(EngineCatalog.canUpgrade(profile.engineVersion, target)) { "Engine downgrade is not supported" }
        val updated = profile.copy(engineVersion = target, updatedAt = System.currentTimeMillis())
        dao.updateProfile(updated)
        return updated
    }

    fun profileDirectory(id: String): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "servers/$id")
    fun backupDirectory(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
}

