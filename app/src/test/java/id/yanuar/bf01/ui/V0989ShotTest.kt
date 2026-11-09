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

/** v0.3.9.8.9: note detail shows the whole transcript, not the 3-line card text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1100dp-420dpi")
class V0989ShotTest {
    private val text = "Oke jadi rencana minggu ini kita fokus ke desain kemasan baru untuk klien kopi. " +
        "Pertama riset kompetitor dulu, lalu bikin tiga arah moodboard, terus presentasi hari Kamis jam dua siang. " +
        "Jangan lupa minta foto produk resolusi tinggi dari tim mereka dan konfirmasi ukuran label botol yang 250 ml. " +
        "Setelah itu kita lanjut ke revisi logo, cek ulang warna di mockup cetak, dan siapkan file final untuk percetakan, " +
        "dan kalimat terakhir ini harus tetap terlihat."
    private val note = Note(1, "n1.wav", 1793260800000, text, "Plan for the coffee packaging project this week", "design,client")

    @Test fun detailShowsFullTranscript() {
        captureRoboImage("build/roborazzi/note_detail.png") {
            PocketFriendTheme(true) { Surface { Box(Modifier.padding(16.dp)) { NoteDetailContent(note, NoteText.defaultTitle(note), false, 0, NoteText.estimateMs(note.transcript), note.localPath.isNotEmpty(), false) } } }
        }
    }
}
