package id.yanuar.bf01.svc

import org.junit.Assert.*
import org.junit.Test

/** Contoh notifikasi navigasi Google Maps berbahasa Indonesia (Galaxy A53). */
class MapsParseTest {
    private fun p(vararg f: String) = MapsListener.parseNav(f.toList())

    @Test fun indonesianTurn() {
        val n = p("250 m", "Belok kiri ke Jl. Sudirman", "12 menit · 4,1 km · tiba pukul 14.35")!!
        assertEquals("250M", n.distance); assertEquals("Belok kiri ke Jl. Sudirman", n.instruction)
        assertEquals("Jl. Sudirman", n.street); assertEquals("14.35", n.eta); assertEquals("12 menit", n.remain)
    }
    @Test fun decimalKmAndColonTime() {
        val n = p("1,2 km", "Belok kanan menuju Jl. Gatot Subroto", "Tiba 09:05")!!
        assertEquals("1.2KM", n.distance); assertEquals("09.05", n.eta); assertEquals("Jl. Gatot Subroto", n.street)
    }
    @Test fun distanceInsideText() {
        val n = p("Belok kiri dalam 300 m", "", "")!!
        assertEquals("300M", n.distance); assertTrue(n.instruction.startsWith("Belok kiri"))
    }
    @Test fun arrivalWithoutDistance() {
        val n = p("Tiba di tujuan", "Jl. Merdeka 10")!!
        assertEquals("", n.distance); assertEquals("Tiba di tujuan", n.instruction)
    }
    @Test fun remoteViewsStyleOrder() {
        // RemoteViews kustom: urutan teks tidak dijamin
        val n = p("tiba pukul 18.20", "Ikuti Jl. Ahmad Yani", "800 m")!!
        assertEquals("800M", n.distance); assertEquals("Ikuti Jl. Ahmad Yani", n.instruction); assertEquals("18.20", n.eta)
    }
    @Test fun emptyIsNull() { assertNull(p()); assertNull(p("", " ")) }
}
