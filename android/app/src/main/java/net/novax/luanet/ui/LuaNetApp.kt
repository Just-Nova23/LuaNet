package net.novax.luanet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.noties.markwon.Markwon
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.data.db.InstalledPackageEntity
import net.novax.luanet.R
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.content.ContentPackage
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.ServerState
import net.novax.luanet.runtime.OrchestratorService
import net.novax.luanet.runtime.RuntimeRegistry
import net.novax.luanet.runtime.RuntimeSnapshot

private sealed interface Destination {
    data object Servers : Destination
    data object Create : Destination
    data class Dashboard(val id: String) : Destination
    data class ContentLibrary(val id: String) : Destination
    data class ZipImport(val id: String) : Destination
}

@Composable
fun LuaNetApp(viewModel: MainViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val contentDetails by viewModel.contentDetails.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    var destination: Destination by remember { mutableStateOf(Destination.Servers) }
    when (val current = destination) {
        Destination.Servers -> ServerList(
            profiles = profiles,
            onCreate = { destination = Destination.Create },
            onOpen = { destination = Destination.Dashboard(it) },
        )
        Destination.Create -> CreateServer(
            onBack = { destination = Destination.Servers },
            onCreate = { name, version, players, creative, damage, pvp ->
                viewModel.create(name, version, players, creative, damage, pvp) {
                    destination = Destination.Dashboard(it)
                }
            },
        )
        is Destination.Dashboard -> {
            val installedPackages by viewModel.installedPackages(current.id).collectAsStateWithLifecycle(emptyList())
            Dashboard(
                profile = profiles.firstOrNull { it.id == current.id },
                installedPackages = installedPackages,
                onBack = { destination = Destination.Servers },
                onOpenContentLibrary = { destination = Destination.ContentLibrary(current.id) },
                onOpenZipImport = { destination = Destination.ZipImport(current.id) },
                onUpdateAutoOff = viewModel::updateAutoOff,
                onCreateBackup = viewModel::createBackup,
                accountState = account,
                onSaveAccountToken = viewModel::saveAccountToken,
                onSyncEntitlement = viewModel::syncEntitlement,
                onStartPublicTunnel = viewModel::startPublicTunnel,
                onStopPublicTunnel = viewModel::stopPublicTunnel,
            )
        }
        is Destination.ContentLibrary -> {
            val installedPackages by viewModel.installedPackages(current.id).collectAsStateWithLifecycle(emptyList())
            ContentBrowserScreen(
                profile = profiles.firstOrNull { it.id == current.id },
                installedPackages = installedPackages,
                state = content,
                details = contentDetails,
                onBack = { destination = Destination.Dashboard(current.id) },
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
                onBack = { destination = Destination.Dashboard(current.id) },
                onImport = viewModel::importArchive,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerList(profiles: List<ServerProfileEntity>, onCreate: () -> Unit, onOpen: (String) -> Unit) {
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
    onCreate: (String, String, Int, Boolean, Boolean, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(EngineCatalog.latest.version) }
    var players by remember { mutableStateOf("8") }
    var creative by remember { mutableStateOf(false) }
    var damage by remember { mutableStateOf(true) }
    var pvp by remember { mutableStateOf(true) }
    var showVersions by remember { mutableStateOf(false) }
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
                    onClick = { onCreate(name, version, players.toIntOrNull() ?: 8, creative, damage, pvp) },
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
}

@Composable
private fun Toggle(label: String, detail: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    profile: ServerProfileEntity?,
    installedPackages: List<InstalledPackageEntity>,
    onBack: () -> Unit,
    onOpenContentLibrary: () -> Unit,
    onOpenZipImport: () -> Unit,
    onUpdateAutoOff: (String, Boolean, Int) -> Unit,
    onCreateBackup: (String, (Result<String>) -> Unit) -> Unit,
    accountState: AccountState,
    onSaveAccountToken: (String) -> Unit,
    onSyncEntitlement: ((Result<String>) -> Unit) -> Unit,
    onStartPublicTunnel: (String, Int, (Result<String>) -> Unit) -> Unit,
    onStopPublicTunnel: (String, (Result<String>) -> Unit) -> Unit,
) {
    if (profile == null) return
    val context = LocalContext.current
    val sessions by RuntimeRegistry.sessions.collectAsStateWithLifecycle()
    val runtime = sessions[profile.id]
    val running = runtime?.state in setOf("STARTING", "RUNNING") || profile.state in setOf(ServerState.STARTING, ServerState.RUNNING)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        DashboardTab("Overview", Icons.Default.Dashboard), DashboardTab("Console", Icons.AutoMirrored.Filled.Terminal),
        DashboardTab("Players", Icons.Default.Group), DashboardTab("Content", Icons.Default.FolderZip),
        DashboardTab("Settings", Icons.Default.Settings), DashboardTab("Backups", Icons.Default.Backup),
    )
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
            when (selectedTab) {
                0 -> Overview(
                    profile = profile,
                    runtime = runtime,
                    localPort = runtime?.localPort ?: profile.localPort,
                    context = context,
                    onStartPublicTunnel = onStartPublicTunnel,
                    onStopPublicTunnel = onStopPublicTunnel,
                )
                1 -> ConsolePanel(profile.id, runtime?.logs.orEmpty(), running) { command ->
                    OrchestratorService.command(context, profile.id, command)
                }
                2 -> PlayersPanel(runtime?.players?.toList().orEmpty(), running) { command ->
                    OrchestratorService.command(context, profile.id, command)
                }
                3 -> ContentSummaryPanel(profile, installedPackages, onOpenContentLibrary, onOpenZipImport)
                4 -> SettingsPanel(profile, accountState, onUpdateAutoOff, onSaveAccountToken, onSyncEntitlement)
                5 -> BackupPanel(profile, onCreateBackup)
            }
        }
    }
}

private data class DashboardTab(val label: String, val icon: ImageVector)

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
                placeholder = { Text("status, kick player, grant player all") },
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
    players: List<String>,
    running: Boolean,
    onCommand: (String) -> Unit,
) {
    if (players.isEmpty()) {
        EmptySection(Icons.Default.Group, "No players online", "Connected players will appear here with moderation actions.")
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(players) { player ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(player.take(1).uppercase(), Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(player, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(enabled = running, onClick = { onCommand("kick ${player.safePlayerName()}") }) { Text("Kick") }
                            FilledTonalButton(enabled = running, onClick = { onCommand("ban ${player.safePlayerName()}") }) { Text("Ban") }
                            TextButton(enabled = running, onClick = { onCommand("grant ${player.safePlayerName()} all") }) { Text("Make admin") }
                        }
                    }
                }
            }
        }
    }
}

