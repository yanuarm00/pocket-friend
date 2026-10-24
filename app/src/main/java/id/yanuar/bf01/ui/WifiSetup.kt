package id.yanuar.bf01.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import id.yanuar.bf01.svc.Hub
import id.yanuar.bf01.wifi.Nearby
import id.yanuar.bf01.wifi.WifiQr
import id.yanuar.bf01.wifi.WifiTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One Wi-Fi slot for BF: 0 = home Wi-Fi, 1 = this phone's hotspot. */
@Composable fun WifiSlot(hub: Hub, i: Int, startList: Boolean = false, startGuide: Boolean = false) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val p = hub.prefs
    val (s0, p0) = p.wifi(i)
    var ssid by rememberSaveable { mutableStateOf(s0) }; var pw by rememberSaveable { mutableStateOf(p0) }
    var show by rememberSaveable { mutableStateOf(false) }; var note by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf(startList) }; var guide by rememberSaveable { mutableStateOf(startGuide) }
    var pendingAfterPerm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    fun applyQr(raw: String?) {
        val q = WifiQr.parse(raw)
        note = when {
            raw == null -> "No QR code found in that picture. Crop closer to the QR and try again."
            q == null -> "That QR isn't a Wi-Fi QR."
            else -> { ssid = q.ssid; pw = q.pass; saved = false; "Filled from QR: \"${q.ssid}\". Tap Save." }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok; if (ok) pendingAfterPerm?.invoke(); pendingAfterPerm = null }
    fun withLocation(f: () -> Unit) { if (WifiTools.hasLocation(ctx) || WifiTools.scanOverride != null) f() else { pendingAfterPerm = f; perm.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }
    val camera = rememberLauncherForActivityResult(ScanContract()) { r -> if (r.contents != null) applyQr(r.contents) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { applyQr(withContext(Dispatchers.Default) { runCatching { WifiTools.qrFromImage(ctx, uri) }.getOrNull() }) } }

    Section(if (i == 0) "Wi-Fi 1 (home)" else "Wi-Fi 2 (phone hotspot)") {
        if (i == 1) {
            val on = remember { WifiTools.hotspotOn(ctx) }
            Text(when (on) { true -> "Your hotspot is on."; false -> "Your hotspot is off. Turn it on so BF can join."; null -> "BF can use this phone's hotspot when you're out." },
                style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(ssid, { ssid = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Wi-Fi name (SSID)") }, shape = MaterialTheme.shapes.large,
            leadingIcon = { Icon(if (i == 1) Sym.Hotspot else Sym.Wifi, null) })
        OutlinedTextField(pw, { pw = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = MaterialTheme.shapes.large, leadingIcon = { Icon(Sym.Key, null) },
            trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) "Hide password" else "Show password") } })
        if (i == 0) AdaptiveRow {
            SmallAction("Use current Wi-Fi", Sym.Wifi, Modifier.cell(1f)) { withLocation {
                val c = WifiTools.currentSsid(ctx)
                if (c != null) { ssid = c; saved = false; note = "Filled \"$c\". Now type its password, or scan its QR." }
                else note = "Couldn't read the Wi-Fi name. Make sure the phone is on Wi-Fi and Location is turned on." } }
            SmallAction("Choose network", Sym.Wifi, Modifier.cell(1f)) { withLocation { WifiTools.startScan(ctx); list = true } }
        }
        AdaptiveRow {
            SmallAction("Scan Wi-Fi QR", Sym.QrScan, Modifier.cell(1f)) {
                camera.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Point at the Wi-Fi QR code").setBeepEnabled(false).setOrientationLocked(false)) }
            SmallAction("From image", Sym.Image, Modifier.cell(1f)) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        if (denied) HelpNote("Location is off for this app, so it can't see Wi-Fi names. You can still type the name or use a QR.")
        else if (i == 0 && !WifiTools.hasLocation(ctx)) HelpNote("Android needs Location permission to show Wi-Fi names. It's only used to fill this form.")
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        ActionButton(if (saved) "Saved" else "Save", icon = if (saved) Sym.CheckCircle else Sym.Save) { p.setWifi(i, ssid.trim(), pw); saved = true; note = "" }
        TextButton({ guide = !guide }) { Text(if (guide) "Hide help" else if (i == 0) "How to get the Wi-Fi QR on Samsung" else "How to use my phone's hotspot") }
        if (guide) if (i == 0) Steps(listOf(
            "Open Settings › Connections › Wi-Fi.",
            "Tap the gear icon next to the Wi-Fi you're on.",
            "Tap QR code at the bottom, then take a screenshot.",
            "Come back here and tap From image, then pick that screenshot."),
            "BF only works with 2.4 GHz Wi-Fi. If your router has two names, pick the one without \"5G\".")
        else Steps(listOf(
            "Open Settings › Connections › Mobile Hotspot and Tethering › Mobile Hotspot.",
            "Turn the hotspot on. Under Configure › Band, choose 2.4 GHz.",
            "Tap the QR code shown there, then take a screenshot.",
            "Come back here, tap From image and pick that screenshot. Then Save and Send to BF."),
            "This app can't read your hotspot password by itself, so the QR screenshot is the easiest way. Keep the hotspot on while BF uses it.",
            openLabel = "Open hotspot settings") { runCatching { ctx.startActivity(Intent(Intent.ACTION_MAIN).setClassName("com.android.settings", "com.android.settings.TetherSettings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
    }
    if (list) NetworkPicker({ list = false }) { n -> ssid = n.ssid; saved = false; list = false
        note = if (n.only5) "\"${n.ssid}\" looks 5 GHz only. BF may not see it." else if (n.secured) "Now type the password for \"${n.ssid}\", or scan its QR." else "This network has no password." }
}

@Composable private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text, maxLines = 2) }

@Composable private fun Steps(steps: List<String>, tip: String, openLabel: String? = null, open: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { n, s -> Row { Text("${n + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp)); Text(s, style = MaterialTheme.typography.bodyMedium) } }
            Text(tip, style = MaterialTheme.typography.bodySmall)
            if (openLabel != null && open != null) TextButton(open) { Text(openLabel) }
        }
    }
}

@Composable fun NetworkPicker(dismiss: () -> Unit, pick: (Nearby) -> Unit) {
    val ctx = LocalContext.current
    var nets by remember { mutableStateOf(WifiTools.nearby(ctx)) }
    LaunchedEffect(Unit) { repeat(4) { delay(2500); nets = WifiTools.nearby(ctx) } }
    AlertDialog(onDismissRequest = dismiss, confirmButton = { TextButton(dismiss) { Text("Close") } }, title = { Text("Choose network") }, text = {
        if (nets.isEmpty()) Text("Looking for Wi-Fi… If nothing shows up, turn on Location and Wi-Fi, or type the name yourself.")
        else LazyColumn(Modifier.heightIn(max = 420.dp)) { items(nets, key = { it.ssid }) { n ->
            Row(Modifier.fillMaxWidth().clickable { pick(n) }.heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Wifi, null, Modifier.size(22.dp), tint = if (n.level > -67) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.ssid, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (n.only5) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(if (n.only5) "5 GHz only · not supported by BF" else if (n.level > -67) "Strong signal" else if (n.level > -80) "OK signal" else "Weak signal",
                        style = MaterialTheme.typography.bodySmall, color = if (n.only5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (n.secured) Icon(Sym.Key, "Password", Modifier.size(18.dp))
            }
        } }
    })
}

/** Send Wi-Fi + server to BF and show what BF answered. */
@Composable fun SendToBf(hub: Hub, preset: Pair<Boolean?, String>? = null) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(preset?.first == null && preset != null) }
    var step by remember { mutableStateOf(if (preset?.first == null) preset?.second ?: "" else "") }
    var result by remember { mutableStateOf(preset?.first?.let { Hub.ProvResult(it, preset.second) }) }
    Section("Send Wi-Fi & server to BF") {
        HelpNote("Save your Wi-Fi above first. BF saves it and restarts (about 5 seconds).")
        ActionButton(if (busy) "Sending…" else "Send to BF", primary = true, enabled = !busy, icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send) {
            busy = true; result = null; step = ""
            scope.launch { result = hub.provisionWithStatus { step = it }; busy = false } }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(step, style = MaterialTheme.typography.bodyMedium) }
        result?.let { r -> Row(verticalAlignment = Alignment.Top) {
            Icon(if (r.ok) Sym.CheckCircle else Sym.Error, null, tint = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp)); Text(r.text, style = MaterialTheme.typography.bodyMedium) } }
    }
}
