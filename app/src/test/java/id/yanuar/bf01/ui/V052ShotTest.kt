package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.svc.ErrorReport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.4.12 previews: To sort, reply box in a BF chat, Error report. Images go to build/roborazzi (not the repo). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class V052ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v052_$f") { PocketFriendTheme(false) { Surface { Box(Modifier.padding(20.dp)) { c() } } } }

    @Test fun toSort() = shot("to_sort.png") {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            ToSortContent(listOf(
                ToSortItem("a", "Today · 14:02", "Remind me to review the design at three"),
                ToSortItem("b", "Today · 11:40", "Idea: a small sticker pack for the launch day"),
                ToSortItem("c", "Yesterday · 21:15", ""),
            ), {}, {}, {})
        }
    }
    @Test fun notesBanner() = shot("notes_banner.png") { ToSortBanner(2) {} }

    @Test fun chatReply() = shot("chat_reply.png") {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            PfBackHeader("Chat", "Ask BF · kept on this phone", {})
            ChatBubble(true, "What should I pack for a day trip?", "10.42", true)
            ChatBubble(false, "Water, a light jacket, sunscreen and a charged phone. Want a short checklist?", "10.42", false)
            ChatBubble(true, "Yes, add snacks too", "10.43", false)
            Spacer(Modifier.height(130.dp))
            AskInput("", {}, "Reply to BF's chat", {})
        }
    }

    @Test fun errorReport() = shot("error_report.png") {
        val info = BfProtocol.parseInfo("""{"fw":"0.4.9","batt":77,"state":"idle","rst":"panic","last_crash":"panic","crash_info":"t=bf_ui pc=0x400d1234 LoadProhibited","restart_why":""}""")
        ErrorReportContent(ErrorReport.build(info, "0.4.12", 51, "15", 35, "0.4.9"), true, {}, {})
    }
}