private fun String.safePlayerName(): String = filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)

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
    onOpenContentLibrary: () -> Unit,
    onOpenZipImport: () -> Unit,
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
        item {
            Text(
                "Stop the server before changing games, mods, modpacks, or ZIP imports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    if (url.isNullOrBlank()) {
        Box(
            imageModifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FolderZip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
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
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color.Black.copy(alpha = 0.96f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
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
    onCreate: (String, (Result<String>) -> Unit) -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backups", style = MaterialTheme.typography.headlineSmall)
        Text("Backups contain the profile world, game, mods, and configuration.")
        Button(
            onClick = { onCreate(profile.id) { result -> message = result.fold({ it }, { it.message ?: "Backup failed" }) } },
            enabled = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create manual backup") }
        message?.let { Text(it) }
    }
}

@Composable
private fun SettingsPanel(
    profile: ServerProfileEntity,
    account: AccountState,
    onSaveAutoOff: (String, Boolean, Int) -> Unit,
    onSaveAccountToken: (String) -> Unit,
    onSyncEntitlement: ((Result<String>) -> Unit) -> Unit,
) {
    var enabled by remember(profile.id, profile.autoOffEnabled) { mutableStateOf(profile.autoOffEnabled) }
    var minutes by remember(profile.id, profile.autoOffMinutes) { mutableStateOf(profile.autoOffMinutes.toString()) }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val parsedMinutes = minutes.toIntOrNull()
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("NovaX account", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (account.tokenConfigured) "Token configured" else "No account token", style = MaterialTheme.typography.titleMedium)
                    Text("Current tier: ${account.tier.name.lowercase().replaceFirstChar(Char::uppercase)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (account.expiresAt != null) Text("Expires: ${account.expiresAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Temporary debug auth accepts dev:<uid>. Production will use Firebase ID tokens from Google/email sign-in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.take(160) },
                label = { Text("NovaX token") },
                placeholder = { Text("dev:tester") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSaveAccountToken(token)
                    message = if (token.isBlank()) "Account token cleared" else "Account token saved"
                    token = ""
                }, modifier = Modifier.weight(1f)) { Text("Save token") }
                FilledTonalButton(onClick = {
                    onSyncEntitlement { result -> message = result.fold({ it }, { it.message ?: "Entitlement sync failed" }) }
                }, enabled = account.tokenConfigured, modifier = Modifier.weight(1f)) { Text("Sync") }
            }
        }
        message?.let { item { Text(it, color = if (it.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) } }
        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
        item { Text("Auto off", style = MaterialTheme.typography.headlineSmall) }
        item { Toggle("Stop when nobody is connected", "Timer starts when the last player leaves", enabled) { enabled = it } }
        item {
            TextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                enabled = enabled,
                label = { Text("Minutes with no players") },
                supportingText = { Text("From 1 minute to 24 hours. Disabled by default.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { onSaveAutoOff(profile.id, enabled, parsedMinutes ?: profile.autoOffMinutes) },
                enabled = !enabled || (parsedMinutes != null && parsedMinutes in 1..1_440),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save auto off") }
        }
        item {
            Text("LAN servers have no mandatory idle timeout. Free public tunnels still expire after four hours.")
        }
    }
}

@Composable
private fun Overview(
    profile: ServerProfileEntity,
    runtime: RuntimeSnapshot?,
    localPort: Int?,
    context: Context,
    onStartPublicTunnel: (String, Int, (Result<String>) -> Unit) -> Unit,
    onStopPublicTunnel: (String, (Result<String>) -> Unit) -> Unit,
) {
    var publicMessage by remember(profile.id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val runtimeState = runtime?.state
        val running = runtimeState in setOf("STARTING", "RUNNING") || profile.state in setOf(ServerState.STARTING, ServerState.RUNNING)
        val state = runtimeState ?: profile.state.name
        val publicHost = runtime?.publicHost ?: profile.publicHost
        val publicPort = runtime?.publicPort ?: profile.publicPort
        val publicEnabled = publicHost != null && publicPort != null && (runtime?.publicPort != null || profile.publicEnabled)
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(if (running) MaterialTheme.colorScheme.primary else stateColor(profile.state), CircleShape))
                        Spacer(Modifier.width(10.dp)); Text(state.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f)); Text("${profile.maxPlayers} slots", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(if (running) "Server is available on this device and your local network." else "Ready when you are.", style = MaterialTheme.typography.bodyLarge)
                }
            }
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
                    requestNotificationPermission(context)
                    OrchestratorService.start(context, profile.id)
                }
            }, enabled = running || profile.gameKey != null, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp)); Text(if (running) "Stop server" else "Start server")
            }
        }
        if (localPort != null && localPort > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lan, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Local address", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp)); Text("127.0.0.1:$localPort", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copy(context, "127.0.0.1:$localPort") }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                        FilledTonalButton(onClick = { openLuanti(context) }) { Text("Open Luanti") }
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
                            onClick = { onStartPublicTunnel(profile.id, localPort ?: 0) { result -> publicMessage = result.fold({ it }, { it.message ?: "Tunnel start failed" }) } },
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

@Composable private fun Placeholder(text: String) = Column(Modifier.padding(20.dp)) { Text(text) }

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("LuaNet address", text))
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
