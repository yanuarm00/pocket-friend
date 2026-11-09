package id.yanuar.bf01.svc

import android.net.Uri
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.fw.BleOtaSender
import id.yanuar.bf01.fw.FirmwareImage
import id.yanuar.bf01.server.XiaozhiServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.SecureRandom

/**
 * Update firmware BF lewat app.
 *  1. Mas pilih file xiaozhi.bin (unduhan rilis GitHub) dari penyimpanan HP. Repo privat -> app tidak bisa mengunduh sendiri.
 *  2. App hitung SHA-256 & baca versi dari file. Harus cocok dengan daftar resmi di app ATAU SHA256SUMS yang dipilih/ditempel.
 *  3. File disajikan di server HP port 8000: /bf/fw/<token acak>.bin (hanya selama sesi).
 *  4. BLE "ota:start:<sha>:<size>:<url>" -> BF nyalakan Wi-Fi, unduh, cek SHA-256, pasang ke slot OTA lain, restart.
 *  5. Setelah BF tersambung lagi, versi dicek. BF menandai firmware sehat setelah 30 dtk; kalau macet, BF kembali ke versi lama.
 * Jalur lama (BF v0.2.x tanpa perintah ota): blok "firmware" force=1 di /xiaozhi/ota/ saat BF menyalakan Wi-Fi (eksperimental).
 */
class FwUpdate(private val hub: Hub) {
    enum class Phase { Idle, Ready, Sending, Accepted, Wifi, Downloading, Verifying, Rebooting, Done, Failed }
    data class Ui(
        val phase: Phase = Phase.Idle,
        val fileName: String? = null,
        val image: FirmwareImage.Parsed? = null,
        val sumsSha: String? = null,
        val progress: Int = -1,
        val message: String = "",
        val legacy: Boolean = false,
        /** v0.3.9: data dikirim lewat Bluetooth (bukan Wi-Fi). */
        val viaBle: Boolean = false,
        val etaS: Int = -1,
    ) {
        val verifiedBy: String? get() = when {
            image == null || !image.ok -> null
            image.knownVersion != null -> "checksum matches the app's list (v${image.knownVersion})"
            sumsSha != null && sumsSha == image.sha256 -> "checksum matches SHA256SUMS"
            else -> null
        }
        val busy get() = phase in setOf(Phase.Sending, Phase.Accepted, Phase.Wifi, Phase.Downloading, Phase.Verifying, Phase.Rebooting)
    }

    val state = MutableStateFlow(Ui())
    private var watchdog: Job? = null
    private val file get() = File(hub.ctx.cacheDir, "fw/update.bin")

    init {
        hub.server.onFwRequest = { if (state.value.legacy) set { it.copy(phase = Phase.Downloading, message = "BF is downloading firmware (legacy path)…") } }
        hub.scope.launch { hub.ble.state.collect { onBle(it) } }
    }

    private inline fun set(f: (Ui) -> Ui) { state.value = f(state.value) }

    /** Salin file pilihan ke cache app lalu periksa. */
    suspend fun pick(uri: Uri, name: String?) = withContext(Dispatchers.IO) {
        if (state.value.busy) return@withContext
        file.parentFile?.mkdirs()
        val parsed = runCatching {
            hub.ctx.contentResolver.openInputStream(uri)!!.use { inp -> file.outputStream().use { inp.copyTo(it) } }
            FirmwareImage.parse(file.inputStream())
        }.getOrElse { FirmwareImage.Parsed(0, "", null, null, "Can't read the file") }
        set { Ui(phase = if (parsed.ok) Phase.Ready else Phase.Idle, fileName = name ?: "firmware.bin", image = parsed, sumsSha = it.sumsSha,
            message = parsed.error ?: "") }
    }

