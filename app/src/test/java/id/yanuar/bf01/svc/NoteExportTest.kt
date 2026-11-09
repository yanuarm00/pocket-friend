package id.yanuar.bf01.svc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Note
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteExportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun wavNote(): Note {
        val f = File(File(ctx.filesDir, "recordings").apply { mkdirs() }, "VN_20261030_101500.wav")
        f.writeBytes(ByteArray(1000) { it.toByte() })
        return Note(7, f.name, 0, "halo ini transkrip lengkap", "Short summary", "", localPath = f.absolutePath)
    }

    @Test fun textHasSummaryThenTranscript() {
        assertEquals("Summary: Short summary\n\nhalo ini transkrip lengkap", NoteExport.text(wavNote()))
        assertEquals("only text", NoteExport.text(Note(1, "a.wav", 0, "only text", "", "")))
        assertEquals("VN_20261030_101500", NoteExport.baseName(wavNote()))
    }

    @Test fun shareAudioUsesFileProviderWithText() {
        val i = NoteExport.shareAudioIntent(ctx, wavNote())!!
        assertEquals(Intent.ACTION_SEND, i.action); assertEquals("audio/wav", i.type)
        @Suppress("DEPRECATION") val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme); assertEquals(ctx.packageName + ".apkfiles", uri.authority)
        assertTrue(uri.path!!.endsWith("/VN_20261030_101500.wav"))
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(i.getStringExtra(Intent.EXTRA_TEXT)!!.contains("transkrip lengkap"))
    }

    @Test fun missingAudioIsHandled() {
        val n = Note(2, "gone.wav", 0, "t", "", "", localPath = "/nope/gone.wav")
        assertNull(NoteExport.audioFile(n)); assertNull(NoteExport.shareAudioIntent(ctx, n))
        assertNull(NoteExport.audioFile(Note(3, "x.wav", 0, "t", "", "")))
        assertEquals("text/plain", NoteExport.shareTextIntent(n).type)
    }

    @Test fun saveToDownloadsWritesWavAndTxt() {
        assertFalse(NoteExport.needsPermission())
        assertEquals(listOf("VN_20261030_101500.wav", "VN_20261030_101500.txt"), NoteExport.saveToDownloads(ctx, wavNote()))
        assertEquals(listOf("gone.txt"), NoteExport.saveToDownloads(ctx, Note(2, "gone.wav", 0, "t", "", "", localPath = "/nope/gone.wav")))
    }
}
