package id.yanuar.bf01.svc

import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.ai.AiCall
import id.yanuar.bf01.ai.AiUse
import id.yanuar.bf01.ai.UsageLog
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.data.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageSummaryTest {
    private val zone = ZoneId.of("Asia/Jakarta"); private val today = LocalDate.of(2026, 11, 3)
    private fun t(daysAgo: Int) = today.minusDays(daysAgo.toLong()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun periodFiltersEverySection() {
        val f = listOf(t(0) to Feature.VOICE_NOTE, t(6) to Feature.VOICE_NOTE, t(10) to Feature.TODO)
        val c = listOf(AiCall(t(1), "xai", "grok-4.3", "chat", 1_000_000, 0, 0.0, AiUse.ASK), AiCall(t(20), "qwen", "qwen-flash", "chat", 10, 10, 0.0, AiUse.ASK),
            AiCall(t(2), "xai", "grok-tts", "tts", 1000, 0, 5.0, AiUse.REPLY))
        val b = listOf(BatteryDays.Day(today.toString(), 10, 12 * 3600, 0, 0, 0, 3600, 0), BatteryDays.Day(today.minusDays(15).toString(), 50, 12 * 3600, 0, 0, 0, 0, 0))
        val w = UsageSummary.build(Period.WEEK, today, zone, f, c, b, 80, emptyMap(), 10000.0)
        assertEquals(2, w.features.first { it.key == Feature.VOICE_NOTE }.count); assertEquals(0, w.features.first { it.key == Feature.TODO }.count)
        assertEquals(2, w.aiRequests); assertEquals(7, w.trend.size)
        // grok-4.3 $1.25 per 1M in -> Rp 12.500; TTS $15 per 1M chars -> 1000 chars = Rp 150
        assertEquals(12500.0, w.uses.first { it.use == AiUse.ASK }.idr, 0.01); assertEquals(150.0, w.uses.first { it.use == AiUse.REPLY }.idr, 0.01)
        assertEquals(20.0, w.perDay!!, 0.01); assertEquals(4.0, w.daysLeft!!, 0.01)
        val m = UsageSummary.build(Period.MONTH, today, zone, f, c, b, 80, emptyMap(), 10000.0)
        assertEquals(3, m.totalUses); assertEquals(3, m.aiRequests); assertEquals(30, m.trend.size)
        assertEquals(60.0, m.perDay!!, 0.01)
    }

    @Test fun emptyHasNoHighlights() {
        val d = UsageSummary.build(Period.WEEK, today, zone, emptyList(), emptyList(), emptyList(), null, emptyMap(), 18100.0)
        assertTrue(d.empty); assertNull(d.topFeature); assertNull(d.hungriest); assertNull(d.topUse); assertNull(d.daysLeft)
    }

    @Test fun batteryStepHandlesRestartAndCharging() {
        fun u(up: Long, awake: Long) = BfProtocol.Usage(up, 0, 0, 0, awake, 0, 0, 0, -1)
        val a = BatteryDays.Snap(90, u(1000, 100)); val b = BatteryDays.Snap(85, u(1600, 400))
        assertEquals(BatteryDays.Day("d", 5, 600, 0, 0, 0, 300, 0), BatteryDays.step(a, b, false, "d"))
        assertEquals(0, BatteryDays.step(a, b, true, "d").drop)
        assertEquals(50L, BatteryDays.step(b, BatteryDays.Snap(84, u(50, 10)), false, "d").up)
    }

    @Test fun useFollowsCoroutineAndDefaultsByKind() = runBlocking {
        val got = mutableListOf<AiCall>(); UsageLog.sink = { got += it }
        try {
            val body = """{"usage":{"prompt_tokens":5,"completion_tokens":2}}""".toByteArray()
            UsageLog.record("xai", "grok-4.3", "chat", body)
            withContext(AiUse.Ctx(AiUse.SEARCH)) { withContext(Dispatchers.IO) { UsageLog.record("xai", "grok-4.3", "chat", body) } }
            UsageLog.tts("phone", "system", 12, 1.0)
            assertEquals(listOf(AiUse.ASK, AiUse.SEARCH, AiUse.REPLY), got.map { it.use })
            assertEquals(AiUse.STT, AiUse.of("", "asr"))
        } finally { UsageLog.sink = null }
    }

    @Test fun migrationFromV5KeepsRows() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase("bf01.db")
        val f = ctx.getDatabasePath("bf01.db").also { it.parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            d.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, file TEXT UNIQUE, created INTEGER, transcript TEXT DEFAULT '', summary TEXT DEFAULT '', tags TEXT DEFAULT '', embedding BLOB, type TEXT DEFAULT 'NOTE', local_path TEXT DEFAULT '', processed INTEGER DEFAULT 0)")
            d.execSQL("CREATE TABLE ai_calls(id INTEGER PRIMARY KEY, t INTEGER, provider TEXT, model TEXT, kind TEXT, in_tok INTEGER DEFAULT 0, out_tok INTEGER DEFAULT 0, audio_sec REAL DEFAULT 0)")
            d.execSQL("INSERT INTO ai_calls(t,provider,model,kind,audio_sec) VALUES(5,'qwen','qwen3-asr-flash','asr',30)")
            d.execSQL("INSERT INTO notes(file,created) VALUES('a.wav',1)")
            d.version = 5
        }
        val db = Db(ctx)
        val calls = db.aiCallsSince(0)
        assertEquals(1, calls.size); assertEquals(AiUse.STT, calls[0].use); assertEquals(1, db.notes().size)
        db.addFeatureUse(Feature.FIND_PHONE, 10); assertEquals(listOf(10L to Feature.FIND_PHONE), db.featureUsesSince(0))
        db.close()
    }
}
