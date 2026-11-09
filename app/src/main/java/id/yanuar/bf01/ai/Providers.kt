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
enum class AiFail { NO_KEY, BAD_KEY, NO_INTERNET, TIMEOUT, QUOTA, BAD_MODEL, NOT_ACTIVATED, DOWN, BAD_REPLY, OTHER }
class AiError(val kind: AiFail, val provider: String, val code: Int = 0, val detail: String = "") :
    ApiException("$provider ${kind.name}${if (code > 0) " $code" else ""}${if (detail.isNotBlank()) ": $detail" else ""}")

/** Plain-English message for any AI failure, shown in the app. */
fun friendlyError(e: Throwable): String {
    val a = e as? AiError ?: classify("AI", e)
    val p = a.provider
    return when (a.kind) {
        AiFail.NO_KEY -> "No $p API key yet. Add it in Settings > AI provider."
        AiFail.BAD_KEY -> "$p rejected the API key. Check that the whole key was copied, and for Qwen that the region matches."
        AiFail.NO_INTERNET -> "Can't reach $p. Check your phone's internet."
        AiFail.TIMEOUT -> "$p took too long to answer. Try again in a moment."
        AiFail.QUOTA -> "$p says the quota or balance ran out, or there were too many requests. Check your $p account."
        AiFail.BAD_MODEL -> "$p doesn't know this model name. Check the model in Settings > AI provider." + serverSays(a.detail)
        AiFail.NOT_ACTIVATED -> "This model isn't active on your Alibaba account yet. Pick another model or turn it on in Model Studio."
        AiFail.DOWN -> "$p is having problems (error ${a.code}). Try again later."
        AiFail.BAD_REPLY -> "$p's answer was empty or unreadable. Try again."
        AiFail.OTHER -> "Problem reaching $p${if (a.code > 0) " (error ${a.code})" else ""}." + serverSays(a.detail)
    }
}

/** Pesan asli dari server (tanpa kunci), supaya penyebab sebenarnya terlihat. */
internal fun serverSays(detail: String): String {
    val m = runCatching { JSONObject(detail).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: Regex("\"message\"\\s*:\\s*\"([^\"]+)").find(detail)?.groupValues?.get(1)
    return m?.let { " Server message: ${it.take(140)}" } ?: ""
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
        code == 403 && "unpurchased" in b -> AiFail.NOT_ACTIVATED
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
    /** 0.3.9.8.8: plain OpenAI path (not Qwen's /compatible-mode normalisation, which gave a 404). */
    fun chatUrl(base: String = BASE) = base.trim().trimEnd('/').removeSuffix("/chat/completions") + "/chat/completions"
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
    }
    /** All AI HTTP goes through here: always off the main thread, errors classified. */
    suspend fun call(req: Request, provider: String = "Grok", usage: UsageTag? = null): ByteArray = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.bytes() ?: ByteArray(0)
                if (!r.isSuccessful) throw httpFail(provider, r.code, String(body))
                if (usage != null) UsageLog.record(usage.provider, usage.model, usage.kind, body, usage.fallbackSec)
                body
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { throw classify(provider, e) }
    }
}

/** v0.3.9: suhu sampling opsional untuk satu panggilan (mis. ingatan otomatis = 0.0). withContext(ChatTemperature(0.0)) { ... } */
class ChatTemperature(val value: Double) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ChatTemperature>
}

internal fun chatBody(model: String, system: String, history: List<Pair<String, String>>, user: String, maxTokens: Int = 400, temperature: Double? = null): JSONObject {
    val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
    history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t)) }
    msgs.put(JSONObject().put("role", "user").put("content", user))
    val j = JSONObject().put("model", model).put("messages", msgs).put("max_tokens", maxTokens)
    if (temperature != null) j.put("temperature", temperature)
    if (model.startsWith("grok-4.3")) j.put("reasoning_effort", "none") // quick replies; reasoning would eat the token budget
    return j
}

