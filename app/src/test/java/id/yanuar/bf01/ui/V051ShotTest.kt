package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.4.11: no full-width nav band; Profile vs Say Hi; Character vs BF menu; offline card text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V051ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v051_$f") {
            PocketFriendTheme(false) { Surface { Box(Modifier.padding(16.dp)) { c() } } }
        }

    private val homeOffline = HomeState(
        "Afternoon, Alex", "Small steps still count.", "Bobo",
        online = false, connecting = false, battery = "Battery 77%", lastSeen = "Seen 6d ago",
        network = "Bluetooth · Wi-Fi off", date = "Wed, 4 Nov", weather = "31° · Sunny",
        tasks = 3, reminders = 1, next = "Call design · 14:00",
        whereLine = "Last seen · Office", hasLocation = true,
        recentTitle = "Project plan", recentSub = "Voice note · 4:32", recentNew = true,
        asked = null, reminderRang = null,
    )

    @Test fun homeOfflineNav() {
        AppTheme.pickAccent("orange")
        // Software canvas: no hazeSource. Shot is the nav area over the cream page (no full-width grey band).
        captureRoboImage("build/roborazzi/v051_home_offline_nav.png") {
            PocketFriendTheme(false) {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            HomeContent(homeOffline, animate = false)
                            Spacer(Modifier.height(PopNavClearance))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                            PopNavBar(Screen.Beranda, true) {}
                        }
                    }
                }
            }
        }
    }

    @Test fun settings() = shot("settings.png") {
        SettingsContent(
            "Alex", "Name, avatar, Say Hi · backup on", {},
            device = listOf(
                SetRow(Sym.Edit, "Device name", "Bobo", {}),
                SetRow(Sym.Apps, "Character", "Ready one · photo · AI", {}, accent = Pop.Orange, deviceThumb = true),
                SetRow(Sym.DeveloperBoard, "BF menu", "Items & order on Bobo", {}),
                SetRow(Sym.Link, "Connection", "DISCONNECTED", {}, chip = "Help", onChip = {}),
                SetRow(Sym.Download, "Software update", "Bobo 1.0.3 · up to date", {}),
                SetRow(Sym.LightMode, "Sleep & wake", "Sleep after 300 s · quiet 22:00–6:00", {}, accent = Pop.Yellow),
            ),
            app = listOf(
                SetRow(Sym.AutoAwesome, "AI & voice", "Qwen · voice eve", {}),
                SetRow(Sym.Notifications, "Notifications", "Music, Maps & what Bobo may show", {}),
                SetRow(Sym.BarChart, "Usage", "Features, battery & AI cost", {}),
                SetRow(Sym.Palette, "Theme", null, {}, swatches = true),
                SetRow(Sym.Shield, "Privacy & data", "Export, clear, audio & chats", {}),
            ),
            online = false, connecting = false, onStatus = {},
        )
    }

    @Test fun profile() = shot("profile.png") {
        ProfileContent(
            name = "Alex", deviceName = "Bobo", online = true, wifi = "Home 5",
            sayHiCount = 4, sayHiOnDevice = "Instagram",
            backupOn = true, backupSub = "Notes & to-dos · last 08:12",
            onBack = {}, onChangeCharacter = {}, onSayHi = {}, onBackup = {},
        )
    }

    @Test fun character() = shot("character.png") {
        CharacterContent(
            deviceName = "Bobo", mode = 0, face = 0,
            onBack = {}, onMode = {}, onFace = {}, onSave = {},
        )
    }
}