    /** v0.3.9: firmware terbaru yang diumumkan di rilis Pocket Friend (manifest JSON). */
    data class Remote(val version: String, val size: Long, val sha256: String, val url: String)
    val remote = kotlinx.coroutines.flow.MutableStateFlow<Remote?>(null)
    suspend fun checkLatest() = withContext(Dispatchers.IO) {
        runCatching {
            val c = java.net.URL(MANIFEST_URL).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000; c.instanceFollowRedirects = true
            if (c.responseCode == 200) remote.value = parseManifest(c.inputStream.bufferedReader().readText())
        }
    }
    /** Unduh firmware terbaru dari rilis, lalu cocokkan SHA-256 dengan manifest. */
    suspend fun fetchLatest() { val r = remote.value ?: return; setSums("${r.sha256}  xiaozhi.bin"); fetch(r.url) }

    /** Opsional: unduh file dari link (https) ke cache app, lalu periksa seperti file pilihan. */
    suspend fun fetch(url: String) = withContext(Dispatchers.IO) {
        if (state.value.busy) return@withContext
        set { it.copy(message = "Downloading…") }
        file.parentFile?.mkdirs()
        val parsed = runCatching {
            require(url.startsWith("https://") || url.startsWith("http://")) { "Link must start with https://" }
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            if (c.responseCode != 200) error("Download failed (HTTP ${c.responseCode})")
            c.inputStream.use { inp -> file.outputStream().use { o ->
                val b = ByteArray(64 * 1024); var tot = 0L
                while (true) { val n = inp.read(b); if (n < 0) break; o.write(b, 0, n); tot += n; if (tot > FirmwareImage.MAX_SIZE) error("File too big") } } }
            FirmwareImage.parse(file.inputStream())
        }.getOrElse { FirmwareImage.Parsed(0, "", null, null, it.message ?: "Download failed") }
        set { Ui(phase = if (parsed.ok) Phase.Ready else Phase.Idle, fileName = url.substringAfterLast('/').ifBlank { "firmware.bin" }, image = parsed,
            sumsSha = it.sumsSha, message = parsed.error ?: "") }
    }

    fun setSums(text: String) { val s = FirmwareImage.shaFromSums(text); set { it.copy(sumsSha = s, message = if (s == null) "SHA256SUMS has no hash for the firmware file" else it.message) } }

    fun canStart(info: BfProtocol.Info?, st: BleManager.State): String? {
        val u = state.value; val img = u.image
        return when {
            u.busy -> "An update is already running"
            img == null || !img.ok -> "Choose or download a firmware file first"
            u.verifiedBy == null -> "SHA-256 checksum doesn't match yet"
            st != BleManager.State.READY -> "BF is not connected"
            info == null -> "Connected, reading BF info… (if this stays, reconnect BF)"
            info.batt in 0 until 30 && !info.usb -> "BF battery ${info.batt}%. Plug in USB or charge to ≥30%"
            else -> null
        }
    }

    private fun token() = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    // ---- v0.3.9: data lewat Bluetooth ----
    private var sender: BleOtaSender? = null
    private var sendJob: Job? = null
    private var t0 = 0L

    fun startBle() {
        val img = state.value.image ?: return
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return fail("Can't read the file")
        set { it.copy(phase = Phase.Sending, progress = -1, message = "Asking BF to get ready…", legacy = false, viaBle = true, etaS = -1) }
        hub.log("Firmware update over Bluetooth: ${img.version} ${img.sha256.take(12)}…")
        sender = BleOtaSender(bytes, BleOtaSender.chunkFor(hub.ble.mtu)) { hub.ble.writeNoRsp(BfProtocol.OTA_DATA, it) }
        hub.scope.launch {
            val ok = hub.ble.write(BfProtocol.FILES, BfProtocol.otaBle(img.sha256, img.size))
            if (!ok) fail("Command not sent over Bluetooth") else arm(30_000, "BF didn't answer the update command")
        }
    }

