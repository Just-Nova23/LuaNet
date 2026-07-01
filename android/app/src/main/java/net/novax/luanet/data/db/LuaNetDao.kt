package net.novax.luanet.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.novax.luanet.domain.ServerState

@Dao
interface LuaNetDao {
    @Query("SELECT * FROM server_profiles ORDER BY updatedAt DESC")
    fun observeProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id=:id")
    suspend fun profile(id: String): ServerProfileEntity?

    @Query("SELECT * FROM server_profiles WHERE state IN ('STARTING','RUNNING','STOPPING')")
    suspend fun activeProfiles(): List<ServerProfileEntity>

    @Query("SELECT * FROM server_profiles WHERE state='CRASHED'")
    suspend fun crashedProfiles(): List<ServerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: ServerProfileEntity)

    @Update suspend fun updateProfile(profile: ServerProfileEntity)
    @Delete suspend fun deleteProfile(profile: ServerProfileEntity)

    @Query("UPDATE server_profiles SET state=:state, localPort=:localPort, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateRuntime(id: String, state: ServerState, localPort: Int?, updatedAt: Long)

    @Query("UPDATE server_profiles SET publicEnabled=:enabled, publicHost=:host, publicPort=:port, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updatePublic(id: String, enabled: Boolean, host: String?, port: Int?, updatedAt: Long)

    @Query("SELECT * FROM installed_packages WHERE profileId=:profileId ORDER BY type,title")
    fun observePackages(profileId: String): Flow<List<InstalledPackageEntity>>

    @Query("SELECT * FROM installed_packages WHERE profileId=:profileId ORDER BY type,title")
    suspend fun packages(profileId: String): List<InstalledPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackage(item: InstalledPackageEntity)

    @Query("SELECT * FROM backups WHERE profileId=:profileId ORDER BY createdAt DESC")
    fun observeBackups(profileId: String): Flow<List<BackupEntity>>

    @Query("SELECT * FROM backups WHERE profileId=:profileId AND id=:id")
    suspend fun backup(profileId: String, id: String): BackupEntity?

    @Insert suspend fun insertBackup(backup: BackupEntity)

    @Query("SELECT * FROM backups WHERE profileId=:profileId AND automatic=1 ORDER BY createdAt DESC")
    suspend fun automaticBackups(profileId: String): List<BackupEntity>

    @Delete suspend fun deleteBackup(backup: BackupEntity)

    @Query("DELETE FROM installed_packages WHERE profileId=:profileId")
    suspend fun deletePackages(profileId: String)

    @Query("DELETE FROM server_config_settings WHERE profileId=:profileId")
    suspend fun deleteConfigSettings(profileId: String)

    @Query("SELECT * FROM server_players WHERE profileId=:profileId ORDER BY online DESC, lastSeenAt DESC, name COLLATE NOCASE")
    fun observePlayers(profileId: String): Flow<List<ServerPlayerEntity>>

    @Query("SELECT * FROM server_players WHERE profileId=:profileId AND name=:name")
    suspend fun player(profileId: String, name: String): ServerPlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayer(player: ServerPlayerEntity)

    @Query("UPDATE server_players SET online=0, lastSeenAt=:seenAt WHERE profileId=:profileId")
    suspend fun markAllPlayersOffline(profileId: String, seenAt: Long)

    @Query("UPDATE server_players SET banned=:banned WHERE profileId=:profileId AND name=:name")
    suspend fun updatePlayerBanned(profileId: String, name: String, banned: Boolean): Int

    @Query("UPDATE server_players SET admin=:admin WHERE profileId=:profileId AND name=:name")
    suspend fun updatePlayerAdmin(profileId: String, name: String, admin: Boolean): Int

    @Query("UPDATE server_players SET admin=:admin, privileges=:privileges WHERE profileId=:profileId AND name=:name")
    suspend fun updatePlayerPrivileges(profileId: String, name: String, admin: Boolean, privileges: String): Int

    @Query("SELECT * FROM server_config_settings WHERE profileId=:profileId ORDER BY key")
    fun observeConfigSettings(profileId: String): Flow<List<ServerConfigSettingEntity>>

    @Query("SELECT * FROM server_config_settings WHERE profileId=:profileId ORDER BY key")
    suspend fun configSettings(profileId: String): List<ServerConfigSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfigSetting(setting: ServerConfigSettingEntity)

    @Query("SELECT * FROM server_crash_reports WHERE profileId=:profileId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestCrashReport(profileId: String): Flow<ServerCrashReportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrashReport(report: ServerCrashReportEntity)
}
