package id.yanuar.bf01.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.svc.Hub
import java.text.SimpleDateFormat
import java.util.*

/** Greeting bucket + one random uplifting line per app open (mockup v5.13). */
object HomeQuotes {
    val POOL = mapOf(
        "Morning" to listOf("Small steps still count.", "Every big thing you admire started as a small first step.", "Fresh start. Go gently.", "One thing at a time today.",
            "Coffee first, then the world.", "Today has room for you.", "Begin where you are.", "A good day starts small.", "Let's make today kind.", "Easy pace, clear head."),
        "Afternoon" to listOf("Halfway there. Keep going.", "You don't have to finish it all today, just keep moving.", "A short break is progress too.",
            "You're doing better than you think.", "Stretch, sip, carry on.", "Still time to make it good.", "Steady beats fast.", "Little wins add up.",
            "Breathe. Then the next thing.", "Keep the good momentum.", "You're right on time."),
        "Evening" to listOf("Look how far today went.", "Whatever got done today, it was enough. Be proud of that.", "Time to slow it down.", "Done is better than perfect.",
            "Be proud of the small stuff.", "Let the rest wait till tomorrow.", "You showed up today.", "Unwind. You earned it.", "Good work counts, even quiet."),
        "Night" to listOf("Rest is part of the plan.", "Put the phone down soon. Tomorrow loves a rested you.", "Tomorrow can hold the rest.", "Let your mind go quiet.",
            "Sleep well, start fresh.", "Today is done. That's enough.", "Soft lights, slow thoughts.", "You did enough today.", "Close the day kindly."),
    )
    fun bucket(hour: Int) = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..20 -> "Evening"; else -> "Night" }
    fun pick(hour: Int, rnd: Random = Random()) = POOL.getValue(bucket(hour)).let { it[rnd.nextInt(it.size)] }
}

/** Weather line. Uses Open-Meteo (free, no API key) at BF's last saved location; null = nothing to show. */
object HomeWeather {
    @Volatile private var cache: Pair<Long, String>? = null
    fun label(code: Int) = when (code) { 0 -> "Sunny"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Foggy"; in 51..67 -> "Rain"; in 71..77 -> "Snow"
        in 80..82 -> "Showers"; in 95..99 -> "Storm"; else -> "" }
    suspend fun fetch(lat: Double, lon: Double): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        cache?.let { (t, s) -> if (System.currentTimeMillis() - t < 30 * 60_000) return@withContext s }
        if (lat.isNaN()) return@withContext null
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code".format(Locale.US, lat, lon)
            okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                val cur = org.json.JSONObject(r.body!!.string()).getJSONObject("current")
                "${Math.round(cur.getDouble("temperature_2m"))}° · ${label(cur.getInt("weather_code"))}".trimEnd(' ', '·')
            }
        }.getOrNull()?.also { cache = System.currentTimeMillis() to it }
    }
}

fun agoLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((now - ms) / 60_000).coerceAtLeast(0)
    return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 24 * 60 -> "${m / 60} h ago"; m < 48 * 60 -> "yesterday"; else -> "${m / (24 * 60)} days ago" }
}

/** Everything Home shows, so tests can render it without a live Hub. */
data class HomeState(
    val greeting: String, val quote: String, val deviceName: String, val online: Boolean, val connecting: Boolean,
    val battery: String, val network: String, val date: String, val weather: String?,
    val tasks: Int, val reminders: Int, val next: String?,
    val whereLine: String, val hasLocation: Boolean,
    val recentTitle: String?, val recentSub: String?, val recentNew: Boolean,
    val asked: String?, val reminderRang: String?,
)

private val fDate = SimpleDateFormat("EEE, d MMM", Locale.US); private val fHm = SimpleDateFormat("HH:mm", Locale.US)

