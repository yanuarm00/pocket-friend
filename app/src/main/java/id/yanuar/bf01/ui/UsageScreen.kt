package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.ai.AiPricing
import id.yanuar.bf01.ai.AiUse
import id.yanuar.bf01.ai.UsageReports
import id.yanuar.bf01.svc.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Loads both periods once (cheap: local DB + prefs), so the Week/Month toggle switches instantly. */
internal fun loadUsage(hub: Hub, period: Period): UsageData {
    val since = System.currentTimeMillis() - 31L * 86_400_000L
    val p = hub.prefs
    return UsageSummary.build(period, LocalDate.now(), ZoneId.systemDefault(), hub.db.featureUsesSince(since), hub.db.aiCallsSince(since),
        BatteryDays.days(hub.ctx), hub.ble.info.value?.batt?.takeIf { it >= 0 } ?: p.lastBatt.takeIf { it >= 0 },
        AiPricing.decode(p.aiPriceOverrides), p.idrPerUsd.toDouble())
}

@Composable fun UsageScreen(hub: Hub) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    val rev by hub.revision.collectAsState()
    val data by produceState<UsageData?>(null, period, rev) { value = withContext(Dispatchers.IO) { runCatching { loadUsage(hub, period) }.getOrNull() } }
    val jump = remember { hub.usageJumpToBattery.also { hub.usageJumpToBattery = false } }
    UsageContent(data, hub.prefs.deviceName.ifBlank { "BF" }, period, { period = it }, jumpToBattery = jump)
}

private val BarColors = listOf(Pop.Orange, Color(0xFF2B2D31), Pop.Grey, Pop.Yellow, Color(0xFFB9BDC2), Color(0xFFFFB088))

