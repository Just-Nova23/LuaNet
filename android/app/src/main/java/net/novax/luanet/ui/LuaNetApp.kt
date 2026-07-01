package net.novax.luanet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List as Terminal
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange as Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share as Cloud
import androidx.compose.material.icons.filled.Share as ContentCopy
import androidx.compose.material.icons.filled.Home as Dashboard
import androidx.compose.material.icons.filled.AddCircle as FolderZip
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person as Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share as Lan
import androidx.compose.material.icons.filled.Build as Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close as Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.noties.markwon.Markwon
import net.novax.luanet.data.db.BackupEntity
import net.novax.luanet.data.db.ServerCrashReportEntity
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.data.db.ServerPlayerEntity
import net.novax.luanet.data.db.ServerConfigSettingEntity
import net.novax.luanet.R
import net.novax.luanet.data.ServerModSetting
import net.novax.luanet.data.ServerProfileSettingsUpdate
import net.novax.luanet.data.ServerRepository
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.content.ContentPackage
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.PackageType
import net.novax.luanet.domain.ServerState
import net.novax.luanet.runtime.OrchestratorService
import net.novax.luanet.runtime.RuntimeRegistry
import net.novax.luanet.runtime.RuntimeSnapshot

private sealed interface Destination {
    data object Servers : Destination
    data object Create : Destination
    data object Account : Destination
    data object Premium : Destination
    data object Credits : Destination
    data class Dashboard(val id: String) : Destination
    data class ContentLibrary(val id: String) : Destination
    data class ZipImport(val id: String) : Destination
    data class AdvancedSettings(val id: String) : Destination
    data class GameSettings(val id: String) : Destination
    data class ModSettings(val id: String) : Destination
    data class PlayerMenu(val id: String, val playerName: String) : Destination
}

private typealias ServerSettingsSaver = (
    update: ServerProfileSettingsUpdate,
    onResult: (Result<String>) -> Unit,
) -> Unit

