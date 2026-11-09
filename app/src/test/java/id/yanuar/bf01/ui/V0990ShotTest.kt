package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Note
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.9.9.0: note detail with Share + Save to Downloads. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp-420dpi")
class V0990ShotTest {
    private val text = "Oke jadi rencana minggu ini kita fokus ke desain kemasan baru untuk klien kopi. " +
        "Pertama riset kompetitor dulu, lalu bikin tiga arah moodboard, terus presentasi hari Kamis jam dua siang."
    private fun shot(n: Note, f: String) = captureRoboImage("build/roborazzi/$f") {
        PocketFriendTheme(true) { Surface { Box(Modifier.padding(16.dp)) { NoteDetailContent(n, NoteText.defaultTitle(n), false, 0, NoteText.estimateMs(n.transcript), n.localPath.isNotEmpty(), false) } } } }

    @Test fun withAudio() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = java.io.File(java.io.File(ctx.filesDir, "recordings").apply { mkdirs() }, "VN_1.wav").apply { writeBytes(ByteArray(2000)) }
        shot(Note(1, "VN_1.wav", 1793260800000, text, "Plan for the coffee packaging project", "", localPath = f.absolutePath), "01_detail_share_save.png")
    }
    @Test fun audioNotOnPhone() = shot(Note(2, "VN_2.wav", 1793260800000, text, "", ""), "02_detail_no_audio.png")
}