@Composable fun rememberHomeState(hub: Hub): HomeState {
    val st by hub.ble.state.collectAsState(); val info by hub.ble.info.collectAsState(); val rev by hub.revision.collectAsState()
    val wifi by hub.wifiOnBf.collectAsState(); val ctx = LocalContext.current; val p = hub.prefs
    val now = remember(rev) { System.currentTimeMillis() }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val quote = remember { HomeQuotes.pick(hour) }
    val data = rememberLoaded(hub, "home040", rev) { Triple(hub.db.tasks().filter { !it.done }, hub.db.notes().firstOrNull { it.type == "NOTE" }, hub.db.aiThreads().firstOrNull()) }
    var weather by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(p.lastLat) { weather = HomeWeather.fetch(p.lastLat, p.lastLon) }
    val ready = st == BleManager.State.READY
    val name = p.deviceName.ifBlank { "BF" }
    val tasks = data?.first ?: emptyList()
    val reminders = tasks.filter { it.due > 0 }
    val next = reminders.filter { it.due >= now }.minByOrNull { it.due }
    val rang = reminders.filter { it.due in (now - 6 * 3600_000)..now }.maxByOrNull { it.due }
    val note = data?.second; val thread = data?.third
    val batt = info?.batt?.takeIf { it >= 0 && ready } ?: p.lastBatt.takeIf { it >= 0 }
    val owner = p.cardName.trim().split(" ").firstOrNull().orEmpty().lowercase().replaceFirstChar { it.titlecase() }
    return HomeState(
        greeting = HomeQuotes.bucket(hour) + if (owner.isNotBlank()) ", $owner" else "", quote = quote, deviceName = name,
        online = ready, connecting = (st == BleManager.State.CONNECTING || st == BleManager.State.CONNECTED || st == BleManager.State.SCANNING),
        battery = when { batt == null -> "Battery unknown"; ready && info?.usb == true -> "Charging · $batt%"; ready -> "Battery $batt%"; else -> "$batt%" },
        network = if (wifi) (p.wifi(1).first.takeIf { it.isNotBlank() }?.let { "Hotspot · $it" } ?: p.wifi(0).first.ifBlank { "Wi-Fi" }.let { "Wi-Fi · $it" }) else "Bluetooth · Wi-Fi off",
        date = fDate.format(Date(now)), weather = weather,
        tasks = tasks.size - reminders.size, reminders = reminders.size, next = next?.let { "${it.title} · ${fHm.format(Date(it.due))}" },
        whereLine = if (p.lastLat.isNaN()) "No saved place yet" else "Last seen ${agoLabel(p.lastLocTime, now)}", hasLocation = !p.lastLat.isNaN(),
        recentTitle = note?.let { it.summary.ifBlank { it.transcript }.ifBlank { "Voice note" } }, recentSub = note?.let { "Voice note · ${agoLabel(it.created, now)}" },
        recentNew = note != null && now - note.created < 3600_000,
        asked = thread?.second?.takeIf { it.role != "user" && it.text.trim().endsWith("?") }?.text?.trim(),
        reminderRang = rang?.let { "${it.title} · ${agoLabel(it.due, now)}" },
    )
}

@Composable fun BerandaScreen(hub: Hub, go: (Screen) -> Unit = {}, onStatus: () -> Unit = {}) {
    val s = rememberHomeState(hub); val ctx = LocalContext.current; val p = hub.prefs
    Column(Modifier.verticalScroll(rememberScrollState())) {
        AppUpdateCard()
        HomeContent(s, go = go, onStatus = onStatus, battery = { hub.usageJumpToBattery = true; go(Screen.Usage) }, reconnect = { hub.reconnect() },
            hotspot = { runCatching { ctx.startActivity(Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) } } },
            maps = { if (!p.lastLat.isNaN()) { hub.db.addFeatureUse(id.yanuar.bf01.svc.Feature.WHERES_MINE); openMaps(ctx, p.lastLat, p.lastLon) } })
        if (hub.findPhone.ringing) Button({ hub.findPhone.stop() }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Stop phone ringing") }
    }
}

@Composable fun HomeContent(s: HomeState, animate: Boolean = true, go: (Screen) -> Unit = {}, onStatus: () -> Unit = {},
                            reconnect: () -> Unit = {}, hotspot: () -> Unit = {}, maps: () -> Unit = {}, battery: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme; val dark = cs.background.luminanceLow()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.greeting, style = MaterialTheme.typography.headlineMedium)
                Text(s.quote, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            LedChip(s.online, s.connecting, onStatus)
        }
        Spacer(Modifier.height(2.dp))
        DeviceCard(s, animate, reconnect, hotspot, maps, battery)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1.12f).fillMaxHeight(), Pop.Yellow, Pop.Ink, Sym.Today, "Today",
                "${s.tasks} task${if (s.tasks == 1) "" else "s"} · ${s.reminders} reminder${if (s.reminders == 1) "" else "s"}",
                s.next?.let { "Next: $it" } ?: "Nothing scheduled", Sym.Notifications) { go(Screen.Tugas) }
            Tile(Modifier.weight(1f).fillMaxHeight(), if (dark) Color(0xFF3A3D43) else Pop.Ink, Color.White, Sym.Chat, "Ask ${s.deviceName}", "Ask, plan, find", "Plan my week", Sym.AutoAwesome) { go(Screen.Ngobrol) }
        }
        WhereRow(s, maps)
        if (s.recentTitle != null) {
            Text("Recent on ${s.deviceName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
            Surface(Modifier.fillMaxWidth().clickable { go(Screen.Catatan) }, shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer, shadowElevation = 1.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Sym.Mic, AppTheme.accentColor(), Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.recentTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            if (s.recentNew) { Spacer(Modifier.width(8.dp)); Text("NEW", Modifier.clip(RoundedCornerShape(8.dp)).background(Pop.Yellow).padding(horizontal = 7.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = Pop.Ink) }
                        }
                        Text(s.recentSub.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        if (s.asked != null) BigRow(Sym.Help, "${s.deviceName} asked", "“${s.asked}” · waiting for you", if (dark) Color(0xFF3A3D43) else Pop.Ink, Color.White, dot = true) { go(Screen.AiChats) }
        if (s.reminderRang != null) BigRow(Sym.Notifications, "Reminder rang", s.reminderRang, Pop.Yellow, Pop.Ink) { go(Screen.Tugas) }
        VersionFooter(small = true)
    }
}

private fun Color.luminanceLow() = (red * 0.3f + green * 0.59f + blue * 0.11f) < 0.4f

/** Small LED status chip (top-right): green = connected, amber pulse = connecting, grey = offline. Opens the status sheet. */
@Composable fun LedChip(online: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c = when { online -> Pop.Led; connecting -> Pop.Yellow; else -> Color(0xFFA3A0AE) }
    Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainer).clickable(onClickLabel = "Device status", onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c).then(if (online) Modifier.border(3.dp, c.copy(alpha = 0.25f), CircleShape) else Modifier))
    }
}

