package com.shielddp.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Navy = Color(0xFF0A0E1F)
private val CardBg = Color(0xFF111830)
private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Muted = Color(0xFF8A93B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShieldApp(ServerStore(applicationContext)) }
    }
}

@Composable
fun ShieldApp(store: ServerStore) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Green, onPrimary = Navy, background = Navy, surface = CardBg)
    ) {
        var tab by remember { mutableIntStateOf(0) }
        val servers = remember { mutableStateListOf<Server>().apply { addAll(store.servers()) } }
        var selectedId by remember { mutableStateOf(store.selectedId) }
        var bypass by remember { mutableStateOf(store.bypass) }
        val selected = servers.firstOrNull { it.id == selectedId } ?: servers.first()

        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = CardBg) {
                    listOf("Shield" to Icons.Default.Lock, "Servers" to Icons.Default.Place, "Settings" to Icons.Default.Settings)
                        .forEachIndexed { i, (name, icon) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(icon, name) }, label = { Text(name) }
                            )
                        }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> ShieldScreen(selected, bypass) { tab = 1 }
                    1 -> ServersScreen(
                        servers, selectedId,
                        onSelect = { selectedId = it; store.selectedId = it },
                        onSave = { s ->
                            val i = servers.indexOfFirst { it.id == s.id }
                            if (i >= 0) servers[i] = s else servers.add(s)
                            store.save(servers.toList())
                        },
                        onDelete = { id ->
                            servers.removeAll { it.id == id }
                            if (selectedId == id) { selectedId = "mumbai"; store.selectedId = "mumbai" }
                            store.save(servers.toList())
                        }
                    )
                    else -> SettingsScreen(bypass) { bypass = it; store.bypass = it }
                }
            }
        }
    }
}

private fun fmt(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    else -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
}

@Composable
fun ShieldScreen(server: Server, bypass: Set<String>, openServers: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by Vpn.connected.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rx by remember { mutableLongStateOf(0) }
    var tx by remember { mutableLongStateOf(0) }
    var secs by remember { mutableLongStateOf(0) }

    fun start() {
        scope.launch {
            busy = true; error = null
            try { Vpn.connect(ctx, server, bypass) }
            catch (e: Exception) { error = e.message ?: "Connection failed. Check the WireGuard config." }
            busy = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) start() else error = "VPN permission was denied."
    }

    fun toggle() {
        if (busy) return
        if (on) {
            scope.launch { busy = true; Vpn.disconnect(ctx); busy = false }
            return
        }
        if (!server.configured) {
            error = "Add a WireGuard config for ${server.label} in the Servers tab first."
            return
        }
        val intent = VpnService.prepare(ctx)
        if (intent != null) permission.launch(intent) else start()
    }

    LaunchedEffect(on) {
        while (on) {
            Vpn.stats(ctx)?.let { rx = it.first; tx = it.second }
            secs = (System.currentTimeMillis() - Vpn.startedAt) / 1000
            delay(1000)
        }
        if (!on) { secs = 0; rx = 0; tx = 0 }
    }

    val color = if (on) Green else Red
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SHIELD-DP", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Free · No signup · Zero fees", color = Muted)
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(210.dp).clip(CircleShape).background(color.copy(alpha = 0.12f))
                .border(4.dp, color, CircleShape).clickable { toggle() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when { busy -> "..."; on -> "PROTECTED"; else -> "TAP TO CONNECT" },
                color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Red) }
        Spacer(Modifier.height(28.dp))
        Card(
            Modifier.fillMaxWidth().clickable { openServers() },
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Server", color = Muted)
                Text(server.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (!server.configured) Text("Not configured yet", color = Red)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Session", "%02d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60), Modifier.weight(1f))
            Stat("Downloaded", fmt(rx), Modifier.weight(1f))
            Stat("Uploaded", fmt(tx), Modifier.weight(1f))
        }
    }
}

@Composable
fun Stat(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ServersScreen(
    servers: List<Server>, selectedId: String,
    onSelect: (String) -> Unit, onSave: (Server) -> Unit, onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<Server?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Servers", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Choose Mumbai or Hyderabad, or add your own", color = Muted)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(servers, key = { it.id }) { s ->
                val sel = s.id == selectedId
                Card(
                    Modifier.fillMaxWidth().clickable { onSelect(s.id) },
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = if (sel) BorderStroke(2.dp, Green) else null,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel, onClick = { onSelect(s.id) })
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontWeight = FontWeight.SemiBold)
                            Text(if (s.configured) "Ready" else "Needs WireGuard config", color = if (s.configured) Green else Muted)
                        }
                        TextButton(onClick = { editing = s }) { Text(if (s.configured) "Edit" else "Set up") }
                    }
                }
            }
        }
        Button(
            onClick = { editing = Server("custom-" + System.currentTimeMillis(), "", "") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add server") }
    }
    editing?.let { s ->
        ServerDialog(s, { editing = null }, { onSave(it); editing = null }, { onDelete(it); editing = null })
    }
}

@Composable
fun ServerDialog(server: Server, onDismiss: () -> Unit, onSave: (Server) -> Unit, onDelete: (String) -> Unit) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(server.label) }
    var cfg by remember { mutableStateOf(server.config) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            ctx.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> cfg = r.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WireGuard server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    label, { label = it }, label = { Text("Name (e.g. Mumbai, India)") },
                    singleLine = true, enabled = !server.isDefault
                )
                OutlinedTextField(
                    cfg, { cfg = it }, label = { Text("Paste .conf contents") },
                    modifier = Modifier.height(180.dp)
                )
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .conf file") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onSave(server.copy(label = label.trim(), config = cfg.trim())) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!server.isDefault && server.label.isNotBlank()) {
                    TextButton(onClick = { onDelete(server.id) }) { Text("Delete", color = Red) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SettingsScreen(bypass: Set<String>, onBypass: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }
                .filter { it.second != ctx.packageName }
                .sortedBy { it.first.lowercase() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kill switch", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android controls this system-wide. Open VPN settings, tap the gear next to SHIELD-DP, " +
                            "then turn on \"Always-on VPN\" and \"Block connections without VPN\".",
                        color = Muted
                    )
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open VPN settings") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Split tunneling", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Switch ON to let an app bypass the VPN. Reconnect to apply changes.", color = Muted)
        }
        items(apps, key = { it.second }) { (name, pkg) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name)
                    Text(if (pkg in bypass) "Direct connection" else "Through VPN", color = if (pkg in bypass) Color(0xFFFFB74D) else Green, fontSize = 12.sp)
                }
                Switch(checked = pkg in bypass, onCheckedChange = { on ->
                    onBypass(if (on) bypass + pkg else bypass - pkg)
                })
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("SHIELD-DP 1.0.0 · Free, no ads, no accounts. Traffic is encrypted with WireGuard.", color = Muted)
        }
    }
}