@Composable fun UsageContent(d: UsageData?, deviceName: String, period: Period, onPeriod: (Period) -> Unit,
                             scroll: ScrollState = rememberScrollState(), jumpToBattery: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    var battY by remember { mutableIntStateOf(-1) }
    LaunchedEffect(jumpToBattery, battY, d) { if (jumpToBattery && battY >= 0 && d != null) scroll.animateScrollTo(battY) }
    Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$deviceName · BF-01", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
        PeriodToggle(period, onPeriod)
        if (d == null) { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }
        Hero(d, deviceName)
        val range = "${d.from.format(DMY)} – ${d.to.format(DMY)}"

        Label("Features used")
        UCard {
            CardTitle("Times used", range)
            if (d.totalUses == 0) Empty("Nothing yet. Use $deviceName and counts show up here.")
            else {
                val max = d.features.maxOf { it.count }.coerceAtLeast(1)
                d.features.forEachIndexed { i, f -> BarRow(featureIcon(f.key), f.label, f.count.toString(), f.count.toFloat() / max, if (i == 0) Pop.Orange else cs.onSurface.copy(alpha = 0.75f)) }
                d.topFeature?.let { Hot(featureIcon(it.key), "${it.label} are used most", "${it.count} of ${d.totalUses} uses") }
            }
        }

        Box(Modifier.onGloballyPositioned { battY = it.positionInParent().y.toInt() }) { Label("Battery") }
        UCard {
            CardTitle("Where it goes", "estimate · ${period.label.lowercase()}")
            if (d.battery.isEmpty()) Empty("Not enough battery data yet. Keep $deviceName connected for a few hours.")
            else {
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    d.battery.forEachIndexed { i, b -> Box(Modifier.weight(b.pct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(BarColors[i % BarColors.size])) }
                }
                d.battery.forEachIndexed { i, b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(BarColors[i % BarColors.size])); Spacer(Modifier.width(8.dp))
                        Text(b.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${b.pct}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                d.hungriest?.let { h -> Hot(Sym.Bolt, "${h.label} uses the most", "About ${h.pct}% of the battery" + (d.perDay?.let { " · ${it.toInt()}% a day in total" } ?: "")) }
            }
        }

        Label("AI usage")
        UCard {
            CardTitle("Requests per day", "${d.aiRequests} ${period.label.lowercase()}")
            if (d.aiRequests == 0) Empty("No AI requests yet.")
            else Trend(d.trend)
        }
        UCard {
            CardTitle("By use", "requests · cost")
            if (d.aiRequests == 0) Empty("Chat, speech to text, voice reply and voice search show up here.")
            else {
                val max = d.uses.maxOf { it.requests }.coerceAtLeast(1)
                d.uses.forEachIndexed { i, u ->
                    BarRow(useIcon(u.use), u.label, u.requests.toString(), u.requests.toFloat() / max, if (i == 0) Pop.Orange else cs.onSurface.copy(alpha = 0.75f),
                        sub = "≈ ${UsageReports.rupiah(u.idr)} estimate")
                }
                d.topUse?.takeIf { it.idr > 0 }?.let { t -> Hot(useIcon(t.use), "${t.label} costs the most",
                    "About ${UsageReports.rupiah(t.idr)}" + if (d.aiIdr > 0 && t.idr * 2 > d.aiIdr) " · over half of the total" else "") }
            }
        }
        UCard {
            CardTitle("By provider", period.label.lowercase())
            if (d.providers.isEmpty()) Empty("No AI requests yet.")
            d.providers.forEach { pr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dark = pr.name == "Grok"
                    Text(pr.name, Modifier.clip(RoundedCornerShape(10.dp)).background(if (dark) Pop.Ink else if (pr.name == "Qwen") Pop.Yellow else cs.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp), color = if (dark) Color.White else if (pr.name == "Qwen") Pop.Ink else cs.onSurface,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pr.requests} requests", style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(if (pr.tokens > 0) "${UsageReports.tokens(pr.tokens)} tokens" else null,
                            if (pr.audioSec >= 60) "${(pr.audioSec / 60).toInt()} min audio" else null).joinToString(" · ").ifBlank { "on this phone" },
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("≈ ${UsageReports.rupiah(pr.idr)}", style = MaterialTheme.typography.titleSmall)
                        Text("estimate", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (d.aiRequests > 0) Text("Total ≈ ${UsageReports.rupiah(d.aiIdr)} · estimate from your price list", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.Lock, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(6.dp))
            Text("Stored on this phone only", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

private val DMY = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@Composable private fun PeriodToggle(p: Period, on: (Period) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        listOf(Period.WEEK to "Week", Period.MONTH to "Month").forEach { (v, l) ->
            val sel = v == p
            Box(Modifier.weight(1f).clip(CircleShape).background(if (sel) cs.inverseSurface else Color.Transparent).clickable { on(v) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (sel) cs.inverseOnSurface else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Hero(d: UsageData, deviceName: String) {
    Box(Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Pop.OrangeDeep, spotColor = Pop.OrangeDeep)
        .clip(RoundedCornerShape(28.dp)).background(Pop.Orange).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(d.period.label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.85f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(d.batteryPct?.toString() ?: "–", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                        Text("%", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
                    }
                    Text(if (d.perDay != null && d.daysLeft != null) "≈ ${daysText(d.daysLeft)} left · ${d.perDay.toInt()}% a day" else "Battery now · days left after a few hours of data",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                FloatingDevice(DeviceArt.Pose.Happy, 76.dp, animate = false)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            HeroLine(d.topFeature?.let { featureIcon(it.key) } ?: Sym.Mic, d.topFeature?.label ?: "No uses yet",
                if (d.totalUses > 0) "top of ${d.totalUses} uses" else "Use $deviceName to see counts")
            HeroLine(Sym.Bolt, d.hungriest?.label ?: "Battery", if (d.hungriest != null) "uses most battery" else "not enough data yet")
            HeroLine(Sym.AutoAwesome, "${d.aiRequests} AI requests", if (d.aiRequests > 0) "≈ ${UsageReports.rupiah(d.aiIdr)} estimate" else "none yet")
        }
    }
}

private fun daysText(d: Double) = if (d >= 1.5) "${Math.round(d)} days" else if (d >= 1) "1 day" else "${(d * 24).toInt().coerceAtLeast(1)} h"

@Composable private fun HeroLine(icon: ImageVector, title: String, sub: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x47963200)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = Color.White) }
    Spacer(Modifier.width(12.dp))
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable private fun Label(t: String) = Text(t.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 6.dp))

@Composable private fun UCard(content: @Composable ColumnScope.() -> Unit) =
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

@Composable private fun CardTitle(t: String, cap: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(t, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
    Text(cap, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun Empty(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable private fun BarRow(icon: ImageVector, label: String, value: String, frac: Float, color: Color, sub: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(color)) }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Highlight note: same outline style in every section (Yanuar, mockup v3). */
@Composable private fun Hot(icon: ImageVector, title: String, sub: String) =
    Row(Modifier.fillMaxWidth().border(2.dp, Pop.Orange, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Pop.Orange); Spacer(Modifier.width(10.dp))
        Column { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

@Composable private fun Trend(bars: List<TrendBar>) {
    val cs = MaterialTheme.colorScheme; val max = bars.maxOf { it.count }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 10) 2.dp else 10.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((b.count.toFloat() / max).coerceAtLeast(0.03f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (b.today) Pop.Orange else cs.onSurface.copy(alpha = 0.7f)))
                }
                Text(b.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun featureIcon(k: String): ImageVector = when (k) {
    Feature.VOICE_NOTE -> Sym.Mic; Feature.ASK -> Sym.Chat; Feature.TODO -> Sym.CheckBox; Feature.SEARCH -> Icons.Default.Search
    Feature.REMINDER -> Sym.Notifications; Feature.WHERES_MINE -> Sym.LocationOn; Feature.SAY_HI -> Sym.Person; else -> Sym.NotificationsActive }
private fun useIcon(u: String): ImageVector = when (u) { AiUse.STT -> Sym.Mic; AiUse.REPLY -> Sym.RecordVoiceOver; AiUse.SEARCH -> Icons.Default.Search; else -> Sym.Chat }
