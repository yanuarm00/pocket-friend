package id.yanuar.bf01.svc

import android.content.Context
import id.yanuar.bf01.ble.BfProtocol
import kotlin.math.roundToInt

/**
 * Analisa baterai BF (v0.3.9.3-test+). Semua angka PERKIRAAN:
 * - laju turun dihitung dari riwayat % yang dicatat HP (hanya saat tidak mengisi),
 *   cadangan: (persen awal - persen sekarang) / lama menyala dari penghitung BF;
 * - porsi pemakaian memakai bobot arus kira-kira per kegiatan (belum diukur di BF asli).
 */
object BatteryStats {
    private const val PREF = "battery_stats"
    // perkiraan arus (mA) per kegiatan, di atas arus dasar
    internal const val MA_BASE = 6.0; internal const val MA_AWAKE = 18.0; internal const val MA_BLE = 4.0
    internal const val MA_REC = 40.0; internal const val MA_WIFI = 75.0; internal const val MA_AI = 95.0

    data class Part(val label: String, val pct: Int, val seconds: Long)
    data class Report(val pct: Int, val mv: Int, val charging: Boolean, val hoursLeft: Double?, val ratePerHour: Double?,
                      val parts: List<Part>, val tips: List<String>, val use: BfProtocol.Usage)

    /** Simpan titik (waktu, %) saat tidak mengisi; dibuang saat mengisi atau % naik. Murah: maks 1 titik / 5 menit. */
    fun record(ctx: Context, i: BfProtocol.Info) {
        if (i.batt < 0 || i.use == null) return
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        accumulate(sp, i)
        val now = System.currentTimeMillis()
        val pts = load(sp.getString("pts", "") ?: "").toMutableList()
        if (i.chg || (pts.isNotEmpty() && i.batt > pts.last().second + 2)) { sp.edit().putString("pts", "").apply(); return }
        if (pts.isNotEmpty() && now - pts.last().first < 5 * 60_000L) return
        pts += now to i.batt
        val keep = pts.filter { now - it.first < 48 * 3600_000L }.takeLast(200)
        sp.edit().putString("pts", keep.joinToString(";") { "${it.first},${it.second}" }).apply()
    }

