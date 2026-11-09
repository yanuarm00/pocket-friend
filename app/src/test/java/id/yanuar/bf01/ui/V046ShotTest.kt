package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V046ShotTest {
    private fun shot(f: String, dark: Boolean = false, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v046_$f") {
            PocketFriendTheme(dark) { Surface { Box(Modifier.padding(16.dp)) { c() } } }
        }

    @Test fun battery() = shot("battery.png") {
        BatteryReportContent(
            82, false, "9 h 40 m",
            listOf("Voice & recording" to 38, "Screen refresh" to 27, "Wi-Fi sync" to 21, "BLE idle" to 14),
            points = id.yanuar.bf01.svc.BatteryStats.demoPoints(),
            accent = Pop.Orange, onCopy = {}, onReset = {},
        )
    }

    @Test fun shareSheet() = shot("share_sheet.png") {
        NoteShareSheet("Meeting ideas", true, 2, {}, {}, {}, {})
    }

    @Test fun lockscreen() = shot("lockscreen.png", dark = true) {
        LockscreenReminderPreview("Bobo", "share the 3 ideas", "19:00")
    }

    @Test fun charAi() = shot("char_ai.png") {
        CharacterAiFlow(2, "☻", {}, {}, {})
    }

    @Test fun homeOrange() {
        AppTheme.pickAccent("orange")
        shot("home_orange.png") {
            HomeContent(
                HomeState("Afternoon, Alex", "Small steps still count.", "Bobo", true, false,
                    "82% · charging", "Hotspot · Alex's phone", "Wed, 4 Nov", "31° · Sunny",
                    3, 1, "Call design · 14:00", "Last seen · Office", true,
                    "Project plan", "Voice note · 4:32", true, null, null),
                animate = false,
            )
        }
    }

    @Test fun homeBlue() {
        AppTheme.pickAccent("blue")
        shot("home_blue.png") {
            HomeContent(
                HomeState("Afternoon, Alex", "Small steps still count.", "Bobo", true, false,
                    "82% · charging", "Hotspot · Alex's phone", "Wed, 4 Nov", "31° · Sunny",
                    3, 1, "Call design · 14:00", "Last seen · Office", true,
                    "Project plan", "Voice note · 4:32", true, null, null),
                animate = false,
            )
        }
    }

    @Test fun deviceSheet() = shot("device_sheet.png") {
        DeviceSheetContent("Bobo", true, "Home 5", "82%", "Good", "2 min ago", Pop.Orange)
    }

    @Test fun themeAccent() = shot("theme_accent.png") {
        AppTheme.pickAccent("orange")
        Column {
            Text("Accent: orange")
            ThemeBoardContent("light", "orange", {}, {})
        }
    }
}