    private fun startSending(offset: Long) {
        val s = sender ?: return
        s.onReady(offset)
        if (sendJob?.isActive == true) return
        t0 = System.currentTimeMillis()
        hub.ble.setFastLink(true)
        val start = offset
        sendJob = hub.scope.launch(Dispatchers.IO) {
            s.run { pct ->
                val el = (System.currentTimeMillis() - t0) / 1000.0
                val rate = if (el > 1) (s.acked - start) / el else 0.0
                val eta = BleOtaSender.etaSeconds(s.size - s.acked, rate)
                set { it.copy(phase = Phase.Downloading, progress = pct, etaS = eta,
                    message = "Sending to BF over Bluetooth" + (if (eta >= 0) " · about ${fmtEta(eta)} left" else "…")) }
                arm(150_000, "Transfer stalled. Keeping the old firmware")
            }
        }
    }

    private fun fmtEta(s: Int) = if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"

    /** BF v0.3.0+: perintah BLE. */
    fun start() {
        val img = state.value.image ?: return
        val t = token()
        hub.server.fw = XiaozhiServer.FwServe(t, file)
        val url = "http://${hub.prefs.serverHost}:8000/bf/fw/$t.bin"
        set { it.copy(phase = Phase.Sending, progress = -1, message = "Sending the command to BF…", legacy = false) }
        hub.log("Firmware update: ${img.version} ${img.sha256.take(12)}… to BF")
        hub.scope.launch {
            val ok = hub.ble.write(BfProtocol.FILES, BfProtocol.otaStart(img.sha256, img.size, url))
            if (!ok) fail("Command not sent over BLE") else arm(30_000, "BF didn't answer the update command")
        }
    }

