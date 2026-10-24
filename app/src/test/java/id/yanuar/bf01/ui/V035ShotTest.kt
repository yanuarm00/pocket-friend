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

/** v0.3.5 Wi-Fi setup on a 411 dp screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class V035ShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v035", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.setWifi(0, "Rumah Yan", "rahasia123"); prefs.setWifi(1, "", "")
        return Hub(ctx, prefs)
    }
    private fun out(n: String, fs: Float) = "/workspace/pf_v035/shots/${n}_fs${(fs * 100).toInt()}.png"
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
    @Test fun wifiPage() = page("WifiPage")
    @Test fun wifiPage130() = page("WifiPage", 1.3f)
    @Test fun passwordShown() = page("WifiPage_password_shown") { rule.onAllNodesWithContentDescription("Show password")[0].performClick() }
    @Test fun samsungHelp() = piece("Wifi_Samsung_QR_help") { WifiSlot(it, 0, startGuide = true) }
    @Test fun hotspotGuide() = piece("Hotspot_guide") { WifiSlot(it, 1, startGuide = true) }
    @Test fun hotspotGuide130() = piece("Hotspot_guide", 1.3f) { WifiSlot(it, 1, startGuide = true) }
    @Test fun networkList() {
        WifiTools.scanOverride = { listOf(Nearby("Rumah Yan", -48, true, true), Nearby("Rumah Yan 5G", -45, true, false).let { it }, Nearby("Tetangga", -72, true, true), Nearby("Warkop Free", -84, false, true)).sortedByDescending { it.level } }
        try { piece("Choose_network") { WifiSlot(it, 0, startList = true) } } finally { WifiTools.scanOverride = null }
    }
    @Test fun sending() = piece("Send_sending") { SendToBf(it, null to "Saved on BF. BF is restarting and trying \"Rumah Yan\"…") }
    @Test fun sendOk() = piece("Send_ok") { SendToBf(it, true to "Done! BF is on Wi-Fi.") }
    @Test fun sendFail() = piece("Send_fail") { SendToBf(it, false to "BF couldn't join the Wi-Fi. Check the password, make sure it's 2.4 GHz (not 5 GHz only), and that the hotspot is on.") }
}
