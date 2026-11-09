package id.yanuar.bf01.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetCard = RoundedCornerShape(22.dp)

/** Accent colours from Prefs.accent — recolour Home device card and primary chips. */
object Accents {
    val map = mapOf(
        "orange" to Pop.Orange,
        "blue" to Color(0xFF3B82F6),
        "lime" to Color(0xFF84CC16),
        "sage" to Color(0xFF6B8F71),
        "pink" to Color(0xFFEC4899),
    )
    fun color(key: String) = map[key] ?: Pop.Orange
}

/* ---- Battery 24h graph ---- */
@Composable fun BatteryGraph(
    points: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier,
    accent: Color = Pop.Orange,
    sleepFrac: ClosedFloatingPointRange<Float> = 0f..0.28f,
) {
    val cs = MaterialTheme.colorScheme
    val axis = listOf("23:00", "05:00", "11:00", "now")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Last 24 hours", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("23:00 → now", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val padL = 4.dp.toPx(); val padR = 4.dp.toPx(); val padT = 8.dp.toPx(); val padB = 4.dp.toPx()
                val w = size.width - padL - padR; val h = size.height - padT - padB
                val sx = padL + sleepFrac.start * w
                val sw = (sleepFrac.endInclusive - sleepFrac.start) * w
                drawRect(Color(0xFFE8E9EC), Offset(sx, padT), androidx.compose.ui.geometry.Size(sw, h))
                val y20 = padT + h * (1f - 0.20f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                drawLine(Color(0xFFB0B4BA), Offset(padL, y20), Offset(padL + w, y20), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                val pts = if (points.size >= 2) points else emptyList()
                if (pts.size >= 2) {
                    val t0 = pts.first().first; val span = (pts.last().first - t0).coerceAtLeast(1L).toFloat()
                    val path = Path()
                    pts.forEachIndexed { i, (tm, pct) ->
                        val x = padL + (tm - t0) / span * w
                        val y = padT + h * (1f - pct.coerceIn(0, 100) / 100f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, accent, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    val last = pts.last()
                    val lx = padL + (last.first - t0) / span * w
                    val ly = padT + h * (1f - last.second.coerceIn(0, 100) / 100f)
                    drawCircle(accent, 5.dp.toPx(), Offset(lx, ly))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(lx, ly))
                }
            }
            Text("Sleep", Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF7A7E86))
            Text("20%", Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA0A6))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            axis.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
        }
    }
}

