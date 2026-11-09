package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Card22 = RoundedCornerShape(22.dp)

/* ---------- Onboarding extras ---------- */
@Composable fun OnboardSearching(devArt: @Composable () -> Unit = {
    Box(Modifier.size(160.dp).clip(RoundedCornerShape(36.dp)).background(Pop.Mist), contentAlignment = Alignment.Center) {
        Icon(Sym.DeveloperBoard, null, Modifier.size(72.dp), tint = Pop.Ink) }
}, onCancel: () -> Unit = {}) =
    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(40.dp))
        Text("Turn on your BF", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text("Hold the power button until the screen lights up.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp)); devArt(); Spacer(Modifier.weight(1f))
        PfOrangeButton("Searching…", Modifier.fillMaxWidth()) {}
        TextButton(onCancel) { Text("Cancel", color = Pop.Orange) }
    }

@Composable fun OnboardPairCode(code: String, signal: String, onPair: () -> Unit, onBack: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Found one!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Sym.Bluetooth, Pop.Yellow, Pop.Ink, 48.dp); Spacer(Modifier.width(12.dp))
                Column { Text("BF-01", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(signal, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text("Pairing code", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            code.take(3).padEnd(3).forEach { c ->
                Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(cardColor()), contentAlignment = Alignment.Center) {
                    Text(c.toString().trim().ifBlank { "·" }, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) }
            }
        }
        Text("Type the code shown on BF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Pair", Modifier.fillMaxWidth(), onPair)
        TextButton(onBack) { Text("Back", color = Pop.Orange) }
    }

@Composable fun OnboardCharacter(selected: Int, onSelect: (Int) -> Unit, onContinue: () -> Unit,
                                 onPhoto: () -> Unit = {}, onAi: () -> Unit = {},
                                 faces: List<String> = listOf("☺", "☻", "◉", "◈", "◇")) =
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick a character", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        listOf(0 to "Pick a ready one", 1 to "Use my photo", 2 to "Make it with AI").forEach { (i, label) ->
            val on = selected / 10 == i
            Surface(Modifier.fillMaxWidth().clickable {
                onSelect(i * 10 + selected % 10)
                when (i) { 1 -> onPhoto(); 2 -> onAi(); else -> {} }
            }, shape = Card22, color = cardColor(),
                border = if (on) androidx.compose.foundation.BorderStroke(2.dp, Pop.Orange) else null) {
                Text(label, Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
        }
        if (selected / 10 == 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            faces.forEachIndexed { i, f ->
                val on = selected % 10 == i
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Pop.Yellow else cardColor())
                    .clickable { onSelect(i) }, contentAlignment = Alignment.Center) { Text(f, fontSize = 22.sp) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (selected / 10 == 0) PfOrangeButton("Continue", Modifier.fillMaxWidth(), onContinue)
    }

@Composable fun OnboardWifiPerms(wifiLabel: String, bt: Boolean, notif: Boolean, mic: Boolean,
                                 onWifi: () -> Unit, onToggle: (String, Boolean) -> Unit, onContinue: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect & allow", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Text("Wi-Fi for BF", style = MaterialTheme.typography.titleSmall)
        Surface(Modifier.fillMaxWidth().clickable(onClick = onWifi), shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Wifi, null); Spacer(Modifier.width(12.dp))
                Text(wifiLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Sym.ChevronRight, null) }
        }
        Text("Permissions", style = MaterialTheme.typography.titleSmall)
        listOf("Bluetooth" to bt, "Notifications" to notif, "Microphone" to mic).forEach { (label, on) ->
            Surface(shape = Card22, color = cardColor()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(on, { onToggle(label, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange)) }
            }
        }
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Continue", Modifier.fillMaxWidth(), onContinue)
    }

/* ---------- To-do edit + repeat ---------- */
@Composable fun TodoEditContent(title: String, onTitle: (String) -> Unit, dueLabel: String, timeLabel: String,
                                remindLabel: String, repeatLabel: String, showOnDev: Boolean, devName: String,
                                onDue: () -> Unit, onTime: () -> Unit, onRemind: () -> Unit, onRepeat: () -> Unit,
                                onShow: (Boolean) -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✕", Modifier.clickable(onClick = onClose).padding(8.dp), fontWeight = FontWeight.Bold)
            Text("Edit to-do", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
            Text("Save", Modifier.clickable(onClick = onSave).padding(8.dp), color = Pop.Orange, fontWeight = FontWeight.Bold)
        }
        Text("Title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().border(2.dp, Pop.Orange, RoundedCornerShape(16.dp)).padding(14.dp)) {
            BasicTextField(title, onTitle, singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Pop.Orange), modifier = Modifier.fillMaxWidth())
        }
        data class Row(val icon: ImageVector, val lab: String, val value: String, val click: () -> Unit)
        listOf(
            Row(Sym.CalendarToday, "Due date", dueLabel, onDue),
            Row(Sym.Today, "Time", timeLabel, onTime),
            Row(Sym.Notifications, "Reminder", remindLabel, onRemind),
            Row(Sym.Refresh, "Repeat", repeatLabel, onRepeat),
        ).forEach { r ->
            Surface(Modifier.fillMaxWidth().clickable(onClick = r.click), shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(r.icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text(r.lab, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(r.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Sym.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Surface(shape = Card22, color = cardColor()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show on $devName", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Keeps this to-do on the device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(showOnDev, onShow, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange))
            }
        }
        TextButton(onDelete, Modifier.fillMaxWidth()) { Text("Delete to-do", color = Pop.Red, fontWeight = FontWeight.Bold) }
    }
}

@Composable fun TodoRepeatSheet(current: String, days: Set<Int>, onPick: (String) -> Unit, onDays: (Set<Int>) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val opts = listOf("never" to "Doesn't repeat", "daily" to "Every day", "weekdays" to "Weekdays", "custom" to "On these days", "monthly" to "Every month")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(MaterialTheme.colorScheme.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Repeat", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        opts.forEach { (k, lab) ->
            val on = current == k
            Row(Modifier.fillMaxWidth().clip(Card22).background(if (on) Pop.Yellow.copy(alpha = 0.5f) else cardColor()).clickable { onPick(k) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(lab, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (on) Icon(Sym.CheckCircle, null, tint = Pop.Orange)
            }
        }
        if (current == "custom") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M","T","W","T","F","S","S").forEachIndexed { i, d ->
                val on = i in days
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) Pop.Ink else cardColor()).clickable {
                    onDays(if (on) days - i else days + i) }, contentAlignment = Alignment.Center) {
                    Text(d, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
            }
        }
        PfOrangeButton("Save", Modifier.fillMaxWidth(), onSave)
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/* ---------- Privacy / battery / notifications / help / theme ---------- */
@Composable fun PrivacyContent(notes: Int, todos: Int, chats: Int, mb: String, keepAudio: Boolean, autoDel: Boolean,
                               onKeep: (Boolean) -> Unit, onAuto: (Boolean) -> Unit, onExport: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Privacy & data", "What stays on this phone.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("$notes" to "voice notes", "$todos" to "to-dos", "$chats" to "chats", mb to "total").forEach { (n, l) ->
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), color = cardColor()) {
                    Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(n, fontWeight = FontWeight.ExtraBold); Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        SectionCap("Your data")
        Surface(Modifier.fillMaxWidth().clickable(onClick = onExport), shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Share, null); Spacer(Modifier.width(12.dp)); Text("Export my data", Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(Sym.ChevronRight, null) }
        }
        Surface(Modifier.fillMaxWidth().clickable(onClick = onClear), shape = Card22, color = cardColor()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.DeleteSweep, null, tint = Pop.Red); Spacer(Modifier.width(12.dp)); Text("Clear data", Modifier.weight(1f), color = Pop.Red, fontWeight = FontWeight.Bold) }
        }
        SectionCap("Controls")
        Surface(shape = Card22, color = cardColor()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep audio after transcribing", Modifier.weight(1f)); Switch(keepAudio, onKeep, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange)) }
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-delete old chats", Modifier.weight(1f)); Switch(autoDel, onAuto, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange)) }
            }
        }
    }
}

