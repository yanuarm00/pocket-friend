package id.yanuar.bf01.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import id.yanuar.bf01.wifi.Nearby
import id.yanuar.bf01.wifi.WifiTools
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.6 Qwen custom URL on a 411 dp screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class V036ShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v036", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.setWifi(0, "Rumah Yan", "rahasia123"); prefs.setWifi(1, "", "")
        return Hub(ctx, prefs)
    }
    private fun out(n: String, fs: Float) = "/workspace/pf_v036/shots/${n}_fs${(fs * 100).toInt()}.png"
    private fun page(n: String, fs: Float = 1.15f, act: () -> Unit = {}) {
        RuntimeEnvironment.setFontScale(fs); val h = hub()
        rule.setContent { PocketFriendTheme(true) { App(h, Screen.SetJaringan) } }
        rule.waitForIdle(); act(); rule.waitForIdle()
        rule.onRoot().captureRoboImage(out(n, fs))
    }
    private fun piece(n: String, fs: Float = 1.15f, c: @androidx.compose.runtime.Composable (Hub) -> Unit) {
        RuntimeEnvironment.setFontScale(fs); val h = hub()
        rule.setContent { PocketFriendTheme(true) { androidx.compose.material3.Surface { Column(Modifier.verticalScroll(rememberScrollState())) { c(h) } } } }
        rule.waitForIdle(); rule.onRoot().captureRoboImage(out(n, fs))
    }
    private fun ai(n: String, fs: Float, url: String) { RuntimeEnvironment.setFontScale(fs); val h = hub(); h.prefs.llmProvider = "qwen"; h.prefs.qwenKey = "sk-test-0000"; h.prefs.qwenBaseUrl = url
        rule.setContent { PocketFriendTheme(true) { App(h, Screen.SetAi) } }; rule.waitForIdle()
        repeat(3) { rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 100f) }; rule.waitForIdle() }; rule.onRoot().captureRoboImage(out(n, fs)) }
    @Test fun custom() = ai("Qwen_custom_url", 1.15f, "https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1")
    @Test fun custom130() = ai("Qwen_custom_url", 1.3f, "https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1")
    @Test fun intl() = ai("Qwen_international", 1.15f, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1")
}