internal fun chatReply(provider: String, res: ByteArray): String = try {
    JSONObject(String(res)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
} catch (e: org.json.JSONException) { throw AiError(AiFail.BAD_REPLY, provider) }.ifBlank { throw AiError(AiFail.BAD_REPLY, provider) }

const val XAI_STT_MODEL = "grok-voice-transcribe-2.0"

class XaiStt(private val prefs: Prefs) : SttProvider {
    override suspend fun transcribe(pcm16k: ShortArray): String {
        if (prefs.xaiKey.isBlank()) throw AiError(AiFail.NO_KEY, "Grok")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", XAI_STT_MODEL)
            .addFormDataPart("language", "id")
            .addFormDataPart("file", "audio.wav", Audio.wav(pcm16k).toRequestBody("audio/wav".toMediaType()))
            .build()
        val res = Xai.call(Request.Builder().url("${Xai.BASE}/stt").header("Authorization", "Bearer ${prefs.xaiKey}").post(body).build(),
            usage = UsageTag("grok", XAI_STT_MODEL, "asr", pcm16k.size / 16000.0))
        return JSONObject(String(res)).optString("text").trim()
    }
}

class XaiChat(private val prefs: Prefs, private val base: String = Xai.BASE) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        if (prefs.xaiKey.isBlank()) throw AiError(AiFail.NO_KEY, "Grok")
        val req = chatBody(prefs.chatModel, system, history, user, temperature = kotlin.coroutines.coroutineContext[ChatTemperature]?.value)
        val res = Xai.call(Request.Builder().url(Xai.chatUrl(base)).header("Authorization", "Bearer ${prefs.xaiKey}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build(), usage = UsageTag("grok", prefs.chatModel, "chat"))
        return chatReply("Grok", res)
    }
}

/** Generic OpenAI-format chat (Qwen / DashScope compatible-mode). */
class OpenAiCompatChat(private val name: String, private val base: () -> String, private val key: () -> String, private val model: () -> String) : ChatProvider {
    override suspend fun chat(system: String, history: List<Pair<String, String>>, user: String): String {
        if (key().isBlank()) throw AiError(AiFail.NO_KEY, name)
        val req = chatBody(model(), system, history, user, temperature = kotlin.coroutines.coroutineContext[ChatTemperature]?.value)
        val r = Request.Builder().url("${base().trimEnd('/')}/chat/completions").header("Authorization", "Bearer ${key()}")
            .post(req.toString().toRequestBody("application/json".toMediaType())).build()
        return chatReply(name, Xai.call(r, name, UsageTag(name.lowercase(), model(), "chat")))
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
    val first = QwenUrl.normalize(prefs.qwenBaseUrl) ?: prefs.qwenBaseUrl.trimEnd('/')
    if (!first.startsWith("https://", true)) throw AiError(AiFail.OTHER, "Qwen", detail = "Base URL must start with https://")
    val other = when (first) { Prefs.QWEN_BASE_INTL -> Prefs.QWEN_BASE_CN; Prefs.QWEN_BASE_CN -> Prefs.QWEN_BASE_INTL; else -> null }
    return try { block(first) } catch (e: AiError) {
        if (e.kind != AiFail.BAD_KEY || other == null) throw e
        val r = try { block(other) } catch (e2: AiError) { throw e } // both regions failed: report the first (saved) region's reason
        prefs.qwenBaseUrl = other; r
    }
}

fun qwenRegionName(base: String): String = QwenUrl.regionName(base)

/** Choose the AI brain from Settings. */
fun chatProvider(prefs: Prefs): ChatProvider = if (prefs.llmProvider == "qwen") QwenChat(prefs) else XaiChat(prefs)

/** "Test key": one tiny chat request. Returns a line to show, e.g. "Connected · Qwen · International (Singapore)". */
suspend fun testKey(prefs: Prefs, provider: ChatProvider = chatProvider(prefs)): Result<String> = runCatching {
    provider.chat("Reply with one word: OK", emptyList(), "ping")
    if (prefs.llmProvider == "qwen") "Connected · Qwen · ${qwenRegionName(prefs.qwenBaseUrl)} · ${prefs.qwenModel} · ${QwenUrl.host(prefs.qwenBaseUrl)}"
    else "Connected · Grok · ${prefs.chatModel}"
}

/** Who is answering right now, for error messages: "Qwen · qwen-flash-character" or "Grok · grok-4.3". */
fun chatLabel(prefs: Prefs): String = if (prefs.llmProvider == "qwen") "Qwen · ${prefs.qwenModel}" else "Grok · ${prefs.chatModel}"
/** Who turns speech into text right now. */
fun sttLabel(prefs: Prefs): String = if (prefs.llmProvider == "qwen") "Qwen · ${prefs.qwenAsrModel}" else "Grok · grok-voice-transcribe-2.0"
fun speechToText(prefs: Prefs): SttProvider = if (prefs.llmProvider == "qwen") QwenStt(prefs) else XaiStt(prefs)

/** What a person sees under Test speech. ok = the model heard the test sentence. */
data class SpeechTestResult(val ok: Boolean, val text: String)

/**
 * "Test speech": speaks a short Indonesian sentence with the phone's own voice, sends it to the speech-to-text model
 * and shows what came back. If the phone has no voice to speak with, sends a short tone instead, which only proves
 * that the key, model and address accept audio.
 */
suspend fun testSpeech(stt: SttProvider, label: String, speak: suspend () -> ShortArray?): SpeechTestResult {
    val said = runCatching { speak() }.getOrNull()?.takeIf { it.size > 1600 }
    val pcm = said ?: ShortArray(16000) { (6000 * Math.sin(2 * Math.PI * 440 * it / 16000.0)).toInt().toShort() }
    val heard = try { stt.transcribe(pcm) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
        return SpeechTestResult(false, "Failed · $label · " + friendlyError(e)) }
    return when {
        said == null -> SpeechTestResult(true, "Connected · $label accepted the audio. This phone has no voice to speak the test sentence, so nothing was heard.")
        heard.isBlank() -> SpeechTestResult(false, "$label answered, but heard nothing. Check the speech model in Settings > AI provider.")
        else -> SpeechTestResult(true, "Heard: \"$heard\" · $label")
    }
}

/**
 * Qwen3-ASR-Flash lewat DashScope compatible-mode (dicek di dokumen resmi Alibaba Cloud Model Studio, 23 Okt 2026):
 *  POST {base}/chat/completions, model=qwen3-asr-flash, content=[{type:input_audio, input_audio:{data:"data:audio/wav;base64,..."}}],
 *  asr_options.language="id" (Indonesia didukung). Maks 10 MB / 5 menit. Hasil di choices[0].message.content.
 */
class QwenStt(private val prefs: Prefs) : SttProvider {
    companion object {
        const val MODEL = "qwen3-asr-flash"
        fun isNative(model: String) = model.startsWith("qwen-audio")
        private fun content(wav: ByteArray): JSONArray {
            val data = "data:audio/wav;base64," + java.util.Base64.getEncoder().encodeToString(wav)
            return JSONArray().put(JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", data)))
        }
        private fun msgs(wav: ByteArray) = JSONArray().put(JSONObject().put("role", "user").put("content", content(wav)))
        /** OpenAI-compatible body (qwen3-asr-flash): POST {base}/chat/completions. */
        fun body(wav: ByteArray, model: String = MODEL): JSONObject = JSONObject().put("model", model).put("stream", false)
            .put("messages", msgs(wav)).put("asr_options", JSONObject().put("language", "id").put("enable_itn", false))
        /** Native body (qwen-audio-3.0-asr-flash): POST /api/v1/services/aigc/multimodal-generation/generation. */
        fun nativeBody(wav: ByteArray, model: String): JSONObject = JSONObject().put("model", model)
            .put("input", JSONObject().put("messages", msgs(wav)))
            .put("parameters", JSONObject().put("format", "wav").put("sample_rate", "16000")
                .put("language_hints", JSONArray().put("id")))
        /** v0.3.9.6: toleran. output.choices[0].message.content[0].text (native qwen-audio) atau choices[0].message.content
         *  (compatible). Tidak ada teks = "" (tidak ada suara), bukan error. Respons bukan JSON / ada "code" error = exception. */
        fun parse(res: String): String {
            val j = try { JSONObject(res) } catch (e: Exception) { throw ApiException("ASR response is not JSON: ${res.take(120)}") }
            if (j.has("code") && j.optString("code").isNotBlank() && !j.has("output")) throw ApiException("ASR: ${j.optString("code")} ${j.optString("message").take(100)}")
            val choices = j.optJSONObject("output")?.optJSONArray("choices") ?: j.optJSONArray("choices")
            val msg = choices?.optJSONObject(0)?.optJSONObject("message")
            val c = msg?.opt("content")
            return when (c) {
                is JSONArray -> (0 until c.length()).joinToString("") { c.optJSONObject(it)?.optString("text").orEmpty() }
                is String -> c
                else -> j.optJSONObject("output")?.optString("text").orEmpty()
            }.trim()
        }
    }
    override suspend fun transcribe(pcm16k: ShortArray): String {
        val model = prefs.qwenAsrModel
        val wav = Audio.wav(pcm16k)
        val native = isNative(model)
        val body = (if (native) nativeBody(wav, model) else body(wav, model)).toString()
        // 0.3.9.8.6: Qwen answers 400 ASR_RESPONSE_HAVE_NO_WORDS for silence; that is "no speech", not a failure
        return try { qwenAsr(model, native, body, pcm16k) } catch (e: Exception) { if (isNoWords(e)) "" else throw e }
    }
    private suspend fun qwenAsr(model: String, native: Boolean, body: String, pcm16k: ShortArray): String {
        return qwenWithRegion(prefs) { base ->
            val url = if (native) QwenUrl.nativeMultimodalUrl(base) else QwenUrl.chatUrl(base)
            val b = Request.Builder().url(url).header("Authorization", "Bearer ${prefs.qwenKey}")
            if (native) b.header("X-DashScope-SSE", "disable")
            val raw = String(Xai.call(b.post(body.toRequestBody("application/json".toMediaType())).build(), "Qwen", UsageTag("qwen", model, "asr", pcm16k.size / 16000.0)))
            android.util.Log.i("BF", "ASR ${pcm16k.size / 16} ms audio, response: ${raw.take(300)}")  // no key
            parse(raw)
        }
    }
}

/** Rangkaian AI yang dipakai: semuanya mengikuti satu pilihan penyedia. */
data class AiStack(val stt: String, val chat: String, val tts: String)
fun aiStack(prefs: Prefs): AiStack = if (prefs.llmProvider == "qwen")
    // Suara sistem Qwen TTS (qwen3-tts-flash / qwen-audio-tts) belum mendukung bahasa Indonesia -> TTS sistem id-ID.
    AiStack("qwen", "qwen", "system")
else AiStack("xai", "grok",   // speech-to-text must be a cloud service; without a key XaiStt says "No Grok API key yet" instead of failing silently
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
    fun close() { tts?.shutdown(); tts = null }
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
        if (!ok) throw ApiException("System TTS failed")
        val (pcm, rate) = Audio.parseWav(bytes)
        return Audio.resample(pcm, rate)
    }
}

/** Mode Cepat (realtime speech-to-speech, wss://api.x.ai/v1/realtime). STUB: hanya toggle, belum tersambung. */
object ModeCepat { const val IMPLEMENTED = false }

/** 0.3.9.8.6: Qwen's "no words heard" reply (HTTP 400 ASR_RESPONSE_HAVE_NO_WORDS). */
fun isNoWords(e: Throwable): Boolean = generateSequence(e) { it.cause }.any { t ->
    "ASR_RESPONSE_HAVE_NO_WORDS" in (t.message ?: "") || (t is AiError && "ASR_RESPONSE_HAVE_NO_WORDS" in t.detail) }
