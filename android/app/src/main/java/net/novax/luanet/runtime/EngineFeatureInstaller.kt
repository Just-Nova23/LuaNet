package net.novax.luanet.runtime

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.novax.luanet.BuildConfig
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.EngineRelease

class EngineFeatureInstaller(private val context: Context) {
    private val manager by lazy { SplitInstallManagerFactory.create(context) }

    fun isInstalled(version: String): Boolean {
        val release = EngineCatalog.find(version) ?: return false
        return isInstalled(release)
    }

    fun isInstalled(release: EngineRelease): Boolean {
        val module = release.featureModule ?: return hasLibraryInInstalledApks(release.libraryName)
        return hasLibraryInInstalledApks(release.libraryName) || module in manager.installedModules
    }

    suspend fun installIfNeeded(version: String): String {
        val release = requireNotNull(EngineCatalog.find(version)) { "Unknown engine $version" }
        val module = release.featureModule ?: return "Engine ${release.version} is included"
        if (isInstalled(release)) return "Engine ${release.version} is already installed"
        if (BuildConfig.DEBUG) {
            throw IllegalStateException("Engine ${release.version} is not included in this debug APK. Use the Play/Internal App Sharing build to download legacy engines.")
        }
        return installModule(module, release.version)
    }

    private suspend fun installModule(module: String, version: String): String = suspendCancellableCoroutine { continuation ->
        var sessionId = 0
        lateinit var listener: SplitInstallStateUpdatedListener
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.INSTALLED -> {
                    manager.unregisterListener(listener)
                    if (continuation.isActive) continuation.resume("Engine $version installed")
                }
                SplitInstallSessionStatus.FAILED -> {
                    manager.unregisterListener(listener)
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Engine $version install failed with Play error ${state.errorCode()}"))
                    }
                }
                SplitInstallSessionStatus.CANCELED -> {
                    manager.unregisterListener(listener)
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Engine $version install was canceled"))
                    }
                }
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    manager.unregisterListener(listener)
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Engine $version install requires Google Play confirmation. Install from the Play build and try again."))
                    }
                }
            }
        }
        manager.registerListener(listener)
        continuation.invokeOnCancellation { manager.unregisterListener(listener) }
        val request = SplitInstallRequest.newBuilder()
            .addModule(module)
            .build()
        manager.startInstall(request)
            .addOnSuccessListener { id -> sessionId = id }
            .addOnFailureListener { error ->
                manager.unregisterListener(listener)
                if (!continuation.isActive) return@addOnFailureListener
                val message = if (error is SplitInstallException) {
                    "Engine $version install failed with Play error ${error.errorCode}"
                } else {
                    error.message ?: "Engine $version install failed"
                }
                continuation.resumeWithException(IllegalStateException(message, error))
            }
    }

    private fun hasLibraryInInstalledApks(libraryName: String): Boolean {
        val filename = "lib$libraryName.so"
        val apkPaths = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let(::addAll)
        }
        return apkPaths.any { path ->
            runCatching {
                ZipFile(path).use { zip ->
                    zip.entries().asSequence().any { entry ->
                        !entry.isDirectory && entry.name.startsWith("lib/") && entry.name.endsWith("/$filename")
                    }
                }
            }.getOrDefault(false)
        }
    }
}
