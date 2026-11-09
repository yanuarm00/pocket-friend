package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lightweight v0.4.5 shots for design compare (one screen per test, short timeout). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp-420dpi")
class V045ShotTest {
    private fun shot(f: String, c: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/roborazzi/v045_$f") {
            PocketFriendTheme(false) { Surface { Box(Modifier.padding(20.dp, 12.dp)) { c() } } }
        }

    @Test fun notes() = shot("notes.png") {
        NotesListContent(
            listOf(
                NoteRowUi("a", "Meeting ideas", "2:14 · #work", "Today", isNew = true, playable = true),
                NoteRowUi("b", "Transcribing…", "Voice note · 0:48", "Today", transcribing = true),
                NoteRowUi("c", "Project plan", "4:32 · Summary ready", "Yesterday", playable = true),
            ), "", {}, 0, {}, "Syncing from BF…", 2, 5, null, {}, {},
        )
    }

    @Test fun todo() = shot("todo.png") {
        TodoListContent(
            listOf(
                TodoRowUi(1, "Book the hotel", "Fri 9:00", false, synced = true),
                TodoRowUi(2, "Call the design team", "Today 14:00 · on BF ✓", false),
                TodoRowUi(4, "Buy milk and eggs", "", false, deleting = true),
                TodoRowUi(3, "Charge BF", "Done 8:10", true),
            ), 3, 2, 5, {}, {}, {}, {}, "BF"
        )
    }

    @Test fun ask() = shot("ask.png") {
        AskHeader("BF", "Calm buddy", {}, {})
        RemembersCard("BF", listOf("Calls you Alex", "Likes short answers", "Trip on Friday"), true, {}, {})
        AskLanding(listOf(Sym.AutoAwesome to "Summarize today's notes", Sym.CheckCircle to "What's left on my to-do?")) {}
    }

    @Test fun search() = shot("search.png") {
        SearchContent("", {}, 0, {}, listOf("hotel", "grocery list", "design team call"), {}, {}, emptyList(), {}, {})
    }

    @Test fun onboard() = shot("onboard.png") { OnboardWelcome({}, {}) }
}