@Composable fun BatteryReportContent(
    pct: Int, charging: Boolean, hoursLeft: String?, features: List<Pair<String, Int>>,
    points: List<Pair<Long, Int>> = emptyList(), accent: Color = Pop.Orange,
    deviceName: String = "Bobo", example: Boolean = true,
    tip: String = "Tip: refresh the screen less often",
    sleepLine: String = "Last night 23:00–06:30 used 4%",
    sleepSub: String = "Sleep mode on · normal drain",
    onBack: (() -> Unit)? = null,
    onCopy: () -> Unit = {}, onReset: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val barColors = listOf(accent, Pop.Ink, Color(0xFF8B9098), Color(0xFFD0D3D8))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(cardColor()).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Sym.ArrowBack, "Back", Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Battery", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text("$deviceName · BF-01", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (example) {
                Text("Example data", Modifier.clip(CircleShape).background(Color(0xFFE9EAEC)).padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Pop.Ink)
            }
        }
        Surface(shape = Card22, color = accent, contentColor = Color.White, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingDevice(DeviceArt.Pose.Happy, 64.dp, animate = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$pct%", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (charging) {
                            Icon(Sym.Bolt, null, Modifier.size(14.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (charging) "Charging · full in 35 min" else "On battery", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Time left ≈\n${hoursLeft ?: "…"} at\nnormal use",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp),
                    textAlign = TextAlign.End,
                )
            }
        }
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            BatteryGraph(if (points.size >= 2) points else emptyList(), Modifier.padding(14.dp), accent = accent)
        }
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
                    Icon(Sym.Bedtime, null, Modifier.size(22.dp), tint = Pop.Yellow)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sleepLine, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(sleepSub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("Good", Modifier.clip(CircleShape).background(Pop.Led.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1B8A3E))
            }
        }
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Usage by feature", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("since full charge", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                features.forEachIndexed { i, (name, pctF) ->
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Row { Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Text("$pctF%", fontWeight = FontWeight.Bold) }
                        LinearProgressIndicator(
                            { pctF / 100f },
                            Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                            color = barColors.getOrElse(i) { accent },
                            trackColor = Color(0xFFE9EAEC),
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth().clickable(onClick = onCopy), shape = Card22, color = Pop.Yellow.copy(alpha = 0.7f), contentColor = Pop.Ink) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Lightbulb, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tip, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Could add about 1 h a day", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Sym.ChevronRight, null)
            }
        }
        // Keep copy/reset reachable but compact (design has no room for big buttons)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onReset) { Text("Reset stats", color = accent) }
            TextButton(onCopy) { Text("Copy report", color = accent, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable fun NotificationsContent(devName: String, phone: List<Triple<String, String, Boolean>>, onPhone: (Int, Boolean) -> Unit,
                                     device: List<Triple<String, String, Boolean>>, onDevice: (Int, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Notifications", "Phone and $devName.")
        SectionCap("On your phone")
        phone.forEachIndexed { i, (t, s, on) -> NotifRow(t, s, on) { onPhone(i, it) } }
        SectionCap("On $devName")
        device.forEachIndexed { i, (t, s, on) -> NotifRow(t, s, on) { onDevice(i, it) } }
        SectionCap("Lock screen preview")
        LockscreenReminderPreview(devName)
    }
}

@Composable private fun NotifRow(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) =
    Surface(shape = Card22, color = cardColor()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = Pop.Orange))
        }
    }

