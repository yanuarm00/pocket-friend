package id.yanuar.bf01.ai

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AiUsageTest {
    @After fun tearDown() { UsageLog.sink = null }

    // ---- parsing (shapes from the Model Studio and xAI docs) ----
    @Test fun qwenCompatChat() {
        val r = UsageParse.parse("""{"choices":[{"message":{"content":"hi"}}],"model":"qwen-flash-character","usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}""")!!
        assertEquals(120L, r.inTokens); assertEquals(30L, r.outTokens); assertEquals(0.0, r.seconds, 0.0)
    }
    @Test fun qwenCompatAsrWithSecondsAndAudioTokens() {
        val r = UsageParse.parse("""{"choices":[{"message":{"content":"halo"}}],"usage":{"prompt_tokens":150,"completion_tokens":5,"total_tokens":155,"prompt_tokens_details":{"audio_tokens":125},"seconds":5}}""")!!
        assertEquals(150L, r.inTokens); assertEquals(5L, r.outTokens); assertEquals(5.0, r.seconds, 0.0)
    }
    @Test fun qwenNativeAsr() {
        val r = UsageParse.parse("""{"output":{"choices":[{"message":{"content":[{"text":"halo"}]}}]},"usage":{"input_tokens":200,"output_tokens":9,"seconds":8}}""")!!
        assertEquals(200L, r.inTokens); assertEquals(9L, r.outTokens); assertEquals(8.0, r.seconds, 0.0)
    }
    @Test fun grokChat() {
        val r = UsageParse.parse("""{"choices":[],"usage":{"prompt_tokens":50,"completion_tokens":12,"total_tokens":62}}""")!!
        assertEquals(50L, r.inTokens); assertEquals(12L, r.outTokens)
    }
    @Test fun xaiSttOnlyDuration() {
        val r = UsageParse.parse("""{"text":"halo dunia","language":"id","duration":4.2}""")!!
        assertEquals(0L, r.inTokens); assertEquals(4.2, r.seconds, 1e-9)
    }
    @Test fun noUsageOrBadJson() {
        assertNull(UsageParse.parse("""{"choices":[]}""")); assertNull(UsageParse.parse("not json")); assertNull(UsageParse.parse(""))
    }
    @Test fun modelFromResponse() { assertEquals("qwen3.8-flash", UsageParse.model("""{"model":"qwen3.8-flash"}""")); assertNull(UsageParse.model("{}")) }

    // ---- recording ----
    @Test fun recordChatUsesRequestedModelAndAutoUsesServerModel() {
        val got = mutableListOf<AiCall>(); UsageLog.sink = { got += it }
        UsageLog.record("Qwen", "qwen-plus", "chat", """{"model":"x","usage":{"prompt_tokens":10,"completion_tokens":2}}""".toByteArray(), now = 5)
        UsageLog.record("qwen", "auto", "chat", """{"model":"qwen3.8-flash","usage":{"prompt_tokens":1,"completion_tokens":1}}""".toByteArray(), now = 6)
        assertEquals(AiCall(5, "qwen", "qwen-plus", "chat", 10, 2, 0.0), got[0])
        assertEquals("qwen3.8-flash", got[1].model)
    }
    @Test fun recordSkipsChatWithoutUsage() {
        val got = mutableListOf<AiCall>(); UsageLog.sink = { got += it }
        UsageLog.record("qwen", "qwen-plus", "chat", """{"choices":[]}""".toByteArray()); assertTrue(got.isEmpty())
    }
    @Test fun recordAsrFallsBackToSentAudioLength() {
        val got = mutableListOf<AiCall>(); UsageLog.sink = { got += it }
        UsageLog.record("grok", "grok-voice-transcribe-2.0", "asr", """{"text":"a"}""".toByteArray(), fallbackSec = 3.5)
        assertEquals(3.5, got.single().audioSec, 0.0)
        UsageLog.record("grok", "grok-voice-transcribe-2.0", "asr", """{"text":"a","duration":2.0}""".toByteArray(), fallbackSec = 3.5)
        assertEquals(2.0, got[1].audioSec, 0.0)   // the server's number wins
    }
    @Test fun recordNeverThrows() {
        UsageLog.sink = { throw IllegalStateException("db closed") }
        UsageLog.record("qwen", "m", "chat", """{"usage":{"prompt_tokens":1}}""".toByteArray())
    }

    // ---- prices and cost math ----
    @Test fun costTokens() {   // qwen-plus-character: 1M in * 0.5 + 0.5M out * 1.4 = 0.5 + 0.7
        assertEquals(1.2, AiPricing.cost(1_000_000, 500_000, 0.0, AiPricing.resolve("qwen-plus-character")!!), 1e-9)
    }
    @Test fun costPerMinute() {   // qwen3-asr-flash: 10 min = 600 s * 0.000035 = 0.021
        assertEquals(0.021, AiPricing.cost(0, 0, 600.0, AiPricing.resolve("qwen3-asr-flash")!!), 1e-9)
        assertEquals(0.10, AiPricing.cost(0, 0, 3600.0, AiPricing.resolve("grok-voice-transcribe-2.0")!!), 1e-9)   // $0.10 per hour
    }
    @Test fun tokenAsrDoesNotAlsoChargeSeconds() {
        assertEquals(150 * 0.15 / 1e6 + 5 * 0.47 / 1e6, AiPricing.cost(150, 5, 5.0, AiPricing.resolve("qwen-audio-3.1-asr-flash")!!), 1e-12)
    }
    @Test fun resolvePrefersExactThenLongestPrefixThenOverride() {
        assertEquals(0.4, AiPricing.resolve("qwen-plus-2025-12-01")!!.inPerM, 0.0)        // dated snapshot of qwen-plus
        assertEquals(0.5, AiPricing.resolve("qwen-plus-character")!!.inPerM, 0.0)         // exact beats the shorter qwen-plus prefix
        assertEquals(0.5, AiPricing.resolve("qwen-plus-character-v2")!!.inPerM, 0.0)      // longest prefix
        assertNull(AiPricing.resolve("auto")); assertNull(AiPricing.resolve("something-else"))
        val o = mapOf("qwen-plus" to Price(9.0, 9.0, 0.0))
        assertEquals(9.0, AiPricing.resolve("qwen-plus", o)!!.inPerM, 0.0); assertEquals(9.0, AiPricing.resolve("qwen-plus-2025", o)!!.inPerM, 0.0)
    }
    @Test fun overridesRoundTrip() {
        val m = mapOf("qwen-plus" to Price(0.1, 0.2, 0.0), "x" to Price(0.0, 0.0, 0.003))
        assertEquals(m, AiPricing.decode(AiPricing.encode(m))); assertTrue(AiPricing.decode("garbage").isEmpty()); assertTrue(AiPricing.decode(null).isEmpty())
    }
    @Test fun defaultsMatchVerifiedPrices() {
        val d = AiPricing.DEFAULTS
        assertEquals(Price(0.05, 0.40, 0.0), d["qwen-flash-character"]); assertEquals(Price(1.25, 2.50, 0.0), d["grok-4.3"])
        assertEquals(0.0021, d["qwen3-asr-flash"]!!.perMin, 1e-12)
    }

    // ---- report: today / month / projection ----
    private val today = LocalDate.of(2026, 10, 27)
    private fun row(day: String, model: String, inT: Long, outT: Long, sec: Double = 0.0, kind: String = "chat", prov: String = "qwen", calls: Int = 1) =
        DayRow(day, prov, model, kind, calls, inT, outT, sec)

    @Test fun todayAndMonthTotals() {
        val rows = listOf(row("2026-09-30", "qwen-flash-character", 5_000_000, 0), row("2026-10-26", "qwen-flash-character", 1_000_000, 1_000_000),
            row("2026-10-27", "qwen-flash-character", 1_000_000, 0, calls = 3))
        val r = UsageReports.build(rows, today, emptyMap(), 18000.0)
        assertEquals(1_000_000L, r.today.tokens); assertEquals(0.05, r.today.usd, 1e-9); assertEquals(3, r.today.calls)
        assertEquals(3_000_000L, r.month.tokens); assertEquals(0.05 * 2 + 0.40, r.month.usd, 1e-9)   // September excluded
        assertEquals(1, r.models.size); assertEquals(0.5, r.models[0].usd!!, 1e-9)
        assertEquals(0.5 * 18000, r.idr(r.month.usd), 1e-6)
    }
    @Test fun unpricedModelIsCountedSeparately() {
        val r = UsageReports.build(listOf(row("2026-10-27", "auto", 1000, 1000), row("2026-10-27", "qwen-flash", 1_000_000, 0)), today, emptyMap(), 18000.0)
        assertEquals(1, r.month.unpriced); assertEquals(0.05, r.month.usd, 1e-9); assertNull(r.models.first { it.model == "auto" }.usd)
    }
    @Test fun projectionAveragesOverDaysWithData() {
        // Qwen: 3 days with data out of the last 30 (the gap days are not counted): 1M, 2M, 3M tokens in -> avg 2M/day -> 60M per 30 days
        val rows = listOf(row("2026-10-20", "qwen-flash", 1_000_000, 0), row("2026-10-22", "qwen-flash", 2_000_000, 0), row("2026-10-27", "qwen-flash", 3_000_000, 0),
            row("2026-10-27", "grok-4.3", 100_000, 0, prov = "grok"))
        val r = UsageReports.build(rows, today, emptyMap(), 18000.0)
        val q = r.projections.first(); assertEquals("qwen", q.provider)   // Qwen listed first
        assertEquals(3, q.daysCounted); assertTrue(q.reliable)
        assertEquals(2_000_000L, q.avgDailyTokens); assertEquals(60_000_000L, q.tokens30)
        assertEquals(0.05 * 6 / 3 * 30, q.usd30, 1e-9)   // 6M tokens in total at $0.05/M
        val g = r.projections[1]; assertEquals("grok", g.provider); assertEquals(1, g.daysCounted); assertFalse(g.reliable)
        assertEquals(3_000_000L, g.tokens30)
    }
    @Test fun projectionIgnoresDaysOlderThan30() {
        val r = UsageReports.build(listOf(row("2026-09-20", "qwen-flash", 9_000_000, 0), row("2026-10-27", "qwen-flash", 1_000_000, 0)), today, emptyMap(), 1.0)
        assertEquals(1, r.projections.single().daysCounted)
    }
    @Test fun projectionIncludesAudioMinutesForSecondBilledAsr() {
        val r = UsageReports.build(listOf(row("2026-10-27", "qwen3-asr-flash", 0, 0, sec = 600.0, kind = "asr")), today, emptyMap(), 18000.0)
        val q = r.projections.single(); assertEquals(300.0, q.audioMin30, 1e-9); assertEquals(0.021 * 30, q.usd30, 1e-9)
    }
    @Test fun emptyReport() {
        val r = UsageReports.build(emptyList(), today, emptyMap(), 18000.0)
        assertEquals(0L, r.month.tokens); assertTrue(r.projections.isEmpty()); assertTrue(r.models.isEmpty())
    }

    // ---- formatting and copied text ----
    @Test fun formats() {
        assertEquals("Rp 12.345", UsageReports.rupiah(12345.4)); assertEquals("Rp 1.234.568", UsageReports.rupiah(1_234_567.8))
        assertEquals("950", UsageReports.tokens(950)); assertEquals("12.3k", UsageReports.tokens(12_345)); assertEquals("1.20M", UsageReports.tokens(1_200_000))
        assertEquals("$1.50", UsageReports.dollars(1.5)); assertEquals("$0.012", UsageReports.dollars(0.0123)); assertEquals("$0.0012", UsageReports.dollars(0.0012))
    }
    @Test fun reportTextHasProjectionNoSecrets() {
        val rows = listOf(row("2026-10-25", "qwen-flash-character", 1_000_000, 100_000), row("2026-10-26", "qwen-flash-character", 1_000_000, 100_000), row("2026-10-27", "qwen-flash-character", 1_000_000, 100_000))
        val t = UsageReports.text(UsageReports.build(rows, today, emptyMap(), 18000.0), "0.3.9.8.4", today)
        assertTrue(t, t.contains("Projection for 30 days")); assertTrue(t, t.contains("Qwen: 3 day(s) counted"))
        assertTrue(t, t.contains("estimate")); assertTrue(t, t.contains("qwen/qwen-flash-character (chat)")); assertTrue(t, t.contains("33.00M tokens"))
        assertFalse(t.lowercase().contains("key"))
    }
}
