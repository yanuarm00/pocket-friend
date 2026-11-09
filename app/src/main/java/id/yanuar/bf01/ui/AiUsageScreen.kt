package id.yanuar.bf01.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.ai.*
import id.yanuar.bf01.svc.Hub
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Billing pages. Remaining credit is not readable with a normal API key, so we open the provider's own page. */
const val QWEN_BILLING_URL = "https://modelstudio.console.alibabacloud.com/ap-southeast-1/?tab=costing-balance"
const val XAI_BILLING_URL = "https://console.x.ai/"

/** Last ~2 months of daily sums; the screen and the Settings row both read this. */
internal fun loadAiDays(hub: Hub): List<DayRow> = hub.db.aiUsageDays(System.currentTimeMillis() - 62L * 86_400_000L)

@Composable fun AiUsageScreen(hub: Hub, startOpenPrices: Boolean = false) {
    val p = hub.prefs; val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val days = rememberLoaded(hub, "aiusage", tick) { loadAiDays(hub) } ?: emptyList()
    var overrides by remember { mutableStateOf(AiPricing.decode(p.aiPriceOverrides)) }
    var idr by remember { mutableDoubleStateOf(p.idrPerUsd.toDouble()) }
    val report = remember(days, overrides, idr) { UsageReports.build(days, LocalDate.now(), overrides, idr) }
    var showPrices by rememberSaveable { mutableStateOf(startOpenPrices) }
    var confirmReset by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    SubPage {
        SummaryCard(report)
        ProjectionSection(report)
        TodayVsMonth(report)
        ModelsSection(report)
        PricesSection(hub, report, overrides, idr, showPrices, { showPrices = it },
            { overrides = it; p.aiPriceOverrides = AiPricing.encode(it) }, { idr = it; p.idrPerUsd = it.toFloat() })
        Section("Check balance") {
            Text("Remaining credit can't be read with a normal API key. Open the billing page of each provider instead.", style = MaterialTheme.typography.bodyMedium)
            ActionButton("Alibaba Cloud Model Studio (Qwen)") { openUrl(ctx, QWEN_BILLING_URL) }
            ActionButton("xAI console (Grok)") { openUrl(ctx, XAI_BILLING_URL) }
            Text("xAI: after signing in, open Billing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Report") {
            ActionButton(if (copied) "Copied" else "Copy usage report", primary = true) {
                val text = UsageReports.text(report, appVersion(ctx), LocalDate.now())
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI usage report", text)); copied = true
            }
            ActionButton("Reset usage", icon = Sym.Delete) { confirmReset = true }
            Text("Reset only clears the AI usage list on this phone. It does not touch the audio minutes under Usage & about.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset AI usage?") },
        text = { Text("This deletes all recorded AI calls (tokens and audio seconds) on this phone. Your prices and exchange rate stay.") },
        confirmButton = { TextButton({ confirmReset = false; uiScope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { hub.db.resetAiUsage() }; hub.uiCache.remove("aiusage"); tick++ } }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } })
}

private fun openUrl(ctx: Context, url: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* no browser */ } }

