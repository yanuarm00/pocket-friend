package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** v0.4.10: single Settings title, Haze frosted nav, brain Remembers icon, tidy Ask. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V050ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v050_$f") {
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

    @Test fun askEmpty() = shot("ask.png") {
        Column {
            AskHeader("BF", "Kamu Astro, sahabat yang tenang dan suka jawaban pendek.", {}, {})
            Spacer(Modifier.height(10.dp))
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
                    Sym.Neurology to "What did I say about the trip budget?",
                ),
            ) {}
        }
    }

    @Test fun askThread() = shot("ask_thread.png") {
        Column {
            AskHeader("BF", "Kamu Astro, sahabat yang tenang.", {}, {})
            Spacer(Modifier.height(10.dp))
            RemembersCard("BF", listOf("Calls you Alex", "Likes short answers"), false, {}, {})
            Spacer(Modifier.height(12.dp))
            ChatBubble("Remind me to drink water", fromBf = false, header = "Alex · 00:03")
            ChatBubble("Got it — I'll nudge you so you don't forget.", fromBf = true, header = "BF · 00:03")
            ChatBubble("Who are you?", fromBf = false, header = "Alex · 00:04")
        }
    }

    @Test fun settings() = shot("settings.png") {
        // One title only (SettingsContent owns it + LED). Do NOT add a second "Settings".
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
            online = false, connecting = false, onStatus = {},
        )
    }

    @Test fun navOverContent() {
        AppTheme.pickAccent("orange")
        // Robolectric is software-canvas — Haze RenderNode blur is device-only.
        // This shot still proves content continues UNDER the floating frosted pill + FAB.
        captureRoboImage("build/roborazzi/v050_nav_over_content.png") {
            PocketFriendTheme(false) {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            HomeContent(homeOnline, animate = false)
                            Spacer(Modifier.height(8.dp))
                            repeat(6) { i ->
                                androidx.compose.material3.Surface(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Text(
                                        "Note line ${i + 1} · scrolls under frosted nav",
                                        Modifier.padding(16.dp),
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
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
}
