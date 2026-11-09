package id.yanuar.bf01.fw

import id.yanuar.bf01.ble.BfProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class BleOtaSenderTest {
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun packetFraming() {
        val src = ByteArray(10) { it.toByte() }
        val p = BfProtocol.otaPacket(0x01020304, src, 2, 3)
        assertArrayEquals(byteArrayOf(4, 3, 2, 1, 2, 3, 4), p)
    }

    @Test fun chunkForMtu() {
        assertEquals(240, BleOtaSender.chunkFor(247)); assertEquals(510, BleOtaSender.chunkFor(517)); assertEquals(512, BleOtaSender.chunkFor(600)); assertEquals(16, BleOtaSender.chunkFor(23))
    }

    @Test fun eventParsing() {
        val r = BfProtocol.parseEvent("ota:ready:8192") as BfProtocol.Event.Ota
        assertEquals("ready", r.stage); assertEquals(8192L, r.offset)
        assertEquals(4096L, (BfProtocol.parseEvent("ota:ack:4096") as BfProtocol.Event.Ota).offset)
        assertEquals("need", (BfProtocol.parseEvent("ota:need:0") as BfProtocol.Event.Ota).stage)
        assertEquals("timeout", (BfProtocol.parseEvent("ota:fail:timeout") as BfProtocol.Event.Ota).reason)
    }

    @Test fun commands() {
        assertTrue(String(BfProtocol.otaBle("AB".repeat(32), 1234)).endsWith("ota:ble:" + "ab".repeat(32) + ":1234"))
    }

    /** Simulasi BF: terima berurutan, ack tiap 4 KB, need kalau offset salah; radio membuang ~7% paket. */
    @Test fun lossyTransferMatchesSha() = runBlocking {
        val img = ByteArray(200_003) { (it * 31 + 7).toByte() }
        val out = java.io.ByteArrayOutputStream(); var rx = 0L; var lastAck = 0L
        val rnd = java.util.Random(1); var s: BleOtaSender? = null
        s = BleOtaSender(img, 240, stallMs = 50) { p ->
            if (rnd.nextInt(100) < 7) return@BleOtaSender true   // hilang di udara
            val off = (p[0].toLong() and 0xff) or ((p[1].toLong() and 0xff) shl 8) or ((p[2].toLong() and 0xff) shl 16) or ((p[3].toLong() and 0xff) shl 24)
            if (off != rx) { s!!.onNeed(rx); return@BleOtaSender true }
            out.write(p, 4, p.size - 4); rx += p.size - 4
            if (rx - lastAck >= 4096 || rx == img.size.toLong()) { lastAck = rx; s!!.onAck(rx) }
            true
        }
        s.onReady(0)
        val pcts = mutableListOf<Int>()
        assertTrue(s.run { pcts += it })
        assertEquals(sha(img), sha(out.toByteArray())); assertEquals(100, pcts.last())
    }

    @Test fun resumeFromOffset() = runBlocking {
        val img = ByteArray(10_000) { it.toByte() }; val seen = mutableListOf<Long>()
        var s: BleOtaSender? = null
        s = BleOtaSender(img, 500) { p -> val off = (p[0].toLong() and 0xff) or ((p[1].toLong() and 0xff) shl 8); seen += off
            s!!.onAck(off + p.size - 4); true }
        s.onReady(8000); assertTrue(s.run()); assertEquals(8000L, seen.first())
    }

    @Test fun abortStops() = runBlocking {
        var s: BleOtaSender? = null
        s = BleOtaSender(ByteArray(5000), 500) { s!!.abort(); true }
        assertFalse(s.run())
    }

    @Test fun sumsAcceptsOtaName() {
        val h = "a".repeat(64)
        assertEquals(h, FirmwareImage.shaFromSums("$h  bf01-v0.3.9-test-OTA.bin\n"))
        assertEquals("0.3.9.8.7-test", FirmwareImage.LATEST)
    }

    @Test fun infoOtaLevel() {
        assertEquals(2, BfProtocol.parseInfo("""{"fw":"0.3.9-test","ota":2}""")?.otaLevel)
        assertEquals(1, BfProtocol.parseInfo("""{"fw":"0.3.8","ota":1}""")?.otaLevel)
        assertEquals(true, BfProtocol.parseInfo("""{"fw":"0.3.9-test","ota":2}""")?.ota)
    }
}