    /** BF v0.2.x: jalur lama xiaozhi (blok firmware force=1), dipicu dengan menyalakan Wi-Fi BF. */
    fun startLegacy() {
        val img = state.value.image ?: return
        val t = token()
        hub.server.fw = XiaozhiServer.FwServe(t, file)
        hub.server.legacyFw = (img.version ?: FirmwareImage.LATEST) to "http://${hub.prefs.serverHost}:8000/bf/fw/$t.bin"
        set { it.copy(phase = Phase.Sending, progress = -1, legacy = true, message = "Waiting for BF to turn on Wi-Fi…") }
        hub.scope.launch {
            hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("wifi:on"))
            arm(180_000, "BF didn't fetch the firmware. Restart BF and try again, or use a cable")
        }
    }

    fun cancel() {
        if (state.value.viaBle && state.value.busy) hub.scope.launch { hub.ble.write(BfProtocol.FILES, BfProtocol.otaAbort()) }
        stopSender(); disarm(); set { Ui(phase = if (it.image?.ok == true) Phase.Ready else Phase.Idle, fileName = it.fileName, image = it.image, sumsSha = it.sumsSha) } }

    private fun arm(ms: Long, why: String) { watchdog?.cancel(); watchdog = hub.scope.launch { delay(ms); fail(why) } }
    private fun disarm() { watchdog?.cancel(); hub.server.fw = null; hub.server.legacyFw = null }

    private fun stopSender() { hub.ble.setFastLink(false); sender?.abort(); sendJob?.cancel(); sender = null; sendJob = null }
    private fun fail(msg: String) { stopSender(); disarm(); set { it.copy(phase = Phase.Failed, message = msg) }; hub.log("Firmware update failed: $msg") }

    fun onEvent(e: BfProtocol.Event.Ota) {
        when (e.stage) {
            "ready" -> { set { it.copy(phase = Phase.Downloading, progress = maxOf(it.progress, 0), message = "Sending to BF over Bluetooth…") }
                arm(150_000, "Transfer stalled. Keeping the old firmware"); startSending(e.offset) }
            "ack" -> sender?.onAck(e.offset)
            "need" -> sender?.onNeed(e.offset)
            "accept" -> { set { it.copy(phase = Phase.Accepted, message = "BF is turning on Wi-Fi…") }; arm(60_000, "BF couldn't join the phone's Wi-Fi") }
            "wifi" -> { set { it.copy(phase = Phase.Wifi, message = "BF is downloading from the phone…") }; arm(60_000, "Download stalled") }
            "progress" -> { set { it.copy(phase = Phase.Downloading, progress = e.progress, message = "BF is downloading from the phone…") }; arm(60_000, "Download stalled") }
            "verify" -> { sendJob = null; sender = null; hub.ble.setFastLink(false)
 set { it.copy(phase = Phase.Verifying, progress = 100, message = "BF is checking SHA-256…") }; arm(60_000, "BF didn't finish the check") }
            "ok" -> { set { it.copy(phase = Phase.Rebooting, progress = 100, message = "Installed. BF is restarting…") }; hub.server.fw = null; arm(120_000, "BF hasn't reconnected after restarting. Press PWR on BF") }
            "fail" -> fail(failText(e.reason))
            "refused" -> fail(if (e.reason.startsWith("battery")) "Refused by BF: battery ${e.reason.substringAfter("battery:", "?")}% (<30%). Plug in USB" else "Refused by BF: busy (recording/chatting)")
        }
    }

    private fun failText(r: String) = when (r) {
        "sha" -> "SHA-256 on BF doesn't match. Keeping the old firmware"
        "wifi" -> "BF can't join the phone's Wi-Fi/hotspot"
        "connect", "status" -> "BF can't reach the phone server (port 8000)"
        "read", "short" -> "Download interrupted. Keeping the old firmware"
        "size" -> "File size doesn't match"
        "image", "verify" -> "Not a valid BF firmware file"
        "timeout" -> "Bluetooth was quiet for too long. Keeping the old firmware"
        "abort" -> "Update cancelled. Keeping the old firmware"
        "begin", "write", "partition" -> "BF couldn't write the update slot. Keeping the old firmware"
        else -> "Failed ($r). Keeping the old firmware"
    }

    private suspend fun onBle(st: BleManager.State) {
        val u = state.value
        if (st == BleManager.State.READY && (u.phase == Phase.Rebooting || (u.legacy && u.phase == Phase.Downloading))) {
            delay(1500); hub.ble.refreshInfo()
            val fw = hub.ble.info.value?.fw; val want = u.image?.version
            disarm()
            if (want != null && fw == want) set { it.copy(phase = Phase.Done, message = "BF is now ${FirmwareImage.displayVersion(fw)}") }
            else set { it.copy(phase = Phase.Failed, message = "BF went back to ${FirmwareImage.displayVersion(fw)} (rollback). Try again or use a cable") }
        } else if (st == BleManager.State.READY && u.viaBle && u.busy && u.phase != Phase.Rebooting && u.phase != Phase.Verifying) {
            // Bluetooth tersambung lagi di tengah kiriman: minta BF lanjut dari byte terakhir.
            val img = u.image ?: return
            delay(1500)
            set { it.copy(message = "Reconnected. Resuming…") }
            hub.ble.write(BfProtocol.FILES, BfProtocol.otaBle(img.sha256, img.size))
        } else if (st != BleManager.State.READY && u.busy && u.viaBle && u.phase != Phase.Rebooting) {
            set { it.copy(message = "Bluetooth dropped. Keep BF close; it resumes when it reconnects (within 2 min)…") }
        } else if (st != BleManager.State.READY && u.busy && u.phase != Phase.Rebooting && !u.legacy) {
            set { it.copy(message = "BLE dropped. BF keeps downloading over Wi-Fi; waiting to reconnect…") }
        }
    }

    /** Untuk screenshot/test. */
    fun preview(ui: Ui) { state.value = ui }
}

const val MANIFEST_URL = "https://github.com/yanuarm00/pocket-friend/releases/latest/download/BF01-firmware.json"

fun parseManifest(text: String): FwUpdate.Remote? = runCatching {
    val j = org.json.JSONObject(text)
    val sha = j.getString("sha256").lowercase()
    require(sha.length == 64)
    FwUpdate.Remote(j.getString("version"), j.getLong("size"), sha,
        j.optString("url", "https://github.com/yanuarm00/pocket-friend/releases/latest/download/BF01-firmware.bin"))
}.getOrNull()