@Composable private fun DeviceCard(s: HomeState, animate: Boolean, reconnect: () -> Unit, hotspot: () -> Unit, maps: () -> Unit, battery: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val accent = AppTheme.accentColor()
    val bg = if (s.online) accent else cs.surfaceVariant
    val fg = if (s.online) Color.White else cs.onSurface
    val sub = if (s.online) Color.White else cs.onSurfaceVariant
    // Same height online and offline, so Home doesn't jump when the device drops.
    Surface(Modifier.fillMaxWidth().height(196.dp), shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg) {
        Box(Modifier.fillMaxSize()) {
            FloatingDevice(if (s.online) DeviceArt.Pose.Happy else DeviceArt.Pose.Offline, 132.dp, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), animate)
            Column(Modifier.fillMaxHeight().padding(start = 20.dp, top = 18.dp, bottom = 16.dp, end = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.online) Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.deviceName, style = MaterialTheme.typography.headlineMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    Spacer(Modifier.width(8.dp)); Text("BF-01", style = MaterialTheme.typography.labelLarge, color = sub.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 5.dp))
                } else Text("${s.deviceName} is offline", style = MaterialTheme.typography.titleLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.online) {
                    Box(Modifier.clickable(onClickLabel = "Battery usage", onClick = battery)) { InfoLine(Sym.Bolt, s.battery, sub) }; InfoLine(Sym.Wifi, s.network, sub)
                    Spacer(Modifier.weight(1f))
                    Text(s.date + (s.weather?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = sub)
                } else {
                    InfoLine(Sym.WifiOff, "Last seen · ${s.battery}", sub)
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Reconnect", AppTheme.accentColor(), Color.White, reconnect)
                        Pill("Hotspot", Color.Transparent, fg, hotspot, outline = true)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Can't find ${s.deviceName}? ›", style = MaterialTheme.typography.labelLarge, color = sub, modifier = Modifier.clickable(onClick = maps))
                }
            }
        }
    }
}

@Composable private fun InfoLine(icon: ImageVector, text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(15.dp), tint = color); Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun Pill(text: String, bg: Color, fg: Color, onClick: () -> Unit, outline: Boolean = false) =
    Text(text, Modifier.clip(CircleShape).background(bg).then(if (outline) Modifier.border(1.dp, fg.copy(alpha = 0.3f), CircleShape) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = fg)

@Composable private fun Tile(m: Modifier, bg: Color, fg: Color, icon: ImageVector, title: String, sub: String, foot: String, footIcon: ImageVector, onClick: () -> Unit) =
    Surface(m.clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), color = bg, contentColor = fg) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(footIcon, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text(foot, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }

@Composable private fun WhereRow(s: HomeState, maps: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = AppTheme.accentColor()
    Surface(Modifier.fillMaxWidth().clickable(enabled = s.hasLocation, onClick = maps), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, shadowElevation = 1.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 110.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                // Fake map snippet (roads + park) so it matches v5.13, not a plain fill.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0xFFE8F0E4))
                    drawRect(Color(0xFFD5E4C8), Offset(0f, size.height * 0.15f), androidx.compose.ui.geometry.Size(size.width * 0.45f, size.height * 0.4f))
                    drawRect(Color(0xFFF2E6D4), Offset(size.width * 0.5f, size.height * 0.35f), androidx.compose.ui.geometry.Size(size.width * 0.5f, size.height * 0.55f))
                    val road = Color(0xFFF7F7F5)
                    drawLine(road, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.45f), strokeWidth = 7.dp.toPx())
                    drawLine(road, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.55f, size.height), strokeWidth = 5.dp.toPx())
                    drawLine(Color(0xFFE0D5C6), Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.45f), strokeWidth = 1.5.dp.toPx())
                }
                IconBadge(Sym.LocationOn, accent, Color.White, 36.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Where's mine", style = MaterialTheme.typography.titleMedium)
                Text(s.whereLine, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (s.hasLocation) Text("Open in Maps ↗", style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}

@Composable fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: androidx.compose.ui.unit.Dp = 44.dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f), tint = fg) }

@Composable private fun BigRow(icon: ImageVector, title: String, sub: String, bg: Color, fg: Color, dot: Boolean = false, onClick: () -> Unit) =
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(26.dp), color = bg, contentColor = fg) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, fg.copy(alpha = 0.12f), if (bg == Pop.Yellow) Pop.Ink else Pop.Yellow, 46.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                    if (dot) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(AppTheme.accentColor())) } }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Sym.ChevronRight, null, tint = fg.copy(alpha = 0.7f))
        }
    }
