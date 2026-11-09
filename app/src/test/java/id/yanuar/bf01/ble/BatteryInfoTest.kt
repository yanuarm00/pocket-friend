package id.yanuar.bf01.ble

import id.yanuar.bf01.svc.BatteryStats
import org.junit.Assert.*
import org.junit.Test

class BatteryInfoTest {
    @Test fun parsesUsageAndEstimates() {
        val i = BfProtocol.parseInfo("""{"fw":"0.3.9.3-test","batt":70,"batt_mv":3900,"chg":false,"use":{"up":7200,"rec":600,"ble":7000,"wifi":60,"awake":1800,"ai":300,"usb":0,"wakes":40,"start_pct":90}}""")
        val u = i.use!!
        assertEquals(3900, i.battMv); assertEquals(600L, u.rec); assertEquals(90, u.startPct)
        val r = BatteryStats.rate(emptyList(), u, i.batt)!!
        assertEquals(10.0, r, 0.01)
        val parts = BatteryStats.parts(u)
        assertTrue(parts.sumOf { it.pct } in 98..102)
        assertNull(BfProtocol.parseInfo("""{"batt":50}""").use)
    }

    /** 0.3.9.8.6: the Oct 29 case — stale phone history (95 -> 90 over 30 h) must not win over BF's counters (92 -> 66 in 1.85 h). */
    @Test fun prefersBfCountersOverStaleHistory() {
        val t0 = 1_700_000_000_000L
        val pts = (0..30).map { t0 + it * 3600_000L to (95 - it / 6) }
        val up = (1.85 * 3600).toLong()
        val i = BfProtocol.parseInfo("""{"fw":"0.3.9.8.6-test","batt":66,"batt_mv":3822,"chg":false,"slp":0,"use":{"up":$up,"rec":0,"ble":$up,"wifi":0,"awake":$up,"ai":0,"usb":0,"wakes":0,"start_pct":92}}""")
        assertEquals(0, i.sleepS)
        val r = BatteryStats.rate(pts, i.use!!, i.batt)!!
        assertEquals(14.05, r, 0.2)
        val left = i.batt / r
        assertTrue("left=$left", left in 4.0..5.0)
        assertTrue(BatteryStats.sleepOff(i.use!!, i.sleepS))
        assertTrue(BatteryStats.tips(BatteryStats.parts(i.use!!), i.use!!, left, i.sleepS).first().startsWith("Sleep is off"))
    }

    @Test fun historyOnlyUsesLast3hAndIsCapped() {
        val t0 = 1_700_000_000_000L
        // 30 h of slow history then a steep 3 h: only the last 3 h count
        val pts = (0..30).map { t0 + it * 3600_000L to 100 - it / 10 } + (1..3).map { t0 + (30 + it) * 3600_000L to 97 - it * 10 }
        val u = BfProtocol.Usage(600, 0, 0, 0, 0, 0, 0, 0, -1)   // BF counters too short -> history
        assertEquals(10.0, BatteryStats.rate(pts, u, 67)!!, 0.5)
        val fast = listOf(t0 to 100, t0 + 1800_000L to 20)
        assertEquals(BatteryStats.MAX_RATE, BatteryStats.rate(fast, u, 20)!!, 0.01)
        // 30 h awake but history says 0.2%/h -> floored by the physical model
        val awake = BfProtocol.Usage(30 * 3600, 0, 0, 0, 30 * 3600, 0, 0, 0, 95)
        assertTrue(BatteryStats.rate(emptyList(), awake, 89)!! >= 3.0)
    }
}
