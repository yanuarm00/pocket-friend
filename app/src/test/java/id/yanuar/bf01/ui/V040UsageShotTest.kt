package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import id.yanuar.bf01.ai.AiCall
import id.yanuar.bf01.ai.AiUse
import id.yanuar.bf01.svc.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** v0.4.0 Usage page (mockup v3): seeded test data in light and dark, and the empty state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2300dp-420dpi")
class V040UsageShotTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = AppTheme.set("system")

    companion object {
        val today: LocalDate = LocalDate.of(2026, 11, 3); val zone: ZoneId = ZoneId.of("Asia/Jakarta")
        private fun t(daysAgo: Int, h: Int = 10) = today.minusDays(daysAgo.toLong()).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        fun seeded(period: Period = Period.WEEK): UsageData {
            val counts = mapOf(Feature.VOICE_NOTE to 42, Feature.ASK to 31, Feature.TODO to 24, Feature.SEARCH to 17, Feature.REMINDER to 12,
                Feature.WHERES_MINE to 6, Feature.SAY_HI to 4, Feature.FIND_PHONE to 2)
            val f = counts.flatMap { (k, n) -> (0 until n).map { t(it % 7, 8 + it % 10) to k } }
            val calls = buildList {
                repeat(40) { add(AiCall(t(it % 7), "xai", "grok-4.3", "chat", 2600, 420, 0.0, AiUse.ASK)) }
                repeat(45) { add(AiCall(t(it % 7), "qwen", "qwen3-asr-flash", "asr", 0, 0, 50.0, AiUse.STT)) }
                repeat(14) { add(AiCall(t(it % 7), "xai", "grok-tts", "tts", 180, 0, 9.0, AiUse.REPLY)) }
                repeat(10) { add(AiCall(t(it % 7), "xai", "grok-4.3", "chat", 3100, 520, 0.0, AiUse.SEARCH)) }
                repeat(12) { add(AiCall(t(0, 9 + it % 8), "qwen", "qwen-flash", "chat", 2000, 300, 0.0, AiUse.ASK)) }
            }
            val batt = (0 until 7).map { BatteryDays.Day(today.minusDays(it.toLong()).toString(), 19, 20 * 3600, 2400, 18 * 3600, 900, 3 * 3600, 600) }
            return UsageSummary.build(period, today, zone, f, calls, batt, 82, emptyMap(), 18100.0)
        }
        fun empty() = UsageSummary.build(Period.WEEK, today, zone, emptyList(), emptyList(), emptyList(), 64, emptyMap(), 18100.0)
    }

    private fun show(mode: String, d: UsageData, file: String) {
        AppTheme.set(mode); rule.mainClock.autoAdvance = false
        rule.setContent { PocketFriendTheme(AppTheme.isDark()) { Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            UsageContent(d, "Pip", d.period, {}) } } } }
        rule.mainClock.advanceTimeBy(1000); captureScreenRoboImage("build/roborazzi/$file")
    }

    @Test fun usageLight() = show("light", seeded(), "13_usage_light.png")
    @Test fun usageDark() = show("dark", seeded(), "14_usage_dark.png")
    @Test fun usageMonthLight() = show("light", seeded(Period.MONTH), "15_usage_month_light.png")
    @Test fun usageEmpty() = show("light", empty(), "16_usage_empty.png")
}
