package net.novax.luanet

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.google.android.play.core.splitcompat.SplitCompat
import net.novax.luanet.account.AccountGateway
import net.novax.luanet.data.ServerRepository
import net.novax.luanet.data.backup.BackupManager
import net.novax.luanet.data.content.ContentDbClient
import net.novax.luanet.data.db.LuaNetDatabase
import net.novax.luanet.data.importer.SafeZipImporter
import net.novax.luanet.monetization.AdMobAdvertisementGate
import net.novax.luanet.monetization.PlayBillingGateway
import net.novax.luanet.network.AuthTokenProvider
import net.novax.luanet.network.AuthTokenStore
import net.novax.luanet.network.ControlPlaneClient
import net.novax.luanet.runtime.EngineFeatureInstaller

class LuaNetApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, LuaNetDatabase::class.java, "luanet.db")
        .addMigrations(LuaNetDatabase.MIGRATION_1_2)
        .addMigrations(LuaNetDatabase.MIGRATION_2_3)
        .addMigrations(LuaNetDatabase.MIGRATION_3_4)
        .addMigrations(LuaNetDatabase.MIGRATION_4_5)
        .build()
    val zipImporter = SafeZipImporter()
    val servers = ServerRepository(application, database.dao(), zipImporter)
    val contentDb = ContentDbClient()
    val backups = BackupManager(servers.backupDirectory(), database.dao())
    val authTokens = AuthTokenStore(application)
    val accountGateway = AccountGateway(application)
    val engineFeatures = EngineFeatureInstaller(application)
    val ads = AdMobAdvertisementGate(application)
    val billing = PlayBillingGateway(application)
    val controlPlane = ControlPlaneClient(AuthTokenProvider {
        if (accountGateway.currentSession().signedIn) accountGateway.freshIdToken() else authTokens.requireBearerToken()
    })
}
