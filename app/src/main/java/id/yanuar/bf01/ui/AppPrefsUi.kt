package id.yanuar.bf01.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub

/** v0.4.0 app-wide theme: System / Light / Dark, saved in Prefs and applied immediately. */
object AppTheme {
    val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    var mode by mutableStateOf("system"); private set
    var accent by mutableStateOf("orange"); private set
    private var prefs: Prefs? = null
    fun init(p: Prefs) { prefs = p; mode = p.themeMode; accent = p.accent.ifBlank { "orange" } }
    fun set(m: String) { mode = m; prefs?.themeMode = m }
    fun pickAccent(k: String) { accent = k; runCatching { prefs?.accent = k } }
    fun accentColor() = Accents.color(accent)
    fun label(m: String = mode) = options.firstOrNull { it.first == m }?.second ?: "System"
    @Composable fun isDark() = when (mode) { "light" -> false; "dark" -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
}

@Composable fun ThemeDialog(current: String, pick: (String) -> Unit, dismiss: () -> Unit) = AlertDialog(dismiss,
    title = { Text("Theme") },
    text = { Column(Modifier.selectableGroup()) { AppTheme.options.forEach { (k, l) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(k == current, role = Role.RadioButton) { pick(k) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(k == current, null); Spacer(Modifier.width(12.dp)); Text(l, style = MaterialTheme.typography.bodyLarge) } } } },
    confirmButton = { TextButton(dismiss) { Text("Done") } })

/** Device rename, shared by Settings and the status sheet so both edit the same Prefs.deviceName. */
object DeviceRename {
    const val MAX = 16
    fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().take(MAX)
    fun apply(hub: Hub, name: String) { hub.prefs.deviceName = clean(name); hub.revision.value++; hub.scope.launch { hub.sendNameCard() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RenameDeviceDialog(current: String, save: (String) -> Unit, dismiss: () -> Unit) =
    BasicAlertDialog(dismiss) { RenameDevicePanel(current, save, dismiss) }

/** The dialog's card on its own (also used by screenshot tests, where a text field inside a window never idles). */
@Composable fun RenameDevicePanel(current: String, save: (String) -> Unit, dismiss: () -> Unit) {
    var t by remember { mutableStateOf(current) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp)) {
            Text("Device name", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp))
            OutlinedTextField(t, { if (it.length <= DeviceRename.MAX) t = it }, singleLine = true, label = { Text("Name") },
                supportingText = { Text("${t.length}/${DeviceRename.MAX}") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(dismiss) { Text("Cancel") }
                TextButton({ save(t) }, enabled = DeviceRename.clean(t).isNotEmpty()) { Text("Save") } }
        }
    }
}

/** Name line with a pencil, used in the status sheet. */
@Composable fun DeviceNameRow(hub: Hub) {
    val rev by hub.revision.collectAsState()
    val name = remember(rev) { hub.prefs.deviceName.ifBlank { "BF" } }
    var edit by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { edit = true }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Name", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp))
        Icon(Sym.Edit, "Rename device", Modifier.size(20.dp))
    }
    if (edit) RenameDeviceDialog(name, { DeviceRename.apply(hub, it); edit = false }) { edit = false }
}
