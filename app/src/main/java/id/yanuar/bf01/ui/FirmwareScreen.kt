package id.yanuar.bf01.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
    val img = u.image

    SubPage {
        Section("Version") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("On BF", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(if (info == null) "not connected" else FirmwareImage.displayVersion(fw), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Latest", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text("v${FirmwareImage.LATEST}", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (info != null && !appOta) HelpNote("This BF needs a one-time cable flash to v${FirmwareImage.MIN_OTA_FW}",
                "Old firmware can't take updates from the app. Flash bf01-firmware-merged-v${FirmwareImage.LATEST}.bin at address 0x0 over USB, for example with the ESP web flasher. After that, later updates can run from here.")
        }

        Section("Firmware file") {
            ActionButton(if (img == null) "Choose xiaozhi.bin" else "Change file", icon = Sym.FolderOpen, enabled = !u.busy) {
                pickBin.launch(arrayOf("application/octet-stream", "*/*"))
            }
            HelpNote("Why pick the file by hand?",
                "The BF firmware repo is private, so the app can't download releases from GitHub without storing a GitHub token on the phone, which isn't safe. Download xiaozhi.bin and SHA256SUMS from the Release page (signed in to GitHub in the browser), then pick them here.")
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
                        Text(if (by != null) "Verified: $by" else "SHA-256 doesn't match the official list yet. Pick or paste SHA256SUMS",
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
            else ActionButton("Firmware update", primary = true, icon = Sym.SystemUpdate, enabled = why == null && (appOta || legacy)) {
                if (appOta) fwu.start() else fwu.startLegacy()
            }
            HelpNote("During the update", "Keep the app open, and keep the phone and BF close on the same Wi-Fi. The phone screen stays on by itself. BF downloads straight from the phone (port 8000), then checks SHA-256 before installing. If BLE drops, BF carries on over Wi-Fi. If the new firmware fails to start, BF rolls back to the old version. Needs battery ≥30% or USB power.")
        }
    }
}
