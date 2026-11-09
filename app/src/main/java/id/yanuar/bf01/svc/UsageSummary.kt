package id.yanuar.bf01.svc

import id.yanuar.bf01.ai.AiCall
import id.yanuar.bf01.ai.AiPricing
import id.yanuar.bf01.ai.AiUse
import id.yanuar.bf01.ai.Price
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** v0.4.0: features counted on the phone for the Usage page. */
object Feature {
    const val VOICE_NOTE = "voice_note"; const val TODO = "todo"; const val ASK = "ask"; const val SEARCH = "search"
    const val REMINDER = "reminder"; const val WHERES_MINE = "wheres_mine"; const val SAY_HI = "say_hi"; const val FIND_PHONE = "find_phone"
    val ALL = listOf(VOICE_NOTE, ASK, TODO, SEARCH, REMINDER, WHERES_MINE, SAY_HI, FIND_PHONE)
    fun label(f: String) = when (f) {
        VOICE_NOTE -> "Voice notes"; TODO -> "To-dos"; ASK -> "Ask AI"; SEARCH -> "Voice search"; REMINDER -> "Reminders on device"
        WHERES_MINE -> "Where's mine"; SAY_HI -> "Say Hi cards"; else -> "Find phone" }
}

enum class Period(val days: Int, val label: String) { WEEK(7, "This week"), MONTH(30, "This month") }

data class FeatureRow(val key: String, val label: String, val count: Int)
data class BatteryPart(val key: String, val label: String, val pct: Int)
data class UseRow(val use: String, val label: String, val requests: Int, val idr: Double)
data class ProviderRow(val name: String, val requests: Int, val tokens: Long, val audioSec: Double, val idr: Double)
data class TrendBar(val label: String, val count: Int, val today: Boolean)

data class UsageData(
    val period: Period, val from: LocalDate, val to: LocalDate,
    /** Battery now, % used per day on average in this period, and days left at that rate (null = not enough data). */
    val batteryPct: Int?, val perDay: Double?, val daysLeft: Double?,
    val features: List<FeatureRow>, val totalUses: Int,
    val battery: List<BatteryPart>,
    val trend: List<TrendBar>, val uses: List<UseRow>, val providers: List<ProviderRow>, val aiRequests: Int, val aiIdr: Double,
) {
    val topFeature get() = features.firstOrNull { it.count > 0 }
    val hungriest get() = battery.firstOrNull()
    val topUse get() = uses.filter { it.requests > 0 }.maxByOrNull { it.idr }
    val empty get() = totalUses == 0 && battery.isEmpty() && aiRequests == 0 && perDay == null
}

object UsageSummary {
    fun useLabel(u: String) = when (u) { AiUse.STT -> "Speech to text"; AiUse.REPLY -> "Voice reply"; AiUse.SEARCH -> "Voice search"; else -> "Chat / Ask AI" }
    private fun partLabel(k: String) = when (k) { "rec" -> "Recording"; "ai" -> "AI chat"; "wifi" -> "Wi-Fi"; "awake" -> "Screen & buttons"; "ble" -> "Bluetooth"; else -> "Standby" }

    fun cost(c: AiCall, prices: Map<String, Price>): Double {
        val p = AiPricing.resolve(c.model, prices)?.takeIf { it.isSet } ?: return 0.0
        return AiPricing.cost(c.inTokens, c.outTokens, if (c.kind == "tts") 0.0 else c.audioSec, p)
    }

    /** Battery split from summed time counters, weighted by modelled current. Empty under 30 min of data. */
    fun batteryParts(d: BatteryDays.Day?): List<BatteryPart> {
        if (d == null || d.up < BatteryStats.MIN_DATA_SEC) return emptyList()
        val raw = listOf("rec" to d.rec * BatteryStats.MA_REC, "ai" to d.ai * BatteryStats.MA_AI, "wifi" to d.wifi * BatteryStats.MA_WIFI,
            "awake" to d.awake * BatteryStats.MA_AWAKE, "ble" to d.ble * BatteryStats.MA_BLE, "idle" to d.up * BatteryStats.MA_BASE)
        val total = raw.sumOf { it.second }.takeIf { it > 0 } ?: return emptyList()
        return raw.map { BatteryPart(it.first, partLabel(it.first), (it.second * 100 / total).roundToInt()) }.filter { it.pct > 0 }.sortedByDescending { it.pct }
    }

    fun build(period: Period, today: LocalDate, zone: ZoneId, features: List<Pair<Long, String>>, calls: List<AiCall>,
              battDays: List<BatteryDays.Day>, batteryPct: Int?, prices: Map<String, Price>, idrPerUsd: Double): UsageData {
        val from = today.minusDays(period.days - 1L)
        fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        fun inP(t: Long) = day(t).let { !it.isBefore(from) && !it.isAfter(today) }
        val f = features.filter { inP(it.first) }.groupingBy { it.second }.eachCount()
        val rows = Feature.ALL.map { FeatureRow(it, Feature.label(it), f[it] ?: 0) }.sortedByDescending { it.count }
        val c = calls.filter { inP(it.time) }
        val byDay = c.groupingBy { day(it.time) }.eachCount()
        val trend = (0 until period.days).map { i -> val d = from.plusDays(i.toLong())
            TrendBar(if (period == Period.WEEK) d.dayOfWeek.name.take(1) else if (i % 7 == 0 || d == today) d.dayOfMonth.toString() else "", byDay[d] ?: 0, d == today) }
        val uses = AiUse.ALL.map { u -> val us = c.filter { it.use == u }; UseRow(u, useLabel(u), us.size, us.sumOf { cost(it, prices) } * idrPerUsd) }
            .sortedByDescending { it.requests }
        val providers = c.groupBy { it.provider }.map { (p, cs) ->
            ProviderRow(when (p) { "xai", "grok" -> "Grok"; "qwen" -> "Qwen"; "phone", "system" -> "Phone"; else -> p.replaceFirstChar { it.uppercase() } }, cs.size,
                cs.filter { it.kind != "tts" }.sumOf { it.inTokens + it.outTokens }, cs.filter { it.kind == "asr" }.sumOf { it.audioSec }, cs.sumOf { cost(it, prices) } * idrPerUsd) }
            .sortedByDescending { it.requests }
        val bd = battDays.filter { val d = LocalDate.parse(it.day); !d.isBefore(from) && !d.isAfter(today) }
        val sum = bd.reduceOrNull { a, b -> a + b }
        val hours = (sum?.up ?: 0) / 3600.0
        // % per day from real drops over the hours BF was on (needs >= 6 h and >= 3 % to mean anything).
        val perDay = if (sum != null && hours >= 6 && sum.drop >= 3) sum.drop / hours * 24 else null
        val left = if (perDay != null && batteryPct != null && perDay > 0) batteryPct / perDay else null
        return UsageData(period, from, today, batteryPct, perDay, left, rows, rows.sumOf { it.count }, batteryParts(sum),
            trend, uses, providers, c.size, uses.sumOf { it.idr })
    }
}
