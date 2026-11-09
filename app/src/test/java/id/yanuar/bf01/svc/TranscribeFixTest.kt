package id.yanuar.bf01.svc

import id.yanuar.bf01.ai.AiError
import id.yanuar.bf01.ai.AiFail
import id.yanuar.bf01.ai.isNoWords
import id.yanuar.bf01.ble.BfProtocol
import org.junit.Assert.*
import org.junit.Test

class TranscribeFixTest {
    @Test fun renames1970FilesOnly() {
        val t = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse("2026-10-29 08:17:05")!!.time
        assertEquals("VN_20261029_081705.wav", RecordingSync.localName("VN_19700101_070047.wav", t))
        assertEquals("VN_20261028_101010.wav", RecordingSync.localName("VN_20261028_101010.wav", t))
    }
    @Test fun noWordsIsNotAnError() {
        assertTrue(isNoWords(AiError(AiFail.OTHER, "Qwen", 400, """{"code":"CLIENT_ERROR","message":"ASR_RESPONSE_HAVE_NO_WORDS"}""")))
        assertTrue(isNoWords(Exception("Qwen OTHER 400 {\"message\":\"ASR_RESPONSE_HAVE_NO_WORDS\"}")))
        assertFalse(isNoWords(AiError(AiFail.BAD_KEY, "Qwen", 401, "bad key")))
    }
    @Test fun wifiEventsLogReadably() {
        assertEquals("WifiFail (BF couldn't join Wi-Fi)", "${BfProtocol.Event.WifiFail}")
        assertEquals("WifiOff", "${BfProtocol.Event.WifiOff}")
    }
}
