package id.yanuar.bf01.svc

import id.yanuar.bf01.ble.BfProtocol.RemoteNote
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingSyncTest {
    @Test fun batteryDefer() {
        assertTrue(RecordingSync.shouldDefer(29, false))
        assertFalse(RecordingSync.shouldDefer(29, true))
        assertFalse(RecordingSync.shouldDefer(30, false))
        assertFalse(RecordingSync.shouldDefer(-1, false))  // tidak diketahui -> jalan
    }
    @Test fun backfillPicksMissingOnly() {
        val r = listOf(RemoteNote("VN_1.wav", 9000, 1, "NOTE", true), RemoteNote("VN_2.wav", 9000, 1, "TASK", false),
            RemoteNote("VN_3.wav", 9000, 1, "PENDING", false), RemoteNote("VN_4.wav", 44, 0, "NOTE", false))
        // VN_1 sudah synced di BF tapi audionya belum di HP -> tetap diambil (backfill)
        assertEquals(listOf("VN_1.wav", "VN_2.wav"), RecordingSync.toDownload(r, emptySet()).map { it.name })
        assertEquals(listOf("VN_2.wav"), RecordingSync.toDownload(r, setOf("VN_1.wav")).map { it.name })
    }
    @Test fun chunking() {
        assertEquals(1, RecordingSync.chunks(ShortArray(16000 * 240)).size)
        val c = RecordingSync.chunks(ShortArray(16000 * 500))
        assertEquals(3, c.size); assertEquals(16000 * 500, c.sumOf { it.size })
    }
    @Test fun metaParsing() {
        val (s, t, ti) = RecordingSync.parseMeta("```json\n{\"summary\":\"Beli baterai\",\"tags\":[\"Belanja\",\"bf\"],\"title\":\"Beli baterai LiPo\"}\n```")
        assertEquals("Beli baterai", s); assertEquals("belanja,bf", t); assertEquals("Beli baterai LiPo", ti)
        assertEquals("teks biasa", RecordingSync.parseMeta("teks biasa").first)
    }
}
