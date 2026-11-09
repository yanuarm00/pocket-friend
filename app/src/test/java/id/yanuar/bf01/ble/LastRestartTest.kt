package id.yanuar.bf01.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class LastRestartTest {
    private val z = ZoneId.of("Asia/Jakarta")
    @Test fun crashStackOverflow() {
        val i = BfProtocol.parseInfo("""{"fw":"0.3.9.1-test","rst":"panic","last_crash":"panic@1792858200",
            "crash_info":"t=bf_vcap c=0 pc=0x4037abcd ***ERROR*** A stack overflow in task bf_vcap has been detected."}""")
        assertEquals("Last restart: crash in bf_vcap (stack overflow) at 23:10, 24 Oct [pc 0x4037abcd]", BfProtocol.lastRestartText(i, z))
    }
    @Test fun normalWithOldCrash() {
        val i = BfProtocol.parseInfo("""{"rst":"poweron","last_crash":"panic@100"}""")
        assertEquals("Last restart: normal power-on. Last crash: crash", BfProtocol.lastRestartText(i, z))
    }
    @Test fun oldFirmware() { assertNull(BfProtocol.lastRestartText(BfProtocol.parseInfo("""{"fw":"0.3.8"}"""), z)) }
}
