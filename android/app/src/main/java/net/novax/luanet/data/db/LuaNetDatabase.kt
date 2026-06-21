package net.novax.luanet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerProfileEntity::class, InstalledPackageEntity::class, BackupEntity::class],
    version = 2,
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
    }
}
