package id.yanuar.bf01.ai

import org.json.JSONObject
import java.time.LocalDate

/** One AI call that reported its own usage. kind = "chat", "asr" or "tts". Audio seconds for speech-to-text and voice reply.
 *  For "tts" inTokens holds the number of characters sent (TTS is billed per character).
 *  use = what it was for (see [AiUse]); empty for calls logged before v6, read as [AiUse.of]. */
data class AiCall(val time: Long, val provider: String, val model: String, val kind: String,
                  val inTokens: Long, val outTokens: Long, val audioSec: Double, val use: String = "")

/** What an AI call was for. Set around a block with `withContext(AiUse.Ctx(AiUse.SEARCH)) { ... }`; it follows the coroutine across threads. */
object AiUse {
    const val ASK = "ask"; const val STT = "stt"; const val REPLY = "reply"; const val SEARCH = "search"
    val ALL = listOf(ASK, STT, REPLY, SEARCH)
    internal val current = ThreadLocal<String?>()
    class Ctx(private val use: String) : kotlinx.coroutines.ThreadContextElement<String?> {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<Ctx>
        override val key get() = Key
        override fun updateThreadContext(context: kotlin.coroutines.CoroutineContext): String? { val old = current.get(); current.set(use); return old }
        override fun restoreThreadContext(context: kotlin.coroutines.CoroutineContext, oldState: String?) = current.set(oldState)
    }
    /** Stored use, or the default for its kind (old rows have none). */
    fun of(use: String?, kind: String): String = use?.takeIf { it.isNotBlank() } ?: when (kind) { "asr" -> STT; "tts" -> REPLY; else -> ASK }
    fun now(kind: String): String = of(current.get(), kind)
}

/** What a response said it used. Any field the API does not report stays 0. */
data class Reported(val inTokens: Long, val outTokens: Long, val seconds: Double)

/**
 * Reads usage from API responses. Shapes seen in the official docs (Model Studio, xAI):
 *  - OpenAI style: usage.prompt_tokens / completion_tokens (Qwen and Grok chat, Qwen ASR compatible mode, which also has usage.seconds)
 *  - DashScope native: usage.input_tokens / output_tokens, ASR native has usage.seconds
 *  - xAI speech-to-text: top-level "duration" in seconds, no tokens
 */
object UsageParse {
    fun parse(res: String): Reported? {
        val j = try { JSONObject(res) } catch (e: Exception) { return null }
        val u = j.optJSONObject("usage")
        var inT = 0L; var outT = 0L; var sec = 0.0
        if (u != null) {
            inT = u.optLong("prompt_tokens", u.optLong("input_tokens", 0))
            outT = u.optLong("completion_tokens", u.optLong("output_tokens", 0))
            sec = u.optDouble("seconds", u.optDouble("audio_seconds", u.optDouble("duration", 0.0)))
        }
        if (sec.isNaN()) sec = 0.0
        if (sec == 0.0 && j.has("duration")) sec = j.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        if (inT == 0L && outT == 0L && sec == 0.0) return null
        return Reported(inT, outT, sec)
    }
    /** The model name the server says it ran, when it says one (used for "auto"). */
    fun model(res: String): String? = try { JSONObject(res).optString("model").takeIf { it.isNotBlank() } } catch (e: Exception) { null }
}

/** Where recorded calls go. Set once by Hub to the local database; null in tests that do not need it. */
object UsageLog {
    @Volatile var sink: ((AiCall) -> Unit)? = null
    /** Never throws: a usage record must not break an AI reply. */
    fun record(provider: String, requestedModel: String, kind: String, res: ByteArray, fallbackSec: Double = 0.0, now: Long = System.currentTimeMillis()) {
        val s = sink ?: return
        try {
            val text = String(res)
            val r = UsageParse.parse(text)
            if (r == null && !(kind == "asr" && fallbackSec > 0)) return
            val model = if (requestedModel == "auto") UsageParse.model(text) ?: requestedModel else requestedModel
            val sec = if (kind == "asr") (r?.seconds?.takeIf { it > 0 } ?: fallbackSec) else 0.0
            s(AiCall(now, provider.lowercase(), model, kind, r?.inTokens ?: 0, r?.outTokens ?: 0, sec, AiUse.now(kind)))
        } catch (e: Exception) { /* ignore */ }
    }
    /** One voice reply. Phone voice is free (provider "phone"); xAI TTS is billed per character. */
    fun tts(provider: String, model: String, chars: Int, audioSec: Double, now: Long = System.currentTimeMillis()) {
        val s = sink ?: return
        try { s(AiCall(now, provider, model, "tts", chars.toLong(), 0, audioSec, AiUse.REPLY)) } catch (e: Exception) { /* ignore */ }
    }
}

/** What to record for one HTTP call: provider/model/kind, and the audio length we sent (used if the response omits it). */
data class UsageTag(val provider: String, val model: String, val kind: String, val fallbackSec: Double = 0.0)

