package net.novax.luanet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.novax.luanet.data.db.ServerProfileEntity
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerList(profiles: List<ServerProfileEntity>, onCreate: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("LuaNet") }) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, "New server") } },
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Host a Luanti world from this phone", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Create a server, install a game from ContentDB, and share it over LAN or NovaX.")
                Spacer(Modifier.height(20.dp))
                Button(onClick = onCreate) { Text("Create first server") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(onClick = { onOpen(profile.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleLarge)
                            Text("Luanti ${profile.engineVersion} · ${profile.state.name.lowercase()}")
                            Text(profile.gameKey ?: "Game not installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { TextField(name, { name = it }, label = { Text("Server name") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Button(onClick = { showVersions = true }, modifier = Modifier.fillMaxWidth()) { Text("Engine $version") }
            }
            item { TextField(players, { players = it.filter(Char::isDigit).take(3) }, label = { Text("Maximum players") }, modifier = Modifier.fillMaxWidth()) }
            item { Toggle("Creative mode", creative) { creative = it } }
            item { Toggle("Damage", damage) { damage = it } }
            item { Toggle("PvP", pvp) { pvp = it } }
            item {
                Text("The server is unlisted and open by default. You can enable an allowlist later.")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onCreate(name, version, players.toIntOrNull() ?: 8, creative, damage, pvp) },
                    enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                ) { Text("Create server") }
            }
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
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(value, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    profile: ServerProfileEntity?,
    onBack: () -> Unit,
    onUpdateAutoOff: (String, Boolean, Int) -> Unit,
) {
    if (profile == null) return
    val context = LocalContext.current
    val sessions by RuntimeRegistry.sessions.collectAsStateWithLifecycle()
    val runtime = sessions[profile.id]
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Console", "Players", "Content", "Settings", "Backups")
    Scaffold(topBar = { TopAppBar(title = { Text(profile.name) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title -> Tab(index == selectedTab, { selectedTab = index }, text = { Text(title) }) }
            }
            when (selectedTab) {
                0 -> Overview(profile, runtime?.state, runtime?.localPort ?: profile.localPort, context)
                1 -> LazyColumn(Modifier.padding(16.dp)) { items(runtime?.logs.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall) } }
                2 -> LazyColumn(Modifier.padding(16.dp)) { items(runtime?.players?.toList().orEmpty()) { Text(it) } }
                3 -> Placeholder("Install games and mods from ContentDB or import a validated ZIP.")
                4 -> AutoOffSettings(profile, onUpdateAutoOff)
                5 -> Placeholder("Automatic backups are retained before content or engine changes.")
            }
        }
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
        item { Toggle("Stop when nobody is connected", enabled) { enabled = it } }
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
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(runtimeState ?: profile.state.name, style = MaterialTheme.typography.headlineSmall)
        Text("Engine ${profile.engineVersion}")
        val running = runtimeState in setOf("STARTING", "RUNNING") || profile.state in setOf(ServerState.STARTING, ServerState.RUNNING)
        Button(onClick = {
            if (running) OrchestratorService.stop(context, profile.id) else OrchestratorService.start(context, profile.id)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            Text(if (running) " Stop server" else " Start server")
        }
        if (localPort != null && localPort > 0) {
            Text("Same phone: 127.0.0.1:$localPort")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(context, "127.0.0.1:$localPort") }) { Icon(Icons.Default.ContentCopy, null); Text(" Copy") }
                Button(onClick = { openLuanti(context) }) { Text("Open Luanti") }
            }
        }
        Text("Public tunnel is optional. LAN hosting never requires an account or advertisement.")
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
