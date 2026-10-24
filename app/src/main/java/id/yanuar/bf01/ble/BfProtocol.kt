package id.yanuar.bf01.ble

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Protokol GATT BF-01, harus sama persis dengan firmware
 * (bf01-firmware: docs/BLE_PROTOCOL.md v0.1 dan main/boards/bf01/bf_ble.cc).
 * Kode murni Kotlin supaya bisa dites di host (lihat BfProtocolTest).
 */
object BfProtocol {
    private fun uuid(x: Int) = UUID.fromString("b0f1%04x-5a1e-4b1d-9e3f-0bf01bf01bf0".format(x))
    val SERVICE: UUID = uuid(0x00)
    val INFO: UUID = uuid(0x01)       // read, JSON
    val TIME: UUID = uuid(0x02)       // write*, 10 byte
    val NOTIFY: UUID = uuid(0x03)     // write*, 1 byte tipe + UTF-8
    val FIND: UUID = uuid(0x05)       // write* + notify
    val NAMECARD: UUID = uuid(0x06)   // write* + read*, JSON
    val PROVISION: UUID = uuid(0x07)  // write*, JSON
    val EVENT: UUID = uuid(0x08)      // notify (read*), teks
    val FILES: UUID = uuid(0x09)      // write* + read*
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val NAME_PREFIX = "BF-01-"
    const val MTU = 247

    /** Kode layar 4 digit -> passkey 6 digit (nol di depan). "4827" -> 4827 (= "004827"). */
    fun passkeyFromCode(code: String): Int {
        val c = code.trim()
        require(c.length == 4 && c.all { it.isDigit() }) { "Kode harus 4 angka" }
        return c.toInt()
    }
    fun passkeyString(code: String) = "%06d".format(passkeyFromCode(code))

    /** int64 detik Unix UTC LE + int16 offset menit LE. */
    fun encodeTime(epochSec: Long, tzOffsetMin: Int): ByteArray =
        ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(epochSec).putShort(tzOffsetMin.toShort()).array()

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ').trim()

    fun notify(type: Char, payload: String): ByteArray = byteArrayOf(type.code.toByte()) + payload.toByteArray(Charsets.UTF_8)

    /** N: panah|jarak|jalan|langkah berikut|sisa|jam tiba. panah "-" = jangan paksa tampil. */
    fun navigation(arrow: String, distance: String, street: String, next: String, remain: String, eta: String) =
        notify('N', listOf(arrow, distance, street, next, remain, eta).joinToString("|") { clean(it) })

    fun music(title: String, artist: String, playing: Boolean) =
        notify('M', "${clean(title)}|${clean(artist)}|${if (playing) 1 else 0}")

    fun agenda(lines: List<String>) = notify('C', lines.joinToString("\n") { clean(it) })

    fun tasks(lines: List<String>, showNow: Boolean = false) =
        notify('T', lines.mapIndexed { i, l -> if (i == 0 && showNow) "!" + clean(l) else clean(l) }.joinToString("\n"))

    fun reminderNow(title: String, range: String, note: String) = notify('R', "${clean(title)}|${clean(range)}|${clean(note)}")

    data class Alarm(val epochUtc: Long, val title: String, val range: String)
    fun alarms(list: List<Alarm>) = notify('A', list.joinToString("\n") { "${it.epochUtc}|${clean(it.title)}|${clean(it.range)}" })

    fun sleepTimeout(sec: Int) = notify('S', sec.coerceIn(15, 3600).toString())

    fun find(ring: Boolean) = byteArrayOf(if (ring) 1 else 0)

    fun nameCard(qr: String, name: String, title: String): ByteArray =
        JSONObject().put("qr", qr).put("name", name).put("title", title).toString().toByteArray()

    data class WifiProfile(val ssid: String, val pass: String)
    fun provision(profiles: List<WifiProfile>, token: String, wsUrl: String, otaUrl: String): ByteArray {
        val arr = JSONArray()
        profiles.take(2).forEach { arr.put(JSONObject().put("ssid", it.ssid).put("pass", it.pass)) }
        return JSONObject().put("profiles", arr).put("token", token).put("ws_url", wsUrl).put("ota_url", otaUrl)
            .toString().toByteArray()
    }

    fun filesCmd(cmd: String) = cmd.toByteArray()
    fun filesSynced(name: String) = filesCmd("synced:$name")
    /** v0.3.0+: minta BF mengunduh firmware dari [url] (server HP), cek ukuran & SHA-256 sebelum dipasang. */
    fun otaStart(sha256: String, size: Long, url: String) = filesCmd("ota:start:${sha256.lowercase()}:$size:$url")

    data class FileEntry(val name: String, val bytes: Long, val synced: Boolean)
    fun parseFiles(s: String): List<FileEntry> = s.lines().mapNotNull { l ->
        val p = l.split('|'); if (p.size < 3) null else FileEntry(p[0], p[1].toLongOrNull() ?: 0, p[2].trim() == "1")
    }

