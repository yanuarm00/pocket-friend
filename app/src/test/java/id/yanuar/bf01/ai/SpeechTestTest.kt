package id.yanuar.bf01.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpeechTestTest {
    private fun stt(f: (ShortArray) -> String) = object : SttProvider { override suspend fun transcribe(pcm16k: ShortArray) = f(pcm16k) }
    private val voice = ShortArray(16000 * 2)

    @Test fun heardSentence() = runBlocking {
        val r = testSpeech(stt { "halo ini tes suara" }, "Qwen · qwen3-asr-flash") { voice }
        assertTrue(r.ok); assertTrue(r.text.contains("halo ini tes suara")); assertTrue(r.text.contains("Qwen · qwen3-asr-flash"))
    }
    @Test fun nothingHeardIsNotOk() = runBlocking {
        val r = testSpeech(stt { "" }, "Qwen · m") { voice }
        assertFalse(r.ok); assertTrue(r.text.contains("heard nothing"))
    }
    @Test fun failureNamesModelAndReason() = runBlocking {
        val r = testSpeech(stt { throw AiError(AiFail.BAD_KEY, "Qwen") }, "Qwen · qwen3-asr-flash") { voice }
        assertFalse(r.ok); assertTrue(r.text.startsWith("Failed · Qwen · qwen3-asr-flash")); assertTrue(r.text.contains("rejected the API key"))
    }
    @Test fun noPhoneVoiceFallsBackToTone() = runBlocking {
        var sent = 0
        val r = testSpeech(stt { sent = it.size; "" }, "Qwen · m") { null }
        assertEquals(16000, sent); assertTrue(r.ok); assertTrue(r.text.contains("accepted the audio"))
    }
    @Test fun phoneVoiceCrashFallsBackToTone() = runBlocking {
        val r = testSpeech(stt { "" }, "Qwen · m") { throw IllegalStateException("tts") }
        assertTrue(r.ok)
    }
}
