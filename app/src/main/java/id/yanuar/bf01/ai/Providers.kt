package id.yanuar.bf01.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.util.Audio
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

interface SttProvider { suspend fun transcribe(pcm16k: ShortArray): String }
interface ChatProvider { suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String }
/** Mengembalikan PCM mono 16 kHz. */
interface TtsProvider { suspend fun speak(text: String): ShortArray }

class ApiException(msg: String) : Exception(msg)

/**
 * xAI. Bentuk endpoint diambil dari docs.x.ai (dibaca 22 Okt 2026):
 *  - STT  POST https://api.x.ai/v1/stt  multipart: file, model=grok-voice-transcribe-2.0, language
 *  - TTS  POST https://api.x.ai/v1/tts  JSON: text, voice_id, language, output_format{codec,sample_rate}
 *  - Chat POST https://api.x.ai/v1/chat/completions (format OpenAI)
 */
object Xai {
    const val BASE = "https://api.x.ai/v1"
    val http: OkHttpClient by lazy { // dibuat sekali (pool koneksi & thread dibagikan ke semua panggilan)
        OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    }
    fun call(req: Request): ByteArray = http.newCall(req).execute().use { r ->
        val body = r.body?.bytes() ?: ByteArray(0)
        if (!r.isSuccessful) throw ApiException("xAI ${r.code}: ${String(body).take(200)}")
        body
    }
}

class XaiStt(private val prefs: Prefs) : SttProvider {
    override suspend fun transcribe(pcm16k: ShortArray): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "grok-voice-transcribe-2.0")
            .addFormDataPart("language", "id")
            .addFormDataPart("file", "audio.wav", Audio.wav(pcm16k).toRequestBody("audio/wav".toMediaType()))
            .build()
        val res = Xai.call(Request.Builder().url("${Xai.BASE}/stt").header("Authorization", "Bearer ${prefs.xaiKey}").post(body).build())
        // TODO [BELUM DICEK]: nama field hasil. Diasumsikan {"text": "..."}.
        return JSONObject(String(res)).optString("text").trim()
    }
}