    data class Info(val fw: String, val batt: Int, val sd: Boolean, val sdFreeMb: Int, val rec: Boolean,
                    val paired: Boolean, val wifi: Boolean, val wifiOn: Boolean, val warm: Boolean, val state: String,
                    /** v0.3.0+: firmware menerima "ota:start"; usb = kabel USB data terdeteksi. */
                    val ota: Boolean = false, val usb: Boolean = false)
    fun parseInfo(s: String): Info {
        val j = JSONObject(s.ifBlank { "{}" })
        fun b(k: String) = when (val v = j.opt(k)) { is Boolean -> v; is Number -> v.toInt() != 0; else -> false }
        return Info(j.optString("fw"), j.optInt("batt", -1), b("sd"), j.optInt("sd_free_mb"), b("rec"),
            b("paired"), b("wifi"), b("wifi_on"), b("warm"), j.optString("state"), b("ota"), b("usb"))
    }

    /** Satu rekaman dari GET /notes (firmware v0.3.4+). type: NOTE, TASK, atau PENDING (belum dipilih di BF). */
    data class RemoteNote(val name: String, val size: Long, val durationS: Int, val type: String, val synced: Boolean)
    const val FILE_PORT = 8080
    fun parseNotesJson(s: String): List<RemoteNote> {
        val a = JSONObject(s.ifBlank { "{}" }).optJSONArray("notes") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o ->
            val n = o.optString("name"); if (!n.endsWith(".wav") || n.contains('/')) null
            else RemoteNote(n, o.optLong("size"), o.optInt("duration"), o.optString("type", "PENDING").uppercase(), o.optBoolean("synced")) } }
    }

    sealed class Event {
        data class Media(val cmd: String) : Event()
        object ReminderOk : Event(); object ReminderSnooze : Event()
        data class ReminderFired(val epoch: Long) : Event()
        object TaskStart : Event()
        data class NoteNew(val file: String) : Event()
        /** Firmware v0.2.0+: rekaman dipilih jadi TASK di layar NOTE/TASK. */
        data class TaskNew(val file: String) : Event()
        /** Firmware v0.3.3+: recording deleted on the device. */
        data class NoteDeleted(val file: String) : Event()
        object FindBfStopped : Event(); object PairOk : Event(); object ProvOk : Event()
        data class ProvError(val why: String) : Event()
        object WifiOn : Event(); object WifiOff : Event(); object WifiFail : Event()
        /** Firmware v0.3.4+: sesi file siap. Server HTTP BF di http://<ip>:8080, token sekali per sesi. */
        data class WifiReady(val ip: String, val token: String) : Event()
        /** Update firmware (v0.3.0+). stage: accept, wifi, verify, ok; progress 0..100; fail/refused + alasan. */
        data class Ota(val stage: String, val progress: Int = -1, val reason: String = "") : Event()
        data class Unknown(val raw: String) : Event()
    }
    fun parseEvent(raw: String): Event {
        val s = raw.trim()
        return when {
            s.startsWith("media:") -> Event.Media(s.removePrefix("media:"))
            s == "reminder:ok" -> Event.ReminderOk
            s == "reminder:snooze" -> Event.ReminderSnooze
            s.startsWith("reminder:fired:") -> Event.ReminderFired(s.removePrefix("reminder:fired:").toLongOrNull() ?: 0)
            s == "task:start" -> Event.TaskStart
            s.startsWith("note:new:") -> Event.NoteNew(s.removePrefix("note:new:"))
            s.startsWith("note:deleted:") -> Event.NoteDeleted(s.removePrefix("note:deleted:"))
            s.startsWith("task:new:") -> Event.TaskNew(s.removePrefix("task:new:"))
            s == "findbf:stopped" -> Event.FindBfStopped
            s == "pair:ok" -> Event.PairOk
            s == "prov:ok" -> Event.ProvOk
            s.startsWith("prov:error:") -> Event.ProvError(s.removePrefix("prov:error:"))
            s == "wifi:on" -> Event.WifiOn
            s.startsWith("wifi:on:") -> s.removePrefix("wifi:on:").split(':').let { p ->
                if (p.size >= 2 && p[0].isNotBlank() && p[1].isNotBlank()) Event.WifiReady(p[0], p[1]) else Event.WifiOn }
            s == "wifi:off" -> Event.WifiOff
            s == "wifi:fail" -> Event.WifiFail
            s.startsWith("ota:progress:") -> Event.Ota("progress", s.removePrefix("ota:progress:").toIntOrNull() ?: 0)
            s.startsWith("ota:fail:") -> Event.Ota("fail", reason = s.removePrefix("ota:fail:"))
            s.startsWith("ota:refused:") -> Event.Ota("refused", reason = s.removePrefix("ota:refused:"))
            s.startsWith("ota:") -> Event.Ota(s.removePrefix("ota:"))
            else -> Event.Unknown(s)
        }
    }

    /** Teks belokan Maps -> kode panah. [BELUM DICEK] kode panah harus cocok dengan aset firmware. */
    fun arrowFor(text: String): String {
        val t = text.lowercase()
        return when {
            "putar balik" in t || "u-turn" in t -> "U"
            "bundaran" in t || "roundabout" in t -> "O"
            ("kiri" in t || "left" in t) && ("sedikit" in t || "slight" in t || "serong" in t) -> "SL"
            ("kanan" in t || "right" in t) && ("sedikit" in t || "slight" in t || "serong" in t) -> "SR"
            "kiri" in t || "left" in t -> "L"
            "kanan" in t || "right" in t -> "R"
            "lurus" in t || "straight" in t || "continue" in t || "head" in t -> "S"
            "tiba" in t || "arrive" in t || "destination" in t -> "D"
            else -> "-"
        }
    }
}
