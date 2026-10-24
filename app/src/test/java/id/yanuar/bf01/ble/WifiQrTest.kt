package id.yanuar.bf01.ble

import id.yanuar.bf01.wifi.WifiQr
import org.junit.Assert.*
import org.junit.Test

class WifiQrTest {
    @Test fun basicWpa() { val q = WifiQr.parse("WIFI:T:WPA;S:Rumah Yan;P:rahasia123;;")!!
        assertEquals("Rumah Yan", q.ssid); assertEquals("rahasia123", q.pass); assertEquals("WPA", q.type); assertFalse(q.hidden) }
    @Test fun anyOrderAndHidden() { val q = WifiQr.parse("WIFI:S:Kantor;H:true;P:abc;T:WPA2;;")!!
        assertEquals("Kantor", q.ssid); assertEquals("abc", q.pass); assertTrue(q.hidden) }
    @Test fun escapes() { val q = WifiQr.parse("""WIFI:T:WPA;S:My\;Net\:5\\x;P:p\;a\,s\"s\:;;""")!!
        assertEquals("My;Net:5\\x", q.ssid); assertEquals("p;a,s\"s:", q.pass) }
    @Test fun samsungHotspotStyle() { val q = WifiQr.parse("WIFI:S:Galaxy A53 5G 1234;T:SAE;P:qwerty12;H:false;;")!!
        assertEquals("Galaxy A53 5G 1234", q.ssid); assertEquals("SAE", q.type) }
    @Test fun openNetwork() { val q = WifiQr.parse("WIFI:T:nopass;S:Cafe;P:;;")!!; assertEquals("", q.pass); assertTrue(q.open) }
    @Test fun lowercasePrefixAndNoTrailing() { val q = WifiQr.parse("wifi:s:Net;p:pw")!!; assertEquals("Net", q.ssid); assertEquals("pw", q.pass) }
    @Test fun quotedValues() { assertEquals("Net", WifiQr.parse("WIFI:S:\"Net\";P:\"pw\";;")!!.ssid) }
    @Test fun passwordWithColon() { assertEquals("a:b", WifiQr.parse("WIFI:S:N;P:a:b;;")!!.pass) }
    @Test fun rejectsNonWifi() { assertNull(WifiQr.parse("https://example.com")); assertNull(WifiQr.parse("WIFI:T:WPA;P:x;;")); assertNull(WifiQr.parse(null)) }
}

class WifiMergeTest {
    @Test fun mergeDedupSortAndBand() {
        val l = id.yanuar.bf01.wifi.WifiTools.merge(listOf(
            Triple("Home", -70, 2437) to "[WPA2-PSK-CCMP]", Triple("Home", -50, 5180) to "[WPA2-PSK-CCMP]",
            Triple("Fast5", -40, 5745) to "[SAE]", Triple("Cafe", -60, 2412) to "[ESS]", Triple("", -30, 2412) to ""))
        assertEquals(listOf("Fast5", "Home", "Cafe"), l.map { it.ssid })
        assertTrue(l[0].only5); assertFalse(l[1].only5); assertEquals(-50, l[1].level)
        assertFalse(l[2].secured); assertTrue(l[1].secured)
    }
}

class WifiQrImageTest {
    @Test fun decodesGeneratedQr() {
        val text = "WIFI:T:WPA;S:Galaxy A53;P:pa\\;ss;;"
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 600, 600)
        val px = IntArray(600 * 600) { if (m[it % 600, it / 600]) 0xFF000000.toInt() else -1 }
        val q = WifiQr.parse(id.yanuar.bf01.wifi.WifiTools.qrFromPixels(px, 600, 600))!!
        assertEquals("Galaxy A53", q.ssid); assertEquals("pa;ss", q.pass)
    }
}
