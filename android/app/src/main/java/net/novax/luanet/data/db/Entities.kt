package net.novax.luanet.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.novax.luanet.domain.AccessMode
import net.novax.luanet.domain.PackageSource
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engineVersion: String,
    val gameKey: String?,
    val mapgen: String,
    val maxPlayers: Int,
    val creative: Boolean,
    val damage: Boolean,
    val pvp: Boolean,
    val accessMode: AccessMode,
    val state: ServerState,
    val localPort: Int?,
    val publicEnabled: Boolean,
    val publicHost: String?,
    val publicPort: Int?,
    val autoOffEnabled: Boolean,
    val autoOffMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "installed_packages",
    foreignKeys = [ForeignKey(
        entity = ServerProfileEntity::class,
        parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "packageKey"], unique = true)],
)
data class InstalledPackageEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val packageKey: String,
    val title: String,
    val type: PackageType,
    val source: PackageSource,
    val releaseId: Long?,
    val compatible: Boolean,
    val enabled: Boolean,
    val installedAt: Long,
)

@Entity(
    tableName = "backups",
    foreignKeys = [ForeignKey(
        entity = ServerProfileEntity::class,
        parentColumns = ["id"], childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId")],
)
data class BackupEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val fileName: String,
    val automatic: Boolean,
    val reason: String,
    val createdAt: Long,
    val sizeBytes: Long,
)