@Composable private fun SummaryCard(r: UsageReport) {
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("THIS MONTH · ESTIMATE", style = MaterialTheme.typography.labelMedium)
            Text(UsageReports.rupiah(r.idr(r.month.usd)), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("≈ ${UsageReports.dollars(r.month.usd)} · ${UsageReports.tokens(r.month.tokens)} tokens", style = MaterialTheme.typography.titleMedium)
            val extra = buildList { if (r.month.audioSec > 0) add(UsageReports.minutes(r.month.audioSec) + " audio"); add("${r.month.calls} calls") }.joinToString(" · ")
            Text(extra, style = MaterialTheme.typography.bodyMedium)
            if (r.month.unpriced > 0) Text("${r.month.unpriced} model(s) have no price yet and are not in the cost.", style = MaterialTheme.typography.bodySmall)
            Text("Estimate from your own calls at list prices. Free quota and promotions are not subtracted.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ProjectionSection(r: UsageReport) = Section("Monthly projection (estimate)") {
    val cs = MaterialTheme.colorScheme
    val qwen = r.projections.firstOrNull { it.provider == "qwen" }
    if (qwen == null) ProjectionRow("qwen", null, r, true)
    r.projections.forEach { ProjectionRow(it.provider, it, r, it.provider == "qwen") }
    Text("Average per day over the days that had at least one AI call (last 30 days), times 30. It needs a few days of data and is not a quote. Use it to judge how much credit to buy.",
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
}

@Composable private fun ProjectionRow(provider: String, p: Projection?, r: UsageReport, highlight: Boolean) {
    val cs = MaterialTheme.colorScheme
    val name = provider.replaceFirstChar { it.uppercase() }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = if (highlight) cs.primaryContainer else cs.surfaceContainerHighest,
        contentColor = if (highlight) cs.onPrimaryContainer else cs.onSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (p == null) Text("No usage recorded yet. Use $name for a few days.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("≈ ${UsageReports.tokens(p.tokens30)} tokens per 30 days" + if (p.audioMin30 > 0) " + ${"%.0f".format(p.audioMin30)} audio min" else "", style = MaterialTheme.typography.bodyLarge)
                Text("≈ ${UsageReports.rupiah(r.idr(p.usd30))} · ${UsageReports.dollars(p.usd30)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Based on ${p.daysCounted} day${if (p.daysCounted == 1) "" else "s"} with data · avg ${UsageReports.tokens(p.avgDailyTokens)} tokens/day", style = MaterialTheme.typography.bodySmall)
                if (!p.reliable) Text("Needs a few more days of data (${p.daysCounted} of ${Projection.MIN_DAYS}). Rough guess for now.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (p.unpriced > 0) Text("${p.unpriced} model(s) without a price are not in the cost.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun TodayVsMonth(r: UsageReport) = Section("Today vs this month") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PeriodCell("Today", r.today, r, Modifier.weight(1f)); PeriodCell("This month", r.month, r, Modifier.weight(1f))
    }
}

@Composable private fun PeriodCell(label: String, t: Totals, r: UsageReport, m: Modifier) = Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(UsageReports.rupiah(r.idr(t.usd)), style = MaterialTheme.typography.titleLarge)
    Text("${UsageReports.dollars(t.usd)} · ${UsageReports.tokens(t.tokens)} tokens", style = MaterialTheme.typography.bodyMedium)
    Text(buildList { if (t.audioSec > 0) add(UsageReports.minutes(t.audioSec)); add("${t.calls} calls") }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ModelsSection(r: UsageReport) = Section("By model · this month") {
    if (r.models.isEmpty()) Text("No AI calls recorded yet. Ask something or record a note and it will show up here.", style = MaterialTheme.typography.bodyMedium)
    r.models.forEachIndexed { i, m ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.model, style = MaterialTheme.typography.titleMedium)
                Text("${m.provider.replaceFirstChar { it.uppercase() }} · ${if (m.kind == "asr") "speech-to-text" else "chat"} · ${m.calls} calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(buildList {
                    if (m.inTokens + m.outTokens > 0) add("in ${UsageReports.tokens(m.inTokens)} · out ${UsageReports.tokens(m.outTokens)}")
                    if (m.audioSec > 0) add(UsageReports.minutes(m.audioSec))
                }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (m.usd != null) { Text(UsageReports.rupiah(r.idr(m.usd)), style = MaterialTheme.typography.titleMedium); Text(UsageReports.dollars(m.usd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else Text("No price", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable private fun PricesSection(hub: Hub, r: UsageReport, overrides: Map<String, Price>, idr: Double, open: Boolean, setOpen: (Boolean) -> Unit,
                                       setOverrides: (Map<String, Price>) -> Unit, setIdr: (Double) -> Unit) {
    val p = hub.prefs
    var resetTick by remember { mutableIntStateOf(0) }
    Section("Prices (estimate)") {
        Row(Modifier.fillMaxWidth().clickable { setOpen(!open) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Prices & exchange rate", style = MaterialTheme.typography.titleMedium)
                Text("1 USD = ${UsageReports.rupiah(idr).removePrefix("Rp ")} IDR · tap to ${if (open) "close" else "edit"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Sym.ExpandMore, null, Modifier.rotate(if (open) 180f else 0f))
        }
        if (open) key(resetTick) {
            var idrText by rememberSaveable { mutableStateOf("%.0f".format(java.util.Locale.US, idr)) }
            OutlinedTextField(idrText, { t -> idrText = t.filter(Char::isDigit).take(7); idrText.toDoubleOrNull()?.takeIf { it > 0 }?.let(setIdr) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("IDR per 1 USD") }, supportingText = { Text("Default ${AiPricing.DEFAULT_IDR.toLong()}, around the Bank Indonesia rate on 26 Oct 2026") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large)
            val models = (listOf(p.qwenModel, p.qwenAsrModel, p.chatModel, XAI_STT_MODEL) + r.models.map { it.model }).distinct()
            var current = overrides
            models.forEach { model ->
                val price = AiPricing.resolve(model, current) ?: Price(0.0, 0.0, 0.0)
                val perMin = (AiPricing.resolve(model) ?: price).perMin > 0
                PriceRow(model, price, perMin) { np -> current = current + (model to np); setOverrides(current) }
            }
            Text("Prices are USD. Chat and token-priced speech: per 1M tokens. Other speech: per minute. Models you have not used yet use built-in defaults.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Defaults: ${AiPricing.SOURCES}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton("Reset prices to defaults", icon = Sym.RestartAlt) { setOverrides(emptyMap()); setIdr(AiPricing.DEFAULT_IDR); resetTick++ }
        }
    }
}

@Composable private fun PriceRow(model: String, price: Price, perMin: Boolean, change: (Price) -> Unit) {
    fun f(v: Double) = if (v == 0.0) "" else java.math.BigDecimal(v).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()
    var a by rememberSaveable(model) { mutableStateOf(f(if (perMin) price.perMin else price.inPerM)) }
    var b by rememberSaveable(model) { mutableStateOf(f(price.outPerM)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(model, style = MaterialTheme.typography.titleSmall)
        val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (perMin) OutlinedTextField(a, { a = it; change(Price(0.0, 0.0, num(it))) }, Modifier.weight(1f), singleLine = true, label = { Text("USD per minute") }, keyboardOptions = kb, shape = MaterialTheme.shapes.large)
            else {
                OutlinedTextField(a, { a = it; change(Price(num(it), num(b), 0.0)) }, Modifier.weight(1f), singleLine = true, label = { Text("In / 1M") }, keyboardOptions = kb, shape = MaterialTheme.shapes.large)
                OutlinedTextField(b, { b = it; change(Price(num(a), num(it), 0.0)) }, Modifier.weight(1f), singleLine = true, label = { Text("Out / 1M") }, keyboardOptions = kb, shape = MaterialTheme.shapes.large)
            }
        }
    }
}
