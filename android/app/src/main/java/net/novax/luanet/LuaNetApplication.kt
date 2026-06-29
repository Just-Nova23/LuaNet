package net.novax.luanet

import android.app.Application
import androidx.room.Room
import net.novax.luanet.data.ServerRepository
import net.novax.luanet.data.backup.BackupManager
import net.novax.luanet.data.content.ContentDbClient
import net.novax.luanet.data.db.LuaNetDatabase
import net.novax.luanet.data.importer.SafeZipImporter
import net.novax.luanet.network.AuthTokenProvider
import net.novax.luanet.network.AuthTokenStore
import net.novax.luanet.network.ControlPlaneClient

class LuaNetApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, LuaNetDatabase::class.java, "luanet.db")
        .addMigrations(LuaNetDatabase.MIGRATION_1_2)
        .addMigrations(LuaNetDatabase.MIGRATION_2_3)
        .addMigrations(LuaNetDatabase.MIGRATION_3_4)
        .build()
    val zipImporter = SafeZipImporter()
    val servers = ServerRepository(application, database.dao(), zipImporter)
    val contentDb = ContentDbClient()
    val backups = BackupManager(servers.backupDirectory(), database.dao())
    val authTokens = AuthTokenStore(application)
    val controlPlane = ControlPlaneClient(AuthTokenProvider { authTokens.requireBearerToken() })
}
