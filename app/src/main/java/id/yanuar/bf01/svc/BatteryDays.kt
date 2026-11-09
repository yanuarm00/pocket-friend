package id.yanuar.bf01.svc

import android.content.Context
import id.yanuar.bf01.ble.BfProtocol
import java.time.LocalDate

/**
 * v0.4.0: per-day battery history for the Usage page (Week / Month).
 * Each info read adds to today's row: % dropped while not charging, and how many seconds BF's time counters grew
 * (awake, AI, recording, Wi-Fi, Bluetooth, up). Kept on the phone only, last 62 days.
 */
object BatteryDays {
    data class Day(val day: String, val drop: Int, val up: Long, val rec: Long, val ble: Long, val wifi: Long, val awake: Long, val ai: Long) {
        operator fun plus(o: Day) = Day(day, drop + o.drop, up + o.up, rec + o.rec, ble + o.ble, wifi + o.wifi, awake + o.awake, ai + o.ai)
    }
    data class Snap(val pct: Int, val u: BfProtocol.Usage)
    private const val PREF = "battery_days"

    /** What changed between two reads. Counters that went down mean BF restarted: count the new value. */
    fun step(prev: Snap?, cur: Snap, charging: Boolean, day: String): Day {
        if (prev == null) return Day(day, 0, 0, 0, 0, 0, 0, 0)
        val restarted = cur.u.up < prev.u.up
        fun d(n: Long, o: Long) = if (restarted) n else (n - o).coerceAtLeast(0)
        val drop = if (!charging && cur.pct < prev.pct) prev.pct - cur.pct else 0
        return Day(day, drop, d(cur.u.up, prev.u.up), d(cur.u.rec, prev.u.rec), d(cur.u.ble, prev.u.ble), d(cur.u.wifi, prev.u.wifi),
            d(cur.u.awake, prev.u.awake), d(cur.u.ai, prev.u.ai))
    }

    fun record(ctx: Context, i: BfProtocol.Info, today: LocalDate = LocalDate.now()) {
        val u = i.use ?: return; if (i.batt < 0) return
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val prev = sp.getString("last", "")?.split(',')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 7 }
            ?.let { Snap(it[0].toInt(), BfProtocol.Usage(it[1], it[2], it[3], it[4], it[5], it[6], 0, 0, -1)) }
        val cur = Snap(i.batt, u)
        val delta = step(prev, cur, i.chg, today.toString())
        val days = load(sp.getString("days", "")).associateBy { it.day }.toMutableMap()
        days[delta.day] = days[delta.day]?.plus(delta) ?: delta
        val from = today.minusDays(62).toString()
        sp.edit().putString("days", days.values.filter { it.day >= from }.sortedBy { it.day }.joinToString(";") { enc(it) })
            .putString("last", listOf(i.batt.toLong(), u.up, u.rec, u.ble, u.wifi, u.awake, u.ai).joinToString(",")).apply()
    }
    fun days(ctx: Context): List<Day> = load(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("days", ""))
    fun reset(ctx: Context) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply() }

    private fun enc(d: Day) = "${d.day}:${d.drop},${d.up},${d.rec},${d.ble},${d.wifi},${d.awake},${d.ai}"
    internal fun load(s: String?): List<Day> = s.orEmpty().split(';').mapNotNull { e ->
        val k = e.substringBefore(':', ""); val a = e.substringAfter(':').split(',').mapNotNull { it.toLongOrNull() }
        if (k.length != 10 || a.size != 7) null else Day(k, a[0].toInt(), a[1], a[2], a[3], a[4], a[5], a[6]) }
}
