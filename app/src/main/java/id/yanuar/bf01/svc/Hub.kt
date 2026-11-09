package id.yanuar.bf01.svc

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import android.content.Context
import android.net.Uri
import android.util.Log
import id.yanuar.bf01.ai.*
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.data.Db
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.data.ProfileTypes
import id.yanuar.bf01.server.XiaozhiServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.TimeZone

/** Pusat app: dipegang BfService (foreground), dibaca UI. */
class Hub internal constructor(val ctx: Context, val prefs: Prefs = Prefs(ctx)) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = Db(ctx)
    init { id.yanuar.bf01.ai.UsageLog.sink = { db.addAiCall(it) } }  // v0.3.9.8.4: every AI call that reports usage is stored for the AI usage screen
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
    /** Set by the UI so screens can open another screen (AI chats). */
    var openScreen: ((id.yanuar.bf01.ui.Screen) -> Unit)? = null
    /** v0.4.0: set by the Home battery line; the Usage page reads it once and scrolls to Battery. */
    @Volatile var usageJumpToBattery = false
    /** Last music/nav lines, re-sent when BF (re)connects so JAM ON / TAKE ME aren't empty after sleep. */
    @Volatile var lastMedia: ByteArray? = null
    @Volatile var lastNav: ByteArray? = null
    fun sendLive(b: ByteArray, nav: Boolean) { if (nav) lastNav = b else lastMedia = b
        if (forwarding) scope.launch { if (ble.write(BfProtocol.NOTIFY, b)) lastSentToBf.value = System.currentTimeMillis() to String(b, 1, b.size - 1) } }
    /** v0.3.9 diagnosa di halaman Music, Maps & alerts. */
    val lastMapsUpdate = MutableStateFlow<Pair<Long, String>?>(null)
    val lastSentToBf = MutableStateFlow<Pair<Long, String>?>(null)

    private val logFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    fun log(s: String) { Log.i("BF", s); val line = synchronized(logFmt) { logFmt.format(java.util.Date()) } + "  " + s; log.value = (log.value + line).takeLast(50) }

    // ---------- v0.3.9.7: AI voice chats from BF ----------
    private val aiPrefs = ctx.getSharedPreferences("bf01_ai", Context.MODE_PRIVATE)
    /** Settings acks from BF ("S" sleep, "I" power times), for "Saved to BF ✓". */
    val setAck = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun aiTidOf(file: String): String = aiPrefs.getString("tid_$file", null) ?: file.removePrefix("VN_").substringBeforeLast('.')
    fun aiUnread(tid: String) = aiPrefs.getBoolean("new_$tid", false)
    suspend fun pushChat(tid: String, pending: Boolean = false): Boolean {
        val msgs = db.aiThread(tid).map { (it.role == "user") to it.text }
        val flags = (if (pending) "p" else "") + (if (aiUnread(tid)) "n" else "")
        return ble.write(BfProtocol.NOTIFY, BfProtocol.chatThread(tid, flags, msgs))
    }
    /** Transcript of an AI recording -> reply in the user's language, stored in the thread and sent to BF. */
    suspend fun answerAi(file: String, text: String) {
        val tid = aiTidOf(file)
        val history = db.aiThread(tid).takeLast(12).map { (if (it.role == "user") "user" else "assistant") to it.text }
        db.addChat("user", text.ifBlank { "(no speech found)" }, src = "ai:$tid")
        val search = text.isNotBlank() && id.yanuar.bf01.ai.PlaceSearch.isPlaceQuery(text)
        if (text.isNotBlank()) db.addFeatureUse(if (search) Feature.SEARCH else Feature.ASK)
        val reply = if (text.isBlank()) "I couldn't hear anything. Try again?" else runCatching {
            withContext(useCtx(search)) { chatProvider(prefs).chat(systemPrompt() + "\nThis chat happens on BF's small screen: reply briefly (2-4 sentences) and always in the same language the user speaks.", history, text) }
                .also { db.addUsage(chat = 1) }
        }.getOrElse { log("AI chat from BF failed: ${friendlyError(it)}"); "(Couldn't answer: ${friendlyError(it)})" }
        db.addChat("assistant", reply, src = "ai:$tid")
        aiPrefs.edit().putBoolean("new_$tid", true).apply()
        revision.value++
        pushChat(tid)
    }
    /** "Tomorrow 17:00" style label for a due time; empty for no due time (BF shows Today). */
    fun whenLabel(due: Long): String {
        if (due <= 0) return ""
        val c = java.util.Calendar.getInstance(); val today = c.get(java.util.Calendar.DAY_OF_YEAR) + c.get(java.util.Calendar.YEAR) * 400
        c.timeInMillis = due; val d = c.get(java.util.Calendar.DAY_OF_YEAR) + c.get(java.util.Calendar.YEAR) * 400
        val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(due))
        return when (d - today) { 0 -> "Today $hm"; 1 -> "Tomorrow $hm"; in 2..6 -> java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(java.util.Date(due)) + " $hm"
            else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).format(java.util.Date(due)) + " $hm" }
    }

    val server = XiaozhiServer({ prefs.wsToken }, { wsUrl() }, object : XiaozhiServer.Pipeline {
        override suspend fun handle(pcm: ShortArray, onStt: suspend (String) -> Unit, onReply: suspend (String) -> Unit): ShortArray? {
            val cap = prefs.monthlyCapMin
            val (sttMin, ttsMin, _) = db.usageThisMonth()
            if (cap > 0 && sttMin + ttsMin >= cap) { onReply("This month's minutes are used up. Check Settings."); return tts("This month's minutes are used up.") }
            val voiced = XiaozhiServer.Vad.voicedMs(pcm)
            log("ASR: audio ${pcm.size / 16} ms (16 kHz), voiced ${voiced} ms")
            // v0.3.9.6: coba ulang sekali bila ASR gagal / respons tak terbaca
            val text = try { stt().transcribe(pcm) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                log("ASR failed, retrying: ${friendlyError(e)}")
                try { stt().transcribe(pcm) } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { throw DeviceAiError("ASR", e2) } }
            db.addUsage(sttSec = pcm.size / 16000.0)
            onStt(text)
            if (text.isBlank()) { log("ASR: no speech"); onReply("Couldn't hear you"); return null }
            if (nextIsTask) { nextIsTask = false; return makeTask(text, onReply) }
            val history = db.chatHistory(12).map { (if (it.role == "user") "user" else "assistant") to it.text }
            val search = id.yanuar.bf01.ai.PlaceSearch.isPlaceQuery(text)
            db.addFeatureUse(if (search) Feature.SEARCH else Feature.ASK)
            val reply = try { withContext(useCtx(search)) { chatProvider(prefs).chat(systemPrompt(), history, text) } }
                catch (e: CancellationException) { throw e } catch (e: Exception) {
                    val k = (e as? AiError ?: classify("AI", e)).kind
                    throw DeviceAiError(if (k == AiFail.NO_KEY || k == AiFail.BAD_KEY || k == AiFail.QUOTA || k == AiFail.NOT_ACTIVATED || k == AiFail.BAD_MODEL) "KEY" else "NET", e) }
            // v0.3.9.5: satu percakapan — pertanyaan dari BF masuk riwayat chat app, ditandai "dari BF".
            db.addChat("user", text, src = "bf")
            db.addUsage(chat = 1); db.addChat("assistant", reply, src = "bf"); maybeTrimChat(); revision.value++; autoMemory.onChatActivity()
            lastDeviceAiError.value = null
            onReply(reply)
            return tts(reply)
        }
        override fun shouldShow(e: Exception, afterReply: Boolean): Boolean {
            val d = e as? DeviceAiError
            if (d?.stage == "ASR" && afterReply) { log("ASR failed on follow-up audio after a reply — ignored (${friendlyError(d.cause ?: d)})"); return false }
            return true
        }
        override fun failText(e: Exception): String {
            val d = e as? DeviceAiError
            val msg = when (d?.stage) {
                "ASR" -> "Couldn't turn speech into text (${friendlyError(d.cause ?: d)})"
                "KEY", "NET" -> friendlyError(d.cause ?: d)
                else -> friendlyError(e)
            }
            lastDeviceAiError.value = System.currentTimeMillis() to "AI FAILED: ${d?.stage ?: "NET"} — $msg"
            log("AI from BF failed (${d?.stage ?: "?"}): $msg")
            return "AI FAILED: ${d?.stage ?: "NET"}\n$msg"
        }
    }, hostProvider = { prefs.serverHost }, devices = object : XiaozhiServer.DeviceGate {
        override fun pairedId() = prefs.pairedDeviceId
        override fun pairingOpen() = prefs.pairingOpen() || prefs.pairedDeviceId.isEmpty()
        override fun accept(deviceId: String): Boolean {
            if (prefs.pairedDeviceId.isEmpty()) { prefs.pairedDeviceId = deviceId; prefs.pairingOpenUntil = 0; log("Paired BF Device-Id $deviceId") }
            return prefs.pairedDeviceId.equals(deviceId, ignoreCase = true)
        }
        override fun forget() { prefs.clearPairing() }
    })
    /** Gagal di tahap tertentu saat BF bertanya: ASR (suara→teks), KEY (API key/kuota), NET (internet/server). */
    class DeviceAiError(val stage: String, cause: Throwable) : Exception(stage, cause)
    /** Error terakhir dari obrolan lewat BF (waktu, teks Indonesia) untuk ditampilkan di app. */
    val lastDeviceAiError = MutableStateFlow<Pair<Long, String>?>(null)
    /** v0.3.9.5: BF memutus sambungan sendiri (bukan error). */
    val bfTurnedOff = MutableStateFlow(false)

    private suspend fun makeTask(text: String, onReply: suspend (String) -> Unit): ShortArray {
        // AI merapikan ucapan jadi judul tugas singkat; kalau gagal, pakai teks mentah.
        val title = runCatching { chatProvider(prefs).chat("Turn this speech into one short task title (max 6 words), in the same language as the speech, no quotes.", emptyList(), text) }
            .getOrDefault(text).take(60)
        db.addTask(title); revision.value++
        scope.launch { pushTasks(showNow = true) }
        val r = "Got it, task \"$title\" saved."
        onReply(r); return tts(r)
    }

    /** STT untuk rekaman BF: penyedia aktif (Qwen qwen3-asr-flash atau xAI), bahasa Indonesia. STT sistem tidak bisa membaca file. */
    internal fun sttForRecordings(): SttProvider = when {
        prefs.llmProvider == "qwen" -> QwenStt(prefs)
        prefs.xaiKey.isNotBlank() -> XaiStt(prefs)
        else -> throw ApiException("Add an API key in Settings > AI provider to transcribe recordings")
    }
    private fun stt(): SttProvider = when (aiStack(prefs).stt) { "qwen" -> QwenStt(prefs); else -> XaiStt(prefs) }

    /** "Test speech" button: phone voice says a sentence, the speech-to-text model must hear it. */
    suspend fun testSpeech(): SpeechTestResult = withContext(Dispatchers.IO) {
        val voice = SystemTts(ctx)
        try { id.yanuar.bf01.ai.testSpeech(speechToText(prefs), sttLabel(prefs)) { kotlinx.coroutines.withTimeoutOrNull(10_000) { voice.speak("Halo, ini tes suara.") } } }
        finally { voice.close() }
    }
    private suspend fun tts(t: String): ShortArray {
        var xai = aiStack(prefs).tts == "xai"
        val p: TtsProvider = if (xai) XaiTts(prefs) else SystemTts(ctx)
        val pcm = runCatching { p.speak(t) }.getOrElse { xai = false; SystemTts(ctx).speak(t) }
        db.addUsage(ttsSec = pcm.size / 16000.0)
        // v0.4.0: one row per voice reply. xAI TTS is billed per character; the phone voice is free.
        id.yanuar.bf01.ai.UsageLog.tts(if (xai) "xai" else "phone", if (xai) "grok-tts" else "system", t.length, pcm.size / 16000.0)
        return pcm
    }
    /** Voice search (place questions) is logged as its own AI use. */
    private fun useCtx(search: Boolean): kotlin.coroutines.CoroutineContext =
        if (search) id.yanuar.bf01.ai.AiUse.Ctx(id.yanuar.bf01.ai.AiUse.SEARCH) else kotlin.coroutines.EmptyCoroutineContext

    /** Typed chat from the phone (same AI brain as voice). Runs off the main thread; throws AiError on failure.
     *  The user's message is saved only together with the reply, so a failed send can be retried without duplicates. */
    suspend fun chatText(text: String): String = withContext(Dispatchers.IO) {
        val history = db.chatHistory(12).map { (if (it.role == "user") "user" else "assistant") to it.text }
        val reply = try { chatProvider(prefs).chat(systemPrompt(), history, text) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { log("Chat failed (${chatLabel(prefs)}): ${friendlyError(e)}"); throw e }
        db.addFeatureUse(Feature.ASK); db.addChat("user", text); db.addUsage(chat = 1); db.addChat("assistant", reply); maybeTrimChat(); revision.value++; autoMemory.onChatActivity()
        reply
    }

    // v0.3.9.5: memori di prompt dibatasi (maks 40 item / 2500 huruf, terbaru diutamakan) dan di-cache sampai berubah
    @Volatile private var memCache: Pair<Int, String>? = null
    private fun memoryBlock(): String {
        val rev = db.memRev
        memCache?.let { if (it.first == rev) return it.second }
        val all = db.memories().takeLast(40)
        val sb = StringBuilder()
        for (m in all.asReversed()) { val line = "- ${m.text.take(300)}\n"; if (sb.length + line.length > 2500) break; sb.insert(0, line) }
        return sb.toString().trimEnd().also { memCache = rev to it }
    }
    private var chatInserts = 0
    /** v0.3.9.5: trimChat tiap 20 pesan saja (bukan setiap kirim). */
    private fun maybeTrimChat() { if (++chatInserts % 20 == 1) db.trimChat() }

    fun systemPrompt(): String {
        val mem = memoryBlock()
        val name = prefs.aiName
        // Nama panggilan AI bisa diubah; ganti kata "BF" di persona supaya tidak bertentangan.
        val persona = if (name == "BF") prefs.persona else prefs.persona.replace(Regex("\\bBF\\b"), name)
        val city = id.yanuar.bf01.ai.PlaceSearch.city(ctx, prefs.lastLat, prefs.lastLon)
        return "Your name: $name.\n" + persona + id.yanuar.bf01.ai.PlaceSearch.RULES +
            (if (city != null) "\nAlex's approximate location (from the phone's last location): $city." else "") +
            (if (mem.isNotBlank()) "\nThings you remember about Alex:\n$mem" else "")
    }

    val fwUpdate by lazy { FwUpdate(this) }
    val recordings by lazy { RecordingSync(this) }
    val autoMemory by lazy { AutoMemory(this) }

    fun wsUrl() = "ws://${prefs.serverHost}:8000/xiaozhi/v1/"
    fun otaUrl() = "http://${prefs.serverHost}:8000/xiaozhi/ota/"

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
        ble.onReady = { syncAll(); recordings.onConnected()
            if (!forwardPaused.value) scope.launch { lastMedia?.let { ble.write(BfProtocol.NOTIFY, it) }; lastNav?.let { ble.write(BfProtocol.NOTIFY, it) } } }
        ble.onDisconnected = { LastLocation.save(ctx, prefs); log("BF disconnected (asleep/out of range). Last location saved.") }
        prefs.deviceAddress?.let { ble.connect(it) }
        if (collectors) return; collectors = true
        scope.launch { ble.events.collect { onEvent(it) } }
        scope.launch { ble.info.collect { it?.let { i -> BatteryStats.record(ctx, i); BatteryDays.record(ctx, i) } } }
        scope.launch { ble.findPhone.collect { if (it) { db.addFeatureUse(Feature.FIND_PHONE); findPhone.start() } else findPhone.stop() } }
        // v0.3.9: setiap perubahan data (hapus/ubah/selesai/batal selesai tugas, dari layar mana pun) -> snapshot tugas ke BF.
        scope.launch { revision.collectLatest { delay(400); if (ble.state.value == BleManager.State.READY) runCatching { pushTasks() } } }
        scope.launch { ble.state.collect { if (it == BleManager.State.READY) bfTurnedOff.value = false } }
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
    /** v0.3.9: Reconnect yang gagal berturut-turut (bond basi dsb.). ≥2 -> tampilkan kartu bantuan Forget phone. */
    val reconnectFails = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var reconnectWatch: Job? = null
    fun reconnect() {
        reconnectWatch?.cancel()
        reconnectWatch = scope.launch {
            val ok = kotlinx.coroutines.withTimeoutOrNull(25_000) { ble.state.first { it == BleManager.State.READY } } != null
            reconnectFails.value = if (ok) 0 else reconnectFails.value + 1
        }
        prefs.linkEnabled = true; linked.value = true
        log("Connecting to BF...")
        BfService.start(ctx)
    }

    suspend fun syncAll() {
        log("BF connected, syncing...")
        syncTime()
        ble.write(BfProtocol.NOTIFY, BfProtocol.sleepTimeout(prefs.sleepSec))
        sendPowerTimes()
        ble.write(BfProtocol.NOTIFY, BfProtocol.agenda(CalendarSource.todayLines(ctx)))
        pushTasks(force = true)
        db.aiThreads().take(4).forEach { pushChat(it.first) }
        pushAutoOpen()
        pushAlarms()
        LastLocation.save(ctx, prefs)
        sendNameCard()
        log("Sync done")
    }

    suspend fun syncTime() {
        val now = System.currentTimeMillis()
        ble.write(BfProtocol.TIME, BfProtocol.encodeTime(now / 1000, TimeZone.getDefault().getOffset(now) / 60000))
    }
    /** v0.3.9: isi terakhir yang terkirim; dikosongkan saat sambung ulang supaya selalu dikirim penuh lagi. */
    @Volatile private var lastTasks: List<Pair<Long, String>>? = null
    suspend fun pushTasks(showNow: Boolean = false, force: Boolean = false): Boolean {
        val v2 = (ble.info.value?.tasksV ?: 0) >= 2
        val open = db.tasks().filter { !it.done }.map { it.id to (if (v2 && it.due > 0) it.title + "\t" + whenLabel(it.due) else it.title) }
        if (!force && !showNow && open == lastTasks) return true
        val ok = if ((ble.info.value?.tasksV ?: 0) >= 1) ble.write(BfProtocol.NOTIFY, BfProtocol.taskSnapshot(System.currentTimeMillis(), open, showNow))
            else ble.write(BfProtocol.NOTIFY, BfProtocol.tasks(open.map { it.second }.ifEmpty { listOf("No tasks") }, showNow))
        if (ok) lastTasks = open
        return ok
    }
    suspend fun pushAutoOpen() = ble.write(BfProtocol.NOTIFY, BfProtocol.autoOpen(prefs.autoOpen))

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

    /** Profiles BF can show: filled in only. */
    fun bfProfiles(): List<BfProtocol.BfProfile> = prefs.profiles().filter { ProfileTypes.url(it).isNotEmpty() }
        .map { BfProtocol.BfProfile(ProfileTypes.logoKey(it), ProfileTypes.displayName(it), ProfileTypes.url(it)) }
    /** Sends the profile list to BF (all writes must succeed). */
    /** Phone's own Bluetooth/device name, shown on BF's Status screen. */
    fun phoneName(): String = runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
    suspend fun sendNameCard(): Boolean {
        val l = bfProfiles()
        for (w in BfProtocol.nameCardWrites(l, l.firstOrNull()?.url ?: "", prefs.cardName, prefs.cardTitle,
            prefs.cardName.trim().split(" ").firstOrNull().orEmpty(), prefs.deviceName.ifBlank { "BF" }, phoneName())) if (!ble.write(BfProtocol.NAMECARD, w)) return false
        return true
    }

    data class ProvResult(val ok: Boolean, val text: String)

    /** Sends Wi-Fi + server to BF and follows BF's replies (prov:ok / prov:error / wifi:on / wifi:fail). */
    suspend fun provisionWithStatus(step: (String) -> Unit): ProvResult = kotlinx.coroutines.coroutineScope {
        val ssids = (0..1).map { prefs.wifi(it).first }.filter { it.isNotBlank() }
        if (ssids.isEmpty()) return@coroutineScope ProvResult(false, "Add a Wi-Fi name first.")
        if (ble.state.value != id.yanuar.bf01.ble.BleManager.State.READY)
            return@coroutineScope ProvResult(false, "BF isn't connected. Connect BF first (press PWR if it's asleep), then try again.")
        step("Sending to BF…")
        val ack = async { kotlinx.coroutines.withTimeoutOrNull(12_000) { ble.events.first { it is BfProtocol.Event.ProvOk || it is BfProtocol.Event.ProvError } } }
        kotlinx.coroutines.yield()
        if (!provision()) { ack.cancel(); return@coroutineScope ProvResult(false, "Couldn't send over Bluetooth. Move closer to BF and try again.") }
        when (val e = ack.await()) {
            null -> return@coroutineScope ProvResult(true, "Sent. BF didn't confirm yet. Check BF's screen.")
            is BfProtocol.Event.ProvError -> return@coroutineScope ProvResult(false,
                if (e.why == "json") "BF couldn't read the settings. Try a shorter Wi-Fi name or password, then send again." else "BF rejected the settings (${e.why}).")
            else -> {}
        }
        log("Wi-Fi settings saved on BF")
        ProvResult(true, "Saved on BF. It will use ${ssids.joinToString(" / ") { "\"$it\"" }} the next time it needs Wi-Fi (chat and file sync).")
    }

    suspend fun provision(): Boolean {
        val profiles = (0..1).map { prefs.wifi(it) }.filter { it.first.isNotBlank() }.map { BfProtocol.WifiProfile(it.first, it.second) }
        prefs.openPairing(); return ble.write(BfProtocol.PROVISION, BfProtocol.provision(profiles, prefs.wsToken, wsUrl(), otaUrl()))
    }

    /** Rekaman TASK dari BF: langsung jadi tugas TO-DO dengan judul sementara; judul AI + transkrip (detail)
     *  mengganti setelah audionya ditarik lewat Wi-Fi dan ditranskrip (RecordingSync). */
    fun addVoiceTask(file: String) {
        db.setNoteType(file, "TASK")
        val exists = db.readableDatabase.rawQuery("SELECT 1 FROM tasks WHERE note_file=?", arrayOf(file)).use { it.moveToFirst() }
        if (!exists && !db.isTombstoned(file)) db.upsertVoiceTask(file, "Voice to-do (transcribing…)")
        revision.value++
        scope.launch { pushTasks() }
    }

    /** Waktu Home/tidur/mati otomatis ke BF (firmware lama mengabaikan 'I'). */
    suspend fun sendPowerTimes() = ble.write(BfProtocol.NOTIFY, BfProtocol.powerTimes(prefs.homeIdleSec, prefs.sleepSec, prefs.autoOffMin))

    /** v0.3.9.5: cari IP HP yang satu jaringan dengan BF, simpan, dan kirim alamat server AI ke BF.
     *  Dulu alamat hanya dikirim saat pairing (default 192.168.43.1) sehingga BF sering menelepon IP yang salah. */
    /** 0.3.9.8.7+: true while BF is on the phone's temporary hotspot; its gateway IP must not become serverHost. */
    @Volatile var hotspotSession = false

    private suspend fun sendServer(bfIp: String) {
        if (hotspotSession) { log("BF on phone hotspot ($bfIp); server address kept at ${prefs.serverHost}"); return }
        val mine = localIpFor(bfIp)
        if (mine != null && mine != prefs.serverHost) { log("Phone server address updated: $mine (BF $bfIp)"); prefs.serverHost = mine }
        if (mine == null) log("Couldn't find the phone IP on BF's network ($bfIp); using ${prefs.serverHost}")
        prefs.openPairing(); ble.write(BfProtocol.FILES, BfProtocol.serverCmd(wsUrl(), otaUrl(), prefs.wsToken))
    }

    private fun localIpFor(bfIp: String): String? = runCatching {
        val target = java.net.InetAddress.getByName(bfIp).address
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }.firstOrNull { ia ->
                val a = ia.address as? java.net.Inet4Address ?: return@firstOrNull false
                val bits = ia.networkPrefixLength.toInt().coerceIn(8, 30)
                val x = java.nio.ByteBuffer.wrap(a.address).int; val y = java.nio.ByteBuffer.wrap(target).int
                val mask = -1 shl (32 - bits)
                (x and mask) == (y and mask) && x != y
            }?.address?.hostAddress
    }.getOrNull()

    private suspend fun onEvent(e: BfProtocol.Event) {
        log("Event: $e")
        when (e) {
            is BfProtocol.Event.Media -> mediaCommand?.invoke(e.cmd)
            is BfProtocol.Event.TaskStart -> nextIsTask = true
            is BfProtocol.Event.NoteNew -> { db.addFeatureUse(Feature.VOICE_NOTE); db.setNoteType(e.file, "NOTE"); revision.value++; recordings.request() }
            is BfProtocol.Event.TaskNew -> { db.addFeatureUse(Feature.TODO); addVoiceTask(e.file); recordings.request() }
            is BfProtocol.Event.NoteDeleted -> {
                val al = ctx.getSharedPreferences("rec_alias", Context.MODE_PRIVATE)   // 0.3.9.8.6: VN_1970 files are saved under phone time
                val k = al.all.keys.firstOrNull { it.startsWith(e.file + "|") }
                db.deleteNoteByFile(k?.let { al.getString(it, null) } ?: e.file); k?.let { al.edit().remove(it).apply() }; revision.value++ }
            is BfProtocol.Event.AiNew -> { db.setNoteType(e.file, "AI"); aiPrefs.edit().putString("tid_${e.file}", aiTidOf(e.file)).apply(); revision.value++; recordings.request() }
            is BfProtocol.Event.AiReply -> { db.setNoteType(e.file, "AIR"); aiPrefs.edit().putString("tid_${e.file}", e.tid).apply(); recordings.request() }
            is BfProtocol.Event.AiSeen -> { aiPrefs.edit().remove("new_${e.tid}").apply(); revision.value++ }
            is BfProtocol.Event.TodoNo -> { e.id.removePrefix("t").toLongOrNull()?.let { db.deleteTask(it) }; revision.value++; pushTasks(force = true) }
            is BfProtocol.Event.TodoOk -> log("To-do confirmed on BF")
            is BfProtocol.Event.SetOk -> setAck.tryEmit(e.what)
            is BfProtocol.Event.Ota -> fwUpdate.onEvent(e)
            is BfProtocol.Event.BleOff -> bfTurnedOff.value = true
            is BfProtocol.Event.AiFail -> lastDeviceAiError.value = System.currentTimeMillis() to "AI FAILED: ${e.stage} (on BF)"
            is BfProtocol.Event.AiNet -> { wifiOnBf.value = true; sendServer(e.ip) }
            is BfProtocol.Event.WifiReady -> { wifiOnBf.value = true; sendServer(e.ip) }
            is BfProtocol.Event.WifiOn -> wifiOnBf.value = true
            is BfProtocol.Event.WifiOff, is BfProtocol.Event.WifiFail -> wifiOnBf.value = false
            is BfProtocol.Event.ProvOk -> log("Wi-Fi settings saved on BF")
            is BfProtocol.Event.ReminderFired -> { db.addFeatureUse(Feature.REMINDER); pushAlarms() }
            BfProtocol.Event.ReminderOk -> pushAlarms()
            else -> {}
        }
    }

    companion object {
        @Volatile private var inst: Hub? = null
        fun get(ctx: Context): Hub = inst ?: synchronized(this) { inst ?: Hub(ctx.applicationContext).also { inst = it } }
    }
}
