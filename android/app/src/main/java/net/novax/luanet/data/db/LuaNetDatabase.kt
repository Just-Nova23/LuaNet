package net.novax.luanet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServerProfileEntity::class,
        InstalledPackageEntity::class,
        BackupEntity::class,
        ServerPlayerEntity::class,
        ServerConfigSettingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LuaNetDatabase : RoomDatabase() {
    abstract fun dao(): LuaNetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN autoOffEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN autoOffMinutes INTEGER NOT NULL DEFAULT 15")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN serverDescription TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN motd TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN announceServer INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN defaultPrivileges TEXT NOT NULL DEFAULT 'interact,shout'")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN disallowEmptyPassword INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN enableRollback INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN timeSpeed INTEGER NOT NULL DEFAULT 72")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN activeBlockRange INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN maxBlockSendDistance INTEGER NOT NULL DEFAULT 10")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN maxBlockGenerateDistance INTEGER NOT NULL DEFAULT 6")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN dedicatedServerStepMs INTEGER NOT NULL DEFAULT 100")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN maxObjectsPerBlock INTEGER NOT NULL DEFAULT 64")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN itemEntityTtl INTEGER NOT NULL DEFAULT 900")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN maxPacketsPerIteration INTEGER NOT NULL DEFAULT 1024")
                database.execSQL("ALTER TABLE server_profiles ADD COLUMN mapgenLimit INTEGER NOT NULL DEFAULT 31000")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS server_players (
                        profileId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        firstSeenAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL,
                        online INTEGER NOT NULL,
                        banned INTEGER NOT NULL,
                        admin INTEGER NOT NULL,
                        PRIMARY KEY(profileId, name),
                        FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_server_players_profileId ON server_players(profileId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_server_players_profileId_name ON server_players(profileId, name)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS server_config_settings (
                        profileId TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, `key`),
                        FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_server_config_settings_profileId ON server_config_settings(profileId)")
            }
        }
    }
}