@Composable
fun LuaNetApp(viewModel: MainViewModel) {
    val profilesLoaded by viewModel.profilesLoaded.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val contentDetails by viewModel.contentDetails.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    var destination: Destination by remember { mutableStateOf(Destination.Servers) }
    var backStack: List<Destination> by remember { mutableStateOf(emptyList()) }
    fun navigate(next: Destination) {
        if (destination != next) {
            backStack = backStack + destination
            destination = next
        }
    }
    fun replaceWith(next: Destination) {
        backStack = emptyList()
        destination = next
    }
    fun goBack() {
        val previous = backStack.lastOrNull()
        if (previous == null) {
            replaceWith(Destination.Servers)
        } else {
            backStack = backStack.dropLast(1)
            destination = previous
        }
    }
    BackHandler(destination != Destination.Servers) { goBack() }
    if (!profilesLoaded) {
        StartupLoadingScreen()
        return
    }
    when (val current = destination) {
        Destination.Servers -> ServerList(
            profiles = profiles,
            account = account,
            onCreate = { navigate(Destination.Create) },
            onOpen = { navigate(Destination.Dashboard(it)) },
            onOpenAccount = { navigate(Destination.Account) },
            onOpenPremium = { navigate(Destination.Premium) },
            onOpenCredits = { navigate(Destination.Credits) },
        )
        Destination.Account -> AccountScreen(
            account = account,
            onBack = { goBack() },
            onOpenPremium = { navigate(Destination.Premium) },
            onSignInEmail = viewModel::signInWithEmail,
            onCreateEmailAccount = viewModel::createEmailAccount,
            onGoogleSignIn = viewModel::signInWithGoogle,
            onGitHubSignIn = viewModel::signInWithGitHub,
            onSendVerificationEmail = viewModel::sendVerificationEmail,
            onSignOut = viewModel::signOut,
            onDeleteAccount = viewModel::deleteAccount,
        )
        Destination.Premium -> PremiumScreen(
            account = account,
            onBack = { goBack() },
            onSyncEntitlement = viewModel::syncEntitlement,
            onPurchasePremium = viewModel::purchasePremium,
            onRestorePremium = viewModel::restorePremium,
        )
        Destination.Credits -> CreditsScreen(
            onBack = { goBack() },
        )
        Destination.Create -> CreateServer(
            onBack = { goBack() },
            onCreate = { name, version, mapgen, players, creative, damage, pvp ->
                viewModel.create(name, version, mapgen, players, creative, damage, pvp) {
                    backStack = listOf(Destination.Servers)
                    destination = Destination.Dashboard(it)
                }
            },
        )
        is Destination.Dashboard -> {
            val installedPackages by viewModel.installedPackages(current.id).collectAsStateWithLifecycle(emptyList())
            val players by viewModel.players(current.id).collectAsStateWithLifecycle(emptyList())
            val backups by viewModel.backups(current.id).collectAsStateWithLifecycle(emptyList())
            val gameSettings by viewModel.gameSettings(current.id).collectAsStateWithLifecycle(emptyList())
            val modSettings by viewModel.modSettings(current.id).collectAsStateWithLifecycle(emptyList())
            val crashReport by viewModel.latestCrashReport(current.id).collectAsStateWithLifecycle(null)
            Dashboard(
                profile = profiles.firstOrNull { it.id == current.id },
                installedPackages = installedPackages,
                players = players,
                backups = backups,
                crashReport = crashReport,
                onBack = { goBack() },
                onOpenContentLibrary = { navigate(Destination.ContentLibrary(current.id)) },
                onOpenZipImport = { navigate(Destination.ZipImport(current.id)) },
                onOpenAdvancedSettings = { navigate(Destination.AdvancedSettings(current.id)) },
                onOpenGameSettings = { navigate(Destination.GameSettings(current.id)) },
                onOpenModSettings = { navigate(Destination.ModSettings(current.id)) },
                onOpenPlayer = { navigate(Destination.PlayerMenu(current.id, it)) },
                hasGameSettings = gameSettings.isNotEmpty(),
                hasModSettings = modSettings.isNotEmpty(),
                onSaveServerSettings = viewModel::updateServerSettings,
                onCreateBackup = viewModel::createBackup,
                onRestoreBackup = viewModel::restoreBackup,
                onDeleteBackup = viewModel::deleteBackup,
                onEnsureEngineInstalled = viewModel::ensureEngineInstalled,
                onStartPublicTunnel = viewModel::startPublicTunnel,
                onStopPublicTunnel = viewModel::stopPublicTunnel,
                onSetPlayerAdminOffline = viewModel::setPlayerAdminOffline,
                onUnbanPlayerOffline = viewModel::unbanPlayerOffline,
            )
        }
        is Destination.ContentLibrary -> {
            val installedPackages by viewModel.installedPackages(current.id).collectAsStateWithLifecycle(emptyList())
            ContentBrowserScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                installedPackages = installedPackages,
                state = content,
                details = contentDetails,
                onBack = { goBack() },
                onSearch = viewModel::searchContent,
                onLoadDetail = viewModel::loadContentDetail,
                onInstall = viewModel::installContent,
            )
        }
        is Destination.ZipImport -> {
            val installedPackages by viewModel.installedPackages(current.id).collectAsStateWithLifecycle(emptyList())
            ZipImportScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                installedPackages = installedPackages,
                operation = content.operation,
                onBack = { goBack() },
                onImport = viewModel::importArchive,
            )
        }
        is Destination.AdvancedSettings -> {
            val advancedSettings by viewModel.advancedSettings(current.id).collectAsStateWithLifecycle(emptyList())
            AdvancedSettingsScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                settings = advancedSettings,
                onBack = { goBack() },
                onSaveServerSettings = viewModel::updateServerSettings,
                onSaveAdvancedSetting = viewModel::saveAdvancedSetting,
            )
        }
        is Destination.GameSettings -> {
            val gameSettings by viewModel.gameSettings(current.id).collectAsStateWithLifecycle(emptyList())
            PackageSettingsScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                title = "Game settings",
                description = "These values come from the installed game settingtypes.txt files and are written to minetest.conf. Restart the server to apply changes.",
                emptyTitle = "No game settings",
                emptyDetail = "The installed game does not expose settingtypes.txt options.",
                icon = Icons.Default.Settings,
                settings = gameSettings,
                onBack = { goBack() },
                onSave = viewModel::saveGameSetting,
            )
        }
        is Destination.ModSettings -> {
            val modSettings by viewModel.modSettings(current.id).collectAsStateWithLifecycle(emptyList())
            PackageSettingsScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                title = "Mod settings",
                description = "These values come from installed mod and modpack settingtypes.txt files and are written to minetest.conf. Restart the server to apply changes.",
                emptyTitle = "No mod settings",
                emptyDetail = "Installed mods do not expose settingtypes.txt options.",
                icon = Icons.Default.Code,
                settings = modSettings,
                onBack = { goBack() },
                onSave = viewModel::saveModSetting,
            )
        }
        is Destination.PlayerMenu -> {
            val context = LocalContext.current
            val players by viewModel.players(current.id).collectAsStateWithLifecycle(emptyList())
            val sessions by RuntimeRegistry.sessions.collectAsStateWithLifecycle()
            val runtime = sessions[current.id]
            val runtimePlayers = runtime?.players.orEmpty()
            val player = players.firstOrNull { it.name == current.playerName }
                ?: ServerPlayerEntity(
                    profileId = current.id,
                    name = current.playerName,
                    firstSeenAt = 0,
                    lastSeenAt = 0,
                    online = current.playerName in runtimePlayers,
                    banned = false,
                    admin = false,
                    privileges = "",
                )
            PlayerMenuScreen(
                profileId = current.id,
                player = player,
                isOnline = player.online || player.name in runtimePlayers,
                running = runtime?.state in setOf("STARTING", "RUNNING", "STOPPING"),
                onBack = { goBack() },
                onCommand = { command -> OrchestratorService.command(context, current.id, command) },
                onSetAdminOffline = viewModel::setPlayerAdminOffline,
                onSetPrivilegeOffline = viewModel::setPlayerPrivilegeOffline,
                onUnbanOffline = viewModel::unbanPlayerOffline,
            )
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    painterResource(R.drawable.ic_luanet),
                    contentDescription = "LuaNet",
                    modifier = Modifier.padding(18.dp).size(72.dp),
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading LuaNet", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerList(
    profiles: List<ServerProfileEntity>,
    account: AccountState,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val activeCount = profiles.count { it.state in setOf(ServerState.STARTING, ServerState.RUNNING) }
    Scaffold(
        topBar = { TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(painterResource(R.drawable.ic_luanet), null, Modifier.padding(7.dp).size(28.dp), tint = Color.Unspecified)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("LuaNet", style = MaterialTheme.typography.titleLarge)
                    Text("On-device Luanti hosting", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }, actions = {
            IconButton(onClick = onOpenPremium) {
                Icon(Icons.Default.Paid, "Premium")
            }
            IconButton(onClick = onOpenCredits) {
                Icon(Icons.Default.Info, "Credits")
            }
            IconButton(onClick = onOpenAccount) {
                AccountAvatar(account.photoUrl, Modifier.size(28.dp))
            }
        }) },
        floatingActionButton = {
            if (profiles.isNotEmpty()) FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, "New server") }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { Spacer(Modifier.height(28.dp)) }
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_luanet), "LuaNet logo", Modifier.size(116.dp), tint = Color.Unspecified)
                        }
                    }
                }
                item {
                    AssistChip(onClick = {}, label = { Text("Runs entirely on your phone") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)) })
                    Spacer(Modifier.height(12.dp))
                    Text("Your world.\nYour server.", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(10.dp))
                    Text("Host Luanti over LAN for free, then add a NovaX public address only when you need it.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            FeatureLine(Icons.Default.Memory, "17 engine versions", "From 5.0.1 through 5.16.1")
                            HorizontalDivider()
                            FeatureLine(Icons.Default.Lan, "LAN without an account", "No ads and no mandatory idle stop")
                            HorizontalDivider()
                            FeatureLine(Icons.Default.Cloud, "Optional public tunnel", "A stable NovaX host and assigned UDP port")
                        }
                    }
                }
                item {
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Create your first server")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("Servers", style = MaterialTheme.typography.headlineMedium)
                        Text("$activeCount active · ${profiles.size} saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        onClick = { onOpen(profile.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(stateColor(profile.state), CircleShape))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(profile.gameKey?.substringAfter('/') ?: "Install a game to begin",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Luanti ${profile.engineVersion} · ${profile.state.name.lowercase()}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    account: AccountState,
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
    onSignInEmail: (String, String, (Result<String>) -> Unit) -> Unit,
    onCreateEmailAccount: (String, String, (Result<String>) -> Unit) -> Unit,
    onGoogleSignIn: (Activity, (Result<String>) -> Unit) -> Unit,
    onGitHubSignIn: (Activity, (Result<String>) -> Unit) -> Unit,
    onSendVerificationEmail: ((Result<String>) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: ((Result<String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val activity = context.findActivity()
    val signedIn = account.signedIn
    val messageIsError = message?.let {
        it.contains("failed", ignoreCase = true) ||
            it.contains("not configured", ignoreCase = true) ||
            it.contains("requires", ignoreCase = true) ||
            it.contains("verify", ignoreCase = true) ||
            it.contains("no active", ignoreCase = true)
    } == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Account")
                        Text("NovaX sign-in and privacy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AccountHeroCard(account)
            }
            if (!signedIn) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sign in to NovaX", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Required only for public tunnels and Premium. Local LAN hosting stays free and accountless.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OAuthButton(
                                    text = "Google",
                                    icon = R.drawable.ic_google,
                                    enabled = account.firebaseAvailable,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val currentActivity = activity
                                    if (currentActivity == null) {
                                        message = "Google sign-in requires an active app screen"
                                    } else {
                                        onGoogleSignIn(currentActivity) { result ->
                                            message = result.fold({ it }, { it.message ?: "Google sign-in failed" })
                                        }
                                    }
                                }
                                OAuthButton(
                                    text = "GitHub",
                                    icon = R.drawable.ic_github,
                                    enabled = account.firebaseAvailable,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val currentActivity = activity
                                    if (currentActivity == null) {
                                        message = "GitHub sign-in requires an active app screen"
                                    } else {
                                        onGitHubSignIn(currentActivity) { result ->
                                            message = result.fold({ it }, { it.message ?: "GitHub sign-in failed" })
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.take(160) },
                                label = { Text("Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = account.firebaseAvailable,
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it.take(128) },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = account.firebaseAvailable,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onSignInEmail(email, password) { result ->
                                            message = result.fold({ it }, { it.message ?: "Sign-in failed" })
                                        }
                                    },
                                    enabled = account.firebaseAvailable && email.isNotBlank() && password.length >= 6,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Sign in") }
                                FilledTonalButton(
                                    onClick = {
                                        onCreateEmailAccount(email, password) { result ->
                                            message = result.fold({ it }, { it.message ?: "Account creation failed" })
                                        }
                                    },
                                    enabled = account.firebaseAvailable && email.isNotBlank() && password.length >= 6,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Create account") }
                            }
                            if (!account.firebaseAvailable) {
                                Text(
                                    "Firebase is not configured in this build.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Account actions", style = MaterialTheme.typography.titleLarge)
                            if (!account.emailVerified) {
                                Text(
                                    "Email verification is required before public tunnels. OAuth providers such as Google/GitHub are accepted by NovaX after Firebase verifies the provider.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        onSendVerificationEmail { result ->
                                            message = result.fold({ it }, { it.message ?: "Verification failed" })
                                        }
                                    },
                                    enabled = !account.emailVerified,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Verify email") }
                                FilledTonalButton(
                                    onClick = {
                                        onSignOut()
                                        message = "Signed out"
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Sign out") }
                            }
                            TextButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Delete NovaX account") }
                        }
                    }
                }
            }
            message?.let {
                item {
                    Text(
                        it,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This revokes public tunnels, removes NovaX entitlement data and deletes the Firebase Auth user on the control plane.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDeleteAccount { result -> message = result.fold({ it }, { it.message ?: "Delete account failed" }) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumScreen(
    account: AccountState,
    onBack: () -> Unit,
    onSyncEntitlement: ((Result<String>) -> Unit) -> Unit,
    onPurchasePremium: (Activity, Boolean, (Result<String>) -> Unit) -> Unit,
    onRestorePremium: ((Result<String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var message by remember { mutableStateOf<String?>(null) }
    val signedIn = account.signedIn && account.tokenConfigured
    val messageIsError = message?.let {
        it.contains("failed", ignoreCase = true) ||
            it.contains("sign in", ignoreCase = true) ||
            it.contains("not configured", ignoreCase = true)
    } == true
    fun purchase(yearly: Boolean) {
        val currentActivity = activity
        if (!signedIn) {
            message = "Sign in from Account before buying Premium"
        } else if (currentActivity == null) {
            message = "Purchase requires an active app screen"
        } else {
            onPurchasePremium(currentActivity, yearly) { result ->
                message = result.fold({ it }, { it.message ?: "Purchase failed" })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Premium")
                        Text("Public hosting without ads or time limits", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                                Icon(Icons.Default.Paid, null, Modifier.padding(10.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("LuaNet Premium", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    if (account.tier.name == "PREMIUM") "Active on this NovaX account" else "Upgrade when public hosting needs to stay online",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccountStatusPill("Current tier", account.tier.name.lowercase().replaceFirstChar(Char::uppercase), Modifier.weight(1f))
                            AccountStatusPill("Account", if (signedIn) "Signed in" else "Required", Modifier.weight(1f), error = !signedIn)
                        }
                        account.expiresAt?.let {
                            Text("Expires: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PremiumPlanCard(
                        title = "Monthly",
                        price = "€1.99",
                        subtitle = "per month",
                        modifier = Modifier.weight(1f),
                        onClick = { purchase(false) },
                        enabled = signedIn,
                    )
                    PremiumPlanCard(
                        title = "Yearly",
                        price = "€19.10",
                        subtitle = "per year",
                        modifier = Modifier.weight(1f),
                        onClick = { purchase(true) },
                        enabled = signedIn,
                    )
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Included", style = MaterialTheme.typography.titleLarge)
                        FeatureLine(Icons.Default.Cloud, "Five active public tunnels", "Limit is shared by the NovaX account across devices")
                        HorizontalDivider()
                        FeatureLine(Icons.Default.CheckCircle, "No public-start interstitial", "Ads remain only on Free public starts")
                        HorizontalDivider()
                        FeatureLine(Icons.Default.Public, "No four-hour lease limit", "Premium leases renew while entitlement is valid")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onRestorePremium { result -> message = result.fold({ it }, { it.message ?: "Restore failed" }) } },
                        enabled = signedIn,
                        modifier = Modifier.weight(1f),
                    ) { Text("Restore") }
                    FilledTonalButton(
                        onClick = { onSyncEntitlement { result -> message = result.fold({ it }, { it.message ?: "Entitlement sync failed" }) } },
                        enabled = signedIn,
                        modifier = Modifier.weight(1f),
                    ) { Text("Sync") }
                }
            }
            if (!signedIn) {
                item {
                    Text(
                        "Open Account and sign in with Google, GitHub or email before buying or restoring Premium.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            message?.let {
                item {
                    Text(
                        it,
                        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumPlanCard(
    title: String,
    price: String,
    subtitle: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(price, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Credits")
                        Text("Project links and legal contact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("LuaNet", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Android hosting for Luanti, with optional NovaX public UDP tunnel. App code is Apache-2.0; Luanti engine fork/bridge notices stay under their upstream licenses.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Language,
                    title = "Website",
                    detail = "luanet.novaxhosting.com",
                    url = "https://luanet.novaxhosting.com",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Code,
                    title = "App source",
                    detail = "github.com/Just-Nova23/LuaNet",
                    url = "https://github.com/Just-Nova23/LuaNet",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Info,
                    title = "Privacy policy",
                    detail = "luanet.novaxhosting.com/privacy",
                    url = "https://luanet.novaxhosting.com/privacy",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Info,
                    title = "Terms",
                    detail = "luanet.novaxhosting.com/terms",
                    url = "https://luanet.novaxhosting.com/terms",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.AccountCircle,
                    title = "Account deletion",
                    detail = "luanet.novaxhosting.com/delete-account",
                    url = "https://luanet.novaxhosting.com/delete-account",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Code,
                    title = "Licenses",
                    detail = "luanet.novaxhosting.com/licenses",
                    url = "https://luanet.novaxhosting.com/licenses",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Language,
                    title = "NovaX status",
                    detail = "status.novaxhosting.com",
                    url = "https://status.novaxhosting.com",
                    context = context,
                )
            }
            item {
                CreditLinkCard(
                    icon = Icons.Default.Forum,
                    title = "Contact",
                    detail = "luanet@novaxhosting.com",
                    url = "mailto:luanet@novaxhosting.com",
                    context = context,
                )
            }
            item {
                Text(
                    "LuaNet does not upload worlds, chat or player names to NovaX. The control plane stores only account, device, tunnel lease, billing entitlement and anti-abuse metadata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreditLinkCard(
    icon: ImageVector,
    title: String,
    detail: String,
    url: String,
    context: Context,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { openExternalLink(context, url) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open")
                }
                FilledTonalButton(
                    onClick = { copy(context, detail) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun AccountHeroCard(account: AccountState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    AccountAvatar(account.photoUrl, Modifier.padding(10.dp).size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (account.signedIn) account.email ?: account.displayName ?: "Signed in" else "NovaX account",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (account.signedIn) "Ready for public tunnels" else "Sign in to enable public hosting and Premium",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountStatusPill(
                    label = "Tier",
                    value = account.tier.name.lowercase().replaceFirstChar(Char::uppercase),
                    modifier = Modifier.weight(1f),
                )
                AccountStatusPill(
                    label = "Verification",
                    value = if (!account.signedIn) "Required" else if (account.emailVerified) "Verified" else "Pending",
                    modifier = Modifier.weight(1f),
                    error = account.signedIn && !account.emailVerified,
                )
            }
            account.expiresAt?.let {
                Text("Premium expires: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "LAN hosting works without an account. NovaX account data is used only for public tunnels, billing and anti-abuse limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountStatusPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccountAvatar(photoUrl: String?, modifier: Modifier = Modifier) {
    if (photoUrl.isNullOrBlank()) {
        Icon(Icons.Default.AccountCircle, "Account", modifier)
    } else {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Account",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    }
}

@Composable
private fun OAuthButton(
    text: String,
    icon: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun stateColor(state: ServerState) = when (state) {
    ServerState.RUNNING -> MaterialTheme.colorScheme.primary
    ServerState.STARTING, ServerState.STOPPING -> MaterialTheme.colorScheme.tertiary
    ServerState.CRASHED -> MaterialTheme.colorScheme.error
    ServerState.STOPPED -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateServer(
    onBack: () -> Unit,
    onCreate: (String, String, String, Int, Boolean, Boolean, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(EngineCatalog.latest.version) }
    var mapgen by remember { mutableStateOf("v7") }
    var players by remember { mutableStateOf("8") }
    var creative by remember { mutableStateOf(false) }
    var damage by remember { mutableStateOf(true) }
    var pvp by remember { mutableStateOf(true) }
    var showVersions by remember { mutableStateOf(false) }
    var showMapgens by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Create server") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Set up the basics", style = MaterialTheme.typography.headlineMedium)
                Text("You can change content, access and network settings later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Identity", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Server name") },
                            supportingText = { Text("Shown in LuaNet and, optionally, the public server list") }, modifier = Modifier.fillMaxWidth())
                        FilledTonalButton(onClick = { showVersions = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Memory, null); Spacer(Modifier.width(8.dp)); Text("Luanti engine $version")
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Gameplay", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { showMapgens = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Public, null); Spacer(Modifier.width(8.dp)); Text("Map generator $mapgen")
                        }
                        OutlinedTextField(players, { players = it.filter(Char::isDigit).take(3) }, label = { Text("Maximum players") }, modifier = Modifier.fillMaxWidth())
                        Toggle("Creative mode", "Unlimited items and instant building", creative) { creative = it }
                        HorizontalDivider()
                        Toggle("Damage", "Players can lose health", damage) { damage = it }
                        HorizontalDivider()
                        Toggle("PvP", "Players can damage each other", pvp) { pvp = it }
                    }
                }
            }
            item {
                Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Starts unlisted and open on LAN. Public access is always opt-in.", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    onClick = { onCreate(name, version, mapgen, players.toIntOrNull() ?: 8, creative, damage, pvp) },
                    enabled = name.isNotBlank() && (players.toIntOrNull() ?: 0) in 1..100,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Create server"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showVersions) AlertDialog(
        onDismissRequest = { showVersions = false },
        title = { Text("Luanti engine") },
        text = {
            LazyColumn { items(EngineCatalog.releases.reversed()) { item ->
                TextButton(onClick = { version = item.version; showVersions = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.version, modifier = Modifier.fillMaxWidth())
                }
            } }
        },
        confirmButton = { TextButton(onClick = { showVersions = false }) { Text("Close") } },
    )
    if (showMapgens) AlertDialog(
        onDismissRequest = { showMapgens = false },
        title = { Text("Map generator") },
        text = {
            LazyColumn { items(ServerRepository.MAPGENS) { item ->
                TextButton(onClick = { mapgen = item; showMapgens = false }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item)
                        Text(mapgenDescription(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
        },
        confirmButton = { TextButton(onClick = { showMapgens = false }) { Text("Close") } },
    )
}

@Composable
private fun Toggle(label: String, detail: String? = null, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange, enabled = enabled)
    }
}

@Composable
private fun NumberSetting(label: String, detail: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(detail) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun String.filterSignedDigits(max: Int): String {
    val sign = if (startsWith("-")) "-" else ""
    return sign + filter(Char::isDigit).take(max)
}

private fun mapgenDescription(name: String): String = when (name) {
    "v7" -> "Modern general-purpose terrain; good default."
    "valleys" -> "Terrain with wide valleys and rivers."
    "carpathian" -> "Mountain-heavy terrain."
    "flat" -> "Mostly flat worlds for building."
    "fractal" -> "Experimental fractal terrain."
    "singlenode" -> "Empty single-node world; used by special games."
    "v6" -> "Older classic terrain generator."
    else -> "Luanti map generator."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    profile: ServerProfileEntity?,
    installedPackages: List<InstalledPackageEntity>,
    players: List<ServerPlayerEntity>,
    backups: List<BackupEntity>,
    crashReport: ServerCrashReportEntity?,
    onBack: () -> Unit,
    onOpenContentLibrary: () -> Unit,
    onOpenZipImport: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenGameSettings: () -> Unit,
    onOpenModSettings: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    hasGameSettings: Boolean,
    hasModSettings: Boolean,
    onSaveServerSettings: ServerSettingsSaver,
    onCreateBackup: (String, (Result<String>) -> Unit) -> Unit,
    onRestoreBackup: (String, String, (Result<String>) -> Unit) -> Unit,
    onDeleteBackup: (String, String, (Result<String>) -> Unit) -> Unit,
    onEnsureEngineInstalled: (String, (Result<String>) -> Unit) -> Unit,
    onStartPublicTunnel: (Activity, String, Int, (Result<String>) -> Unit) -> Unit,
    onStopPublicTunnel: (String, (Result<String>) -> Unit) -> Unit,
    onSetPlayerAdminOffline: (String, String, Boolean, (Result<String>) -> Unit) -> Unit,
    onUnbanPlayerOffline: (String, String, (Result<String>) -> Unit) -> Unit,
) {
    if (profile == null) return
    val context = LocalContext.current
    val sessions by RuntimeRegistry.sessions.collectAsStateWithLifecycle()
    val runtime = sessions[profile.id]
    val running = runtime?.state in setOf("STARTING", "RUNNING", "STOPPING")
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add(DashboardTab(DashboardTabKey.OVERVIEW, "Overview", Icons.Default.Dashboard))
        add(DashboardTab(DashboardTabKey.CONSOLE, "Console", Icons.AutoMirrored.Filled.Terminal))
        add(DashboardTab(DashboardTabKey.PLAYERS, "Players", Icons.Default.Group))
        add(DashboardTab(DashboardTabKey.CONTENT, "Content", Icons.Default.FolderZip))
        add(DashboardTab(DashboardTabKey.SETTINGS, "Settings", Icons.Default.Settings))
        add(DashboardTab(DashboardTabKey.BACKUPS, "Backups", Icons.Default.Backup))
    }
    if (selectedTab > tabs.lastIndex) selectedTab = tabs.lastIndex
    Scaffold(topBar = { TopAppBar(title = {
        Column { Text(profile.name); Text("Luanti ${profile.engineVersion}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.width(10.dp)) }
                items(tabs) { tab ->
                    AssistChip(
                        onClick = { selectedTab = tabs.indexOf(tab) },
                        label = { Text(tab.label) },
                        leadingIcon = { Icon(tab.icon, null, Modifier.size(18.dp)) },
                        colors = if (tabs[selectedTab] == tab) AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ) else AssistChipDefaults.assistChipColors(),
                    )
                }
                item { Spacer(Modifier.width(10.dp)) }
            }
            HorizontalDivider()
            when (tabs[selectedTab].key) {
                DashboardTabKey.OVERVIEW -> Overview(
                    profile = profile,
                    runtime = runtime,
                    crashReport = crashReport,
                    localPort = runtime?.localPort ?: profile.localPort?.takeUnless {
                        runtime == null && profile.state in setOf(ServerState.STARTING, ServerState.RUNNING, ServerState.STOPPING)
                    },
                    context = context,
                    onEnsureEngineInstalled = onEnsureEngineInstalled,
                    onStartPublicTunnel = onStartPublicTunnel,
                    onStopPublicTunnel = onStopPublicTunnel,
                )
                DashboardTabKey.CONSOLE -> ConsolePanel(profile.id, runtime?.logs.orEmpty(), running) { command ->
                    OrchestratorService.command(context, profile.id, command)
                }
                DashboardTabKey.PLAYERS -> PlayersPanel(
                    players = players,
                    runtimePlayers = runtime?.players.orEmpty(),
                    onOpenPlayer = onOpenPlayer,
                )
                DashboardTabKey.CONTENT -> ContentSummaryPanel(
                    profile = profile,
                    installedPackages = installedPackages,
                    hasGameSettings = hasGameSettings,
                    hasModSettings = hasModSettings,
                    onOpenContentLibrary = onOpenContentLibrary,
                    onOpenZipImport = onOpenZipImport,
                    onOpenGameSettings = onOpenGameSettings,
                    onOpenModSettings = onOpenModSettings,
                )
                DashboardTabKey.SETTINGS -> SettingsPanel(profile, installedPackages, onOpenAdvancedSettings, onSaveServerSettings)
                DashboardTabKey.BACKUPS -> BackupPanel(profile, backups, onCreateBackup, onRestoreBackup, onDeleteBackup)
            }
        }
    }
}

private enum class DashboardTabKey { OVERVIEW, CONSOLE, PLAYERS, CONTENT, SETTINGS, BACKUPS }
private data class DashboardTab(val key: DashboardTabKey, val label: String, val icon: ImageVector)

@Composable
private fun ConsolePanel(
    profileId: String,
    logs: List<String>,
    running: Boolean,
    onCommand: (String) -> Unit,
) {
    var command by remember(profileId) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (logs.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.AutoMirrored.Filled.Terminal, null, Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text("Console is quiet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Start the server to see engine logs and run commands.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(logs) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it.take(240) },
                label = { Text("Server command") },
                placeholder = { Text("/status, /kick player, /grant player all") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                enabled = running,
            )
            Button(
                enabled = running && command.isNotBlank(),
                onClick = {
                    onCommand(command)
                    command = ""
                },
            ) { Text("Send") }
        }
    }
}

@Composable
private fun PlayersPanel(
    players: List<ServerPlayerEntity>,
    runtimePlayers: Set<String>,
    onOpenPlayer: (String) -> Unit,
) {
    val merged = remember(players, runtimePlayers) {
        val byName = players.associateBy { it.name }.toMutableMap()
        runtimePlayers.forEach { name ->
            if (name.isNotBlank() && byName[name] == null) {
                byName[name] = ServerPlayerEntity("", name, 0, 0, online = true, banned = false, admin = false, privileges = "")
            }
        }
        byName.values.sortedWith(compareByDescending<ServerPlayerEntity> { it.online || it.name in runtimePlayers }.thenBy { it.name.lowercase() })
    }
    if (merged.isEmpty()) {
        EmptySection(Icons.Default.Group, "No players yet", "Players will stay listed here after they join once, even when the server is stopped.")
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text("Players", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap a player to open moderation and privilege actions. Offline players stay listed after they join once.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(merged) { player ->
                val isOnline = player.online || player.name in runtimePlayers
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPlayer(player.name) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Text(player.name.take(1).uppercase(), Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(player.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildList {
                                    add(if (isOnline) "Online" else "Offline")
                                    if (player.admin) add("Admin")
                                    if (player.banned) add("Banned")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Actions", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMenuScreen(
    profileId: String,
    player: ServerPlayerEntity,
    isOnline: Boolean,
    running: Boolean,
    onBack: () -> Unit,
    onCommand: (String) -> Unit,
    onSetAdminOffline: (String, String, Boolean, (Result<String>) -> Unit) -> Unit,
    onSetPrivilegeOffline: (String, String, String, Boolean, (Result<String>) -> Unit) -> Unit,
    onUnbanOffline: (String, String, (Result<String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val safe = player.name.safePlayerName()
    val currentPrivileges = remember(player.privileges, player.admin) {
        val parsed = player.privileges.privilegeSet()
        if (parsed.isEmpty() && player.admin) COMMON_PLAYER_PRIVILEGES.mapTo(linkedSetOf()) { it.key } else parsed
    }
    var kickReason by remember(player.name) { mutableStateOf("") }
    var actionMessage by remember(player.name) { mutableStateOf<String?>(null) }
    val messageIsError = actionMessage?.let { message ->
        message.contains("failed", ignoreCase = true) ||
            message.contains("does not", ignoreCase = true) ||
            message.contains("must join", ignoreCase = true) ||
            message.contains("stop the server", ignoreCase = true) ||
            message.contains("offline", ignoreCase = true)
    } == true
    fun sendRunningCommand(command: String, message: String = "Command sent. LuaNet will update this screen when Luanti confirms the result.") {
        onCommand(command)
        actionMessage = message
    }
    fun offlineOnlyError() {
        actionMessage = "Stop the server before changing privileges for an offline player. LuaNet will not fake a server action."
    }
    fun runOfflineAction(action: ((Result<String>) -> Unit) -> Unit) {
        actionMessage = "Applying offline change..."
        action { result -> actionMessage = result.fold({ it }, { it.message ?: "Action failed" }) }
    }
    fun changePrivilege(privilege: String, enabled: Boolean) {
        if (running && isOnline) {
            sendRunningCommand(
                "/${if (enabled) "grant" else "revoke"} $safe $privilege",
                "Privilege toggle updated. LuaNet verifies auth.sqlite and reverts if Luanti did not apply it.",
            )
        } else if (running) {
            offlineOnlyError()
        } else {
            runOfflineAction { onSetPrivilegeOffline(profileId, safe, privilege, enabled, it) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(player.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Player status", style = MaterialTheme.typography.titleLarge)
                        Text(
                            buildList {
                                add(if (isOnline) "Online" else "Offline")
                                if (player.admin) add("Admin")
                                if (player.banned) add("Banned")
                                add(if (running) "Server running" else "Server stopped")
                            }.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (actionMessage != null) {
                            Text(
                                actionMessage.orEmpty(),
                                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Moderation", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = kickReason,
                            onValueChange = { kickReason = it.replace("\n", " ").take(120) },
                            label = { Text("Kick reason") },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerActionButton("Kick", running && isOnline && safe.isNotBlank()) { sendRunningCommand("/kick $safe") }
                            PlayerActionButton("Kick with reason", running && isOnline && safe.isNotBlank() && kickReason.isNotBlank()) {
                                sendRunningCommand("/kick $safe ${kickReason.trim()}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerActionButton("Ban", running && isOnline && safe.isNotBlank() && !player.banned) { sendRunningCommand("/ban $safe") }
                            PlayerActionButton("Unban", safe.isNotBlank() && (running || player.banned)) {
                                if (running) sendRunningCommand("/unban $safe") else runOfflineAction { onUnbanOffline(profileId, safe, it) }
                            }
                        }
                        if (running && !isOnline) {
                            Text(
                                "Ban needs the player online because Luanti bans IPs. Privilege changes for offline players require the server stopped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Admin preset", style = MaterialTheme.typography.titleLarge)
                        Text("Use this for full server admin. For precise control, use the privilege toggles below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerActionButton("Grant admin preset", safe.isNotBlank() && !player.admin) {
                                if (running && isOnline) {
                                    sendRunningCommand(
                                        "/grant $safe all",
                                        "Admin preset applied locally. LuaNet verifies auth.sqlite and reverts if Luanti did not apply it.",
                                    )
                                } else if (running) offlineOnlyError()
                                else runOfflineAction { onSetAdminOffline(profileId, safe, true, it) }
                            }
                            PlayerActionButton("Refresh from server", running && safe.isNotBlank()) {
                                sendRunningCommand("/privs $safe", "Refreshing privileges from Luanti auth data...")
                            }
                        }
                    }
                }
            }
            item {
                Text("Privileges", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Toggles change real Luanti privileges. Online players use /grant and /revoke; offline players require the server stopped.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(COMMON_PLAYER_PRIVILEGES) { privilege ->
                PrivilegeToggleRow(
                    privilege = privilege,
                    checked = privilege.key in currentPrivileges,
                    enabled = safe.isNotBlank() && (isOnline || !running),
                    onCheckedChange = { checked -> changePrivilege(privilege.key, checked) },
                )
            }
            item {
                FilledTonalButton(onClick = { copy(context, player.name) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy player name")
                }
            }
        }
    }
}

@Composable
private fun PrivilegeToggleRow(
    privilege: PlayerPrivilege,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(privilege.title, style = MaterialTheme.typography.titleMedium)
                Text("${privilege.key} · ${privilege.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun RowScope.PlayerActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(modifier = Modifier.weight(1f), enabled = enabled, onClick = onClick) { Text(label) }
}

private fun String.safePlayerName(): String = filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
private fun String.safePrivilegeName(): String = filter { it.isLetterOrDigit() || it == '_' }.take(32)
private fun String.privilegeSet(): Set<String> = split(',')
    .map { it.trim().safePrivilegeName() }
    .filterTo(linkedSetOf()) { it.isNotBlank() }

private data class PlayerPrivilege(val key: String, val title: String, val detail: String)

private val COMMON_PLAYER_PRIVILEGES = listOf(
    PlayerPrivilege("interact", "Interact", "Build, dig, use items"),
    PlayerPrivilege("shout", "Shout", "Speak in chat"),
    PlayerPrivilege("basic_privs", "Basic privileges", "Grant/revoke basic privileges"),
    PlayerPrivilege("privs", "Privileges admin", "Modify all privileges"),
    PlayerPrivilege("server", "Server maintenance", "Server maintenance commands"),
    PlayerPrivilege("ban", "Ban", "Ban and unban players"),
    PlayerPrivilege("kick", "Kick", "Kick players"),
    PlayerPrivilege("teleport", "Teleport", "Teleport self"),
    PlayerPrivilege("bring", "Bring", "Teleport other players"),
    PlayerPrivilege("give", "Give", "Use give/giveme inventory commands"),
    PlayerPrivilege("password", "Password", "Set or clear player passwords"),
    PlayerPrivilege("fly", "Fly", "Use fly mode"),
    PlayerPrivilege("fast", "Fast", "Use fast movement"),
    PlayerPrivilege("noclip", "Noclip", "Fly through solid nodes"),
    PlayerPrivilege("settime", "Set time", "Change world time"),
    PlayerPrivilege("rollback", "Rollback", "Use rollback tools"),
    PlayerPrivilege("debug", "Debug", "Use debug/wireframe privileges"),
    PlayerPrivilege("protection_bypass", "Protection bypass", "Bypass protected nodes"),
)

@Composable
private fun EmptySection(icon: ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, null, Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContentSummaryPanel(
    profile: ServerProfileEntity,
    installedPackages: List<InstalledPackageEntity>,
    hasGameSettings: Boolean,
    hasModSettings: Boolean,
    onOpenContentLibrary: () -> Unit,
    onOpenZipImport: () -> Unit,
    onOpenGameSettings: () -> Unit,
    onOpenModSettings: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Content", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Install a game first, then add mods and modpacks. The full ContentDB browser opens as its own screen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { InstalledContentSummary(installedPackages) }
        if (profile.gameKey == null) {
            item {
                Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderZip, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("A game is required before the server can start", fontWeight = FontWeight.SemiBold)
                            Text("Open ContentDB to download a game, or use ZIP Import for a local archive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onOpenContentLibrary,
                enabled = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.FolderZip, null)
                Spacer(Modifier.width(8.dp))
                Text("Open ContentDB browser")
            }
        }
        item {
            FilledTonalButton(
                onClick = onOpenZipImport,
                enabled = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.FolderZip, null)
                Spacer(Modifier.width(8.dp))
                Text("Import ZIP archive")
            }
        }
        if (hasGameSettings) {
            item {
                FilledTonalButton(
                    onClick = onOpenGameSettings,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open game settings")
                }
            }
        }
        if (hasModSettings) {
            item {
                FilledTonalButton(
                    onClick = onOpenModSettings,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open installed mod settings")
                }
            }
        }
        item {
            Text(
                "Stop the server before changing games, mods, modpacks, ZIP imports, or content-specific settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsScreen(
    profile: ServerProfileEntity?,
    settings: List<ServerConfigSettingEntity>,
    onBack: () -> Unit,
    onSaveServerSettings: ServerSettingsSaver,
    onSaveAdvancedSetting: (String, String, String, (Result<String>) -> Unit) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Advanced settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (profile == null) {
                EmptySection(Icons.Default.Memory, "Server not found", "Return to the server list and open the profile again.")
            } else {
                AdvancedSettingsPanel(profile, settings, onSaveServerSettings, onSaveAdvancedSetting)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageSettingsScreen(
    profile: ServerProfileEntity?,
    title: String,
    description: String,
    emptyTitle: String,
    emptyDetail: String,
    icon: ImageVector,
    settings: List<ServerModSetting>,
    onBack: () -> Unit,
    onSave: (String, String, String, (Result<String>) -> Unit) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (profile == null) {
                EmptySection(icon, "Server not found", "Return to the server list and open the profile again.")
            } else {
                PackageSettingsPanel(
                    profile = profile,
                    title = title,
                    description = description,
                    emptyTitle = emptyTitle,
                    emptyDetail = emptyDetail,
                    icon = icon,
                    settings = settings,
                    onSave = onSave,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentBrowserScreen(
    profile: ServerProfileEntity?,
    installedPackages: List<InstalledPackageEntity>,
    state: ContentBrowserState,
    details: Map<String, ContentDetailState>,
    onBack: () -> Unit,
    onSearch: (String, String, String) -> Unit,
    onLoadDetail: (String) -> Unit,
    onInstall: (String, ContentPackage, (Result<String>) -> Unit) -> Unit,
) {
    if (profile == null) return
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text("ContentDB")
                    Text(profile.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ContentPanel(profile, installedPackages, state, details, onSearch, onLoadDetail, onInstall)
        }
    }
}

@Composable
private fun ContentPanel(
    profile: ServerProfileEntity,
    installedPackages: List<InstalledPackageEntity>,
    state: ContentBrowserState,
    details: Map<String, ContentDetailState>,
    onSearch: (String, String, String) -> Unit,
    onLoadDetail: (String) -> Unit,
    onInstall: (String, ContentPackage, (Result<String>) -> Unit) -> Unit,
) {
    var type by remember(profile.id) { mutableStateOf("game") }
    var query by remember(profile.id) { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<ContentPackage?>(null) }
    var selectedPackage by remember(profile.id) { mutableStateOf<ContentPackage?>(null) }
    val installedKeys = remember(installedPackages) { installedPackages.mapTo(hashSetOf()) { it.packageKey } }
    val operation = state.operation?.takeIf { it.profileId == profile.id }
    val operationBusy = operation != null

    LaunchedEffect(profile.id) {
        if (state.profileId != profile.id && !state.loading) {
            onSearch(profile.id, type, "")
        }
    }

    val requestInstall: (ContentPackage) -> Unit = { item ->
        if (item.compatible) {
            onInstall(profile.id, item) { result -> message = result.fold({ it }, { it.message ?: "Install failed" }) }
        } else {
            pendingInstall = item
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Explore ContentDB", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Browse featured games and mods, search the full catalog, then tap a package for screenshots, details and install.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { InstalledContentSummary(installedPackages) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("game" to "Games", "mod" to "Mods & modpacks").forEach { (value, label) ->
                    AssistChip(onClick = { type = value }, label = { Text(label) },
                        colors = if (type == value) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors())
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it.take(80) }, label = { Text("Search ContentDB") }, singleLine = true, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { onSearch(profile.id, type, query) }, enabled = !state.loading && !operationBusy) { Text("Search") }
            }
        }
        operation?.let { item { ContentOperationCard(it) } }
        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (!state.loading && state.profileId == profile.id && state.query.isBlank() && state.sections.isEmpty() && state.error == null) {
            item { Text("Loading featured ContentDB sections. Use Search if you want a specific package.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (state.profileId == profile.id && state.query.isBlank()) {
            state.sections.forEach { section ->
                item(key = section.title) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleLarge)
                        Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(section.items, key = { it.key }) { item ->
                                ContentPackageCard(
                                    item = item,
                                    installed = item.key in installedKeys,
                                    packageBusy = operation?.packageKey == item.key,
                                    installEnabled = !operationBusy && profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                                    compact = true,
                                    onOpen = { selectedPackage = item },
                                    onInstall = { requestInstall(item) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.profileId == profile.id && state.query.isNotBlank()) {
            if (!state.loading && state.items.isEmpty() && state.error == null) {
                item { Text("No packages found for “${state.query}”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.items, key = { it.key }) { item ->
                ContentPackageCard(
                    item = item,
                    installed = item.key in installedKeys,
                    packageBusy = operation?.packageKey == item.key,
                    installEnabled = !operationBusy && profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                    compact = false,
                    onOpen = { selectedPackage = item },
                    onInstall = { requestInstall(item) },
                )
            }
        }
        message?.let { item { Text(it) } }
        item { Spacer(Modifier.height(20.dp)) }
    }

    selectedPackage?.let { item ->
        val detailState = details[item.key]
        LaunchedEffect(item.key) {
            if (detailState?.detail == null && detailState?.loading != true) onLoadDetail(item.key)
        }
        ContentDetailDialog(
            profile = profile,
            item = item,
            detailState = detailState,
            installed = item.key in installedKeys,
            packageBusy = operation?.packageKey == item.key,
            operationBusy = operationBusy,
            onDismiss = { selectedPackage = null },
            onInstall = { requestInstall(item) },
        )
    }

    pendingInstall?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text("Compatibility warning") },
            text = { Text("${item.title} does not declare support for Luanti ${profile.engineVersion} or this game. It may fail to load. Install it anyway?") },
            dismissButton = { TextButton(onClick = { pendingInstall = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = {
                pendingInstall = null
                onInstall(profile.id, item) { result -> message = result.fold({ it }, { it.message ?: "Install failed" }) }
            }) { Text("Install anyway") } },
        )
    }
}

@Composable
private fun ContentPackageCard(
    item: ContentPackage,
    installed: Boolean,
    packageBusy: Boolean,
    installEnabled: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
) {
    if (compact) {
        Card(
            onClick = onOpen,
            modifier = Modifier.width(260.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentThumbnail(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Text(item.title.ifBlank { item.name }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text("${item.author}/${item.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                PackageBadgeRow(item = item, installed = installed, maxBadges = 2)
            }
        }
    } else {
        Card(
            onClick = onOpen,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    ContentThumbnail(item.thumbnail, Modifier.size(96.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title.ifBlank { item.name }, style = MaterialTheme.typography.titleLarge)
                        Text("${item.author}/${item.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.shortDescription.isNotBlank()) {
                            Text(item.shortDescription, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                PackageBadgeRow(item = item, installed = installed, maxBadges = 4)
                Button(
                    onClick = onInstall,
                    enabled = !installed && !packageBusy && installEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (installed) "Installed" else if (packageBusy) "Installing…" else "Install")
                }
            }
        }
    }
}

@Composable
private fun ContentThumbnail(url: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val imageModifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surface)
    val candidates = remember(url) { contentImageCandidates(url) }
    var imageIndex by remember(url) { mutableIntStateOf(0) }
    val imageUrl = candidates.getOrNull(imageIndex)
    if (imageUrl == null) {
        Box(
            imageModifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FolderZip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
            onError = {
                if (imageIndex < candidates.lastIndex) imageIndex += 1
            },
        )
    }
}

@Composable
private fun ClickableContentImage(url: String?, modifier: Modifier, onOpen: (String) -> Unit) {
    if (url == null) {
        ContentThumbnail(null, modifier)
    } else {
        Box(modifier.clickable { onOpen(url) }) {
            ContentThumbnail(url, Modifier.matchParentSize())
        }
    }
}

private fun contentImageCandidates(url: String?): List<String> {
    val normalized = url?.trim()?.takeIf { it.isNotBlank() }?.let {
        if (it.startsWith("//")) "https:$it" else it
    } ?: return emptyList()
    val clean = normalized.substringBefore('?')
    val fileName = clean.substringAfterLast('/', "")
    if ("/thumbnails/" !in clean || fileName.isBlank()) return listOf(normalized)
    val stem = fileName.substringBeforeLast('.', fileName)
    val uploadBase = "https://content.luanti.org/uploads/$stem"
    return listOf(
        "$uploadBase.png",
        "$uploadBase.jpg",
        "$uploadBase.jpeg",
        normalized,
    ).distinct()
}

@Composable
private fun PackageBadgeRow(item: ContentPackage, installed: Boolean, maxBadges: Int) {
    val badges = buildList {
        if (installed) add("Installed")
        add(if (item.compatible) "Compatible" else "Check")
        addAll(item.badges.take(maxBadges))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(badges) { badge ->
            val color = when (badge) {
                "Check" -> MaterialTheme.colorScheme.errorContainer
                "Installed" -> MaterialTheme.colorScheme.primaryContainer
                "Compatible" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
            Surface(shape = CircleShape, color = color) {
                Text(badge, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ContentDetailDialog(
    profile: ServerProfileEntity,
    item: ContentPackage,
    detailState: ContentDetailState?,
    installed: Boolean,
    packageBusy: Boolean,
    operationBusy: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val detail = detailState?.detail
    val mergedBadges = remember(item, detail) { (item.badges + detail?.badges().orEmpty()).distinct() }
    val heroImage = detail?.screenshots?.firstOrNull() ?: detail?.thumbnail ?: item.thumbnail
    var zoomImage by remember(item.key) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val externalLinks = remember(detail) {
        buildList {
            detail?.url?.let { add(ExternalContentLink("ContentDB", it, Icons.Default.Public)) }
            detail?.website?.let { add(ExternalContentLink("Website", it, Icons.Default.Language)) }
            detail?.repo?.let { add(ExternalContentLink("Repo", it, Icons.Default.Code)) }
            detail?.forumUrl?.let { add(ExternalContentLink("Forum", it, Icons.Default.Forum)) }
            detail?.issueTracker?.let { add(ExternalContentLink("Issues", it, Icons.Default.BugReport)) }
        }.distinctBy { it.url }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail?.title?.ifBlank { item.title } ?: item.title.ifBlank { item.name }) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ClickableContentImage(heroImage, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onOpen = { zoomImage = it })
                }
                item {
                    Text("${item.author}/${item.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detailState?.loading == true) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                detailState?.error?.let { error ->
                    item { Text(error, color = MaterialTheme.colorScheme.error) }
                }
                if (mergedBadges.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(mergedBadges) { badge ->
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(badge, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                item {
                    MarkdownDescription(
                        detail?.longDescription?.takeIf { it.isNotBlank() }
                            ?: detail?.shortDescription?.takeIf { it.isNotBlank() }
                            ?: item.shortDescription.ifBlank { "No description provided." },
                    )
                }
                if (!detail?.screenshots.isNullOrEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Screenshots", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(detail!!.screenshots) { screenshot ->
                                    ClickableContentImage(
                                        url = screenshot,
                                        modifier = Modifier.width(220.dp).aspectRatio(16f / 9f),
                                        onOpen = { zoomImage = it },
                                    )
                                }
                            }
                        }
                    }
                }
                if (detail != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            detail.downloads?.let { Text("Downloads: $it", style = MaterialTheme.typography.bodySmall) }
                            Text("Type: ${detail.type.ifBlank { item.type }}", style = MaterialTheme.typography.bodySmall)
                            detail.license?.let { Text("License: $it", style = MaterialTheme.typography.bodySmall) }
                            detail.mediaLicense?.let { Text("Media license: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                if (externalLinks.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Links", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(externalLinks) { link ->
                                    FilledTonalButton(onClick = { openExternalLink(context, link.url) }) {
                                        Icon(link.icon, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(link.label)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        confirmButton = {
            Button(
                onClick = onInstall,
                enabled = !installed && !packageBusy && !operationBusy && profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
            ) { Text(if (installed) "Installed" else if (packageBusy) "Installing…" else "Install") }
        },
    )
    zoomImage?.let { image ->
        ZoomableImageDialog(imageUrl = image, onDismiss = { zoomImage = null })
    }
}

private data class ExternalContentLink(val label: String, val url: String, val icon: ImageVector)

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun MarkdownDescription(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val markwon = remember(context) { Markwon.create(context) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.12f)
            }
        },
        update = { textView ->
            textView.setTextColor(colors.onSurfaceVariant.toArgb())
            textView.setLinkTextColor(colors.primary.toArgb())
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun ZoomableImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    val candidates = remember(imageUrl) { contentImageCandidates(imageUrl).ifEmpty { listOf(imageUrl) } }
    var imageIndex by remember(imageUrl) { mutableIntStateOf(0) }
    var scale by remember(imageUrl) { mutableStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        if (scale <= 1.01f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        } else {
            val panMultiplier = (scale * 1.75f).coerceIn(1.75f, 6f)
            offsetX = (offsetX + panChange.x * panMultiplier).coerceIn(-6_000f, 6_000f)
            offsetY = (offsetY + panChange.y * panMultiplier).coerceIn(-6_000f, 6_000f)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color.Black.copy(alpha = 0.96f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = candidates.getOrElse(imageIndex) { imageUrl },
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(transformState)
                        .pointerInput(imageUrl) {
                            detectTapGestures(onDoubleTap = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            })
                        },
                    contentScale = ContentScale.Fit,
                    onError = {
                        if (imageIndex < candidates.lastIndex) imageIndex += 1
                    },
                )
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                ) { Text("Close") }
                Text(
                    "Pinch to zoom · drag to pan · double tap to reset",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZipImportScreen(
    profile: ServerProfileEntity?,
    installedPackages: List<InstalledPackageEntity>,
    operation: ContentOperationState?,
    onBack: () -> Unit,
    onImport: (String, Uri, ImportKind, (Result<String>) -> Unit) -> Unit,
) {
    if (profile == null) return
    var requestedKind by remember { mutableStateOf(ImportKind.GAME) }
    var message by remember { mutableStateOf<String?>(null) }
    val profileOperation = operation?.takeIf { it.profileId == profile.id }
    val operationBusy = profileOperation != null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(profile.id, uri, requestedKind) { result ->
            message = result.fold({ it }, { it.message ?: "Import failed" })
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text("ZIP Import")
                    Text(profile.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Import local archives", style = MaterialTheme.typography.headlineMedium) }
            item {
                Text(
                    "Use this for worlds, games, mods and modpacks you already have as ZIP files. ContentDB downloads stay in the ContentDB browser.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { InstalledContentSummary(installedPackages) }
            profileOperation?.let { item { ContentOperationCard(it) } }
            item {
                Text(
                    "LuaNet checks ZIP archives for path traversal, symlinks, expansion size, compression ratio and invalid Luanti structure before installing them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ImportKind.entries) { kind ->
                FilledTonalButton(
                    onClick = {
                        requestedKind = kind
                        launcher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    enabled = !operationBusy && profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Import ${kind.name.lowercase()} ZIP") }
            }
            message?.let { item { Text(it) } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun ContentOperationCard(operation: ContentOperationState) {
    val total = operation.totalBytes
    val determinate = !operation.indeterminate && total != null && total > 0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(operation.title, style = MaterialTheme.typography.titleMedium)
            Text(operation.phase, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (determinate) {
                val progress = (operation.bytesRead.toFloat() / total!!.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${formatBytes(operation.bytesRead)} / ${formatBytes(total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (operation.bytesRead > 0) {
                    Text(
                        formatBytes(operation.bytesRead),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun InstalledContentSummary(packages: List<InstalledPackageEntity>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Installed content", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${packages.size}", style = MaterialTheme.typography.labelLarge)
            }
            if (packages.isEmpty()) {
                Text("Install a game first, then add mods or import ZIP archives.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                packages.take(6).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            Text("${item.type.name.lowercase()} · ${item.source.name.lowercase()} · ${item.packageKey}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = CircleShape, color = if (item.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                            Text(if (item.enabled) "Enabled" else "Off", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (packages.size > 6) {
                    Text("+${packages.size - 6} more installed items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BackupPanel(
    profile: ServerProfileEntity,
    backups: List<BackupEntity>,
    onCreate: (String, (Result<String>) -> Unit) -> Unit,
    onRestore: (String, String, (Result<String>) -> Unit) -> Unit,
    onDelete: (String, String, (Result<String>) -> Unit) -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<BackupEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupEntity?>(null) }
    val canChange = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Backups", style = MaterialTheme.typography.headlineSmall) }
        item {
            Button(
                onClick = { onCreate(profile.id) { result -> message = result.fold({ it }, { it.message ?: "Backup failed" }) } },
                enabled = canChange,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create manual backup") }
        }
        message?.let { item { Text(it) } }
        if (backups.isEmpty()) {
            item { EmptySection(Icons.Default.Backup, "No backups yet", "Create one before changing engines, games or mods.") }
        } else {
            items(backups, key = { it.id }) { backup ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (backup.automatic) "Automatic backup" else "Manual backup", style = MaterialTheme.typography.titleMedium)
                                Text("${formatCrashTime(backup.createdAt)} · ${formatBytes(backup.sizeBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (backup.reason.isNotBlank()) Text(backup.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { restoreTarget = backup },
                                enabled = canChange,
                                modifier = Modifier.weight(1f),
                            ) { Text("Restore") }
                            FilledTonalButton(
                                onClick = { deleteTarget = backup },
                                enabled = canChange,
                                modifier = Modifier.weight(1f),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    restoreTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore backup?") },
            text = { Text("The current world, content and server configuration will be replaced.") },
            confirmButton = {
                Button(onClick = {
                    restoreTarget = null
                    onRestore(profile.id, backup.id) { result -> message = result.fold({ it }, { it.message ?: "Restore failed" }) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete backup?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    onDelete(profile.id, backup.id) { result -> message = result.fold({ it }, { it.message ?: "Delete failed" }) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PackageSettingsPanel(
    profile: ServerProfileEntity,
    title: String,
    description: String,
    emptyTitle: String,
    emptyDetail: String,
    icon: ImageVector,
    settings: List<ServerModSetting>,
    onSave: (String, String, String, (Result<String>) -> Unit) -> Unit,
) {
    val canEdit = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)
    var values by remember(profile.id, settings) { mutableStateOf(settings.associate { it.key to it.value }) }
    var message by remember(profile.id) { mutableStateOf<String?>(null) }
    if (settings.isEmpty()) {
        EmptySection(icon, emptyTitle, emptyDetail)
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!canEdit) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Stop the server before changing these settings.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        items(settings) { setting ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(setting.title, style = MaterialTheme.typography.titleMedium)
                    Text("${setting.source} · ${setting.key} · ${setting.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (setting.description.isNotBlank()) {
                        Text(setting.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (setting.type in setOf("bool", "boolean")) {
                        Toggle(
                            label = "Enabled",
                            value = values[setting.key].equals("true", ignoreCase = true),
                            enabled = canEdit,
                        ) { checked -> values = values + (setting.key to checked.toString()) }
                    } else {
                        OutlinedTextField(
                            value = values[setting.key].orEmpty(),
                            onValueChange = { values = values + (setting.key to it.take(512)) },
                            label = { Text("Value") },
                            supportingText = { Text("Default: ${setting.defaultValue.ifBlank { "(empty)" }}") },
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(
                        enabled = canEdit,
                        onClick = {
                            onSave(profile.id, setting.key, values[setting.key].orEmpty()) { result ->
                                message = result.fold({ it }, { it.message ?: "Save failed" })
                            }
                        },
                    ) { Text("Save ${setting.title}") }
                }
            }
        }
        message?.let { item { Text(it, color = if (it.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

private data class AdvancedEngineSetting(
    val key: String,
    val title: String,
    val detail: String,
    val defaultValue: String,
    val kind: AdvancedSettingKind,
)

private enum class AdvancedSettingKind { TEXT, NUMBER, BOOL }

private val advancedEngineSettings = listOf(
    AdvancedEngineSetting(
        "max_simultaneous_block_sends_per_client",
        "Simultaneous block sends",
        "Maximum mapblock sends per client. Lower values reduce spikes on phones.",
        "40",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "server_unload_unused_data_timeout",
        "Unload unused server data",
        "Seconds before Luanti unloads unused map data. Lower saves memory.",
        "29",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "sqlite_synchronous",
        "SQLite synchronous mode",
        "0 is fastest/riskier, 1 balanced, 2 safest. LuaNet default keeps Luanti default 2.",
        "2",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "max_forceloaded_blocks",
        "Max forceloaded blocks",
        "Limits always-loaded blocks. Lower values protect phone memory and battery.",
        "16",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "chat_message_max_size",
        "Chat message max size",
        "Maximum chat message length accepted by the server.",
        "500",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "chat_message_limit_per_10sec",
        "Chat messages per 10 sec",
        "Anti-spam limit before Luanti starts refusing chat messages.",
        "8.0",
        AdvancedSettingKind.TEXT,
    ),
    AdvancedEngineSetting(
        "chat_message_limit_trigger_kick",
        "Chat kick threshold",
        "How many chat limit violations trigger an automatic kick.",
        "50",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "emergequeue_limit_total",
        "Total emerge queue limit",
        "Global queued mapblock load/generation limit.",
        "1024",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "emergequeue_limit_diskonly",
        "Disk emerge queue per player",
        "Per-player queued mapblocks loaded from disk.",
        "128",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "emergequeue_limit_generate",
        "Generate queue per player",
        "Per-player queued mapblocks generated from mapgen.",
        "128",
        AdvancedSettingKind.NUMBER,
    ),
    AdvancedEngineSetting(
        "enable_mapgen_debug_info",
        "Mapgen debug info",
        "Verbose mapgen diagnostics. Keep off unless debugging terrain generation.",
        "false",
        AdvancedSettingKind.BOOL,
    ),
)

@Composable
private fun AdvancedSettingsPanel(
    profile: ServerProfileEntity,
    settings: List<ServerConfigSettingEntity>,
    onSaveSettings: ServerSettingsSaver,
    onSaveConfig: (String, String, String, (Result<String>) -> Unit) -> Unit,
) {
    val canEdit = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)
    var serverDescription by remember(profile.id, profile.serverDescription) { mutableStateOf(profile.serverDescription) }
    var motd by remember(profile.id, profile.motd) { mutableStateOf(profile.motd) }
    var announceServer by remember(profile.id, profile.announceServer) { mutableStateOf(profile.announceServer) }
    var defaultPrivileges by remember(profile.id, profile.defaultPrivileges) { mutableStateOf(profile.defaultPrivileges) }
    var disallowEmptyPassword by remember(profile.id, profile.disallowEmptyPassword) { mutableStateOf(profile.disallowEmptyPassword) }
    var enableRollback by remember(profile.id, profile.enableRollback) { mutableStateOf(profile.enableRollback) }
    var timeSpeed by remember(profile.id, profile.timeSpeed) { mutableStateOf(profile.timeSpeed.toString()) }
    var activeBlockRange by remember(profile.id, profile.activeBlockRange) { mutableStateOf(profile.activeBlockRange.toString()) }
    var maxBlockSendDistance by remember(profile.id, profile.maxBlockSendDistance) { mutableStateOf(profile.maxBlockSendDistance.toString()) }
    var maxBlockGenerateDistance by remember(profile.id, profile.maxBlockGenerateDistance) { mutableStateOf(profile.maxBlockGenerateDistance.toString()) }
    var dedicatedServerStepMs by remember(profile.id, profile.dedicatedServerStepMs) { mutableStateOf(profile.dedicatedServerStepMs.toString()) }
    var maxObjectsPerBlock by remember(profile.id, profile.maxObjectsPerBlock) { mutableStateOf(profile.maxObjectsPerBlock.toString()) }
    var itemEntityTtl by remember(profile.id, profile.itemEntityTtl) { mutableStateOf(profile.itemEntityTtl.toString()) }
    var maxPacketsPerIteration by remember(profile.id, profile.maxPacketsPerIteration) { mutableStateOf(profile.maxPacketsPerIteration.toString()) }
    var mapgenLimit by remember(profile.id, profile.mapgenLimit) { mutableStateOf(profile.mapgenLimit.toString()) }
    var configValues by remember(profile.id, settings) {
        mutableStateOf(advancedEngineSettings.associate { setting ->
            setting.key to (settings.firstOrNull { it.key == setting.key }?.value ?: setting.defaultValue)
        })
    }
    var message by remember(profile.id) { mutableStateOf<String?>(null) }

    val parsedTimeSpeed = timeSpeed.toIntOrNull()
    val parsedActiveBlockRange = activeBlockRange.toIntOrNull()
    val parsedSendDistance = maxBlockSendDistance.toIntOrNull()
    val parsedGenerateDistance = maxBlockGenerateDistance.toIntOrNull()
    val parsedStepMs = dedicatedServerStepMs.toIntOrNull()
    val parsedObjects = maxObjectsPerBlock.toIntOrNull()
    val parsedItemTtl = itemEntityTtl.toIntOrNull()
    val parsedPackets = maxPacketsPerIteration.toIntOrNull()
    val parsedMapgenLimit = mapgenLimit.toIntOrNull()
    val profileValid =
        parsedTimeSpeed != null && parsedTimeSpeed in 0..2_400 &&
        parsedActiveBlockRange != null && parsedActiveBlockRange in 1..10 &&
        parsedSendDistance != null && parsedSendDistance in 1..64 &&
        parsedGenerateDistance != null && parsedGenerateDistance in 1..64 &&
        parsedStepMs != null && parsedStepMs in 20..1_000 &&
        parsedObjects != null && parsedObjects in 1..256 &&
        parsedItemTtl != null && parsedItemTtl in -1..86_400 &&
        parsedPackets != null && parsedPackets in 64..16_384 &&
        parsedMapgenLimit != null && parsedMapgenLimit in 100..31_000

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Advanced settings", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Stop the server before changing these values. Profile settings are stored in LuaNet; engine overrides are written to minetest.conf.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!canEdit) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Stop the server before changing advanced settings.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        item { Text("Profile advanced", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedTextField(
                value = serverDescription,
                onValueChange = { serverDescription = it.take(240) },
                label = { Text("Server description") },
                supportingText = { Text("Writes server_description") },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = motd,
                onValueChange = { motd = it.take(240) },
                label = { Text("MOTD") },
                supportingText = { Text("Message shown to players on join") },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Toggle("Announce on Luanti server list", "Writes server_announce. Keep off for private LAN servers.", announceServer, canEdit) { announceServer = it } }
        item {
            OutlinedTextField(
                value = defaultPrivileges,
                onValueChange = { defaultPrivileges = it.take(120) },
                label = { Text("Default privileges") },
                supportingText = { Text("Comma-separated, for example interact,shout") },
                singleLine = true,
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Toggle("Require passwords", "Writes disallow_empty_password", disallowEmptyPassword, canEdit) { disallowEmptyPassword = it } }
        item { Toggle("Rollback recording", "Useful for moderation, costs storage and CPU.", enableRollback, canEdit) { enableRollback = it } }
        item { NumberSetting("Time speed", "0 freezes time. Default 72.", timeSpeed, canEdit) { timeSpeed = it.filterSignedDigits(4) } }
        item { NumberSetting("Active block range", "Simulation radius around players, 1-10.", activeBlockRange, canEdit) { activeBlockRange = it.filter(Char::isDigit).take(2) } }
        item { NumberSetting("Block send distance", "Higher distance costs network/CPU, 1-64.", maxBlockSendDistance, canEdit) { maxBlockSendDistance = it.filter(Char::isDigit).take(2) } }
        item { NumberSetting("Block generate distance", "World generation distance, 1-64.", maxBlockGenerateDistance, canEdit) { maxBlockGenerateDistance = it.filter(Char::isDigit).take(2) } }
        item { NumberSetting("Server step ms", "Dedicated server tick interval, 20-1000 ms.", dedicatedServerStepMs, canEdit) { dedicatedServerStepMs = it.filter(Char::isDigit).take(4) } }
        item { NumberSetting("Max objects per block", "Entity cap per mapblock, 1-256.", maxObjectsPerBlock, canEdit) { maxObjectsPerBlock = it.filter(Char::isDigit).take(3) } }
        item { NumberSetting("Dropped item TTL seconds", "-1 disables automatic cleanup, 0-86400 seconds.", itemEntityTtl, canEdit) { itemEntityTtl = it.filterSignedDigits(6) } }
        item { NumberSetting("Max packets per iteration", "Network throughput guard, 64-16384.", maxPacketsPerIteration, canEdit) { maxPacketsPerIteration = it.filter(Char::isDigit).take(5) } }
        item { NumberSetting("Mapgen limit", "World generation limit, 100-31000.", mapgenLimit, canEdit) { mapgenLimit = it.filter(Char::isDigit).take(5) } }
        item {
            Button(
                onClick = {
                    onSaveSettings(
                        ServerProfileSettingsUpdate(
                            profileId = profile.id,
                            name = profile.name,
                            engineVersion = profile.engineVersion,
                            gameKey = profile.gameKey,
                            mapgen = profile.mapgen,
                            maxPlayers = profile.maxPlayers,
                            creative = profile.creative,
                            damage = profile.damage,
                            pvp = profile.pvp,
                            autoOffEnabled = profile.autoOffEnabled,
                            autoOffMinutes = profile.autoOffMinutes,
                            serverDescription = serverDescription,
                            motd = motd,
                            announceServer = announceServer,
                            defaultPrivileges = defaultPrivileges,
                            disallowEmptyPassword = disallowEmptyPassword,
                            enableRollback = enableRollback,
                            timeSpeed = parsedTimeSpeed ?: profile.timeSpeed,
                            activeBlockRange = parsedActiveBlockRange ?: profile.activeBlockRange,
                            maxBlockSendDistance = parsedSendDistance ?: profile.maxBlockSendDistance,
                            maxBlockGenerateDistance = parsedGenerateDistance ?: profile.maxBlockGenerateDistance,
                            dedicatedServerStepMs = parsedStepMs ?: profile.dedicatedServerStepMs,
                            maxObjectsPerBlock = parsedObjects ?: profile.maxObjectsPerBlock,
                            itemEntityTtl = parsedItemTtl ?: profile.itemEntityTtl,
                            maxPacketsPerIteration = parsedPackets ?: profile.maxPacketsPerIteration,
                            mapgenLimit = parsedMapgenLimit ?: profile.mapgenLimit,
                        ),
                    ) { result -> message = result.fold({ it }, { it.message ?: "Save failed" }) }
                },
                enabled = canEdit && profileValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save profile advanced settings") }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        item {
            Text("Engine config overrides", style = MaterialTheme.typography.titleLarge)
            Text("These are direct Luanti settings. Restart the server after saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(advancedEngineSettings) { setting ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(setting.title, style = MaterialTheme.typography.titleMedium)
                    Text("${setting.key} · default ${setting.defaultValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(setting.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val current = configValues[setting.key].orEmpty()
                    if (setting.kind == AdvancedSettingKind.BOOL) {
                        Toggle("Enabled", setting.detail, current.equals("true", ignoreCase = true), canEdit) { checked ->
                            configValues = configValues + (setting.key to checked.toString())
                        }
                    } else {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { value ->
                                val next = if (setting.kind == AdvancedSettingKind.NUMBER) {
                                    value.filter { it.isDigit() || it == '.' || it == '-' }.take(24)
                                } else {
                                    value.take(120)
                                }
                                configValues = configValues + (setting.key to next)
                            },
                            label = { Text("Value") },
                            enabled = canEdit,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            enabled = canEdit && current.isNotBlank(),
                            onClick = {
                                onSaveConfig(profile.id, setting.key, current) { result ->
                                    message = result.fold({ it }, { it.message ?: "Save failed" })
                                }
                            },
                        ) { Text("Save") }
                        TextButton(
                            enabled = canEdit,
                            onClick = { configValues = configValues + (setting.key to setting.defaultValue) },
                        ) { Text("Reset field") }
                    }
                }
            }
        }
        message?.let { item { Text(it, color = if (it.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun SettingsPanel(
    profile: ServerProfileEntity,
    installedPackages: List<InstalledPackageEntity>,
    onOpenAdvancedSettings: () -> Unit,
    onSaveSettings: ServerSettingsSaver,
) {
    val canEdit = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED)
    val gamePackages = remember(installedPackages) { installedPackages.filter { it.type == PackageType.GAME } }
    val upgradeTargets = remember(profile.engineVersion) {
        EngineCatalog.releases.filter { EngineCatalog.canUpgrade(profile.engineVersion, it.version) }
    }
    var name by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var engineVersion by remember(profile.id, profile.engineVersion) { mutableStateOf(profile.engineVersion) }
    var gameKey by remember(profile.id, profile.gameKey) { mutableStateOf(profile.gameKey) }
    var mapgen by remember(profile.id, profile.mapgen) { mutableStateOf(profile.mapgen) }
    var maxPlayers by remember(profile.id, profile.maxPlayers) { mutableStateOf(profile.maxPlayers.toString()) }
    var creative by remember(profile.id, profile.creative) { mutableStateOf(profile.creative) }
    var damage by remember(profile.id, profile.damage) { mutableStateOf(profile.damage) }
    var pvp by remember(profile.id, profile.pvp) { mutableStateOf(profile.pvp) }
    var autoOffEnabled by remember(profile.id, profile.autoOffEnabled) { mutableStateOf(profile.autoOffEnabled) }
    var autoOffMinutes by remember(profile.id, profile.autoOffMinutes) { mutableStateOf(profile.autoOffMinutes.toString()) }
    var showVersions by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    var showMapgens by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val parsedPlayers = maxPlayers.toIntOrNull()
    val parsedAutoOffMinutes = autoOffMinutes.toIntOrNull()
    val selectedGameTitle = gamePackages.firstOrNull { it.packageKey == gameKey }?.title
        ?: gameKey
        ?: "No game selected"
    val valid = name.isNotBlank() &&
        mapgen in ServerRepository.MAPGENS &&
        parsedPlayers != null &&
        parsedPlayers in 1..100 &&
        (!autoOffEnabled || (parsedAutoOffMinutes != null && parsedAutoOffMinutes in 1..1_440))

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Server settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            FilledTonalButton(
                onClick = onOpenAdvancedSettings,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Memory, null)
                Spacer(Modifier.width(8.dp))
                Text("Open advanced settings")
            }
        }
        if (!canEdit) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Stop the server before changing its configuration.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Server name") },
                singleLine = true,
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilledTonalButton(
                onClick = { showVersions = true },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Luanti engine: $engineVersion") }
        }
        item {
            FilledTonalButton(
                onClick = { showGames = true },
                enabled = canEdit && gamePackages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Game: $selectedGameTitle") }
        }
        if (gamePackages.isEmpty()) {
            item { Text("Install a game from ContentDB or ZIP Import before selecting one here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            FilledTonalButton(
                onClick = { showMapgens = true },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Map generator: $mapgen") }
        }
        item {
            OutlinedTextField(
                value = maxPlayers,
                onValueChange = { maxPlayers = it.filter(Char::isDigit).take(3) },
                label = { Text("Max players") },
                supportingText = { Text("1 to 100 players") },
                singleLine = true,
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Toggle("Creative mode", "Give creative inventory and fast building defaults", creative, canEdit) { creative = it } }
        item { Toggle("Damage", "Players can take damage", damage, canEdit) { damage = it } }
        item { Toggle("PvP", "Players can hurt each other", pvp, canEdit) { pvp = it } }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        item { Text("Auto off", style = MaterialTheme.typography.headlineSmall) }
        item { Toggle("Stop when nobody is connected", "Optional timer. LAN has no mandatory idle stop.", autoOffEnabled, canEdit) { autoOffEnabled = it } }
        item {
            TextField(
                value = autoOffMinutes,
                onValueChange = { autoOffMinutes = it.filter(Char::isDigit).take(4) },
                enabled = canEdit && autoOffEnabled,
                label = { Text("Minutes with no players") },
                supportingText = { Text("From 1 minute to 24 hours. Disabled by default.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        message?.let { item { Text(it, color = if (it.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) } }
        item {
            Button(
                onClick = {
                    onSaveSettings(
                        ServerProfileSettingsUpdate(
                            profileId = profile.id,
                            name = name,
                            engineVersion = engineVersion,
                            gameKey = gameKey,
                            mapgen = mapgen,
                            maxPlayers = parsedPlayers ?: profile.maxPlayers,
                            creative = creative,
                            damage = damage,
                            pvp = pvp,
                            autoOffEnabled = autoOffEnabled,
                            autoOffMinutes = parsedAutoOffMinutes ?: profile.autoOffMinutes,
                            serverDescription = profile.serverDescription,
                            motd = profile.motd,
                            announceServer = profile.announceServer,
                            defaultPrivileges = profile.defaultPrivileges,
                            disallowEmptyPassword = profile.disallowEmptyPassword,
                            enableRollback = profile.enableRollback,
                            timeSpeed = profile.timeSpeed,
                            activeBlockRange = profile.activeBlockRange,
                            maxBlockSendDistance = profile.maxBlockSendDistance,
                            maxBlockGenerateDistance = profile.maxBlockGenerateDistance,
                            dedicatedServerStepMs = profile.dedicatedServerStepMs,
                            maxObjectsPerBlock = profile.maxObjectsPerBlock,
                            itemEntityTtl = profile.itemEntityTtl,
                            maxPacketsPerIteration = profile.maxPacketsPerIteration,
                            mapgenLimit = profile.mapgenLimit,
                        ),
                    ) { result -> message = result.fold({ it }, { it.message ?: "Save failed" }) }
                },
                enabled = canEdit && valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save server settings") }
        }
        item {
            Text("Engine changes are upgrade-only. Free public tunnels still expire after four hours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showVersions) {
        AlertDialog(
            onDismissRequest = { showVersions = false },
            title = { Text("Select engine") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(upgradeTargets.reversed()) { release ->
                        TextButton(
                            onClick = {
                                engineVersion = release.version
                                showVersions = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(if (release.version == profile.engineVersion) "${release.version} · current" else release.version)
                                Text(
                                    "Protocol ${release.protocolMin}-${release.protocolMax}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVersions = false }) { Text("Close") } },
        )
    }

    if (showGames) {
        AlertDialog(
            onDismissRequest = { showGames = false },
            title = { Text("Select game") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(gamePackages) { game ->
                        TextButton(
                            onClick = {
                                gameKey = game.packageKey
                                showGames = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(if (game.packageKey == profile.gameKey) "${game.title} · current" else game.title)
                                Text(
                                    "${game.source.name.lowercase()} · ${game.packageKey}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGames = false }) { Text("Close") } },
        )
    }

    if (showMapgens) {
        AlertDialog(
            onDismissRequest = { showMapgens = false },
            title = { Text("Select map generator") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(ServerRepository.MAPGENS) { option ->
                        TextButton(
                            onClick = {
                                mapgen = option
                                showMapgens = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(if (option == profile.mapgen) "$option · current" else option)
                                Text(mapgenDescription(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMapgens = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Overview(
    profile: ServerProfileEntity,
    runtime: RuntimeSnapshot?,
    crashReport: ServerCrashReportEntity?,
    localPort: Int?,
    context: Context,
    onEnsureEngineInstalled: (String, (Result<String>) -> Unit) -> Unit,
    onStartPublicTunnel: (Activity, String, Int, (Result<String>) -> Unit) -> Unit,
    onStopPublicTunnel: (String, (Result<String>) -> Unit) -> Unit,
) {
    var publicMessage by remember(profile.id) { mutableStateOf<String?>(null) }
    var startMessage by remember(profile.id) { mutableStateOf<String?>(null) }
    var preparingStart by remember(profile.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val runtimeState = runtime?.state
        val staleActiveState = runtime == null && profile.state in setOf(ServerState.STARTING, ServerState.RUNNING, ServerState.STOPPING)
        val running = runtimeState in setOf("STARTING", "RUNNING", "STOPPING")
        val state = runtimeState ?: if (staleActiveState) "STOPPED" else profile.state.name
        val displayProfileState = if (staleActiveState) ServerState.STOPPED else profile.state
        val publicHost = runtime?.publicHost ?: profile.publicHost
        val publicPort = runtime?.publicPort ?: profile.publicPort
        val publicEnabled = !staleActiveState && publicHost != null && publicPort != null && (runtime?.publicPort != null || profile.publicEnabled)
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(if (running) MaterialTheme.colorScheme.primary else stateColor(displayProfileState), CircleShape))
                        Spacer(Modifier.width(10.dp)); Text(serverStateLabel(state), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f)); Text("${profile.maxPlayers} slots", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        when {
                            running -> "Server is available on this device and your local network."
                            staleActiveState -> "LuaNet was restarted or updated while this server was active. The local state was reset to stopped; start it again when ready."
                            profile.state == ServerState.CRASHED -> "The engine crashed during this app session. Check the support code below."
                            else -> "Ready when you are."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (profile.state == ServerState.CRASHED || crashReport != null) item {
            CrashReportCard(
                report = crashReport,
                activeCrash = profile.state == ServerState.CRASHED,
                context = context,
            )
        }
        if (profile.gameKey == null) item {
            Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp)); Column { Text("Game required", fontWeight = FontWeight.SemiBold); Text("Open Content and import a game ZIP before starting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Button(onClick = {
                if (running) OrchestratorService.stop(context, profile.id) else {
                    preparingStart = true
                    startMessage = "Checking engine ${profile.engineVersion}…"
                    requestNotificationPermission(context)
                    requestBackgroundHostingExemption(context)
                    onEnsureEngineInstalled(profile.engineVersion) { result ->
                        preparingStart = false
                        result.onSuccess { message ->
                            startMessage = message.takeIf { it.isNotBlank() && !it.contains("included", ignoreCase = true) && !it.contains("already installed", ignoreCase = true) }
                            OrchestratorService.start(context, profile.id)
                        }.onFailure { error ->
                            startMessage = error.message ?: "Engine install failed"
                        }
                    }
                }
            }, enabled = running || (profile.gameKey != null && !preparingStart), modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp)); Text(if (running) "Stop server" else if (preparingStart) "Preparing engine" else "Start server")
            }
        }
        startMessage?.let { message ->
            item {
                Text(
                    message,
                    color = if (message.contains("failed", ignoreCase = true) || message.contains("not included", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (localPort != null && localPort > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Local address", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp)); Text("127.0.0.1:$localPort", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "On the same phone, use address 127.0.0.1 and port $localPort in Luanti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copy(context, "127.0.0.1:$localPort") }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                        FilledTonalButton(onClick = { openLuanti(context) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("Open Luanti") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Public access", style = MaterialTheme.typography.titleMedium)
                            Text("Optional NovaX UDP tunnel · LAN never needs an account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (publicEnabled) "On" else "Off", style = MaterialTheme.typography.labelLarge)
                    }
                    if (publicEnabled) {
                        Text("$publicHost:$publicPort", style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { copy(context, "$publicHost:$publicPort") }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                            Button(onClick = { onStopPublicTunnel(profile.id) { result -> publicMessage = result.fold({ it }, { it.message ?: "Tunnel stop failed" }) } }) { Text("Stop public") }
                        }
                    } else {
                        Button(
                            onClick = {
                                val activity = context.findActivity()
                                if (activity == null) {
                                    publicMessage = "Public tunnel requires an active app screen"
                                } else {
                                    onStartPublicTunnel(activity, profile.id, localPort ?: 0) { result ->
                                        publicMessage = result.fold({ it }, { it.message ?: "Tunnel start failed" })
                                    }
                                }
                            },
                            enabled = running && localPort != null && localPort > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start public tunnel") }
                    }
                    publicMessage?.let { Text(it, color = if (it.contains("failed", ignoreCase = true) || it.contains("required", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CrashReportCard(
    report: ServerCrashReportEntity?,
    activeCrash: Boolean,
    context: Context,
) {
    val title = if (activeCrash) "Server crashed" else "Last engine problem"
    val reportText = if (report == null) {
        "No support report was recorded for this crash."
    } else {
        buildString {
            appendLine("Code: ${report.code}")
            appendLine("Reason: ${report.reason}")
            if (report.engineVersion.isNotBlank()) appendLine("Engine: ${report.engineVersion}")
            appendLine("Time: ${formatCrashTime(report.createdAt)}")
            if (report.detail.isNotBlank()) {
                appendLine()
                append(report.detail)
            }
        }.trim()
    }
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Send the support code to LuaNet support if you report this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (report != null) {
                Text("Support code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(report.code, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(report.reason, color = MaterialTheme.colorScheme.onErrorContainer)
                if (report.detail.isNotBlank()) {
                    Text(
                        report.detail.take(700),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            } else {
                Text(reportText, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report != null) {
                    FilledTonalButton(onClick = { copy(context, report.code) }) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy code")
                    }
                }
                FilledTonalButton(onClick = { copy(context, reportText) }) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy report")
                }
            }
        }
    }
}

@Composable private fun Placeholder(text: String) = Column(Modifier.padding(20.dp)) { Text(text) }

private fun serverStateLabel(state: String): String =
    state.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun formatCrashTime(createdAt: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(createdAt))

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("LuaNet address", text))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openLuanti(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("net.minetest.minetest")
    if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun requestNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41) }
    }
}

private fun requestBackgroundHostingExemption(context: Context) {
    if (Build.VERSION.SDK_INT < 23) return
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) return
    val packageUri = Uri.parse("package:${context.packageName}")
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(request) }.onFailure {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}