/* ---- Note share sheet ---- */
@Composable fun NoteShareSheet(
    title: String, hasAudio: Boolean, mode: Int, onMode: (Int) -> Unit,
    onShareApp: (String) -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit = {},
) {
    val modes = listOf("Text", "Audio", "Text + audio")
    val targets = listOf(
        Triple("Messages", Sym.Chat, Color(0xFF3B82F6)),
        Triple("Mail", Sym.Email, Pop.Orange),
        Triple("Notes app", Sym.Notes, Pop.Yellow),
        Triple("Files", Sym.FolderOpen, Color(0xFF6B8F71)),
        Triple("Nearby", Sym.Broadcast, Pop.Ink),
        Triple("Copy", Sym.ContentCopy, Color(0xFFD1D5DB)),
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Surface(shape = RoundedCornerShape(18.dp), color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Pop.Orange), contentAlignment = Alignment.Center) {
                    Icon(Sym.Mic, null, Modifier.size(24.dp), tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    Text("Text · summary + transcript", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Share as", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEachIndexed { i, lab ->
                val enabled = i == 0 || hasAudio
                val on = mode == i && enabled
                Text(
                    lab,
                    Modifier.clip(CircleShape)
                        .background(if (on) Pop.Ink else Color(0xFFE9EAEC))
                        .clickable(enabled = enabled) { onMode(i) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (on) Color.White else Pop.Ink,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            targets.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (lab, ic, bg) ->
                        val fg = if (bg == Pop.Yellow || bg == Color(0xFFD1D5DB) || lab == "Notes app") Pop.Ink else Color.White
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(96.dp).clickable {
                                if (lab == "Copy") onCopy() else onShareApp(lab)
                            },
                        ) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                                Icon(ic, lab, Modifier.size(28.dp), tint = fg)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(lab, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/* ---- Lockscreen reminder preview ---- */
@Composable fun LockscreenReminderPreview(devName: String, title: String = "share the 3 ideas", time: String = "19:00") {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1E21)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            time, color = Color.White,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF2B2D31), contentColor = Color.White) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pocket Friend", style = MaterialTheme.typography.labelLarge, color = Pop.Orange)
                Text("Reminder · $title", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Done", "Snooze 1 h", "Open").forEach { lab ->
                        Text(
                            lab,
                            Modifier.clip(CircleShape).background(Color(0xFF3A3D43)).padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF2B2D31), contentColor = Color.White) {
            Column(Modifier.padding(14.dp)) {
                Text(devName, style = MaterialTheme.typography.labelLarge, color = Pop.Yellow)
                Text("Want me to save this? Tap to answer.", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/* ---- Character photo / AI flows ---- */
@Composable fun CharacterPhotoFlow(onPick: () -> Unit, onContinue: () -> Unit, onBack: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Use my photo", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Text("Pick a clear face photo. BF shows an 8-bit version.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)).background(Pop.Mist).clickable(onClick = onPick), contentAlignment = Alignment.Center) {
            Icon(Sym.Image, "Pick photo", Modifier.size(48.dp), tint = Pop.Ink)
        }
        Text("Tap to choose a photo", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Continue", Modifier.fillMaxWidth(), onContinue)
        TextButton(onBack) { Text("Back", color = Pop.Orange) }
    }

@Composable fun CharacterAiFlow(triesLeft: Int, face: String, onTry: () -> Unit, onUse: () -> Unit, onBack: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Make it with AI", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)).background(Pop.Mist), contentAlignment = Alignment.Center) {
                    Icon(Sym.Person, null, Modifier.size(40.dp))
                }
                Text("Your photo", style = MaterialTheme.typography.labelMedium)
            }
            Text("→", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
                    Text(face, fontSize = 36.sp)
                }
                Text("AI 8-bit", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("$triesLeft of 3 tries left", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onTry, enabled = triesLeft > 0) { Text("Try again") }
        Surface(shape = RoundedCornerShape(16.dp), color = Pop.Yellow.copy(alpha = 0.55f), contentColor = Pop.Ink) {
            Text(
                "Your photo is sent to the AI service to make this face, then deleted. Nothing is kept on our servers.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Use this face", Modifier.fillMaxWidth(), onUse)
        TextButton(onBack) { Text("Back", color = Pop.Orange) }
    }

/** Bolder waveform bars for note rows (design: thick rounded strokes). */
@Composable fun WaveBarsBold(seed: String, color: Color, modifier: Modifier = Modifier) =
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        var h = seed.hashCode()
        repeat(12) {
            h = h * 1103515245 + 12345
            val f = 0.35f + ((h ushr 16) and 0xff) / 255f * 0.65f
            Box(Modifier.width(4.dp).fillMaxHeight(f).clip(CircleShape).background(color))
        }
    }

/** Device sheet (design 02): big face, connected pill, 3 stats, tweaks + find. */
@Composable fun DeviceSheetContent(
    name: String, online: Boolean, wifi: String, batt: String, signal: String, synced: String,
    accent: Color = Pop.Orange, onTweaks: () -> Unit = {}, onFind: () -> Unit = {}, onDismiss: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        FloatingDevice(if (online) DeviceArt.Pose.Happy else DeviceArt.Pose.Offline, 120.dp, animate = false)
        Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Surface(
            shape = CircleShape,
            color = if (online) accent else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (online) Color.White else MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                if (online) "Connected · Wi-Fi “$wifi”" else "Offline",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(batt to "Battery", signal to "Signal", synced to "Synced").forEach { (v, l) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(Modifier.fillMaxWidth().clickable(onClick = onTweaks), shape = SheetCard, color = cardColor()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.LightMode, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tweaks", fontWeight = FontWeight.Bold)
                    Text("Sleep / wake times", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Sym.ChevronRight, null)
            }
        }
        Surface(Modifier.fillMaxWidth().clickable(onClick = onFind), shape = SheetCard, color = cardColor()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.LocationOn, null); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Can't find $name?", fontWeight = FontWeight.Bold)
                    Text("Ring or open the map", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Sym.ChevronRight, null)
            }
        }
        TextButton(onDismiss) { Text("Close") }
    }
}