@Composable fun ConnHelpContent(devName: String, lastSeen: String, onReconnect: () -> Unit, onRing: () -> Unit, onMaps: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("$devName isn't connecting", "Try these in order.")
        listOf("1. Nearby?" to "Keep the phone within a few metres.",
            "2. Charged?" to "Battery above 20% or on USB.",
            "3. Restart" to "Hold power on $devName for 5 seconds.",
            "4. Bluetooth" to "Toggle phone Bluetooth off and on.").forEach { (t, s) ->
            Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Text(t, fontWeight = FontWeight.Bold); Text(s, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        PfOrangeButton("Reconnect", Modifier.fillMaxWidth(), onReconnect)
        Button(onRing, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(Pop.Ink, Color.White)) {
            Text("Ring $devName", fontWeight = FontWeight.Bold) }
        Text("Last seen: $lastSeen", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onMaps))
    }
}

@Composable fun ThemeBoardContent(mode: String, accent: String, onMode: (String) -> Unit, onAccent: (String) -> Unit) {
    val accents = listOf("orange" to Pop.Orange, "blue" to Color(0xFF3B82F6), "lime" to Color(0xFF84CC16), "sage" to Color(0xFF86A789), "pink" to Color(0xFFEC4899))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Theme", "Light, dark, or match the phone.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                PfChip(l, mode == k) { onMode(k) } }
        }
        SectionCap("Accent")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accents.forEach { (k, c) ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(c).border(if (accent == k) 3.dp else 0.dp, Pop.Ink, CircleShape)
                    .clickable { onAccent(k) })
            }
        }
        Text("Accent recolours the Home device card and primary buttons. Yellow tiles stay brand.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
