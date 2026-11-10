package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.4.9: frosted nav, offline Home parity, Ask header, Settings LED + AI icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V049ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v049_$f") {
            PocketFriendTheme(false) { Surface { Box(Modifier.padding(16.dp)) { c() } } }
        }

    private val homeOnline = HomeState(
        "Afternoon, Alex", "Small steps still count.", "Bobo",
        online = true, connecting = false, battery = "Charging · 82%", lastSeen = "Just now",
        network = "Hotspot · Alex's phone", date = "Wed, 4 Nov", weather = "31° · Sunny",
        tasks = 3, reminders = 1, next = "Call design · 14:00",
        whereLine = "Last seen · Office", hasLocation = true,
        recentTitle = "Project plan", recentSub = "Voice note · 4:32", recentNew = true,
        asked = null, reminderRang = null,
    )
    private val homeOffline = homeOnline.copy(
        online = false, connecting = false,
        battery = "Battery 77%", lastSeen = "Last seen · 5 min ago",
        network = "Bluetooth · Wi-Fi off",
    )

    @Test fun homeOffline() {
        AppTheme.pickAccent("orange")
        shot("home_offline.png") { HomeContent(homeOffline, animate = false) }
    }

    @Test fun ask() = shot("ask.png") {
        Column {
            AskHeader("BF", "Kamu Astro, sahabat yang tenang dan suka jawaban pendek.", {}, {})
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            RemembersCard(
                "BF",
                listOf("Calls you Alex", "Likes short answers", "Works on the design team", "Trip on Friday"),
                true, {}, {},
            )
            AskLanding(
                listOf(
                    Sym.AutoAwesome to "Summarize today's notes",
                    Sym.CheckCircle to "What's left on my to-do?",
                    Sym.Notifications to "Remind me to call the team at 2",
                    Sym.Lightbulb to "What did I say about the trip budget?",
                ),
            ) {}
        }
    }

    @Test fun settings() = shot("settings.png") {
        Column {
            Box(Modifier.fillMaxWidth()) {
                Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                Box(Modifier.align(Alignment.CenterEnd)) {
                    LedChip(online = false, connecting = false, onClick = {})
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            SettingsContent(
                "Alex", "Name, avatar, Say Hi · backup on", {},
                device = listOf(
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
            )
        }
    }

    @Test fun navOverContent() {
        AppTheme.pickAccent("orange")
        captureRoboImage("build/roborazzi/v049_nav_over_content.png") {
            PocketFriendTheme(false) {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            HomeContent(homeOnline, animate = false)
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                            PopNavBar(Screen.Beranda, true) {}
                        }
                    }
                }
            }
        }
    }
}
