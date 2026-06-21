package net.novax.luanet

import android.app.Application
import androidx.room.Room
import net.novax.luanet.data.ServerRepository
import net.novax.luanet.data.backup.BackupManager
import net.novax.luanet.data.content.ContentDbClient
import net.novax.luanet.data.db.LuaNetDatabase
import net.novax.luanet.data.importer.SafeZipImporter

class LuaNetApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, LuaNetDatabase::class.java, "luanet.db").build()
    val servers = ServerRepository(application, database.dao())
    val contentDb = ContentDbClient()
    val zipImporter = SafeZipImporter()
    val backups = BackupManager(servers.backupDirectory(), database.dao())
}

