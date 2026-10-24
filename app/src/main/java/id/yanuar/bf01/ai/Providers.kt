package id.yanuar.bf01.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.util.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

open class ApiException(msg: String) : Exception(msg)

/** Why an AI call failed, in words a person understands. The key is never part of the message. */
enum class AiFail { NO_KEY, BAD_KEY, NO_INTERNET, TIMEOUT, QUOTA, BAD_MODEL, DOWN, BAD_REPLY, OTHER }
class AiError(val kind: AiFail, val provider: String, val code: Int = 0, detail: String = "") :
    ApiException("$provider ${kind.name}${if (code > 0) " $code" else ""}${if (detail.isNotBlank()) ": $detail" else ""}")

/** Plain-English message for any AI failure, shown in the app. */
fun friendlyError(e: Throwable): String {
    val a = e as? AiError ?: classify("AI", e)
    val p = a.provider
    return when (a.kind) {
        AiFail.NO_KEY -> "No $p API key yet. Add one in Settings > AI provider."
        AiFail.BAD_KEY -> "$p didn't accept the API key. Check it was copied fully, and for Qwen that the key's region matches."
        AiFail.NO_INTERNET -> "Can't reach $p. Check the phone's internet connection."
        AiFail.TIMEOUT -> "$p took too long to answer. Try again."
        AiFail.QUOTA -> "$p says the quota or balance has run out, or too many requests. Check your $p account."
        AiFail.BAD_MODEL -> "$p doesn't know the chat model name. Check the model in Settings > AI provider."
        AiFail.DOWN -> "$p is having problems right now (error ${a.code}). Try again in a bit."
        AiFail.BAD_REPLY -> "$p sent back an empty or unreadable reply. Try again."
        AiFail.OTHER -> "Something went wrong talking to $p${if (a.code > 0) " (error ${a.code})" else ""}."
    }
}

internal fun classify(provider: String, e: Throwable): AiError = when (e) {
    is AiError -> e
    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException,
    is javax.net.ssl.SSLException -> AiError(AiFail.NO_INTERNET, provider, detail = e.javaClass.simpleName)
    is java.io.InterruptedIOException -> AiError(AiFail.TIMEOUT, provider)
    is org.json.JSONException -> AiError(AiFail.BAD_REPLY, provider)
    else -> AiError(AiFail.OTHER, provider, detail = e.javaClass.simpleName)
}

internal fun httpFail(provider: String, code: Int, body: String): AiError {
    val b = body.lowercase()
    val kind = when {
        code == 401 || code == 403 || "invalidapikey" in b || "incorrect api key" in b -> AiFail.BAD_KEY
        code == 429 || code == 402 || "quota" in b || "arrearage" in b || "insufficient" in b -> AiFail.QUOTA
        code == 404 || "model" in b && ("not found" in b || "not exist" in b || "does not exist" in b) -> AiFail.BAD_MODEL
        code == 400 && "api key" in b -> AiFail.BAD_KEY
        code >= 500 -> AiFail.DOWN
        else -> AiFail.OTHER
    }
    return AiError(kind, provider, code, body.take(160))
}

/**
 * xAI. Endpoint shape from docs.x.ai:
 *  - STT  POST https://api.x.ai/v1/stt  multipart: file, model=grok-voice-transcribe-2.0, language
 *  - TTS  POST https://api.x.ai/v1/tts  JSON
 *  - Chat POST https://api.x.ai/v1/chat/completions (OpenAI format)
 */
object Xai {
    const val BASE = "https://api.x.ai/v1"
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
    /** All AI HTTP goes through here: always off the main thread, errors classified. */
    suspend fun call(req: Request, provider: String = "Grok"): ByteArray = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.bytes() ?: ByteArray(0)
                if (!r.isSuccessful) throw httpFail(provider, r.code, String(body))
                body
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { throw classify(provider, e) }
    }
}

internal fun chatBody(model: String, system: String, history: List<Pair<String, String>>, user: String, maxTokens: Int = 400): JSONObject {
    val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
    history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
    msgs.put(JSONObject().put("role", "user").put("content", user))
    val j = JSONObject().put("model", model).put("messages", msgs).put("max_tokens", maxTokens)
    if (model.startsWith("grok-4.3")) j.put("reasoning_effort", "none") // quick replies; reasoning would eat the token budget
    return j
}

