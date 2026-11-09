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
    val OTA_DATA: UUID = uuid(0x0a)   // v0.3.9+: write tanpa respons*, [offset u32 LE][isi]
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val NAME_PREFIX = "BF-01-"
    const val MTU = 247

    /** Kode layar 4 digit -> passkey 6 digit (nol di depan). "4827" -> 4827 (= "004827"). */
    fun passkeyFromCode(code: String): Int {
        val c = code.trim()
        require(c.length == 4 && c.all { it.isDigit() }) { "Code must be 4 digits" }
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

    /** v0.3.9: snapshot daftar tugas lengkap + nomor versi (BF membuang snapshot yang lebih lama). Baris "#t<id>|judul". */
    fun taskSnapshot(version: Long, tasks: List<Pair<Long, String>>, showNow: Boolean = false) =
        notify('T', (if (showNow) "!" else "") + "#v$version" + tasks.joinToString("") { (id, t) -> "\n#t$id|" + t.split('\t').joinToString("\t") { clean(it) } })
    fun autoOpen(on: Boolean) = notify('O', if (on) "1" else "0")

    fun reminderNow(title: String, range: String, note: String) = notify('R', "${clean(title)}|${clean(range)}|${clean(note)}")

    data class Alarm(val epochUtc: Long, val title: String, val range: String)
    fun alarms(list: List<Alarm>) = notify('A', list.joinToString("\n") { "${it.epochUtc}|${clean(it.title)}|${clean(it.range)}" })

    fun sleepTimeout(sec: Int) = notify('S', sec.coerceIn(15, 3600).toString())
    /** v0.3.9.5-test+: waktu kembali ke Home (detik) | tidur layar (detik) | mati otomatis saat tidur (menit, 0 = tidak). */
    fun powerTimes(homeSec: Int, sleepSec: Int, offMin: Int) = notify('I', "${homeSec.coerceIn(5, 30)}|${sleepSec.coerceIn(15, 3600)}|${offMin.coerceIn(0, 240)}")
    /** v0.3.9.5-test+: alamat server AI di HP, disimpan BF tanpa restart. */
    fun serverCmd(ws: String, ota: String, token: String) = filesCmd("srv:$ws|$ota|$token")

    fun find(ring: Boolean) = byteArrayOf(if (ring) 1 else 0)

    /** One Hi! profile as BF gets it: [logo] = logo key ("ig", "yt", "#K" ...), [label] = name under the QR, [url] = text in the QR. */
    data class BfProfile(val logo: String, val label: String, val url: String)

    /** Longest single BLE write we make (Android long writes go up to 512 bytes). */
    const val NAMECARD_CHUNK = 480

    /**
     * Hi! profiles for BF (protocol v2): a list of writes, each under [NAMECARD_CHUNK] bytes. Every write carries
     * "v":2 and "off" (index of its first profile); BF replaces its list on off 0 and appends otherwise.
     * The first write also has qr / name / title, which firmware older than 0.3.9.8.2 still shows as its Name card.
     */
    fun nameCardWrites(profiles: List<BfProfile>, qr: String, name: String, title: String,
                      user: String = "", device: String = "", phone: String = ""): List<ByteArray> {
        fun cut(s: String, max: Int): String { var o = s; while (o.toByteArray().size > max) o = o.dropLast(1); return o }
        fun item(p: BfProfile) = JSONObject().put("t", cut(p.logo, 4)).put("l", cut(p.label, 72)).put("q", cut(p.url, 250))
        fun write(off: Int, items: List<JSONObject>) = JSONObject().put("v", 2).put("off", off).apply {
            if (off == 0) put("qr", cut(qr, 120)).put("name", cut(name, 40)).put("title", cut(title, 40))
                .put("user", cut(user, 23)).put("device", cut(device, 23)).put("phone", cut(phone, 31))
        }.put("profiles", org.json.JSONArray(items)).toString().toByteArray()
        val out = ArrayList<ByteArray>(); var off = 0
        val cur = ArrayList<JSONObject>()
        for ((i, p) in profiles.withIndex()) {
            cur.add(item(p))
            if (cur.size > 1 && write(off, cur).size > NAMECARD_CHUNK) { cur.removeAt(cur.size - 1); out.add(write(off, cur)); off = i; cur.clear(); cur.add(item(p)) }
        }
        out.add(write(off, cur))
        return out
    }
    /** 'H' chat thread: "tid|flags\nU:..\nA:.." kept under ~450 bytes (newest messages win). */
    fun chatThread(tid: String, flags: String, msgs: List<Pair<Boolean, String>>): ByteArray {
        val out = ArrayList<String>(); var size = tid.length + flags.length + 2
        for ((user, t) in msgs.asReversed()) {
            val line = (if (user) "U:" else "A:") + t.replace('\n', ' ').trim().let { if (it.length > 220) it.take(219) + "…" else it }
            val n = line.toByteArray().size + 1
            if (size + n > 450) break
            out.add(0, line); size += n
        }
        return notify('H', "$tid|$flags" + out.joinToString("") { "\n" + it })
    }
    fun todoConfirm(id: Long, title: String, whenLabel: String) = notify('K', "t$id|${clean(title)}|${clean(whenLabel)}")

    data class WifiProfile(val ssid: String, val pass: String)
    fun provision(profiles: List<WifiProfile>, token: String, wsUrl: String, otaUrl: String): ByteArray {
        val arr = JSONArray()
        profiles.take(2).forEach { arr.put(JSONObject().put("ssid", it.ssid).put("pass", it.pass)) }
        return JSONObject().put("profiles", arr).put("token", token).put("ws_url", wsUrl).put("ota_url", otaUrl)
            .toString().toByteArray()
    }

    fun filesCmd(cmd: String) = cmd.toByteArray()
    /** 0.3.9.8.7: temporary phone hotspot for this file session only (BF restores its saved Wi-Fi afterwards). */
    fun filesHotspot(ssid: String, pass: String) = filesCmd("wifi:hs:{\"s\":${jsonStr(ssid)},\"p\":${jsonStr(pass)}}")
    fun jsonStr(v: String) = buildString {
        append('"')
        for (c in v) when {
            c == '"' -> append("\\\""); c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
    fun filesSynced(name: String) = filesCmd("synced:$name")
    /** v0.3.0+: minta BF mengunduh firmware dari [url] (server HP), cek ukuran & SHA-256 sebelum dipasang. */
    fun otaStart(sha256: String, size: Long, url: String) = filesCmd("ota:start:${sha256.lowercase()}:$size:$url")

    /** v0.3.9+: kirim firmware lewat BLE. Sesi sama (sha+ukuran) = lanjut dari offset terakhir. */
    fun otaBle(sha256: String, size: Long) = filesCmd("ota:ble:${sha256.lowercase()}:$size")
    fun otaAbort() = filesCmd("ota:abort")
    /** Paket data OTA: offset u32 little-endian + [len] byte dari [src] mulai [from]. */
    fun otaPacket(offset: Long, src: ByteArray, from: Int, len: Int): ByteArray {
        val p = ByteArray(4 + len); val o = offset.toInt()
        p[0] = o.toByte(); p[1] = (o ushr 8).toByte(); p[2] = (o ushr 16).toByte(); p[3] = (o ushr 24).toByte()
        System.arraycopy(src, from, p, 4, len); return p
    }

    data class FileEntry(val name: String, val bytes: Long, val synced: Boolean)
    fun parseFiles(s: String): List<FileEntry> = s.lines().mapNotNull { l ->
        val p = l.split('|'); if (p.size < 3) null else FileEntry(p[0], p[1].toLongOrNull() ?: 0, p[2].trim() == "1")
    }

    data class Info(val fw: String, val batt: Int, val sd: Boolean, val sdFreeMb: Int, val rec: Boolean,
                    val paired: Boolean, val wifi: Boolean, val wifiOn: Boolean, val warm: Boolean, val state: String,
                    /** v0.3.0+: firmware menerima "ota:start"; usb = kabel USB data terdeteksi. */
                    val ota: Boolean = false, val usb: Boolean = false,
                    /** 1 = OTA lewat Wi-Fi (ota:start), 2 = v0.3.9+: juga lewat BLE (ota:ble). */
                    val otaLevel: Int = if (ota) 1 else 0,
                    /** v0.3.9+: BF menerima snapshot tugas berversi (taskSnapshot). */
                    val tasksV: Int = 0,
                    /** v0.3.9.1-test+: alasan restart terakhir dari InfoJson. */
                    val rst: String = "", val lastCrash: String = "", val crashInfo: String = "",
                    /** v0.3.9.2-test+: siapa yang memanggil restart (mis. "mcp-reboot"). */
                    val restartWhy: String = "",
                    /** v0.3.9.3-test+: tegangan baterai, sedang mengisi (USB), penghitung pemakaian. */
                    val battMv: Int = 0, val chg: Boolean = false, val use: Usage? = null,
                    /** v0.3.9.6-test+: sumber AI terakhir (mis. "chat:touch"). */
                    val aiSrc: String = "",
                    /** 0.3.9.8.6-test+: BF sleep timer in seconds, 0 = sleep OFF, -1 = older firmware (unknown). */
                    val sleepS: Int = -1)
    /** Detik pemakaian sejak baterai penuh (atau sejak BF menyala). */
    data class Usage(val up: Long, val rec: Long, val ble: Long, val wifi: Long, val awake: Long, val ai: Long,
                     val usb: Long, val wakes: Int, val startPct: Int)
    /** v0.3.9.8.1: BF's info JSON can exceed the 512-byte BLE read limit (long crash_info), arriving cut off.
     *  Rebuild a valid object from the complete top-level fields instead of failing (which hid BF info -> "BF is not connected"). */
    fun parseInfo(s: String): Info {
        val j = runCatching { JSONObject(s.ifBlank { "{}" }) }.getOrElse { lenientInfo(s) }
        fun b(k: String) = when (val v = j.opt(k)) { is Boolean -> v; is Number -> v.toInt() != 0; else -> false }
        return Info(j.optString("fw"), j.optInt("batt", -1), b("sd"), j.optInt("sd_free_mb"), b("rec"),
            b("paired"), b("wifi"), b("wifi_on"), b("warm"), j.optString("state"), b("ota"), b("usb"),
            when (val v = j.opt("ota")) { is Number -> v.toInt(); is Boolean -> if (v) 1 else 0; else -> 0 }, j.optInt("tasks_v"),
            j.optString("rst"), j.optString("last_crash"), j.optString("crash_info"), j.optString("restart_why"),
            j.optInt("batt_mv"), b("chg"), j.optJSONObject("use")?.let { u ->
                Usage(u.optLong("up"), u.optLong("rec"), u.optLong("ble"), u.optLong("wifi"), u.optLong("awake"), u.optLong("ai"),
                    u.optLong("usb"), u.optInt("wakes"), u.optInt("start_pct", -1)) }, j.optString("ai_src"), j.optInt("slp", -1))
    }

    internal fun lenientInfo(s: String): JSONObject {
        val o = JSONObject()
        Regex(""""([a-z_]+)":("(?:[^"\\]|\\.)*"|true|false|-?\d+(?:\.\d+)?)""").findAll(s.substringBefore("\"use\":{") + s.substringAfter("\"use\":{", "").substringAfter("}", "")).forEach { m ->
            val v = m.groupValues[2]
            o.put(m.groupValues[1], when { v.startsWith("\"") -> runCatching { JSONObject("{\"v\":$v}").getString("v") }.getOrDefault(""); v == "true" -> true; v == "false" -> false
                v.contains('.') -> v.toDouble(); else -> v.toLong() })
        }
        return o
    }

    /** Plain-English "Last restart: ..." line, or null if BF reported nothing. */
    fun lastRestartText(i: Info, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
        if (i.rst.isBlank() && i.lastCrash.isBlank() && i.crashInfo.isBlank() && i.restartWhy.isBlank()) return null
        val ci = i.crashInfo
        val task = Regex("""t=(\S+)""").find(ci)?.groupValues?.get(1)?.takeIf { it != "?" }
        val pc = Regex("""pc=(0x[0-9a-fA-F]+)""").find(ci)?.groupValues?.get(1)
        val why = when {
            ci.contains("stack overflow", true) -> "stack overflow"
            ci.contains("LoadProhibited") -> "bad memory read"
            ci.contains("StoreProhibited") -> "bad memory write"
            ci.contains("IllegalInstruction") -> "illegal instruction"
            ci.contains("Interrupt wdt", true) -> "froze (watchdog)"
            ci.contains("abort", true) || ci.contains("assert", true) -> "abort"
            else -> null
        }
        fun kind(r: String) = when (r) {
            "panic" -> "crash"; "int_wdt", "task_wdt", "wdt" -> "froze (watchdog)"; "brownout" -> "power dip (brownout)"
            "poweron" -> "normal power-on"; "sw" -> "software restart (update or reboot)"; "deepsleep" -> "wake from sleep"
            "usb" -> "USB reset"; "ext" -> "reset button"; "" -> "unknown"; else -> r
        }
        val (lcKind, lcEpoch) = i.lastCrash.split('@').let { it[0] to it.getOrNull(1)?.toLongOrNull() }
        val time = lcEpoch?.takeIf { it > 1767225600L }?.let {
            java.time.format.DateTimeFormatter.ofPattern("HH:mm, d MMM").format(java.time.Instant.ofEpochSecond(it).atZone(zone))
        }
        val sb = StringBuilder("Last restart: ")
        if (i.rst == "panic" || i.rst.endsWith("wdt") || i.rst == "brownout" || (i.rst.isBlank() && i.lastCrash.isNotBlank())) {
            sb.append(kind(i.rst.ifBlank { lcKind }))
            task?.let { sb.append(" in ").append(it) }
            why?.takeIf { kind(i.rst) == "crash" }?.let { sb.append(" (").append(it).append(")") }
            time?.let { sb.append(" at ").append(it) }
        } else {
            sb.append(kind(i.rst))
            if (i.lastCrash.isNotBlank()) {
                sb.append(". Last crash: ").append(kind(lcKind))
                task?.let { sb.append(" in ").append(it) }
                why?.let { sb.append(" (").append(it).append(")") }
                time?.let { sb.append(" at ").append(it) }
            }
        }
        if (pc != null && ci.isNotBlank()) sb.append(" [pc ").append(pc).append("]")
        if (i.restartWhy.isNotBlank()) sb.append(" why=").append(i.restartWhy)
        // v0.3.9.6: restart software / tanpa panic -> tampilkan seluruh catatan (task, pemanggil, breadcrumb)
        if (ci.isNotBlank() && pc == null) sb.append(" [").append(ci.removePrefix("restart ")).append("]")
        return sb.toString()
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
        /** v0.3.9.7: AI voice chat from BF (new thread / reply), seen, to-do confirm, settings ack. */
        data class AiNew(val file: String) : Event()
        data class AiReply(val tid: String, val file: String) : Event()
        data class AiSeen(val tid: String) : Event()
        data class TodoOk(val id: String) : Event()
        data class TodoNo(val id: String) : Event()
        data class SetOk(val what: String) : Event()
        /** Firmware v0.3.3+: recording deleted on the device. */
        data class NoteDeleted(val file: String) : Event()
        object FindBfStopped : Event(); object PairOk : Event(); object ProvOk : Event()
        data class ProvError(val why: String) : Event()
        object WifiOn : Event() { override fun toString() = "WifiOn" }
        object WifiOff : Event() { override fun toString() = "WifiOff" }
        object WifiFail : Event() { override fun toString() = "WifiFail (BF couldn't join Wi-Fi)" }
        /** Firmware v0.3.4+: sesi file siap. Server HTTP BF di http://<ip>:8080, token sekali per sesi. */
        data class WifiReady(val ip: String, val token: String) : Event()
        /** v0.3.9.5-test+: BF dapat Wi-Fi untuk obrolan AI dan menunggu alamat server HP. */
        data class AiNet(val ip: String) : Event()
        /** v0.3.9.5: BF mematikan sambungan HP sendiri (tahan layar 1,5 dtk). Bond tetap. */
        object BleOff : Event()
        data class AiFail(val stage: String) : Event()
        /** Update firmware (v0.3.0+). stage: accept, wifi, verify, ok; progress 0..100; fail/refused + alasan. */
        data class Ota(val stage: String, val progress: Int = -1, val reason: String = "", val offset: Long = -1) : Event()
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
            s.startsWith("ai:new:") -> Event.AiNew(s.removePrefix("ai:new:"))
            s.startsWith("ai:reply:") -> s.removePrefix("ai:reply:").let { Event.AiReply(it.substringBefore(':'), it.substringAfter(':')) }
            s.startsWith("ai:seen:") -> Event.AiSeen(s.removePrefix("ai:seen:"))
            s.startsWith("todo:ok:") -> Event.TodoOk(s.removePrefix("todo:ok:"))
            s.startsWith("todo:no:") -> Event.TodoNo(s.removePrefix("todo:no:"))
            s.startsWith("set:ok:") -> Event.SetOk(s.removePrefix("set:ok:"))
            s == "findbf:stopped" -> Event.FindBfStopped
            s == "pair:ok" -> Event.PairOk
            s == "prov:ok" -> Event.ProvOk
            s.startsWith("prov:error:") -> Event.ProvError(s.removePrefix("prov:error:"))
            s == "wifi:on" -> Event.WifiOn
            s.startsWith("wifi:on:") -> s.removePrefix("wifi:on:").split(':').let { p ->
                if (p.size >= 2 && p[0].isNotBlank() && p[1].isNotBlank()) Event.WifiReady(p[0], p[1]) else Event.WifiOn }
            s.startsWith("ai:net:") -> Event.AiNet(s.removePrefix("ai:net:"))
            s == "ble:off" -> Event.BleOff
            s.startsWith("ai:fail:") -> Event.AiFail(s.removePrefix("ai:fail:"))
            s == "wifi:off" -> Event.WifiOff
            s == "wifi:fail" -> Event.WifiFail
            s.startsWith("ota:ready:") || s.startsWith("ota:ack:") || s.startsWith("ota:need:") -> s.split(':').let {
                Event.Ota(it[1], offset = it.getOrNull(2)?.toLongOrNull() ?: 0) }
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
            "lurus" in t || "ikuti" in t || "terus" in t || "straight" in t || "continue" in t || "head" in t -> "S"
            "tiba" in t || "arrive" in t || "destination" in t -> "D"
            else -> "-"
        }
    }
}
