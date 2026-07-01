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
        ServerCrashReportEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LuaNetDatabase : RoomDatabase() {
    abstract fun dao(): LuaNetDao

	    companion object {
	        val MIGRATION_1_2 = object : Migration(1, 2) {
	            override fun migrate(db: SupportSQLiteDatabase) {
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN autoOffEnabled INTEGER NOT NULL DEFAULT 0")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN autoOffMinutes INTEGER NOT NULL DEFAULT 15")
	            }
	        }

	        val MIGRATION_2_3 = object : Migration(2, 3) {
	            override fun migrate(db: SupportSQLiteDatabase) {
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN serverDescription TEXT NOT NULL DEFAULT ''")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN motd TEXT NOT NULL DEFAULT ''")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN announceServer INTEGER NOT NULL DEFAULT 0")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN defaultPrivileges TEXT NOT NULL DEFAULT 'interact,shout'")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN disallowEmptyPassword INTEGER NOT NULL DEFAULT 1")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN enableRollback INTEGER NOT NULL DEFAULT 0")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN timeSpeed INTEGER NOT NULL DEFAULT 72")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN activeBlockRange INTEGER NOT NULL DEFAULT 3")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN maxBlockSendDistance INTEGER NOT NULL DEFAULT 10")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN maxBlockGenerateDistance INTEGER NOT NULL DEFAULT 6")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN dedicatedServerStepMs INTEGER NOT NULL DEFAULT 100")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN maxObjectsPerBlock INTEGER NOT NULL DEFAULT 64")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN itemEntityTtl INTEGER NOT NULL DEFAULT 900")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN maxPacketsPerIteration INTEGER NOT NULL DEFAULT 1024")
	                db.execSQL("ALTER TABLE server_profiles ADD COLUMN mapgenLimit INTEGER NOT NULL DEFAULT 31000")
	                db.execSQL("""
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
	                db.execSQL("CREATE INDEX IF NOT EXISTS index_server_players_profileId ON server_players(profileId)")
	                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_server_players_profileId_name ON server_players(profileId, name)")
	                db.execSQL("""
	                    CREATE TABLE IF NOT EXISTS server_config_settings (
	                        profileId TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, `key`),
                        FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON DELETE CASCADE
	                    )
	                """.trimIndent())
	                db.execSQL("CREATE INDEX IF NOT EXISTS index_server_config_settings_profileId ON server_config_settings(profileId)")
	            }
	        }

	        val MIGRATION_3_4 = object : Migration(3, 4) {
	            override fun migrate(db: SupportSQLiteDatabase) {
	                db.execSQL("ALTER TABLE server_players ADD COLUMN privileges TEXT NOT NULL DEFAULT ''")
	            }
	        }

	        val MIGRATION_4_5 = object : Migration(4, 5) {
	            override fun migrate(db: SupportSQLiteDatabase) {
	                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS server_crash_reports (
                        id TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL,
                        code TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        engineVersion TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES server_profiles(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_server_crash_reports_profileId ON server_crash_reports(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_server_crash_reports_profileId_code ON server_crash_reports(profileId, code)")
            }
        }
    }
}
