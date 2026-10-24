package id.yanuar.bf01.server

import android.util.Log
import id.yanuar.bf01.util.Audio
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondFile
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/**
 * Server kompatibel xiaozhi di HP (port 8000).
 *  - POST/GET /xiaozhi/ota/  -> JSON berisi blok "websocket" {url, token} (tanpa "mqtt"), lihat firmware main/ota.cc
 *  - WS /xiaozhi/v1/         -> protokol WebSocket xiaozhi v1 (docs/websocket.md): Opus biner mentah, 16 kHz, 60 ms
 * Klien tanpa header `Authorization: Bearer <token>` yang cocok ditolak.
 */
class XiaozhiServer(
    private val tokenProvider: () -> String,
    private val wsUrlProvider: () -> String,
    private val pipeline: Pipeline,
    private val port: Int = 8000,
) {
    /** Dijalankan per ucapan. Kembalikan (teks pengguna, teks balasan, PCM 16k balasan) atau lempar error. */
    interface Pipeline {
        suspend fun handle(pcm: ShortArray, onStt: suspend (String) -> Unit, onReply: suspend (String) -> Unit): ShortArray?
    }

    /** Update firmware: satu file yang boleh diunduh BF, di /bf/fw/<token>.bin (token acak per sesi). */
    class FwServe(val token: String, val file: java.io.File)
    @Volatile var fw: FwServe? = null
    /** Jalur lama (BF v0.2.x): kalau diisi, /xiaozhi/ota/ ikut mengirim blok "firmware" {version, url, force:1}. */
    @Volatile var legacyFw: Pair<String, String>? = null
    /** Dipanggil saat BF mulai mengunduh firmware (untuk status di app). */
    @Volatile var onFwRequest: (() -> Unit)? = null

    private var engine: ApplicationEngine? = null
    @Volatile var clients = 0; private set

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) { pingPeriodMillis = 20_000; maxFrameSize = 1L shl 20 }
            routing {
                post("/xiaozhi/ota/") { call.receiveText(); call.respondText(otaJson(), ContentType.Application.Json) }
                get("/xiaozhi/ota/") { call.respondText(otaJson(), ContentType.Application.Json) }
                get("/") { call.respondText("BF-01 companion server OK") }
                get("/bf/fw/{name}") {
                    val f = fw
                    if (f == null || call.parameters["name"] != "${f.token}.bin" || !f.file.isFile) {
                        call.respondText("not found", status = HttpStatusCode.NotFound); return@get
                    }
                    onFwRequest?.invoke()
                    call.response.header("Cache-Control", "no-store")
                    call.respondFile(f.file)
                }
                webSocket("/xiaozhi/v1/") {
                    val auth = call.request.headers["Authorization"] ?: ""
                    if (!Auth.check(auth, tokenProvider())) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "token")); return@webSocket
                    }
                    clients++
                    try { Session(this).run() } catch (e: Exception) { Log.w(TAG, "sesi selesai: ${e.message}") } finally { clients-- }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() { engine?.stop(500, 1000); engine = null }

    /** Tanpa server_time: jam BF disetel lewat BLE (Time 02, UTC + offset). Lihat README "ketidakcocokan". */
    fun otaJson(): String = JSONObject()
        .put("websocket", JSONObject().put("url", wsUrlProvider()).put("token", tokenProvider()))
        .also { j -> legacyFw?.let { (v, u) -> j.put("firmware", JSONObject().put("version", v).put("url", u).put("force", 1)) } }
        .toString()

    private inner class Session(private val ws: DefaultWebSocketSession) {
        private val id = UUID.randomUUID().toString()
        private val dec = Audio.Decoder()
        private val buf = ArrayList<Short>()
        private var listening = false
        private var autoMode = false
        private var job: Job? = null
        private val vad = Vad()

        suspend fun run() {
            for (f in ws.incoming) when (f) {
                is Frame.Text -> onText(JSONObject(f.readText()))
                is Frame.Binary -> onAudio(f.data)
                else -> {}
            }
        }

        private suspend fun send(j: JSONObject) = ws.send(Frame.Text(j.put("session_id", id).toString()))

        private suspend fun onText(j: JSONObject) {
            when (j.optString("type")) {
                "hello" -> send(JSONObject().put("type", "hello").put("transport", "websocket")
                    .put("audio_params", JSONObject().put("format", "opus").put("sample_rate", Audio.RATE)
                        .put("channels", 1).put("frame_duration", Audio.FRAME_MS)))
                "listen" -> when (j.optString("state")) {
                    "start" -> { listening = true; autoMode = j.optString("mode") != "manual"; synchronized(buf) { buf.clear() }; vad.reset() }
                    "stop" -> { listening = false; finishUtterance() }
                    "detect" -> j.optString("text").takeIf { it.isNotBlank() }?.let { /* wake word; abaikan */ }
                }
                "abort" -> { job?.cancel(); send(JSONObject().put("type", "tts").put("state", "stop")) }
                else -> {}
            }
        }

        private suspend fun onAudio(pkt: ByteArray) {
            if (!listening) return
            val pcm = runCatching { dec.decode(pkt) }.getOrNull() ?: return
            synchronized(buf) { pcm.forEach { buf.add(it) } }
            if (autoMode && vad.feed(pcm)) { listening = false; finishUtterance() }
        }

        private fun finishUtterance() {
            val pcm = synchronized(buf) { buf.toShortArray().also { buf.clear() } }
            if (pcm.size < Audio.RATE / 4) return
            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reply = pipeline.handle(pcm,
                        onStt = { send(JSONObject().put("type", "stt").put("text", it)) },
                        onReply = {
                            send(JSONObject().put("type", "llm").put("emotion", "happy").put("text", "\uD83D\uDE00"))
                            send(JSONObject().put("type", "tts").put("state", "start"))
                            send(JSONObject().put("type", "tts").put("state", "sentence_start").put("text", it))
                        })
                    if (reply != null) for (frame in Audio.encode(reply)) {
                        ws.send(Frame.Binary(true, frame)); delay(Audio.FRAME_MS.toLong() - 5)
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    Log.w(TAG, "pipeline gagal", e)
                    runCatching { send(JSONObject().put("type", "tts").put("state", "start")) }
                    runCatching { send(JSONObject().put("type", "tts").put("state", "sentence_start").put("text", "Maaf Mas Yan, lagi error: ${e.message?.take(60)}")) }
                } finally {
                    runCatching { send(JSONObject().put("type", "tts").put("state", "stop")) }
                }
            }
        }
    }

    /** VAD energi sederhana: selesai setelah ada suara lalu diam ±800 ms. TODO: ganti dengan VAD yang lebih baik. */
    class Vad(private val threshold: Double = 500.0, private val silenceMs: Int = 800) {
        private var spoke = false; private var silent = 0
        fun reset() { spoke = false; silent = 0 }
        fun feed(pcm: ShortArray): Boolean {
            if (pcm.isEmpty()) return false
            val rms = Math.sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
            if (rms > threshold) { spoke = true; silent = 0 } else if (spoke) silent += pcm.size * 1000 / Audio.RATE
            return spoke && silent >= silenceMs
        }
    }

    companion object { const val TAG = "BFServer" }
}

object Auth {
    /** Bandingkan header "Bearer <token>" dengan waktu konstan. */
    fun check(header: String, token: String): Boolean {
        if (token.isEmpty()) return false
        val got = header.trim().removePrefix("Bearer").trim().toByteArray()
        return java.security.MessageDigest.isEqual(got, token.toByteArray())
    }
}
