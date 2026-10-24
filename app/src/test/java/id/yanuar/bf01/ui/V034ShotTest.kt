package id.yanuar.bf01.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.4 fixes on a Galaxy A53-sized screen (411 dp wide). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class V034ShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v034", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.lastBatt = 100
        return Hub(ctx, prefs).also { it.db.clearChat() }
    }
    private fun shot(s: Screen, name: String, fs: Float = 1.15f, act: (Hub) -> Unit = {}) {
        RuntimeEnvironment.setFontScale(fs); val h = hub()
        rule.setContent { PocketFriendTheme(true) { App(h, s) } }
        rule.waitForIdle(); act(h); rule.waitForIdle()
        rule.onRoot().captureRoboImage("/workspace/pf_v034/shots/${name}_fs${(fs * 100).toInt()}.png")
    }
    @Test fun home() = shot(Screen.Beranda, "Home")
    @Test fun home130() = shot(Screen.Beranda, "Home", 1.3f)
    @Test fun perms() = shot(Screen.SetIzin, "Permissions")
    @Test fun perms130() = shot(Screen.SetIzin, "Permissions", 1.3f)
    @Test fun status() = shot(Screen.Pengaturan, "StatusSheet") { rule.onAllNodes(hasContentDescription("status", substring = true, ignoreCase = true) and hasClickAction())[0].performClick() }
    @Test fun status130() = shot(Screen.Pengaturan, "StatusSheet", 1.3f) { rule.onAllNodes(hasContentDescription("status", substring = true, ignoreCase = true) and hasClickAction())[0].performClick() }
    @Test fun askKeepsFocusAfterSend() = shot(Screen.Ngobrol, "AskAI_after_send") { h ->
        h.db.addChat("user", "halo"); h.db.addChat("assistant", "Halo Mas Yan! Ada yang bisa aku bantu?")
        val f = rule.onNode(hasSetTextAction()); f.performClick(); f.performTextInput("test")
        rule.onNodeWithContentDescription("Send").performClick(); rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertIsFocused()
    }
}
