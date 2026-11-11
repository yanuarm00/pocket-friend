package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.data.ProfileTypes
import id.yanuar.bf01.svc.Hub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Account / profile page (v5.13 · 35). Say Hi stays a shortcut from here. */
@Composable fun ProfileScreen(hub: Hub, back: () -> Unit, open: (Screen) -> Unit) {
    val p = hub.prefs
    val st by hub.ble.state.collectAsState()
    val rev by hub.revision.collectAsState()
    val ready = st == id.yanuar.bf01.ble.BleManager.State.READY || st == id.yanuar.bf01.ble.BleManager.State.CONNECTED
    val name = remember(rev) { p.cardName.ifBlank { "Alex" } }
    val dev = p.deviceName.ifBlank { "BF" }
    val ssid = p.wifi(0).first
    val list = remember(rev) { p.profiles() }
    val sayHiCount = list.size
    val sayHiOn = list.firstOrNull()?.let { ProfileTypes.displayName(it) }
    val backupOn = p.backupOn
    val backupSub = if (p.lastRecSync > 0) {
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.lastRecSync))
        "Notes & to-dos · last $clock"
    } else "Notes & to-dos · on this phone"
    var renaming by remember { mutableStateOf(false) }
    if (renaming) RenameOwnerDialog(name, {
        val cleaned = it.trim().take(24)
        if (cleaned.isNotEmpty()) { p.cardName = cleaned; hub.revision.value++ }
        renaming = false
    }) { renaming = false }
    ProfileContent(
        name = name, deviceName = dev, online = ready, wifi = ssid,
        sayHiCount = sayHiCount, sayHiOnDevice = sayHiOn,
        backupOn = backupOn, backupSub = backupSub,
        onBack = back, onRename = { renaming = true },
        onChangeCharacter = { open(Screen.Character) },
        onSayHi = { open(Screen.Kartu) },
        onBackup = { p.backupOn = it; hub.revision.value++ },
    )
}