/** Summed usage for one day, model and kind (what the database returns). day = yyyy-MM-dd in local time. */
data class DayRow(val day: String, val provider: String, val model: String, val kind: String,
                  val calls: Int, val inTokens: Long, val outTokens: Long, val audioSec: Double)

/** USD per 1M tokens (in/out) and USD per minute of audio. 0 = does not apply. */
data class Price(val inPerM: Double, val outPerM: Double, val perMin: Double) {
    val isSet get() = inPerM > 0 || outPerM > 0 || perMin > 0
}

object AiPricing {
    const val DEFAULT_IDR = 18100.0
    /** Checked 27 Oct 2026. Alibaba Cloud Model Studio: Singapore (International) list prices, non-thinking. xAI: docs.x.ai/developers/models (under 200k prompt tokens). Free quota and promotions are not applied. */
    val DEFAULTS: Map<String, Price> = linkedMapOf(
        "qwen-flash-character" to Price(0.05, 0.40, 0.0),
        "qwen-plus-character" to Price(0.50, 1.40, 0.0),
        "qwen3.8-flash" to Price(0.15, 0.47, 0.0),
        "qwen3.7-plus" to Price(0.40, 1.60, 0.0),
        "qwen3.8-max" to Price(2.0, 6.0, 0.0),
        "qwen3.7-max" to Price(2.5, 7.5, 0.0),
        "qwen3.6-flash" to Price(0.25, 1.50, 0.0),
        "qwen-flash" to Price(0.05, 0.40, 0.0),
        "qwen-plus" to Price(0.40, 1.20, 0.0),
        "qwen-max" to Price(1.60, 6.40, 0.0),
        "qwen-audio-3.1-asr-flash" to Price(0.15, 0.47, 0.0),
        "qwen-audio-3.0-asr-flash" to Price(0.0, 0.0, 0.000035 * 60),
        "qwen3-asr-flash" to Price(0.0, 0.0, 0.000035 * 60),
        "grok-4.3" to Price(1.25, 2.50, 0.0),
        "grok-voice-transcribe-2.0" to Price(0.0, 0.0, 0.10 / 60),
        // v0.4.0: voice reply. Billed per input character: inTokens holds characters. docs.x.ai/developers/models/text-to-speech, checked 4 Nov 2026.
        "grok-tts" to Price(15.0, 0.0, 0.0),
    )
    const val SOURCES = "Alibaba Cloud Model Studio pricing (alibabacloud.com/help/en/model-studio/model-pricing, Singapore list prices) and xAI models page (docs.x.ai/developers/models), checked 27 Oct 2026."

    /** Edited prices win. Then an exact default. Then the longest default that the name starts with (dated snapshots such as qwen-plus-2025-12-01). */
    fun resolve(model: String, overrides: Map<String, Price> = emptyMap()): Price? {
        overrides[model]?.let { return it }
        DEFAULTS[model]?.let { return it }
        val best = (DEFAULTS.keys + overrides.keys).filter { model.startsWith("$it-") }.maxByOrNull { it.length } ?: return null
        return overrides[best] ?: DEFAULTS[best]
    }
    fun cost(inTokens: Long, outTokens: Long, audioSec: Double, p: Price): Double =
        inTokens * p.inPerM / 1e6 + outTokens * p.outPerM / 1e6 + audioSec / 60.0 * p.perMin

    fun encode(m: Map<String, Price>): String = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, org.json.JSONArray().put(v.inPerM).put(v.outPerM).put(v.perMin)) } }.toString()
    fun decode(s: String?): Map<String, Price> = try {
        val o = JSONObject(s ?: "{}"); o.keys().asSequence().associateWith { k -> val a = o.getJSONArray(k); Price(a.optDouble(0, 0.0), a.optDouble(1, 0.0), a.optDouble(2, 0.0)) }
    } catch (e: Exception) { emptyMap() }
}

data class Totals(val tokens: Long, val audioSec: Double, val usd: Double, val calls: Int, val unpriced: Int)
data class ModelRow(val provider: String, val model: String, val kind: String, val calls: Int, val inTokens: Long, val outTokens: Long,
                    val audioSec: Double, val usd: Double?)
/** Projection for 30 days, from the average over days that had any calls (window = last 30 days). */
data class Projection(val provider: String, val daysCounted: Int, val avgDailyTokens: Long, val tokens30: Long, val audioMin30: Double, val usd30: Double, val unpriced: Int) {
    /** A few days of data are needed before the number means much. */
    val reliable get() = daysCounted >= MIN_DAYS
    companion object { const val MIN_DAYS = 3; const val HORIZON = 30 }
}
data class UsageReport(val today: Totals, val month: Totals, val models: List<ModelRow>, val projections: List<Projection>, val idr: Double) {
    fun idr(usd: Double) = usd * idr
}

object UsageReports {
    private fun totals(rows: List<DayRow>, prices: Map<String, Price>): Totals {
        var tok = 0L; var sec = 0.0; var usd = 0.0; var calls = 0; val unpriced = HashSet<String>()
        for (r in rows) {
            tok += r.inTokens + r.outTokens; sec += r.audioSec; calls += r.calls
            val p = AiPricing.resolve(r.model, prices)
            if (p == null || !p.isSet) unpriced += r.model else usd += AiPricing.cost(r.inTokens, r.outTokens, r.audioSec, p)
        }
        return Totals(tok, sec, usd, calls, unpriced.size)
    }