internal fun chatReply(provider: String, res: ByteArray): String = try {
    JSONObject(String(res)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
} catch (e: org.json.JSONException) { throw AiError(AiFail.BAD_REPLY, provider) }.ifBlank { throw AiError(AiFail.BAD_REPLY, provider) }

class XaiStt(private val prefs: Prefs) : SttProvider {
    override suspend fun transcribe(pcm16k: ShortArray): String {
        if (prefs.xaiKey.isBlank()) throw AiError(AiFail.NO_KEY, "Grok")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "grok-voice-transcribe-2.0")
            .addFormDataPart("language", "id")
            .addFormDataPart("file", "audio.wav", Audio.wav(pcm16k).toRequestBody("audio/wav".toMediaType()))
            .build()
        val res = Xai.call(Request.Builder().url("${Xai.BASE}/stt").header("Authorization", "Bearer ${prefs.xaiKey}").post(body).build())
        return JSONObject(String(res)).optString("text").trim()
    }
}

class XaiChat(private val prefs: Prefs, private val base: String = Xai.BASE) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        if (prefs.xaiKey.isBlank()) throw AiError(AiFail.NO_KEY, "Grok")
        val req = chatBody(prefs.chatModel, system, history, user)
        val res = Xai.call(Request.Builder().url("$base/chat/completions").header("Authorization", "Bearer ${prefs.xaiKey}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build())
        return chatReply("Grok", res)
    }
}

/** Generic OpenAI-format chat (Qwen / DashScope compatible-mode). */
class OpenAiCompatChat(private val name: String, private val base: () -> String, private val key: () -> String, private val model: () -> String) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        if (key().isBlank()) throw AiError(AiFail.NO_KEY, name)
        val req = chatBody(model(), system, history, user)
        val r = Request.Builder().url("${base().trimEnd('/')}/chat/completions").header("Authorization", "Bearer ${key()}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build()
        return chatReply(name, Xai.call(r, name))
    }
}

/**
 * Qwen with region auto-detect: an international (Singapore) key gets 401 on the China endpoint and vice versa.
 * Try the saved Base URL first; on BAD_KEY try the other region once and remember the one that works.
 * A custom (workspace) Base URL is used as is.
 */
class QwenChat(private val prefs: Prefs) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String =
        qwenWithRegion(prefs) { base -> OpenAiCompatChat("Qwen", { base }, { prefs.qwenKey }, { prefs.qwenModel }).chat(system, history, user) }
}

internal suspend fun <T> qwenWithRegion(prefs: Prefs, block: suspend (String) -> T): T {
    if (prefs.qwenKey.isBlank()) throw AiError(AiFail.NO_KEY, "Qwen")
    val first = prefs.qwenBaseUrl.trimEnd('/')
    val other = when (first) { Prefs.QWEN_BASE_INTL -> Prefs.QWEN_BASE_CN; Prefs.QWEN_BASE_CN -> Prefs.QWEN_BASE_INTL; else -> null }
    return try { block(first) } catch (e: AiError) {
        if (e.kind != AiFail.BAD_KEY || other == null) throw e
        val r = try { block(other) } catch (e2: AiError) { throw e } // both regions failed: report the first (saved) region's reason
        prefs.qwenBaseUrl = other; r
    }
}

fun qwenRegionName(base: String): String = when (base.trimEnd('/')) {
    Prefs.QWEN_BASE_INTL -> "International (Singapore)"; Prefs.QWEN_BASE_CN -> "China (Beijing)"; else -> "Custom URL" }

/** Choose the AI brain from Settings. */
fun chatProvider(prefs: Prefs): ChatProvider = if (prefs.llmProvider == "qwen") QwenChat(prefs) else XaiChat(prefs)

/** "Test key": one tiny chat request. Returns a line to show, e.g. "Connected · Qwen · International (Singapore)". */
suspend fun testKey(prefs: Prefs, provider: ChatProvider = chatProvider(prefs)): Result<String> = runCatching {
    provider.chat("Reply with one word: OK", emptyList(), "ping")
    if (prefs.llmProvider == "qwen") "Connected · Qwen · ${qwenRegionName(prefs.qwenBaseUrl)} · ${prefs.qwenModel}"
    else "Connected · Grok · ${prefs.chatModel}"
}

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
        val body = body(Audio.wav(pcm16k)).toString()
        return qwenWithRegion(prefs) { base ->
            val r = Request.Builder().url("$base/chat/completions").header("Authorization", "Bearer ${prefs.qwenKey}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            parse(String(Xai.call(r, "Qwen")))
        }
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