@Composable fun ProfileContent(
    name: String, deviceName: String, online: Boolean, wifi: String,
    sayHiCount: Int, sayHiOnDevice: String?,
    backupOn: Boolean, backupSub: String,
    onBack: () -> Unit, onRename: () -> Unit = {},
    onChangeCharacter: () -> Unit, onSayHi: () -> Unit, onBackup: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PfBackHeader("Profile", "Name, avatar, Say Hi · backup", onBack)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Pop.Orange, contentColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    BfFace(52.dp, BfMood.Senyum, true, color = Pop.Ink)
                }
                Spacer(Modifier.height(12.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onRename, onClickLabel = "Change name"))
                Text("Avatar uses ${deviceName}'s character", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.clip(CircleShape).background(Pop.Ink).clickable(onClick = onChangeCharacter)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Change character", color = Pop.Yellow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Text("MY BF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold,
            color = cs.onSurfaceVariant, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLowest, shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
                    Text("bf", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$deviceName BF-01", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (online) (if (wifi.isNotBlank()) "Connected · Wi-Fi “$wifi”" else "Connected · Bluetooth") else "Offline",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (online) Box(Modifier.clip(CircleShape).background(Color(0xFFE6F6EA)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Active", color = Color(0xFF1B7A3A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Surface(Modifier.fillMaxWidth().clickable(onClick = onSayHi), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLowest, shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Pop.Orange), contentAlignment = Alignment.Center) {
                    Icon(Sym.Apps, null, Modifier.size(22.dp), tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Say Hi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val count = if (sayHiCount == 1) "1 QR profile" else "$sayHiCount QR profiles"
                    Text(
                        if (sayHiOnDevice.isNullOrBlank()) count else "$count · $sayHiOnDevice on $deviceName",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Sym.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
        }
        Text("BACKUP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold,
            color = cs.onSurfaceVariant, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLowest, shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Pop.Yellow), contentAlignment = Alignment.Center) {
                    Icon(Sym.CloudSync, null, Modifier.size(22.dp), tint = Pop.Ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(backupSub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(backupOn, onBackup, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange, checkedThumbColor = Color.White))
            }
        }
        Text(
            "Stored on this phone only · no account needed",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(PopNavClearance))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RenameOwnerDialog(current: String, save: (String) -> Unit, dismiss: () -> Unit) =
    BasicAlertDialog(dismiss) {
        var t by remember { mutableStateOf(current) }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text("Your name", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(t, { if (it.length <= 24) t = it }, singleLine = true, label = { Text("Name") },
                    supportingText = { Text("${t.length}/24") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(dismiss) { Text("Cancel") }
                    TextButton({ save(t) }, enabled = t.trim().isNotEmpty()) { Text("Save") }
                }
            }
        }
    }

/** Settings → Character (v5.13 change-character). BF menu stays a separate row. */
@Composable fun CharacterScreen(hub: Hub, back: () -> Unit) {
    val p = hub.prefs
    var mode by remember { mutableIntStateOf(p.charMode) }
    var face by remember { mutableIntStateOf(p.charFace) }
    CharacterContent(
        deviceName = p.deviceName.ifBlank { "BF" },
        mode = mode, face = face,
        onBack = back,
        onMode = { mode = it },
        onFace = { face = it },
        onSave = { p.charMode = mode; p.charFace = face; hub.revision.value++; back() },
    )
}

private val CharFaces = listOf(BfMood.Senyum to true, BfMood.Senyum to false, BfMood.Datar to true, BfMood.Sedih to true)

@Composable fun CharacterContent(
    deviceName: String, mode: Int, face: Int,
    onBack: () -> Unit, onMode: (Int) -> Unit, onFace: (Int) -> Unit, onSave: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val moods = CharFaces
    val mood = moods[face.coerceIn(moods.indices)]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  Back", Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        Text("Change\ncharacter", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp))
        Text("3 ways. Change it any time.", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        CharacterWay(0, mode, Sym.Apps, Pop.Orange, Color.White, "Pick a ready one", "Presets · quick start", defaultBadge = true, onMode)
        CharacterWay(1, mode, Sym.Image, Pop.Ink, Color.White, "Use my photo", "8-bit on your phone · no AI · private", onClick = onMode)
        CharacterWay(2, mode, Sym.AutoAwesome, Pop.Ink, Color.White, "Make it with AI", "Photo redrawn as an 8-bit character", onClick = onMode)
        if (mode == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                moods.forEachIndexed { i, (m, filled) ->
                    val on = i == face
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Color.White else cs.surfaceContainerLowest)
                            .then(if (on) Modifier.border(2.dp, Pop.Ink, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { onFace(i) },
                        contentAlignment = Alignment.Center,
                    ) { BfFace(36.dp, m, filled, color = Pop.Ink) }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLowest, shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
                        BfFace(36.dp, mood.first, mood.second, color = Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("On $deviceName · B/W", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Pop.Orange), contentAlignment = Alignment.Center) {
                        BfFace(32.dp, mood.first, true, color = Pop.Ink)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Connect face", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Text("Same face on the device and the app button.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        PfOrangeButton("Save character", Modifier.fillMaxWidth(), onSave)
        Spacer(Modifier.height(PopNavClearance))
    }
}

@Composable private fun CharacterWay(
    index: Int, mode: Int, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color, iconFg: Color, title: String, sub: String, defaultBadge: Boolean = false,
    onClick: (Int) -> Unit,
) {
    val on = mode == index
    val cs = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().clickable { onClick(index) },
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLowest,
        border = if (on) androidx.compose.foundation.BorderStroke(2.dp, Pop.Orange) else null,
        shadowElevation = if (on) 0.dp else 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Pop.Orange else iconBg),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(22.dp), tint = if (on) Color.White else iconFg) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (defaultBadge) Box(Modifier.clip(CircleShape).background(Pop.Ink).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Default", color = Pop.Yellow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .border(2.dp, if (on) Pop.Orange else cs.outlineVariant, CircleShape)
                    .background(if (on) Pop.Orange else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