    fun build(days: List<DayRow>, today: LocalDate, prices: Map<String, Price>, idr: Double): UsageReport {
        val todayS = today.toString(); val monthS = todayS.substring(0, 7)
        val month = days.filter { it.day.startsWith(monthS) }
        val models = month.groupBy { Triple(it.provider, it.model, it.kind) }.map { (k, rs) ->
            val inT = rs.sumOf { it.inTokens }; val outT = rs.sumOf { it.outTokens }; val sec = rs.sumOf { it.audioSec }
            val p = AiPricing.resolve(k.second, prices)
            ModelRow(k.first, k.second, k.third, rs.sumOf { it.calls }, inT, outT, sec, p?.takeIf { it.isSet }?.let { AiPricing.cost(inT, outT, sec, it) })
        }.sortedWith(compareByDescending<ModelRow> { it.usd ?: -1.0 }.thenByDescending { it.inTokens + it.outTokens })
        val from = today.minusDays(29).toString()
        val window = days.filter { it.day >= from && it.day <= todayS }
        val proj = window.groupBy { it.provider }.map { (prov, rs) ->
            val n = rs.map { it.day }.distinct().size
            val t = totals(rs, prices)
            Projection(prov, n, if (n > 0) t.tokens / n else 0, if (n > 0) Math.round(t.tokens.toDouble() / n * Projection.HORIZON) else 0,
                if (n > 0) t.audioSec / n * Projection.HORIZON / 60.0 else 0.0, if (n > 0) t.usd / n * Projection.HORIZON else 0.0, t.unpriced)
        }.sortedBy { if (it.provider == "qwen") 0 else 1 }
        return UsageReport(totals(days.filter { it.day == todayS }, prices), totals(month, prices), models, proj, idr)
    }

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> "%.2fM".format(java.util.Locale.US, n / 1e6)
        n >= 10_000 -> "%.1fk".format(java.util.Locale.US, n / 1e3)
        else -> n.toString()
    }
    fun rupiah(v: Double): String = "Rp " + "%,.0f".format(java.util.Locale.US, v).replace(',', '.')
    fun dollars(v: Double): String = when { v >= 1 -> "$%.2f"; v >= 0.01 -> "$%.3f"; else -> "$%.4f" }.let { it.format(java.util.Locale.US, v) }
    fun minutes(sec: Double): String = "%.1f min".format(java.util.Locale.US, sec / 60)

    /** Plain text Yanuar can paste into a chat. No keys, no personal data. */
    fun text(r: UsageReport, appVersion: String, date: LocalDate): String = buildString {
        appendLine("Pocket Friend AI usage report (estimate) · app v$appVersion · $date")
        appendLine("Rate: 1 USD = ${rupiah(r.idr).removePrefix("Rp ")} IDR. Prices are list prices; free quota and promotions are not subtracted.")
        appendLine()
        appendLine("This month: ${tokens(r.month.tokens)} tokens, ${minutes(r.month.audioSec)} audio, ${r.month.calls} calls, ≈ ${dollars(r.month.usd)} / ${rupiah(r.idr(r.month.usd))}" +
            if (r.month.unpriced > 0) " (${r.month.unpriced} model(s) without a price)" else "")
        appendLine("Today: ${tokens(r.today.tokens)} tokens, ${minutes(r.today.audioSec)} audio, ${r.today.calls} calls, ≈ ${dollars(r.today.usd)} / ${rupiah(r.idr(r.today.usd))}")
        appendLine()
        appendLine("Projection for 30 days (average over days with data, last 30 days):")
        if (r.projections.isEmpty()) appendLine("  no data yet")
        r.projections.forEach { p ->
            appendLine("  ${p.provider.replaceFirstChar { it.uppercase() }}: ${p.daysCounted} day(s) counted, avg ${tokens(p.avgDailyTokens)} tokens/day → ${tokens(p.tokens30)} tokens" +
                (if (p.audioMin30 > 0) ", ${"%.0f".format(java.util.Locale.US, p.audioMin30)} audio min" else "") +
                " per 30 days ≈ ${dollars(p.usd30)} / ${rupiah(r.idr(p.usd30))}" + if (p.reliable) "" else "  [too few days, rough]")
        }
        appendLine()
        appendLine("By model this month:")
        if (r.models.isEmpty()) appendLine("  none")
        r.models.forEach { m ->
            appendLine("  ${m.provider}/${m.model} (${m.kind}): ${m.calls} calls, in ${tokens(m.inTokens)}, out ${tokens(m.outTokens)}" +
                (if (m.audioSec > 0) ", ${minutes(m.audioSec)}" else "") + ", " + (m.usd?.let { "≈ ${dollars(it)} / ${rupiah(r.idr(it))}" } ?: "no price set"))
        }
    }.trimEnd()
}
