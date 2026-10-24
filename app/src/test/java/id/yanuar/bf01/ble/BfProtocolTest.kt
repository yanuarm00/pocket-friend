package id.yanuar.bf01.ble

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BfProtocolTest {
    @Test fun uuidsMatchFirmware() {
        assertEquals("b0f10000-5a1e-4b1d-9e3f-0bf01bf01bf0", BfProtocol.SERVICE.toString())
        assertEquals("b0f10009-5a1e-4b1d-9e3f-0bf01bf01bf0", BfProtocol.FILES.toString())
    }
    @Test fun noteDeletedEvent() {
        assertEquals(BfProtocol.Event.NoteDeleted("REC_0007.wav"), BfProtocol.parseEvent("note:deleted:REC_0007.wav"))
    }
    @Test fun passkeyPadsTo6() {
        assertEquals("004827", BfProtocol.passkeyString("4827")); assertEquals(4827, BfProtocol.passkeyFromCode("4827"))
        assertEquals("000042", BfProtocol.passkeyString("0042"))
    }
    @Test fun timeIsLittleEndian10Bytes() {
        val b = BfProtocol.encodeTime(0x0102030405060708L, 420)
        assertEquals(10, b.size)
        assertArrayEquals(byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1, (420 and 0xff).toByte(), (420 shr 8).toByte()), b)
    }
    @Test fun negativeOffset() {
        val b = BfProtocol.encodeTime(0, -300)
        assertEquals((-300).toShort(), ((b[9].toInt() shl 8) or (b[8].toInt() and 0xff)).toShort())
    }
    @Test fun notifyTypeByteAndPayload() {
        val b = BfProtocol.music("Lagu|A", "Artis", true)
        assertEquals('M'.code.toByte(), b[0]); assertEquals("Lagu/A|Artis|1", String(b, 1, b.size - 1))
        assertEquals("N-|250M|Jl. A|Lurus|2 km|10.30", String(BfProtocol.navigation("-", "250M", "Jl. A", "Lurus", "2 km", "10.30")))
        assertEquals("T!Beli kopi\nTelpon", String(BfProtocol.tasks(listOf("Beli kopi", "Telpon"), true)))
        assertEquals("A1700000000|Meeting|11.00-12.00", String(BfProtocol.alarms(listOf(BfProtocol.Alarm(1700000000, "Meeting", "11.00-12.00")))))
        assertEquals("S15", String(BfProtocol.sleepTimeout(3))); assertEquals("S3600", String(BfProtocol.sleepTimeout(99999)))
    }
    @Test fun provisionMax2Profiles() {
        val j = JSONObject(String(BfProtocol.provision(List(3) { BfProtocol.WifiProfile("s$it", "p") }, "tok", "ws://x/", "http://x/")))
        assertEquals(2, j.getJSONArray("profiles").length()); assertEquals("tok", j.getString("token"))
        assertEquals("ws://x/", j.getString("ws_url")); assertEquals("http://x/", j.getString("ota_url"))
    }
    @Test fun parseEventsAndFiles() {
        assertEquals(BfProtocol.Event.Media("next"), BfProtocol.parseEvent("media:next"))
        assertEquals(BfProtocol.Event.ReminderFired(123), BfProtocol.parseEvent("reminder:fired:123"))
        assertEquals(BfProtocol.Event.NoteNew("n1.wav"), BfProtocol.parseEvent("note:new:n1.wav"))
        assertEquals(BfProtocol.Event.TaskNew("t1.wav"), BfProtocol.parseEvent("task:new:t1.wav"))
        assertSame(BfProtocol.Event.WifiFail, BfProtocol.parseEvent("wifi:fail"))
        val f = BfProtocol.parseFiles("a.wav|1000|1\nb.wav|20|0\n")
        assertEquals(2, f.size); assertTrue(f[0].synced); assertFalse(f[1].synced)
        val i = BfProtocol.parseInfo("""{"fw":"0.1","batt":80,"sd":true,"wifi_on":false,"warm":1}""")
        assertEquals(80, i.batt); assertTrue(i.warm)
    }
    @Test fun nameCardJson() {
        val j = JSONObject(String(BfProtocol.nameCard("https://wa.me/1", "YANUAR", "CD")))
        assertEquals("https://wa.me/1", j.getString("qr")); assertEquals("YANUAR", j.getString("name"))
    }

    @Test fun otaEvents() {
        assertEquals(BfProtocol.Event.Ota("accept"), BfProtocol.parseEvent("ota:accept"))
        assertEquals(BfProtocol.Event.Ota("progress", 45), BfProtocol.parseEvent("ota:progress:45"))
        assertEquals(BfProtocol.Event.Ota("fail", reason = "sha"), BfProtocol.parseEvent("ota:fail:sha"))
        assertEquals(BfProtocol.Event.Ota("refused", reason = "battery:22"), BfProtocol.parseEvent("ota:refused:battery:22"))
        assertEquals(BfProtocol.Event.Ota("ok"), BfProtocol.parseEvent("ota:ok"))
    }
    @Test fun infoOtaFlags() {
        val i = BfProtocol.parseInfo("""{"fw":"0.3.0","batt":50,"ota":1,"usb":true}""")
        assertTrue(i.ota); assertTrue(i.usb)
        assertFalse(BfProtocol.parseInfo("""{"fw":"2.5.3"}""").ota)
    }
    @Test fun wifiReadyEvent() {
        assertEquals(BfProtocol.Event.WifiReady("192.168.43.57", "a1b2c3"), BfProtocol.parseEvent("wifi:on:192.168.43.57:a1b2c3"))
        assertEquals(BfProtocol.Event.WifiOn, BfProtocol.parseEvent("wifi:on"))
        assertEquals(BfProtocol.Event.WifiOn, BfProtocol.parseEvent("wifi:on:"))
    }
    @Test fun notesJson() {
        val l = BfProtocol.parseNotesJson("""{"notes":[{"name":"VN_20261024_101500.wav","size":96044,"duration":3,"type":"TASK","synced":false},
            {"name":"../x.wav","size":1},{"name":"VN_2.wav","size":50,"type":"PENDING","synced":true}],"recording":false}""")
        assertEquals(2, l.size)
        assertEquals(BfProtocol.RemoteNote("VN_20261024_101500.wav", 96044, 3, "TASK", false), l[0])
        assertTrue(l[1].synced)
        assertTrue(BfProtocol.parseNotesJson("").isEmpty())
    }
}
