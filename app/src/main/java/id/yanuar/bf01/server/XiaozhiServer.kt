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
 *  - POST/GET /xiaozhi/ota/  -> JSON {websocket:{url}} only (no token; token is BLE-provisioned). Firmware main/ota.cc
 *  - WS /xiaozhi/v1/         -> protokol WebSocket xiaozhi v1 (docs/websocket.md): Opus biner mentah, 16 kHz, 60 ms
 * Klien tanpa header `Authorization: Bearer <token>` yang cocok ditolak.
 */
class XiaozhiServer(
    private val tokenProvider: () -> String,
    private val wsUrlProvider: () -> String,
    private val pipeline: Pipeline,
    private val port: Int = 8000,
    /** Preferred bind address (phone IP BF reaches). Empty / invalid -> 0.0.0.0. */
    private val hostProvider: () -> String = { "0.0.0.0" },
    /** Gate for Device-Id pairing: known MAC, open pairing window, remember first auth. */
    private val devices: DeviceGate = DeviceGate.Open,
) {
    /** Per-device allowlist for WebSocket (and optional OTA token). Firmware sends Device-Id = Wi-Fi MAC. */
    interface DeviceGate {
        fun pairedId(): String
        fun pairingOpen(): Boolean
        fun accept(deviceId: String): Boolean  // remember + allow
        fun forget()
        companion object {
            val Open = object : DeviceGate {
                override fun pairedId() = ""; override fun pairingOpen() = true
                override fun accept(deviceId: String) = true; override fun forget() {}
            }
        }
    }
    /** Dijalankan per ucapan. Kembalikan (teks pengguna, teks balasan, PCM 16k balasan) atau lempar error. */
    interface Pipeline {
        suspend fun handle(pcm: ShortArray, onStt: suspend (String) -> Unit, onReply: suspend (String) -> Unit): ShortArray?
        /** Teks error singkat untuk layar BF (mis. "AI FAILED: KEY\n..."). */
        /** v0.3.9.6: false = jangan tampilkan error (mis. ASR gagal pada sisa suara setelah jawaban sukses). */
        fun shouldShow(e: Exception, afterReply: Boolean): Boolean = true
        fun failText(e: Exception): String = "AI FAILED: NET\n${e.message?.take(60)}"
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
    @Volatile private var current: Session? = null
    private val limiter = RateLimit()

    /** 0.3.9.8.8: at most [maxPerMin] connection attempts per address per minute; [maxFails] bad tokens -> blocked 5 min. */
    class RateLimit(private val maxPerMin: Int = 20, private val maxFails: Int = 5) {
        private val hits = HashMap<String, ArrayDeque<Long>>()
        private val fails = HashMap<String, ArrayDeque<Long>>()
        @Synchronized fun allow(ip: String, now: Long): Boolean {
            val f = fails.getOrPut(ip) { ArrayDeque() }.apply { while (isNotEmpty() && now - first() > 300_000) removeFirst() }
            if (f.size >= maxFails) return false
            val h = hits.getOrPut(ip) { ArrayDeque() }.apply { while (isNotEmpty() && now - first() > 60_000) removeFirst() }
            if (h.size >= maxPerMin) return false
            h.addLast(now); return true
        }
        @Synchronized fun fail(ip: String, now: Long) { fails.getOrPut(ip) { ArrayDeque() }.addLast(now) }
    }

    /** 0.3.9.8.8: audio for one utterance is capped at [cap] samples (~60 s); extra audio is dropped. */
    class PcmBuf(val cap: Int = Audio.RATE * 60) {
        private val a = ShortArray(cap); var size = 0; private set
        val full get() = size >= cap
        @Synchronized fun add(p: ShortArray) { val n = minOf(p.size, cap - size); if (n > 0) { System.arraycopy(p, 0, a, size, n); size += n } }
        @Synchronized fun take(): ShortArray = a.copyOf(size).also { size = 0 }
        @Synchronized fun clear() { size = 0 }
    }

    fun start() {
        if (engine != null) return
        val host = bindHost(hostProvider())
        engine = embeddedServer(CIO, port = port, host = host) {
            install(WebSockets) { pingPeriodMillis = 20_000; maxFrameSize = 1L shl 20 }
            routing {
                // OTA never returns the bearer token (BF gets it over encrypted BLE). LAN peers only see the WS URL.
                post("/xiaozhi/ota/") {
                    call.receiveText()
                    val did = Auth.deviceId(call.request.headers["Device-Id"])
                    if (did.isNotEmpty() && devices.pairingOpen() && devices.pairedId().isEmpty()) devices.accept(did)
                    call.respondText(otaJson(), ContentType.Application.Json)
                }
                get("/xiaozhi/ota/") {
                    val did = Auth.deviceId(call.request.headers["Device-Id"])
                    if (did.isNotEmpty() && devices.pairingOpen() && devices.pairedId().isEmpty()) devices.accept(did)
                    call.respondText(otaJson(), ContentType.Application.Json)
                }
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
                    val ip = call.request.local.remoteHost
                    val now = System.currentTimeMillis()
                    if (!limiter.allow(ip, now)) {
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "rate")); return@webSocket
                    }
                    val auth = call.request.headers["Authorization"] ?: ""
                    if (!Auth.check(auth, tokenProvider())) {
                        limiter.fail(ip, now)
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "token")); return@webSocket
                    }
                    val did = Auth.deviceId(call.request.headers["Device-Id"])
                    if (!Auth.allowDevice(did, devices)) {
                        limiter.fail(ip, now)
                        Log.w(TAG, "ws rejected device=${did.ifBlank { "?" }} paired=${devices.pairedId().ifBlank { "-" }}")
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "device")); return@webSocket
                    }
                    val s = Session(this)
                    val old = synchronized(this@XiaozhiServer) { current.also { current = s } }
                    old?.kick()
                    clients = 1
                    try { s.run() } catch (e: Exception) { Log.w(TAG, "session ended: ${e.message}") } finally {
                        s.end()
                        synchronized(this@XiaozhiServer) { if (current === s) { current = null; clients = 0 } }
                    }
                }
            }
        }.also { it.start(wait = false); Log.i(TAG, "listening on $host:$port") }
    }


    fun stop() { engine?.stop(500, 1000); engine = null }

    /** Tanpa server_time: jam BF disetel lewat BLE (Time 02, UTC + offset). Lihat README "ketidakcocokan". */
    // 0.3.9.8.8: no token here (this endpoint has no auth). BF gets the token over encrypted BLE (provision / "srv:")
    // and its OTA parser only overwrites keys that are present, so the saved token stays.
    fun otaJson(): String = JSONObject()
        .put("websocket", JSONObject().put("url", wsUrlProvider()))
        .also { j -> legacyFw?.let { (v, u) -> j.put("firmware", JSONObject().put("version", v).put("url", u).put("force", 1)) } }
        .toString()

    private inner class Session(private val ws: DefaultWebSocketSession) {
        private val id = UUID.randomUUID().toString()
        private val dec = Audio.Decoder()
        private val buf = PcmBuf()
        private var listening = false
        private var autoMode = false
        private var job: Job? = null
        private val vad = Vad()
        @Volatile private var lastReplyAt = 0L

        /** Session over: stop paid AI work for it (0.3.9.8.8). */
        fun end() { job?.cancel(); job = null; listening = false }
        suspend fun kick() { end(); runCatching { ws.close(CloseReason(CloseReason.Codes.NORMAL, "replaced")) } }

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
                    "start" -> { listening = true; autoMode = j.optString("mode") != "manual"; buf.clear(); vad.reset() }
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
            buf.add(pcm)
            if (buf.full) { Log.w(TAG, "utterance hit 60 s cap"); listening = false; finishUtterance(); return }
            if (autoMode && vad.feed(pcm)) { listening = false; finishUtterance() }
        }

        private fun finishUtterance() {
            val pcm = buf.take()
            if (pcm.size < Audio.RATE / 4) return
            // v0.3.9.6: potongan tanpa suara orang (gema TTS / bising setelah jawaban) tidak dikirim ke ASR;
            // dulu menghasilkan "AI FAILED: ASR" palsu padahal jawaban sudah keluar.
            if (Vad.voicedMs(pcm) < 300) {
                Log.i(TAG, "silent chunk skipped (${pcm.size} samples)")
                if (autoMode) { listening = true; vad.reset() }
                return
            }
            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                var failed = false
                try {
                    val reply = pipeline.handle(pcm,
                        onStt = { send(JSONObject().put("type", "stt").put("text", it)) },
                        onReply = {
                            send(JSONObject().put("type", "llm").put("emotion", "happy").put("text", "\uD83D\uDE00"))
                            send(JSONObject().put("type", "tts").put("state", "start"))
                            send(JSONObject().put("type", "tts").put("state", "sentence_start").put("text", it))
                        })
                    if (reply != null) lastReplyAt = System.currentTimeMillis()
                    if (reply != null) for (frame in Audio.encode(reply)) {
                        ws.send(Frame.Binary(true, frame)); delay(Audio.FRAME_MS.toLong() - 5)
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    Log.w(TAG, "pipeline failed", e)
                    val afterReply = System.currentTimeMillis() - lastReplyAt < 60_000
                    failed = true
                    if (!pipeline.shouldShow(e, afterReply)) return@launch
                    runCatching { send(JSONObject().put("type", "tts").put("state", "start")) }
                    runCatching { send(JSONObject().put("type", "tts").put("state", "sentence_start").put("text", pipeline.failText(e).take(160))) }
                } finally {
                    runCatching { send(JSONObject().put("type", "tts").put("state", "stop")) }
                    // v0.3.9.6: gagal -> tutup sesi supaya BF melepas mic/audio dan status "BF sedang ngobrol" hilang
                    if (failed) { delay(300); runCatching { ws.close(CloseReason(CloseReason.Codes.NORMAL, "failed")) } }
                }
            }
        }
    }

    /** VAD energi sederhana: selesai setelah ada suara lalu diam ±800 ms. TODO: ganti dengan VAD yang lebih baik. */
    class Vad(private val threshold: Double = 500.0, private val silenceMs: Int = 800) {
        companion object {
            /** Total ms (bingkai 20 ms) dengan energi di atas ambang suara. */
            fun voicedMs(pcm: ShortArray, threshold: Double = 500.0): Int {
                val n = Audio.RATE / 50; var ms = 0; var i = 0
                while (i + n <= pcm.size) {
                    var e = 0.0; for (k in i until i + n) e += pcm[k].toDouble() * pcm[k]
                    if (Math.sqrt(e / n) > threshold) ms += 20
                    i += n
                }
                return ms
            }
        }
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
    /** Compare "Bearer <token>" in constant time. */
    fun check(header: String, token: String): Boolean {
        if (token.isEmpty()) return false
        val got = header.trim().removePrefix("Bearer").trim().toByteArray(Charsets.UTF_8)
        val expect = token.toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.isEqual(got, expect)
    }

    fun deviceId(raw: String?): String = raw?.trim()?.lowercase().orEmpty()
        .replace('-', ':').filter { it.isDigit() || it in 'a'..'f' || it == ':' }

    /** Allow if unpaired+pairing-open (first Device-Id wins), or Device-Id matches the paired MAC. */
    fun allowDevice(deviceId: String, gate: XiaozhiServer.DeviceGate): Boolean {
        if (deviceId.isEmpty()) return false  // firmware always sends Device-Id; refuse blank impersonation
        val paired = gate.pairedId()
        if (paired.isNotEmpty()) return java.security.MessageDigest.isEqual(
            deviceId(paired).toByteArray(Charsets.UTF_8), deviceId(deviceId).toByteArray(Charsets.UTF_8))
        if (!gate.pairingOpen()) return false
        return gate.accept(deviceId)
    }
}

/** Bind to a concrete IPv4 when possible so the server is not exposed on every interface. */
internal fun bindHost(preferred: String): String {
    val p = preferred.trim()
    if (p.isEmpty() || p == "0.0.0.0" || p.equals("localhost", true)) return "0.0.0.0"
    return runCatching {
        val addr = java.net.InetAddress.getByName(p)
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress) return@runCatching "0.0.0.0"
        // Confirm the address is still one of this phone's interfaces.
        val nif = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }.any { it.hostAddress == addr.hostAddress }
        if (nif) addr.hostAddress!! else "0.0.0.0"
    }.getOrDefault("0.0.0.0")
}
