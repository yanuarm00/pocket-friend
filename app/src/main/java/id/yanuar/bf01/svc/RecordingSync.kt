package id.yanuar.bf01.svc

import id.yanuar.bf01.ai.chatProvider
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.util.Audio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Tarik rekaman BF ke HP lalu transkrip (app 0.3.2 + firmware 0.3.4-test).
 *  1. BLE Files "wifi:on" -> BF nyalakan Wi-Fi (profil yang sudah diprovisioning) + server HTTP :8080.
 *  2. BF kirim event "wifi:on:<ip>:<token>" (BLE terenkripsi). HP dan BF harus di jaringan yang sama.
 *  3. GET /notes -> daftar lengkap; unduh WAV yang belum ada di HP ke filesDir/recordings; BLE "synced:<nama>".
 *  4. BLE "wifi:off". Lalu transkrip (penyedia AI aktif, bahasa Indonesia) + ringkasan & tag; TASK -> To-do.
 * Baterai BF < 30% dan tidak dicas: ditunda (kecuali tombol Sync manual).
 */
class RecordingSync(private val hub: Hub) {
    enum class Phase { Idle, Waiting, Connecting, Downloading, Transcribing, Done, Deferred, Failed }
    data class Status(val phase: Phase = Phase.Idle, val message: String = "", val notOnPhone: Int = 0, val done: Int = 0, val total: Int = 0)
    val status = MutableStateFlow(Status())
    private val mutex = Mutex()
    @Volatile private var queued = false
    private var debounce: Job? = null
    /** Maps bf.local → current BF LAN IP so cleartext is allowed only for that alias (see network_security_config). */
    @Volatile private var bfHttpIp = "127.0.0.1"
    private val bfDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname.equals("bf.local", true)) listOf(InetAddress.getByName(bfHttpIp)) else Dns.SYSTEM.lookup(hostname)
    }
    private val http = OkHttpClient.Builder().dns(bfDns).connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val dir get() = File(hub.ctx.filesDir, "recordings").apply { mkdirs() }
    private fun bfBase(ip: String): String { bfHttpIp = ip; return "http://bf.local:${BfProtocol.FILE_PORT}" }

    companion object {
        const val MIN_BATT = 30
        /** Tunda kalau baterai BF diketahui < 30% dan tidak sedang dicas. */
        fun shouldDefer(batt: Int, usb: Boolean) = batt in 0 until MIN_BATT && !usb
        /** File yang perlu diunduh: belum ada audio di HP. PENDING (NOTE/TASK belum dipilih) ditunggu dulu. */
        /** 0.3.9.8.6: "VN_1970…" (BF clock not set yet) -> "VN_<phone time>.wav"; other names unchanged. */
        fun localName(bfName: String, nowMs: Long): String {
            if (!bfName.startsWith("VN_1970")) return bfName
            return "VN_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(nowMs)) + ".wav"
        }
        fun aliasKey(name: String, size: Long) = "$name|$size"
        /** Too small to hold speech: header only or under 0.3 s of 16-bit mono audio. */
        const val MIN_WAV_BYTES = 44 + 16000 * 2 * 3 / 10
        const val NO_SPEECH = "No speech heard in this recording"
        fun toDownload(remote: List<BfProtocol.RemoteNote>, haveAudio: Set<String>) =
            remote.filter { it.type != "PENDING" && it.name !in haveAudio && it.size > 44 }
        /** Potong PCM 16 kHz jadi bagian ≤ 4 menit (Qwen ASR maks 5 menit / 10 MB). */
        fun chunks(pcm: ShortArray, maxSec: Int = 240): List<ShortArray> {
            val n = 16000 * maxSec
            if (pcm.size <= n) return listOf(pcm)
            return (pcm.indices step n).map { pcm.copyOfRange(it, minOf(pcm.size, it + n)) }
        }
        /** Baca jawaban AI {"summary","tags","title"}; toleran terhadap ```json``` atau teks tambahan. */
        fun parseMeta(s: String): Triple<String, String, String> {
            val j = runCatching { JSONObject(s.substring(s.indexOf('{'), s.lastIndexOf('}') + 1)) }.getOrNull()
                ?: return Triple(s.trim().take(120), "", "")
            val tags = j.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it).trim().lowercase() }.filter { it.isNotEmpty() } }
                ?.take(4)?.joinToString(",") ?: j.optString("tags")
            return Triple(j.optString("summary").trim(), tags, j.optString("title").trim().removeSurrounding("\"").take(60))
        }
    }

    /** Jumlah rekaman yang diketahui (dari event BF) tapi audionya belum di HP. */
    fun refreshCount() { status.value = status.value.copy(notOnPhone = hub.db.notes().count { it.localPath.isEmpty() && it.file.startsWith("VN_") }) }

    /** Dipanggil setelah rekaman baru / saat tersambung. Kalau BF belum tersambung, antre sampai tersambung. */
    fun request(delayMs: Long = 2500) {
        queued = true; refreshCount()
        if (hub.ble.state.value != BleManager.State.READY) { status.value = status.value.copy(phase = Phase.Waiting, message = "Will sync when BF connects"); return }
        debounce?.cancel()
        debounce = hub.scope.launch { delay(delayMs); run(manual = false) }
    }
    @Volatile private var lastBackfill = 0L
    /** v0.3.9.8: isi ulang saat tersambung hanya bila ada rekaman yang belum di HP, dan paling sering tiap 10 menit
     *  (dulu tiap sambung ulang BLE menyalakan Wi-Fi BF -> BF tak pernah tidur). */
    fun onConnected() { hub.scope.launch { delay(4000); refreshCount()
        val now = System.currentTimeMillis()
        if (!queued && (status.value.notOnPhone == 0 || now - lastBackfill < 600_000)) return@launch
        lastBackfill = now; run(manual = false, backfill = true) } }

    suspend fun run(manual: Boolean, backfill: Boolean = false) {
        if (!manual && !queued && !backfill) return
        if (!mutex.tryLock()) return
        try {
            if (hub.ble.state.value != BleManager.State.READY) { set(Phase.Waiting, "Will sync when BF connects"); return }
            hub.ble.refreshInfo()
            val info = hub.ble.info.value
            if (!manual && info != null && shouldDefer(info.batt, info.usb)) { set(Phase.Deferred, "BF battery below $MIN_BATT%. Waiting for charge or tap Sync"); return }
            if (info != null && !info.sd) { set(Phase.Failed, "No memory card in BF"); queued = false; return }
            queued = false
            pull()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            hub.log("Recording sync failed: ${e.message}"); set(Phase.Failed, e.message ?: "Sync failed"); queued = false; lastBackfill = System.currentTimeMillis()
        } finally { mutex.unlock(); refreshCount() }
        processPending()
    }

    private fun set(p: Phase, m: String) { status.value = status.value.copy(phase = p, message = m) }

    private suspend fun pull() {
        if (hub.fwUpdate.state.value.busy) return  // v0.3.9.8.1: never touch BF Wi-Fi during a firmware update
        set(Phase.Connecting, "Turning on BF Wi-Fi…")
        var hs: PhoneHotspot.Session? = null
        val ready: BfProtocol.Event.WifiReady
        try {
            // 0.3.9.8.7: saved Wi-Fi first; if BF can't join it or the phone can't reach BF there -> phone hotspot
            var r = askWifi(BfProtocol.filesCmd("wifi:on"), 60_000)
            var why = if (r == null) "BF couldn't join its saved Wi-Fi" else ""
            if (r != null && !reachable(r)) { why = "Phone can't reach BF on ${r.ip} (different Wi-Fi?)"; r = null }
            if (r == null) {
                hub.log("$why; trying phone hotspot")
                set(Phase.Connecting, "Starting phone hotspot for BF…")
                val s = try { PhoneHotspot.start(hub.ctx) } catch (e: Exception) { throw NeedWifi("$why. Hotspot: ${e.message}") }
                hs = s
                hub.hotspotSession = true
                hub.log("Phone hotspot on; sending it to BF")
                r = askWifi(BfProtocol.filesHotspot(s.ssid, s.pass), 60_000)
                if (r == null) throw NeedWifi("$why. BF couldn't join the phone hotspot (2.4 GHz needed)")
                if (!reachable(r)) throw NeedWifi("$why. Phone can't reach BF on the hotspot (${r.ip})")
                hub.log("BF joined the phone hotspot (${r.ip})")
            }
            ready = r
        } catch (e: Exception) {
            hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("wifi:off"))
            hs?.close(); hub.hotspotSession = false
            if (e is NeedWifi) {
                hub.log("${PhoneHotspot.NEED_WIFI} (${e.message})")
                PhoneHotspot.notifyNeedWifi(hub.ctx, e.message ?: "")
                throw Exception(PhoneHotspot.NEED_WIFI)
            }
            throw e
        }
        try {
            val base = bfBase(ready.ip)
            val list = withContext(Dispatchers.IO) { get("$base/notes", ready.token).use { BfProtocol.parseNotesJson(it.body?.string() ?: "") } }
            val have = hub.db.notes().filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }.map { it.file }.toSet()
            // 0.3.9.8.6: BF without a clock names files VN_1970...; we save those under the phone time instead
            val alias = hub.ctx.getSharedPreferences("rec_alias", android.content.Context.MODE_PRIVATE)
            val todo = toDownload(list, have).filter { r -> alias.getString(aliasKey(r.name, r.size), null)?.let { it !in have } ?: true }
            hub.log("Recordings on BF: ${list.size}, to pull: ${todo.size}")
            todo.forEachIndexed { i, r ->
                status.value = status.value.copy(phase = Phase.Downloading, message = "Downloading ${i + 1} of ${todo.size}…", done = i, total = todo.size)
                val local = alias.getString(aliasKey(r.name, r.size), null) ?: localName(r.name, System.currentTimeMillis())
                val f = File(dir, local); val tmp = File(dir, local + ".part")
                withContext(Dispatchers.IO) {
                    get("$base/notes/${r.name}", ready.token).use { resp -> resp.body!!.byteStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } } }
                }
                if (tmp.length() < 44) { tmp.delete(); throw Exception("Download of ${r.name} was empty") }
                tmp.renameTo(f)
                if (local != r.name) { alias.edit().putString(aliasKey(r.name, r.size), local).apply(); hub.log("Recording ${r.name} has no BF clock time; saved as $local (phone time)") }
                hub.db.upsertNote(local); hub.db.setNoteType(local, r.type); hub.db.setNoteAudio(local, f.absolutePath)
                hub.ble.write(BfProtocol.FILES, BfProtocol.filesSynced(r.name))  // BF boleh menghapusnya saat kartu penuh
                hub.revision.value++
            }
            hub.prefs.lastRecSync = System.currentTimeMillis()
            set(Phase.Done, if (todo.isEmpty()) "Everything is on the phone" else "Pulled ${todo.size} recording(s)")
        } finally {
            hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("wifi:off"))
            if (hs != null) { delay(500); hs.close(); hub.hotspotSession = false; hub.log("Phone hotspot off") }
        }
    }

    private class NeedWifi(msg: String) : Exception(msg)

    /** Send a Wi-Fi command and wait for BF's "wifi:on:<ip>:<token>" (null on wifi:fail / timeout). */
    private suspend fun askWifi(cmd: ByteArray, ms: Long): BfProtocol.Event.WifiReady? = coroutineScope {
        val w = async { withTimeoutOrNull(ms) { hub.ble.events.filterIsInstance<BfProtocol.Event.WifiReady>().first() } }
        val fail = async { withTimeoutOrNull(ms) { hub.ble.events.first { it is BfProtocol.Event.WifiFail } } }
        delay(150)  // listeners first, then the command
        if (!hub.ble.write(BfProtocol.FILES, cmd)) { w.cancel(); fail.cancel(); throw Exception("Couldn't reach BF over Bluetooth") }
        val r = select2(w, fail); w.cancel(); fail.cancel(); r
    }

    /** Quick check that the phone can open BF's file server (same network). */
    private suspend fun reachable(r: BfProtocol.Event.WifiReady): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            probe.newCall(Request.Builder().url("${bfBase(r.ip)}/notes").header("X-BF-Token", r.token).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
    private val probe by lazy { http.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build() }

    private suspend fun <T> select2(a: Deferred<T?>, b: Deferred<*>): T? = kotlinx.coroutines.selects.select {
        a.onAwait { it }
        b.onAwait { null }
    }

    private fun get(url: String, token: String): okhttp3.Response {
        val r = http.newCall(Request.Builder().url(url).header("X-BF-Token", token).build()).execute()
        if (!r.isSuccessful) { r.close(); throw Exception("BF file server ${r.code}") }
        return r
    }

    /** Transkrip semua rekaman yang audionya sudah di HP tapi belum diproses. Gagal (mis. key kosong) -> dicoba lagi lain kali. */
    suspend fun processPending() {
        val pending = hub.db.notes().filter { it.localPath.isNotEmpty() && !it.processed && File(it.localPath).exists() }
        pending.forEachIndexed { i, n ->
            status.value = status.value.copy(phase = Phase.Transcribing, message = "Transcribing ${i + 1} of ${pending.size}…")
            runCatching { process(n.file, n.type, File(n.localPath)) }
                .onFailure { hub.log("Transcribe ${n.file} failed: ${it.message}"); set(Phase.Failed, "Transcript failed: " + id.yanuar.bf01.ai.friendlyError(it)); return }
            hub.revision.value++
        }
        if (pending.isNotEmpty()) set(Phase.Done, "Transcribed ${pending.size} recording(s)")
    }

    /** v0.3.9.7: "beli garam besok jam 5 sore" -> "Buy salt" due tomorrow 17:00; BF asks to confirm (✓/✗). */
    private suspend fun voiceTodo(file: String, text: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", java.util.Locale.US).format(java.util.Date())
        val sys = "You turn a spoken to-do into JSON. Now is $now (phone local time). Reply ONLY JSON: " +
            "{\"title\":\"short to-do in English, max 6 words\",\"date\":\"yyyy-MM-dd or empty\",\"time\":\"HH:mm 24h or empty\",\"summary\":\"one short sentence\",\"tags\":[\"1-3 short lowercase tags\"]}. " +
            "Resolve relative words (besok=tomorrow, lusa=day after tomorrow, sore/malam=pm, pagi=am) from now. Leave date and time empty if none was said."
        val o = runCatching { val r = chatProvider(hub.prefs).chat(sys, emptyList(), text); hub.db.addUsage(chat = 1)
            org.json.JSONObject(r.substring(r.indexOf('{'), r.lastIndexOf('}') + 1)) }.getOrNull()
        val title = o?.optString("title")?.takeIf { it.isNotBlank() } ?: text.take(60)
        val date = o?.optString("date").orEmpty(); val time = o?.optString("time").orEmpty()
        val due = runCatching {
            if (date.isBlank() && time.isBlank()) 0L else {
                val d = date.ifBlank { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).parse(d + " " + time.ifBlank { "09:00" })!!.time }
        }.getOrDefault(0L)
        val tags = o?.optJSONArray("tags")?.let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } }.orEmpty()
        hub.db.upsertNote(file, transcript = text, summary = o?.optString("summary").orEmpty(), tags = listOf("task", tags).filter { it.isNotBlank() }.joinToString(","))
        val id = hub.db.upsertVoiceTask(file, title, text, due)
        hub.revision.value++
        if (id > 0) hub.scope.launch { runCatching {
            hub.pushTasks(force = true)
            if ((hub.ble.info.value?.tasksV ?: 0) >= 2) hub.ble.write(BfProtocol.NOTIFY, BfProtocol.todoConfirm(id, title, hub.whenLabel(due).ifBlank { "Today" }))
            hub.pushAlarms() } }
    }

    private suspend fun process(file: String, type: String, wav: File) = withContext(Dispatchers.IO) {
        // 0.3.9.8.6: don't send empty / header-only files to the AI; they only come back as an error
        if (wav.length() < MIN_WAV_BYTES) {
            hub.log("Recording $file is only ${wav.length()} bytes; skipped ($NO_SPEECH)")
            hub.db.upsertNote(file, transcript = "($NO_SPEECH)"); hub.db.setNoteProcessed(file); return@withContext
        }
        val (pcm0, rate) = Audio.parseWav(wav.readBytes())
        val pcm = Audio.resample(pcm0, rate)
        if (pcm.size < 16000 * 3 / 10 || pcm.maxOfOrNull { kotlin.math.abs(it.toInt()) }?.let { it < 150 } != false) {
            hub.log("Recording $file is silent (${pcm.size / 16} ms); skipped ($NO_SPEECH)")
            hub.db.upsertNote(file, transcript = "($NO_SPEECH)"); hub.db.setNoteProcessed(file); return@withContext
        }
        val stt = hub.sttForRecordings()
        // One chunk after another with a normal suspend call, so cancelling (leaving, app closing) stops between chunks.
        val parts = ArrayList<String>()
        for (c in chunks(pcm)) parts += stt.transcribe(c)
        if (parts.all { it.isBlank() }) hub.log("Transcribe $file: $NO_SPEECH")
        val text = parts.joinToString(" ").trim()
        hub.db.addUsage(sttSec = pcm.size / 16000.0)
        if (type == "AI" || type == "AIR" || type == "AIREPLY") {
            hub.db.upsertNote(file, transcript = text.ifBlank { "($NO_SPEECH)" })
            hub.answerAi(file, text); hub.db.setNoteProcessed(file); return@withContext
        }
        val task = type == "TASK"
        if (task && text.isNotBlank() && !hub.db.isTombstoned(file)) { voiceTodo(file, text); hub.db.setNoteProcessed(file); return@withContext }
        val (summary, tags, title) = if (text.isBlank()) Triple("", "", "") else runCatching {
            val sys = "Kamu merapikan catatan suara berbahasa Indonesia. Balas HANYA JSON: " +
                "{\"summary\":\"ringkasan satu kalimat pendek\",\"tags\":[\"1-3 tag pendek huruf kecil\"]" +
                (if (task) ",\"title\":\"judul tugas singkat maks 6 kata\"" else "") + "}"
            parseMeta(chatProvider(hub.prefs).chat(sys, emptyList(), text)).also { hub.db.addUsage(chat = 1) }
        }.getOrElse { Triple("", "", "") }
        hub.db.upsertNote(file, transcript = text.ifBlank { "($NO_SPEECH)" }, summary = summary, tags = if (task) listOf("task", tags).filter { it.isNotBlank() }.joinToString(",") else tags)
        if (task) {
            hub.db.upsertVoiceTask(file, title.ifBlank { text.take(60).ifBlank { "Voice task" } }, text)
            hub.scope.launch { runCatching { hub.pushTasks() } }
        }
        hub.db.setNoteProcessed(file)
        if (!task && text.isNotBlank()) hub.autoMemory.fromNote(text)
    }
}
