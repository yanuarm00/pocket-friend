package id.yanuar.bf01.ble

import org.junit.Assert.*
import org.junit.Test

class TaskSnapshotTest {
    private fun body(b: ByteArray) = String(b, Charsets.UTF_8).substring(1)
    @Test fun snapshotHasVersionAndIds() {
        val s = body(BfProtocol.taskSnapshot(42, listOf(7L to "Beli susu", 9L to "Kirim invoice")))
        assertEquals("#v42\n#t7|Beli susu\n#t9|Kirim invoice", s)
    }
    @Test fun emptySnapshotClearsList() {
        assertEquals("#v5", body(BfProtocol.taskSnapshot(5, emptyList())))
    }
    @Test fun showNowPrefix() { assertTrue(body(BfProtocol.taskSnapshot(1, listOf(1L to "A"), true)).startsWith("!#v1")) }
    @Test fun autoOpenToggle() { assertEquals("1", body(BfProtocol.autoOpen(true))); assertEquals("0", body(BfProtocol.autoOpen(false))) }
    @Test fun infoTasksV() { assertEquals(1, BfProtocol.parseInfo("""{"fw":"0.3.9-test","tasks_v":1}""").tasksV); assertEquals(0, BfProtocol.parseInfo("{}").tasksV) }
}
