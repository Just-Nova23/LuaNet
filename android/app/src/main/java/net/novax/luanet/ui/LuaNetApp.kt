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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List as Terminal
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange as Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share as Cloud
import androidx.compose.material.icons.filled.Share as ContentCopy
import androidx.compose.material.icons.filled.Home as Dashboard
import androidx.compose.material.icons.filled.AddCircle as FolderZip
import androidx.compose.material.icons.filled.Person as Group
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.novax.luanet.data.db.ServerProfileEntity
import net.novax.luanet.R
import net.novax.luanet.data.importer.ImportKind
import net.novax.luanet.data.content.ContentPackage
import net.novax.luanet.domain.EngineCatalog
import net.novax.luanet.domain.ServerState
import net.novax.luanet.runtime.OrchestratorService
import net.novax.luanet.runtime.RuntimeRegistry

private sealed interface Destination {
    data object Servers : Destination
    data object Create : Destination
    data class Dashboard(val id: String) : Destination
}

@Composable
fun LuaNetApp(viewModel: MainViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
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
        is Destination.Dashboard -> Dashboard(
            profile = profiles.firstOrNull { it.id == current.id },
            onBack = { destination = Destination.Servers },
            onUpdateAutoOff = viewModel::updateAutoOff,
            onImportArchive = viewModel::importArchive,
            onCreateBackup = viewModel::createBackup,
            contentState = content,
            onSearchContent = viewModel::searchContent,
            onInstallContent = viewModel::installContent,
        )
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
    onBack: () -> Unit,
    onUpdateAutoOff: (String, Boolean, Int) -> Unit,
    onImportArchive: (String, Uri, ImportKind, (Result<String>) -> Unit) -> Unit,
    onCreateBackup: (String, (Result<String>) -> Unit) -> Unit,
    contentState: ContentBrowserState,
    onSearchContent: (String, String, String) -> Unit,
    onInstallContent: (String, ContentPackage, (Result<String>) -> Unit) -> Unit,
) {
    if (profile == null) return
    val context = LocalContext.current
    val sessions by RuntimeRegistry.sessions.collectAsStateWithLifecycle()
    val runtime = sessions[profile.id]
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
                0 -> Overview(profile, runtime?.state, runtime?.localPort ?: profile.localPort, context)
                1 -> ConsolePanel(runtime?.logs.orEmpty())
                2 -> PlayersPanel(runtime?.players?.toList().orEmpty())
                3 -> ContentPanel(profile, contentState, onSearchContent, onInstallContent, onImportArchive)
                4 -> AutoOffSettings(profile, onUpdateAutoOff)
                5 -> BackupPanel(profile, onCreateBackup)
            }
        }
    }
}

private data class DashboardTab(val label: String, val icon: ImageVector)

@Composable
private fun ConsolePanel(logs: List<String>) {
    if (logs.isEmpty()) {
        EmptySection(Icons.AutoMirrored.Filled.Terminal, "Console is quiet", "Start the server to see engine logs and run commands.")
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PlayersPanel(players: List<String>) {
    if (players.isEmpty()) {
        EmptySection(Icons.Default.Group, "No players online", "Connected players will appear here with moderation actions.")
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(players) { player ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(player.take(1).uppercase(), Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp)); Text(player, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySection(icon: ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(icon, null, Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContentPanel(
    profile: ServerProfileEntity,
    state: ContentBrowserState,
    onSearch: (String, String, String) -> Unit,
    onInstall: (String, ContentPackage, (Result<String>) -> Unit) -> Unit,
    onImport: (String, Uri, ImportKind, (Result<String>) -> Unit) -> Unit,
) {
    var type by remember(profile.id) { mutableStateOf("game") }
    var query by remember(profile.id) { mutableStateOf("") }
    var requestedKind by remember { mutableStateOf(ImportKind.GAME) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<ContentPackage?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(profile.id, uri, requestedKind) { result ->
            message = result.fold({ it }, { it.message ?: "Import failed" })
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("ContentDB", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Browse the complete catalog. Compatibility is shown as a warning, never hidden.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                FilledTonalButton(onClick = { onSearch(profile.id, type, query) }, enabled = !state.loading) { Text("Search") }
            }
        }
        if (state.loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.profileId == profile.id && state.items.isEmpty() && state.error == null) {
            item { Text("Search for a game or mod to install.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.profileId == profile.id) items(state.items, key = { it.key }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text("${item.author}/${item.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Surface(shape = CircleShape, color = if (item.compatible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                            Text(if (item.compatible) "Compatible" else "Check", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (item.shortDescription.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(item.shortDescription, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { if (item.compatible) onInstall(profile.id, item) { result -> message = result.fold({ it }, { it.message ?: "Install failed" }) } else pendingInstall = item },
                        enabled = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED), modifier = Modifier.fillMaxWidth()) { Text("Install") }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { Text("Import your own ZIP", style = MaterialTheme.typography.titleLarge) }
        item { Text("Archives are checked for traversal, links, expansion size, compression ratio and Luanti structure.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(ImportKind.entries) { kind ->
            FilledTonalButton(
                onClick = {
                    requestedKind = kind
                    launcher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                enabled = profile.state in setOf(ServerState.STOPPED, ServerState.CRASHED),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import ${kind.name.lowercase()} ZIP") }
        }
        message?.let { item { Text(it) } }
        item { Spacer(Modifier.height(20.dp)) }
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
private fun AutoOffSettings(
    profile: ServerProfileEntity,
    onSave: (String, Boolean, Int) -> Unit,
) {
    var enabled by remember(profile.id, profile.autoOffEnabled) { mutableStateOf(profile.autoOffEnabled) }
    var minutes by remember(profile.id, profile.autoOffMinutes) { mutableStateOf(profile.autoOffMinutes.toString()) }
    val parsedMinutes = minutes.toIntOrNull()
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
                onClick = { onSave(profile.id, enabled, parsedMinutes ?: profile.autoOffMinutes) },
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
private fun Overview(profile: ServerProfileEntity, runtimeState: String?, localPort: Int?, context: Context) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val running = runtimeState in setOf("STARTING", "RUNNING") || profile.state in setOf(ServerState.STARTING, ServerState.RUNNING)
        val state = runtimeState ?: profile.state.name
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
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Public access", style = MaterialTheme.typography.titleMedium); Text("Optional NovaX tunnel · LAN never needs an account or ad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Off", style = MaterialTheme.typography.labelLarge)
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