    /** v0.3.9.6: kalau penghitung BF mulai dari 0 lagi (BF mati total) tanpa dicas penuh, jumlahkan ke sesi sebelumnya. */
    private fun accumulate(sp: android.content.SharedPreferences, i: BfProtocol.Info) {
        val u = i.use ?: return
        if (sp.getString("fw", "") != i.fw) {   // v0.3.9.8.1: new firmware starts its counters fresh; don't mix
            sp.edit().putString("fw", i.fw).putString("base", "").putString("pts", "").putString("last", enc(u)).apply(); return }
        val last = dec(sp.getString("last", "") ?: ""); var base = dec(sp.getString("base", "") ?: "")
        if (u.usb > 0 && i.batt >= 98) base = null            // penuh: BF juga mulai dari 0
        else if (last != null && u.up + 60 < last.up) {
            base = add(base, last)
            sp.edit().putString("pts", "").apply()   // 0.3.9.8.6: BF counters restarted -> old phone history no longer matches
        }
        sp.edit().putString("last", enc(u)).putString("base", base?.let { enc(it) } ?: "").apply()
    }
    private fun enc(u: BfProtocol.Usage) = listOf(u.up, u.rec, u.ble, u.wifi, u.awake, u.ai, u.usb, u.wakes.toLong(), u.startPct.toLong()).joinToString(",")
    private fun dec(s: String): BfProtocol.Usage? { val a = s.split(',').mapNotNull { it.toLongOrNull() }; if (a.size != 9) return null
        return BfProtocol.Usage(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7].toInt(), a[8].toInt()) }
    private fun add(b: BfProtocol.Usage?, u: BfProtocol.Usage) = if (b == null) u else BfProtocol.Usage(b.up + u.up, b.rec + u.rec,
        b.ble + u.ble, b.wifi + u.wifi, b.awake + u.awake, b.ai + u.ai, b.usb + u.usb, b.wakes + u.wakes, b.startPct)
    fun merged(ctx: Context, u: BfProtocol.Usage): BfProtocol.Usage {
        val base = dec(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("base", "") ?: "") ?: return u
        return add(base, u)
    }
    /** "Reset battery stats": forget the phone-side history too. */
    fun reset(ctx: Context) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("base", "").putString("pts", "").putString("last", "").apply() }
    const val MIN_DATA_SEC = 30 * 60L

    fun points(ctx: Context): List<Pair<Long, Int>> {
        val now = System.currentTimeMillis()
        return load(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("pts", "") ?: "")
            .filter { now - it.first < 24 * 3600_000L }
    }

    /** Demo / empty-state curve for UI when no live samples yet. */
    fun demoPoints(now: Long = System.currentTimeMillis()): List<Pair<Long, Int>> {
        val vals = listOf(92, 90, 88, 85, 84, 82, 80, 78, 76, 74, 72, 70, 68, 66, 64, 62, 60, 58, 70, 75, 78, 80, 81, 82)
        return vals.mapIndexed { i, p -> (now - (23 - i) * 3600_000L) to p }
    }

    private fun load(s: String) = s.split(';').mapNotNull { e -> e.split(',').takeIf { it.size == 2 }?.let { (a, b) ->
        val t = a.toLongOrNull(); val p = b.toIntOrNull(); if (t != null && p != null) t to p else null } }

    const val CELL_MAH = 400.0
    /** Fastest believable drop for a 400 mAh cell (~250 mA peak): % per hour. */
    const val MAX_RATE = 60.0
    const val WINDOW_MS = 3 * 3600_000L

    /** Modelled average current (mA) from BF's time counters. */
    fun modelMa(u: BfProtocol.Usage): Double? {
        if (u.up <= 0) return null
        val mas = u.awake * MA_AWAKE + u.ai * MA_AI + u.rec * MA_REC + u.wifi * MA_WIFI + u.ble * MA_BLE + u.up * MA_BASE
        return mas / u.up
    }

    /**
     * 0.3.9.8.6: % per hour.
     * 1) BF's own counters (start % -> now over time on battery) when they cover >= 30 min;
     * 2) else phone history, only the last 3 h;
     * then clamp to what a 400 mAh cell can physically do: not faster than [MAX_RATE], and not slower than
     * half of what BF's time counters say it should draw (stops "4 days left" when the screen never sleeps).
     */
    fun rate(pts: List<Pair<Long, Int>>, u: BfProtocol.Usage, pct: Int): Double? {
        var r: Double? = null
        val hb = (u.up - u.usb) / 3600.0; val db = u.startPct - pct
        if (u.startPct >= 0 && hb >= 0.5 && db >= 3) r = db / hb
        if (r == null && pts.size >= 2) {
            val end = pts.last().first
            val w = pts.filter { end - it.first <= WINDOW_MS }
            if (w.size >= 2) {
                val h = (w.last().first - w.first().first) / 3600_000.0; val d = w.first().second - w.last().second
                if (h >= 0.5 && d >= 3) r = d / h
            }
        }
        if (r == null) return null
        val floor = modelMa(u)?.takeIf { u.up >= MIN_DATA_SEC }?.let { it * 100.0 / CELL_MAH * 0.5 } ?: 0.0
        return r.coerceAtLeast(floor).coerceAtMost(MAX_RATE)
    }

    fun parts(u: BfProtocol.Usage): List<Part> {
        val sleep = (u.up - u.awake).coerceAtLeast(0)
        if (u.up < MIN_DATA_SEC || (u.awake + u.ai + u.rec + u.wifi + u.ble) == 0L) return emptyList()
        val raw = listOf(
            Triple("Screen & buttons (BF awake)", u.awake, MA_AWAKE),
            Triple("AI chat", u.ai, MA_AI),
            Triple("Voice recording", u.rec, MA_REC),
            Triple("Wi-Fi (files & updates)", u.wifi, MA_WIFI),
            Triple("Connected to phone (Bluetooth)", u.ble, MA_BLE),
            Triple("Idle / asleep", u.up, MA_BASE))
        val total = raw.sumOf { it.second * it.third }.takeIf { it > 0 } ?: return emptyList()
        return raw.map { Part(it.first, (it.second * it.third * 100 / total).roundToInt(), if (it.first.startsWith("Idle")) sleep else it.second) }
            .filter { it.seconds > 0 }.sortedByDescending { it.pct }
    }

    /** 0.3.9.8.6: BF reports sleep OFF, or (older firmware) it was awake >= 95% of the time. */
    fun sleepOff(u: BfProtocol.Usage, sleepS: Int = -1): Boolean =
        sleepS == 0 || (u.up >= MIN_DATA_SEC && u.awake * 100 >= u.up * 95)

    fun tips(parts: List<Part>, u: BfProtocol.Usage, hoursLeft: Double?, sleepS: Int = -1): List<String> {
        val t = mutableListOf<String>()
        if (sleepOff(u, sleepS)) t += "Sleep is off: BF's screen never sleeps, so the battery lasts hours, not days. On BF, open Tweaks and turn SLEEP on."
        if (parts.isEmpty()) return t
        parts.firstOrNull()?.let { top -> when {
            top.label.startsWith("AI chat") -> t += "AI chat uses the most. Use it sparingly when the battery is low."
            top.label.startsWith("Wi-Fi") -> t += "Wi-Fi uses the most. Get files or update firmware while BF is charging."
            top.label.startsWith("Voice") -> t += "Long recordings use the most. Record in parts instead of leaving it on."
            top.label.startsWith("Screen") && sleepOff(u, sleepS) -> {}
            top.label.startsWith("Screen") -> t += if (u.wakes * 3600.0 / u.up.coerceAtLeast(1) >= 6)
                "BF wakes up a lot. Shorten \"Screen sleeps after\" in the Sleep timer card below."
                else "BF's screen stays awake most of the time. Shorten \"Screen sleeps after\" in the Sleep timer card below."
            else -> {}
        } }
        if (u.up > 0 && u.wakes * 3600.0 / u.up > 30) t += "BF wakes more than 30 times an hour. Turn off noisy notifications in Music, Maps & notifications."
        if (hoursLeft != null && hoursLeft < 3) t += "Battery is low. Charge BF now."
        if (t.isEmpty()) t += "BF is using little power. Keep it up."
        return t
    }

    fun report(ctx: Context, i: BfProtocol.Info): Report? {
        val u = merged(ctx, i.use ?: return null)
        val pts = load(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("pts", "") ?: "")
        val r = if (i.chg) null else rate(pts, u, i.batt)
        val left = r?.let { i.batt / it }
        val ps = parts(u)
        return Report(i.batt, i.battMv, i.chg, left, r, ps, tips(ps, u, left, i.sleepS), u)
    }

    fun dur(sec: Long): String { val h = sec / 3600; val m = sec % 3600 / 60; return if (h > 0) "$h h $m min" else "$m min" }
    fun left(h: Double): String = if (h >= 48) "about ${(h / 24).roundToInt()} days" else if (h >= 1) "about ${h.roundToInt()} h" else "under 1 hour"

    fun text(r: Report, fw: String): String = buildString {
        appendLine("BF battery report (firmware $fw)")
        appendLine("Battery: ${r.pct}% · ${r.mv} mV · ${if (r.charging) "charging" else "not charging"}")
        if (r.tips.firstOrNull()?.startsWith("Sleep is off") == true) appendLine("Warning: Sleep is off")
        appendLine("Time left: " + (r.hoursLeft?.let { left(it) } ?: "not enough data yet"))
        r.ratePerHour?.let { appendLine("Dropping about ${"%.1f".format(it)}% per hour") }
        val u = r.use
        appendLine("Since full/on: ${dur(u.up)} (start ${u.startPct}%), woke ${u.wakes}x")
        appendLine("Time: screen ${dur(u.awake)}, AI ${dur(u.ai)}, recording ${dur(u.rec)}, Wi-Fi ${dur(u.wifi)}, Bluetooth ${dur(u.ble)}, USB ${dur(u.usb)}")
        if (r.parts.isEmpty()) appendLine("Only ${u.up / 60} min of data so far. Breakdown needs 30 min.")
        else appendLine("Estimated usage share:")
        r.parts.forEach { appendLine("- ${it.label}: ${it.pct}%") }
        appendLine("Tips:")
        r.tips.forEach { appendLine("- $it") }
    }
}
