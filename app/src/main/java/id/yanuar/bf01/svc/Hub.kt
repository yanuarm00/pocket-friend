package id.yanuar.bf01.svc

import android.content.Context
import android.net.Uri
import android.util.Log
import id.yanuar.bf01.ai.*
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.data.Db
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.server.XiaozhiServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.TimeZone

/** Pusat app: dipegang BfService (foreground), dibaca UI. */
class Hub internal constructor(val ctx: Context, val prefs: Prefs = Prefs(ctx)) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = Db(ctx)
    val ble = BleManager(ctx, scope)
    val findPhone = FindPhone(ctx)
    val log = MutableStateFlow(listOf<String>())
    val wifiOnBf = MutableStateFlow(false)
    /** Cache data layar di memori: saat kembali ke layar, data langsung ada sehingga posisi scroll bisa dipulihkan. */
    val uiCache = java.util.concurrent.ConcurrentHashMap<String, Any>()
    val revision = MutableStateFlow(0) // naik saat data DB berubah, supaya UI memuat ulang
    @Volatile private var nextIsTask = false
    /** Diisi NotificationListener untuk perintah media dari BF. */
    var mediaCommand: ((String) -> Unit)? = null

    fun log(s: String) { Log.i("BF", s); log.value = (log.value + s).takeLast(50) }

    val server = XiaozhiServer({ prefs.wsToken }, { wsUrl() }, object : XiaozhiServer.Pipeline {
        override suspend fun handle(pcm: ShortArray, onStt: suspend (String) -> Unit, onReply: suspend (String) -> Unit): ShortArray? {
            val cap = prefs.monthlyCapMin
            val (sttMin, ttsMin, _) = db.usageThisMonth()
            if (cap > 0 && sttMin + ttsMin >= cap) { onReply("Mas Yan, jatah menit bulan ini sudah habis. Cek Pengaturan ya."); return tts("Jatah menit bulan ini sudah habis.") }
            if (prefs.modeCepat && ModeCepat.IMPLEMENTED) TODO("Mode Cepat realtime")
            val text = stt().transcribe(pcm)
            db.addUsage(sttSec = pcm.size / 16000.0)
            onStt(text)
            if (text.isBlank()) return null
            if (nextIsTask) { nextIsTask = false; return makeTask(text, onReply) }
            db.addChat("user", text)
            val history = db.chatHistory(12).map { (if (it.role == "user") "user" else "assistant") to it.text }
            val reply = chatProvider(prefs).chat(systemPrompt(), history, text)
            db.addUsage(chat = 1); db.addChat("assistant", reply); db.trimChat(); revision.value++; autoMemory.onChatActivity()
            onReply(reply)
            return tts(reply)
        }
    })

    private suspend fun makeTask(text: String, onReply: suspend (String) -> Unit): ShortArray {
        // AI merapikan ucapan jadi judul tugas singkat; kalau gagal, pakai teks mentah.
        val title = runCatching { chatProvider(prefs).chat("Ubah ucapan berikut jadi satu judul tugas singkat (maks 6 kata), bahasa Indonesia, tanpa tanda kutip.", emptyList(), text) }
            .getOrDefault(text).take(60)
        db.addTask(title); revision.value++
        scope.launch { pushTasks(showNow = true) }
        val r = "Siap Mas Yan, tugas \"$title\" sudah dicatat."
        onReply(r); return tts(r)
    }

    /** STT untuk rekaman BF: penyedia aktif (Qwen qwen3-asr-flash atau xAI), bahasa Indonesia. STT sistem tidak bisa membaca file. */
    internal fun sttForRecordings(): SttProvider = when {
        prefs.llmProvider == "qwen" -> QwenStt(prefs)
        prefs.xaiKey.isNotBlank() -> XaiStt(prefs)
        else -> throw ApiException("Add an API key in Settings > AI provider to transcribe recordings")
    }
    private fun stt(): SttProvider = when (aiStack(prefs).stt) { "qwen" -> QwenStt(prefs); "xai" -> XaiStt(prefs); else -> SystemStt() }
    private suspend fun tts(t: String): ShortArray {
        val p: TtsProvider = if (aiStack(prefs).tts == "xai") XaiTts(prefs) else SystemTts(ctx)
        val pcm = runCatching { p.speak(t) }.getOrElse { SystemTts(ctx).speak(t) }
        db.addUsage(ttsSec = pcm.size / 16000.0)
        return pcm
    }

    /** Ngobrol lewat ketikan dari HP (otak AI sama dengan suara). */
    suspend fun chatText(text: String): String {
        db.addChat("user", text)
        // Ambil riwayat langsung dari DB tanpa menarik 200 baris ke memori.
        val history = db.chatHistory(12).map { (if (it.role == "user") "user" else "assistant") to it.text }
        val reply = chatProvider(prefs).chat(systemPrompt(), history, text)
        db.addUsage(chat = 1); db.addChat("assistant", reply); db.trimChat(); revision.value++; autoMemory.onChatActivity()
        return reply
    }

    fun systemPrompt(): String {
        val mem = db.memories().joinToString("\n") { "- ${it.text}" }
        val name = prefs.aiName
        // Nama panggilan AI bisa diubah; ganti kata "BF" di persona supaya tidak bertentangan.
        val persona = if (name == "BF") prefs.persona else prefs.persona.replace(Regex("\\bBF\\b"), name)
        return "Nama panggilanmu: $name.\n" + persona + if (mem.isNotBlank()) "\nHal yang kamu ingat tentang Mas Yan:\n$mem" else ""
    }

    val fwUpdate by lazy { FwUpdate(this) }
    val recordings by lazy { RecordingSync(this) }
    val autoMemory by lazy { AutoMemory(this) }

    fun wsUrl() = "ws://${prefs.serverHost}:8000/xiaozhi/v1/"
    fun otaUrl() = "http://${prefs.serverHost}:8000/xiaozhi/ota/"

    fun waLink(): String = "https://wa.me/${prefs.cardPhone.filter { it.isDigit() }}?text=" + Uri.encode(prefs.cardMsg)

    // ---------- sinkron ----------
    @Volatile private var started = false
    @Volatile private var collectors = false
    /** Status sakelar Sambungkan/Putuskan untuk UI. */
    val linked = kotlinx.coroutines.flow.MutableStateFlow(prefs.linkEnabled)
    val forwardPaused = kotlinx.coroutines.flow.MutableStateFlow(prefs.forwardPaused)
    fun setForwardPaused(v: Boolean) { prefs.forwardPaused = v; forwardPaused.value = v }
    /** Dipakai MapsListener: kirim ke BF hanya kalau tersambung & tidak dijeda. */
    val forwarding: Boolean get() = linked.value && !forwardPaused.value

    @Synchronized fun start() {
        if (started || !prefs.linkEnabled) return; started = true
        server.start()
        ble.onReady = { syncAll(); recordings.onConnected() }
        ble.onDisconnected = { LastLocation.save(ctx, prefs); log("BF disconnected (asleep/out of range). Last location saved.") }
        prefs.deviceAddress?.let { ble.connect(it) }
        if (collectors) return; collectors = true
        scope.launch { ble.events.collect { onEvent(it) } }
        scope.launch { ble.findPhone.collect { if (it) findPhone.start() else findPhone.stop() } }
        scope.launch { while (isActive) { delay(15 * 60_000L); if (ble.state.value == BleManager.State.READY) LastLocation.save(ctx, prefs) } }
    }

    /** Putuskan total: tutup GATT (hentikan autoConnect), matikan server Wi-Fi, hentikan layanan + notifikasinya. Disimpan di prefs. */
    @Synchronized fun disconnectAll() {
        prefs.linkEnabled = false; linked.value = false
        ble.disconnect(); runCatching { server.stop() }; findPhone.stop(); wifiOnBf.value = false
        if (started) LastLocation.save(ctx, prefs)
        started = false
        ctx.stopService(android.content.Intent(ctx, BfService::class.java))
        log("Disconnected manually. BF stays disconnected until you tap Connect.")
    }
    fun reconnect() {
        prefs.linkEnabled = true; linked.value = true
        log("Connecting to BF...")
        BfService.start(ctx)
    }

    suspend fun syncAll() {
        log("BF connected, syncing...")
        syncTime()
        ble.write(BfProtocol.NOTIFY, BfProtocol.sleepTimeout(prefs.sleepSec))
        ble.write(BfProtocol.NOTIFY, BfProtocol.agenda(CalendarSource.todayLines(ctx)))
        pushTasks()
        pushAlarms()
        LastLocation.save(ctx, prefs)
        log("Sync done")
    }

    suspend fun syncTime() {
        val now = System.currentTimeMillis()
        ble.write(BfProtocol.TIME, BfProtocol.encodeTime(now / 1000, TimeZone.getDefault().getOffset(now) / 60000))
    }
    suspend fun pushTasks(showNow: Boolean = false) =
        ble.write(BfProtocol.NOTIFY, BfProtocol.tasks(db.tasks().filter { !it.done }.map { it.title }.ifEmpty { listOf("No tasks") }, showNow))

    /** Pengingat + tugas bertenggat dikirim lebih awal sebagai entri A (BF menyimpan di NVS & memasang alarm RTC). */
    suspend fun pushAlarms() {
        val now = System.currentTimeMillis()
        val taskAlarms = db.tasks().filter { !it.done && it.due > now }.map { BfProtocol.Alarm(it.due / 1000, it.title, "") }
        val all = CalendarSource.upcomingAlarms(ctx, taskAlarms).filterNot { inQuiet(it.epochUtc * 1000) }
        ble.write(BfProtocol.NOTIFY, BfProtocol.alarms(all))
    }
    private fun inQuiet(t: Long): Boolean {
        val h = java.util.Calendar.getInstance().apply { timeInMillis = t }.get(java.util.Calendar.HOUR_OF_DAY)
        val s = prefs.quietStart; val e = prefs.quietEnd
        return if (s == e) false else if (s < e) h in s until e else h >= s || h < e
    }

    suspend fun sendNameCard() = ble.write(BfProtocol.NAMECARD, BfProtocol.nameCard(waLink(), prefs.cardName, prefs.cardTitle))

    suspend fun provision(): Boolean {
        val profiles = (0..1).map { prefs.wifi(it) }.filter { it.first.isNotBlank() }.map { BfProtocol.WifiProfile(it.first, it.second) }
        return ble.write(BfProtocol.PROVISION, BfProtocol.provision(profiles, prefs.wsToken, wsUrl(), otaUrl()))
    }

    /** Rekaman TASK dari BF: langsung jadi tugas TO-DO dengan judul sementara; judul AI + transkrip (detail)
     *  mengganti setelah audionya ditarik lewat Wi-Fi dan ditranskrip (RecordingSync). */
    fun addVoiceTask(file: String) {
        db.setNoteType(file, "TASK")
        val exists = db.readableDatabase.rawQuery("SELECT 1 FROM tasks WHERE note_file=?", arrayOf(file)).use { it.moveToFirst() }
        if (!exists) db.upsertVoiceTask(file, "Voice task (transcribing…)")
        revision.value++
        scope.launch { pushTasks() }
    }

    private suspend fun onEvent(e: BfProtocol.Event) {
        log("Event: $e")
        when (e) {
            is BfProtocol.Event.Media -> mediaCommand?.invoke(e.cmd)
            is BfProtocol.Event.TaskStart -> nextIsTask = true
            is BfProtocol.Event.NoteNew -> { db.setNoteType(e.file, "NOTE"); revision.value++; recordings.request() }
            is BfProtocol.Event.TaskNew -> { addVoiceTask(e.file); recordings.request() }
            is BfProtocol.Event.NoteDeleted -> { db.deleteNoteByFile(e.file); revision.value++ }
            is BfProtocol.Event.Ota -> fwUpdate.onEvent(e)
            is BfProtocol.Event.WifiOn, is BfProtocol.Event.WifiReady -> wifiOnBf.value = true
            is BfProtocol.Event.WifiOff, is BfProtocol.Event.WifiFail -> wifiOnBf.value = false
            is BfProtocol.Event.ProvOk -> log("Provisioning saved, BF restarting")
            is BfProtocol.Event.ReminderFired, BfProtocol.Event.ReminderOk -> pushAlarms()
            else -> {}
        }
    }

    companion object {
        @Volatile private var inst: Hub? = null
        fun get(ctx: Context): Hub = inst ?: synchronized(this) { inst ?: Hub(ctx.applicationContext).also { inst = it } }
    }
}
