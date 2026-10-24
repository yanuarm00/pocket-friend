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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
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
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val dir get() = File(hub.ctx.filesDir, "recordings").apply { mkdirs() }

    companion object {
        const val MIN_BATT = 30
        /** Tunda kalau baterai BF diketahui < 30% dan tidak sedang dicas. */
        fun shouldDefer(batt: Int, usb: Boolean) = batt in 0 until MIN_BATT && !usb
        /** File yang perlu diunduh: belum ada audio di HP. PENDING (NOTE/TASK belum dipilih) ditunggu dulu. */
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
    fun onConnected() { hub.scope.launch { delay(4000); run(manual = false, backfill = true) } }

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
            hub.log("Recording sync failed: ${e.message}"); set(Phase.Failed, e.message ?: "Sync failed"); queued = true
        } finally { mutex.unlock(); refreshCount() }
        processPending()
    }

    private fun set(p: Phase, m: String) { status.value = status.value.copy(phase = p, message = m) }

    private suspend fun pull() {
        set(Phase.Connecting, "Turning on BF Wi-Fi…")
        val ready = coroutineScope {
            val w = async { withTimeoutOrNull(60_000) { hub.ble.events.filterIsInstance<BfProtocol.Event.WifiReady>().first() } }
            val fail = async { withTimeoutOrNull(60_000) { hub.ble.events.first { it is BfProtocol.Event.WifiFail } } }
            delay(150)  // pastikan pendengar event sudah aktif sebelum perintah dikirim
            if (!hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("wifi:on"))) { w.cancel(); fail.cancel(); throw Exception("Couldn't reach BF over Bluetooth") }
            val r = select2(w, fail); w.cancel(); fail.cancel(); r
        } ?: throw Exception("BF couldn't join Wi-Fi. Phone and BF must be on the same Wi-Fi or phone hotspot")
        try {
            val base = "http://${ready.ip}:${BfProtocol.FILE_PORT}"
            val list = withContext(Dispatchers.IO) { get("$base/notes", ready.token).use { BfProtocol.parseNotesJson(it.body?.string() ?: "") } }
            val have = hub.db.notes().filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }.map { it.file }.toSet()
            val todo = toDownload(list, have)
            hub.log("Recordings on BF: ${list.size}, to pull: ${todo.size}")
            todo.forEachIndexed { i, r ->
                status.value = status.value.copy(phase = Phase.Downloading, message = "Downloading ${i + 1} of ${todo.size}…", done = i, total = todo.size)
                val f = File(dir, r.name); val tmp = File(dir, r.name + ".part")
                withContext(Dispatchers.IO) {
                    get("$base/notes/${r.name}", ready.token).use { resp -> resp.body!!.byteStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } } }
                }
                if (tmp.length() < 44) { tmp.delete(); throw Exception("Download of ${r.name} was empty") }
                tmp.renameTo(f)
                hub.db.upsertNote(r.name); hub.db.setNoteType(r.name, r.type); hub.db.setNoteAudio(r.name, f.absolutePath)
                hub.ble.write(BfProtocol.FILES, BfProtocol.filesSynced(r.name))  // BF boleh menghapusnya saat kartu penuh
                hub.revision.value++
            }
            hub.prefs.lastRecSync = System.currentTimeMillis()
            set(Phase.Done, if (todo.isEmpty()) "Everything is on the phone" else "Pulled ${todo.size} recording(s)")
        } finally {
            hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("wifi:off"))
        }
    }

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
                .onFailure { hub.log("Transcribe ${n.file} failed: ${it.message}"); set(Phase.Failed, "Transcription failed: ${it.message?.take(80)}"); return }
            hub.revision.value++
        }
        if (pending.isNotEmpty()) set(Phase.Done, "Transcribed ${pending.size} recording(s)")
    }

    private suspend fun process(file: String, type: String, wav: File) = withContext(Dispatchers.IO) {
        val (pcm0, rate) = Audio.parseWav(wav.readBytes())
        val pcm = Audio.resample(pcm0, rate)
        val stt = hub.sttForRecordings()
        val text = chunks(pcm).joinToString(" ") { c -> runBlocking { stt.transcribe(c) } }.trim()
        hub.db.addUsage(sttSec = pcm.size / 16000.0)
        val task = type == "TASK"
        val (summary, tags, title) = if (text.isBlank()) Triple("", "", "") else runCatching {
            val sys = "Kamu merapikan catatan suara berbahasa Indonesia. Balas HANYA JSON: " +
                "{\"summary\":\"ringkasan satu kalimat pendek\",\"tags\":[\"1-3 tag pendek huruf kecil\"]" +
                (if (task) ",\"title\":\"judul tugas singkat maks 6 kata\"" else "") + "}"
            parseMeta(chatProvider(hub.prefs).chat(sys, emptyList(), text)).also { hub.db.addUsage(chat = 1) }
        }.getOrElse { Triple("", "", "") }
        hub.db.upsertNote(file, transcript = text.ifBlank { "(no speech found)" }, summary = summary, tags = if (task) listOf("task", tags).filter { it.isNotBlank() }.joinToString(",") else tags)
        if (task) {
            hub.db.upsertVoiceTask(file, title.ifBlank { text.take(60).ifBlank { "Voice task" } }, text)
            hub.scope.launch { runCatching { hub.pushTasks() } }
        }
        hub.db.setNoteProcessed(file)
        if (!task && text.isNotBlank()) hub.autoMemory.fromNote(text)
    }
}
