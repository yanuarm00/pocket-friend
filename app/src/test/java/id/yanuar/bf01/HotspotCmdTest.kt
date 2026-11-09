package id.yanuar.bf01

import id.yanuar.bf01.ble.BfProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotCmdTest {
    @Test fun hotspotCommandIsJson() {
        assertEquals("wifi:hs:{\"s\":\"AndroidShare_1234\",\"p\":\"abc12345\"}", String(BfProtocol.filesHotspot("AndroidShare_1234", "abc12345")))
    }
    @Test fun hotspotCommandEscapes() {
        assertEquals("wifi:hs:{\"s\":\"a\\\"b\\\\c\",\"p\":\"x\\u000ay\"}", String(BfProtocol.filesHotspot("a\"b\\c", "x\ny")))
    }
    @Test fun wifiReadyOnHotspotSubnet() {
        val e = BfProtocol.parseEvent("wifi:on:192.168.49.23:0123456789abcdef01234567")
        assertEquals(BfProtocol.Event.WifiReady("192.168.49.23", "0123456789abcdef01234567"), e)
    }
}
