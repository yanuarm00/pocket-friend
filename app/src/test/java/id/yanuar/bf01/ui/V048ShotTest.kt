package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Note
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V048ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v048_$f") {
            PocketFriendTheme(false) { Surface { Box(Modifier.padding(8.dp)) { c() } } }
        }

    private val home = HomeState(
        "Afternoon, Alex", "Small steps still count.", "Bobo",
        online = true, connecting = false, battery = "82% · charging", lastSeen = "Last seen · 5 min ago", network = "Hotspot · Alex's phone",
        date = "Wed, 4 Nov", weather = "31° · Sunny", tasks = 3, reminders = 1, next = "Call design · 14:00",
        whereLine = "Last seen · Office", hasLocation = true,
        recentTitle = "Project plan", recentSub = "Voice note · 4:32", recentNew = true,
        asked = null, reminderRang = null,
    )

    @Test fun battery() = shot("battery.png") {
        BatteryReportContent(
            pct = 82, charging = true, hoursLeft = "9 h 40 m",
            features = listOf("Voice & recording" to 38, "Screen refresh" to 27, "Wi-Fi sync" to 21, "BLE idle" to 14),
            points = id.yanuar.bf01.svc.BatteryStats.demoPoints(),
            accent = Pop.Orange, deviceName = "Bobo", example = true,
            onBack = {},
        )
    }

    @Test fun home() {
        AppTheme.pickAccent("orange")
        shot("home.png") { HomeContent(home, animate = false) }
    }

    @Test fun shareSheet() = shot("share_sheet.png") {
        val n = Note(
            1, "meeting.wav", System.currentTimeMillis(),
            "We should share the 3 ideas. Book the hotel. Call the design team.",
            "Ideas to share and next steps for the trip.", "",
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 720.dp)) {
            NoteDetailContent(
                n, "Meeting ideas", playing = false, posMs = 42_000, durMs = 134_000,
                hasAudio = true, todosAdded = false,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                NoteShareSheet("Meeting ideas", true, 0, {}, {}, {})
            }
        }
    }
}
