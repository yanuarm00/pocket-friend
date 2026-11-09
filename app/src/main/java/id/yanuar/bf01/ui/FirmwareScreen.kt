package id.yanuar.bf01.ui

import id.yanuar.bf01.ble.BfProtocol
import androidx.compose.material3.TextButton
import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.fw.FirmwareImage
import id.yanuar.bf01.svc.FwUpdate.Phase
import id.yanuar.bf01.svc.Hub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SETELAN > Perangkat > Update firmware. */
@Composable fun FirmwareScreen(hub: Hub) {
    val fwu = hub.fwUpdate
    val u by fwu.state.collectAsState()
    val info by hub.ble.info.collectAsState()
    val ble by hub.ble.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // Layar HP tetap menyala selama update (server HP harus tetap hidup).
    val keepOn = u.busy
    DisposableEffect(keepOn) {
        val w = (ctx as? Activity)?.window
        if (keepOn) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    fun nameOf(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
    val pickBin = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { fwu.pick(uri, nameOf(uri)) }
    }
    val pickSums = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val t = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrDefault("") }
            fwu.setSums(t)
        }
    }
    var paste by remember { mutableStateOf("") }
    var legacy by rememberSaveable { mutableStateOf(false) }

    val fw = info?.fw
    val appOta = FirmwareImage.supportsAppOta(fw, info?.ota == true)
    val bleOta = (info?.otaLevel ?: 0) >= 2
    var link by rememberSaveable { mutableStateOf("") }
    val remote by fwu.remote.collectAsState()
    LaunchedEffect(Unit) { if (hub.ble.state.value == id.yanuar.bf01.ble.BleManager.State.READY) hub.ble.refreshInfo(); fwu.checkLatest() }
    val img = u.image

    val dev = hub.prefs.deviceName.ifBlank { "BF" }
    val busy = u.busy
    val steps = listOf(
        "Download" to (u.phase >= Phase.Downloading || u.phase == Phase.Done),
        "Check file" to (u.phase >= Phase.Verifying || u.phase == Phase.Done),
        "Send to $dev" to (u.phase >= Phase.Rebooting || u.phase == Phase.Done || (u.viaBle && u.phase >= Phase.Downloading)),
        "Restart" to (u.phase == Phase.Done),
    )
    val newer = remote != null && (fw == null || FirmwareImage.compare(remote!!.version, fw ?: "") > 0)
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FwUpdateContent(
            devName = dev,
            installed = if (info == null) "not connected" else FirmwareImage.displayVersion(fw),
            available = if (newer) remote?.version else null,
            sizeLabel = remote?.let { "${"%.1f".format(it.size / 1048576.0)} MB" },
            notes = listOf("Faster wake and clearer Say Hi QR", "Keeps pairing and Wi-Fi", "Rolls back if the new build fails"),
            batt = info?.batt,
            phase = when {
                !busy && u.phase == Phase.Done -> "Update installed"
                !busy && u.phase == Phase.Failed -> u.message.ifBlank { "Update failed" }
                busy -> u.message.ifBlank { "Working…" }
                else -> ""
            },
            progress = (if (u.progress < 0) 0 else u.progress) / 100f,
            steps = steps,
            onUpdate = {
                scope.launch {
                    if (img == null) fwu.fetchLatest()
                    if (bleOta) fwu.startBle() else if (appOta) fwu.start() else fwu.startLegacy()
                }
            },
            onCheck = { scope.launch { fwu.checkLatest() } },
            busy = busy || u.phase == Phase.Failed || u.phase == Phase.Done,
        )
        if (u.busy) TextButton({ fwu.cancel() }, Modifier.fillMaxWidth()) { Text("Cancel", color = Pop.Orange, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        Section("Advanced") {
        Section("Version") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("On BF", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(if (info == null) "not connected" else FirmwareImage.displayVersion(fw), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Available", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text("v${remote?.version ?: FirmwareImage.LATEST}", style = MaterialTheme.typography.titleLarge)
                }
            }
            RestartLine(info, hub)
            if (info != null && appOta && !bleOta) HelpNote("One cable flash unlocks Bluetooth updates",
                "This BF can only take app updates over Wi-Fi. Flash v0.3.9-test once with the web flasher (rows 0xd000 otadata and 0x20000 app, no Erase; pairing stays). After that, updates go straight over Bluetooth from here.")
            if (info != null && !appOta) HelpNote("This BF needs a one-time cable flash to v${FirmwareImage.MIN_OTA_FW}",
                "Old firmware can't take updates from the app. Flash v0.3.9-test once with the web flasher: otadata at 0xd000 and the app file at 0x20000, no Erase, so pairing stays. After that, updates run from here over Bluetooth.")
        }

        Section("Firmware file") {
            ActionButton(if (img == null) "Choose firmware file (.bin)" else "Change file", icon = Sym.FolderOpen, enabled = !u.busy) {
                pickBin.launch(arrayOf("application/octet-stream", "*/*"))
            }
            remote?.let { r ->
                val newer = fw == null || FirmwareImage.compare(r.version, fw) > 0
                ActionButton(if (newer) "Download v${r.version} (${r.size / 1024} KB)" else "Download v${r.version} again",
                    primary = newer && img == null, icon = Sym.SystemUpdate, enabled = !u.busy) { scope.launch { fwu.fetchLatest() } }
            }
            OutlinedTextField(link, { link = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, enabled = !u.busy,
                label = { Text("or paste a download link (optional)") })
            if (link.isNotBlank()) ActionButton("Download from link", icon = Sym.SystemUpdate, enabled = !u.busy) { scope.launch { fwu.fetch(link) } }
            HelpNote("Which file?",
                "Use the file that ends in -OTA.bin (the same as -app-0x20000.bin). Save it to the phone first, for example from Google Drive or a chat, then pick it here. Don't use a merged file.")
            if (img != null) {
                Text(u.fileName ?: "", style = MaterialTheme.typography.titleMedium)
                if (!img.ok) Text(img.error ?: "Invalid file", color = cs.error)
                else {
                    Text("Version ${img.version ?: "?"} · ${"%.2f".format(img.size / 1048576.0)} MB", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer { Text("SHA-256 ${img.sha256}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = cs.onSurfaceVariant) }
                    val by = u.verifiedBy
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (by != null) Sym.Verified else Sym.Info, null, Modifier.size(20.dp), tint = if (by != null) cs.primary else cs.error)
                        Spacer(Modifier.width(8.dp))
                        Text(if (by != null) "Checksum OK: ${by.removePrefix("checksum ")}" else "SHA-256 checksum doesn't match the app's list yet. Pick or paste SHA256SUMS",
                            color = if (by != null) cs.primary else cs.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (by == null) {
                        ActionButton("Choose SHA256SUMS", enabled = !u.busy) { pickSums.launch(arrayOf("text/plain", "*/*")) }
                        OutlinedTextField(paste, { paste = it; fwu.setSums(it) }, Modifier.fillMaxWidth(), label = { Text("or paste the SHA256SUMS text") }, minLines = 2)
                    }
                }
            }
        }

        Section("Install") {
            val why = fwu.canStart(info, ble)
            if (u.phase != Phase.Idle && u.phase != Phase.Ready) {
                val p = u.progress
                if (u.busy && p < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { (if (p < 0) 0 else p) / 100f }, Modifier.fillMaxWidth(),
                    color = if (u.phase == Phase.Failed) cs.error else cs.primary)
                Text((if (p >= 0 && u.busy) "$p% · " else "") + u.message, style = MaterialTheme.typography.bodyLarge,
                    color = when (u.phase) { Phase.Failed -> cs.error; Phase.Done -> cs.primary; else -> cs.onSurface })
            } else if (why != null) Text(why, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (!appOta && info != null) Toggle("Legacy path (experimental)", legacy, "For BF v0.2.x: BF fetches the firmware when it turns on Wi-Fi. Restart BF first. If it fails, use a cable.", enabled = !u.busy) { legacy = it }
            if (u.busy) ActionButton("Cancel") { fwu.cancel() }
            else ActionButton("Install update", primary = true, icon = Sym.SystemUpdate, enabled = why == null && (appOta || legacy)) {
                if (bleOta) fwu.startBle() else if (appOta) fwu.start() else fwu.startLegacy()
            }
            if (bleOta) HelpNote("During the update", "Keep the app open and the phone next to BF (about 2–3 minutes). The phone screen stays on by itself. The file goes over Bluetooth, no Wi-Fi needed. BF checks SHA-256 before installing and beeps when it's done. If Bluetooth drops, it picks up where it stopped. If the new firmware fails to start, BF goes back to the old version. Pairing and Wi-Fi stay saved. Needs battery ≥30% or USB power.")
            else HelpNote("During the update", "Keep the app open, and keep the phone and BF close on the same Wi-Fi. The phone screen stays on by itself. BF downloads straight from the phone (port 8000), then checks SHA-256 before installing. If BLE drops, BF carries on over Wi-Fi. If the new firmware fails to start, BF rolls back to the old version. Needs battery ≥30% or USB power.")
        }
        }
    }
}