class XaiChat(private val prefs: Prefs) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
        msgs.put(JSONObject().put("role", "user").put("content", user))
        // TODO [BELUM DICEK]: nama model default ("grok-4-fast") bisa diubah di Pengaturan.
        val req = JSONObject().put("model", prefs.chatModel).put("messages", msgs).put("max_tokens", 200)
        val res = Xai.call(Request.Builder().url("${Xai.BASE}/chat/completions").header("Authorization", "Bearer ${prefs.xaiKey}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build())
        return JSONObject(String(res)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}

/** Chat format OpenAI generik (dipakai Qwen/DashScope compatible-mode). */
class OpenAiCompatChat(private val name: String, private val base: () -> String, private val key: () -> String, private val model: () -> String) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        if (key().isBlank()) throw ApiException("API key $name belum diisi di Pengaturan")
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
        msgs.put(JSONObject().put("role", "user").put("content", user))
        val req = JSONObject().put("model", model()).put("messages", msgs).put("max_tokens", 200)
        val r = Request.Builder().url("${base().trimEnd('/')}/chat/completions").header("Authorization", "Bearer ${key()}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build()
        val res = Xai.http.newCall(r).execute().use { resp ->
            val b = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) throw ApiException("$name ${resp.code}: ${String(b).take(200)}")
            b
        }
        return JSONObject(String(res)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}

/** Pilih otak AI sesuai Pengaturan. */
fun chatProvider(prefs: Prefs): ChatProvider = if (prefs.llmProvider == "qwen")
    OpenAiCompatChat("Qwen", { prefs.qwenBaseUrl }, { prefs.qwenKey }, { prefs.qwenModel }) else XaiChat(prefs)

/**
 * Qwen3-ASR-Flash lewat DashScope compatible-mode (dicek di dokumen resmi Alibaba Cloud Model Studio, 23 Okt 2026):
 *  POST {base}/chat/completions, model=qwen3-asr-flash, content=[{type:input_audio, input_audio:{data:"data:audio/wav;base64,..."}}],
 *  asr_options.language="id" (Indonesia didukung). Maks 10 MB / 5 menit. Hasil di choices[0].message.content.
 */
class QwenStt(private val prefs: Prefs) : SttProvider {
    companion object {
        const val MODEL = "qwen3-asr-flash"
        fun body(wav: ByteArray): JSONObject {
            val data = "data:audio/wav;base64," + java.util.Base64.getEncoder().encodeToString(wav)
            val content = JSONArray().put(JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", data)))
            return JSONObject().put("model", MODEL).put("stream", false)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("asr_options", JSONObject().put("language", "id").put("enable_itn", false))
        }
        fun parse(res: String): String =
            JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
    }
    override suspend fun transcribe(pcm16k: ShortArray): String {
        if (prefs.qwenKey.isBlank()) throw ApiException("API key Qwen belum diisi di Setelan")
        val r = Request.Builder().url("${prefs.qwenBaseUrl.trimEnd('/')}/chat/completions").header("Authorization", "Bearer ${prefs.qwenKey}")
            .post(body(Audio.wav(pcm16k)).toString().toRequestBody("application/json".toMediaType())).build()
        val res = Xai.http.newCall(r).execute().use { resp ->
            val b = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) throw ApiException("Qwen ASR ${resp.code}: ${String(b).take(200)}")
            b
        }
        return parse(String(res))
    }
}

/** Rangkaian AI yang dipakai: semuanya mengikuti satu pilihan penyedia. */
data class AiStack(val stt: String, val chat: String, val tts: String)
fun aiStack(prefs: Prefs): AiStack = if (prefs.llmProvider == "qwen")
    // Suara sistem Qwen TTS (qwen3-tts-flash / qwen-audio-tts) belum mendukung bahasa Indonesia -> TTS sistem id-ID.
    AiStack("qwen", "qwen", "system")
else AiStack(if (prefs.sttProvider == "system" || prefs.xaiKey.isBlank()) "system" else "xai", "grok",
    if (prefs.ttsProvider == "system" || prefs.xaiKey.isBlank()) "system" else "xai")

class XaiTts(private val prefs: Prefs) : TtsProvider {
    override suspend fun speak(text: String): ShortArray {
        val req = JSONObject().put("text", text).put("voice_id", prefs.ttsVoice).put("language", "id")
            .put("output_format", JSONObject().put("codec", "wav").put("sample_rate", Audio.RATE))
        val res = Xai.call(Request.Builder().url("${Xai.BASE}/tts").header("Authorization", "Bearer ${prefs.xaiKey}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build())
        // Respons = byte audio mentah (WAV karena codec=wav). [BELUM DIUJI dengan key asli]
        val (pcm, rate) = Audio.parseWav(res)
        return Audio.resample(pcm, rate)
    }
}

/** Cadangan: TTS sistem Android id-ID (gratis, offline jika paket suara terpasang). */
class SystemTts(private val ctx: Context) : TtsProvider {
    private var tts: TextToSpeech? = null
    private suspend fun engine(): TextToSpeech = tts ?: suspendCancellableCoroutine { k ->
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(ctx) { val e = holder[0]!!; e.language = Locale("id", "ID"); tts = e; k.resume(e) }
    }
    override suspend fun speak(text: String): ShortArray {
        val e = engine(); val f = File.createTempFile("tts", ".wav", ctx.cacheDir)
        val ok = suspendCancellableCoroutine { k ->
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (k.isActive) k.resume(true) }
                @Deprecated("") override fun onError(id: String?) { if (k.isActive) k.resume(false) }
            })
            e.synthesizeToFile(text, null, f, "bf")
        }
        val bytes = f.readBytes(); f.delete()
        if (!ok) throw ApiException("TTS sistem gagal")
        val (pcm, rate) = Audio.parseWav(bytes)
        return Audio.resample(pcm, rate)
    }
}

/**
 * Cadangan STT: SpeechRecognizer Android.
 * STUB: SpeechRecognizer biasanya merekam dari mic HP sendiri; menyuapkan audio dari BF
 * butuh EXTRA_AUDIO_SOURCE (API 33+) dan dukungan dari mesin pengenal. [BELUM DIUJI]
 */
class SystemStt : SttProvider {
    override suspend fun transcribe(pcm16k: ShortArray): String =
        throw ApiException("STT cadangan (SpeechRecognizer) belum diimplementasikan; pakai xAI")
}

/** Mode Cepat (realtime speech-to-speech, wss://api.x.ai/v1/realtime). STUB: hanya toggle, belum tersambung. */
object ModeCepat { const val IMPLEMENTED = false }
