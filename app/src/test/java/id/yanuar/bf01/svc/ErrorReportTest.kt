package id.yanuar.bf01.svc

import id.yanuar.bf01.ble.BfProtocol
import org.junit.Assert.*
import org.junit.Test

class ErrorReportTest {
    private fun info(crash: String = "", rst: String = "", last: String = "", why: String = "") =
        BfProtocol.parseInfo("""{"fw":"0.4.9","batt":77,"state":"idle","rst":"$rst","last_crash":"$last","crash_info":"$crash","restart_why":"$why"}""")

    @Test fun reportHasVersionsAndCrashFields() {
        val r = ErrorReport.build(info(crash = "t=bf_ui pc=0x400d1234 LoadProhibited", rst = "panic", last = "panic", why = "mcp-reboot"), "0.4.12", 51, "15", 35, "0.4.9")
        assertTrue(r.contains("App: 0.4.12 (51)")); assertTrue(r.contains("BF firmware: 0.4.9"))
        assertTrue(r.contains("rst): panic")); assertTrue(r.contains("last_crash): panic"))
        assertTrue(r.contains("pc=0x400d1234")); assertTrue(r.contains("restart_why): mcp-reboot"))
        assertTrue(r.endsWith(ErrorReport.NOT_INCLUDED))
    }
    @Test fun notConnectedStillWorks() {
        val r = ErrorReport.build(null, "0.4.12", 51, "15", 35, null)
        assertTrue(r.contains("not connected")); assertTrue(r.contains("App: 0.4.12 (51)"))
    }
    @Test fun emptyFieldsSayNone() {
        val r = ErrorReport.build(info(), "0.4.12", 51, "15", 35, "0.4.9")
        assertTrue(r.contains("crash_info): none"))
    }
    @Test fun secretsAreRemoved() {
        val dirty = "token=abc123secret key: sk-ABCDEF1234567890 Bearer eyJhbGciOi.payload.sig pass=hunter2 ssid=HomeWifi at 192.168.1.20 deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        val r = ErrorReport.build(info(crash = dirty), "0.4.12", 51, "15", 35, "0.4.9")
        for (s in listOf("abc123secret", "sk-ABCDEF", "eyJhbGciOi", "hunter2", "HomeWifi", "192.168.1.20", "deadbeefdeadbeef")) assertFalse("leaked $s", r.contains(s))
        assertTrue(r.contains("[removed]"))
    }
    @Test fun versionStringsAreNotRemoved() {
        assertEquals("fw 0.4.9 pc=0x400d1234", ErrorReport.redact("fw 0.4.9 pc=0x400d1234"))
    }
}
