package net.novax.luanet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ServerProfileEntity::class, InstalledPackageEntity::class, BackupEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LuaNetDatabase : RoomDatabase() {
    abstract fun dao(): LuaNetDao
}

