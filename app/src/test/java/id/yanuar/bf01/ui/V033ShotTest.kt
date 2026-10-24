package id.yanuar.bf01.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.3: dropdown "persona & memory" terbuka, berisi ingatan auto; plus hapus ingatan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp-420dpi")
class V033ShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v033", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"
        val h = Hub(ctx, prefs)
        h.db.clearChat(); h.db.memories().forEach { h.db.deleteMemory(it.id) }
        h.db.addMemory("Mas Yan suka kopi susu gula aren")
        h.db.addMemory("Mas Yan biasa olahraga lari pagi tiap Sabtu", "chat", auto = true)
        h.db.addMemory("Rencana cetak 50 unit casing BF bulan depan", "note", auto = true)
        h.db.addMemory("Tim UI dipegang Dimas", "chat", auto = true)
        return h
    }

    private fun shot(fs: Float, dark: Boolean) {
        RuntimeEnvironment.setFontScale(fs)
        val h = hub()
        rule.setContent { PocketFriendTheme(dark) { App(h, Screen.Ngobrol) } }
        rule.onNodeWithText("BF persona & memory").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tim UI dipegang Dimas").assertExists()
        rule.onRoot().captureRoboImage("/workspace/pf_v033/fs${(fs * 100).toInt()}_${if (dark) "dark" else "light"}_AskAI_memory.png")
    }
    @Test fun dropdown100Dark() = shot(1.0f, true)
    @Test fun dropdown100Light() = shot(1.0f, false)
    @Test fun dropdown130Light() = shot(1.3f, false)
    @Test fun dropdown130Dark() = shot(1.3f, true)

    @Test fun deleteButtonRemovesMemory() {
        val h = hub()
        rule.setContent { PocketFriendTheme(true) { App(h, Screen.Ngobrol) } }
        rule.onNodeWithText("BF persona & memory").performClick()
        rule.onAllNodesWithContentDescription("Delete memory")[1].performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(3, h.db.memories().size)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Mas Yan biasa olahraga lari pagi tiap Sabtu").fetchSemanticsNodes().isEmpty() }
    }
}
