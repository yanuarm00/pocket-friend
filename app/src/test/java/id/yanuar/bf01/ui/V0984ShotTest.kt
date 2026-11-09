package id.yanuar.bf01.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.ai.AiCall
import id.yanuar.bf01.data.Db
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.9.8.4: AI usage screen and the database table behind it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1900dp-420dpi")
class V0984ShotTest {
    private val day = 86_400_000L
    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v0984", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.deviceName = "BF-01-4E27"
        val hub = Hub(ctx, prefs)
        hub.ble.preview(id.yanuar.bf01.ble.BleManager.State.READY, id.yanuar.bf01.ble.BfProtocol.Info(fw = "0.1.0", batt = 78, sd = true, sdFreeMb = 28_412, rec = false,
            paired = true, wifi = true, wifiOn = false, warm = false, state = "idle"))
        return hub
    }
    private fun seed(h: Hub) {
        h.db.resetAiUsage()
        val now = System.currentTimeMillis() - 2 * 3600_000L   // 2 h ago, so "today" holds whenever the test runs after 02:00
        for (d in 0..4) {
            val t = now - d * day
            repeat(3 + d) { h.db.addAiCall(AiCall(t - it * 1000, "qwen", "qwen-flash-character", "chat", 900L + it * 40, 120L + it * 10, 0.0)) }
            repeat(2) { h.db.addAiCall(AiCall(t - 5000 - it, "qwen", "qwen-audio-3.1-asr-flash", "asr", 700L, 25L, 28.0)) }
            if (d % 2 == 0) h.db.addAiCall(AiCall(t - 9000, "grok", "grok-4.3", "chat", 1500L, 200L, 0.0))
            if (d == 0) h.db.addAiCall(AiCall(t - 9500, "grok", "grok-voice-transcribe-2.0", "asr", 0, 0, 41.5))
        }
        h.db.addAiCall(AiCall(now - 3000, "qwen", "auto", "chat", 300, 30, 0.0))
    }

    @Test fun aiUsageScreens() {
        RuntimeEnvironment.setFontScale(1.15f)
        val h = hub(); seed(h)
        captureRoboImage("build/roborazzi/01_AI_usage.png") { PocketFriendTheme(true) { App(h, Screen.AiUsage) } }
        captureRoboImage("build/roborazzi/02_Settings_entry.png") { PocketFriendTheme(true) { App(h, Screen.Pengaturan) } }
    }
    @Test fun aiUsageEmptyAndPrices() {
        RuntimeEnvironment.setFontScale(1.0f)
        val h = hub(); h.db.resetAiUsage()
        captureRoboImage("build/roborazzi/03_AI_usage_empty.png") { PocketFriendTheme(true) { App(h, Screen.AiUsage) } }
    }

    @Test fun dbStoresAndAggregatesAndResets() {
        val h = hub(); h.db.resetAiUsage()
        val now = System.currentTimeMillis()
        h.db.addAiCall(AiCall(now, "qwen", "qwen-plus", "chat", 100, 10, 0.0)); h.db.addAiCall(AiCall(now, "qwen", "qwen-plus", "chat", 50, 5, 0.0))
        h.db.addAiCall(AiCall(now - 3 * day, "grok", "grok-voice-transcribe-2.0", "asr", 0, 0, 12.5))
        val rows = h.db.aiUsageDays(now - 10 * day)
        val q = rows.single { it.model == "qwen-plus" }
        assertEquals(2, q.calls); assertEquals(150L, q.inTokens); assertEquals(15L, q.outTokens)
        assertEquals(12.5, rows.single { it.kind == "asr" }.audioSec, 1e-9)
        assertEquals(1, h.db.aiUsageDays(now - day).map { it.model }.distinct().size)
        h.db.resetAiUsage(); assertTrue(h.db.aiUsageDays(0).isEmpty())
    }
    @Test fun migrationFromV4KeepsOldDataAndAddsTable() {
        val h = hub(); h.db.addUsage(sttSec = 60.0, chat = 2)
        val raw = h.db.writableDatabase
        raw.execSQL("DROP TABLE ai_calls")                 // what a v4 database looks like
        h.db.onUpgrade(raw, 4, 5)
        h.db.addAiCall(AiCall(System.currentTimeMillis(), "qwen", "m", "chat", 1, 1, 0.0))
        assertEquals(1, h.db.aiUsageDays(0).size)
        assertEquals(2, h.db.usageThisMonth().third)       // existing audio-minutes tracking untouched
        h.db.onUpgrade(raw, 4, 5)                          // running it twice must not crash
    }
}
