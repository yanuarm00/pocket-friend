package id.yanuar.bf01.svc

import org.junit.Assert.*
import org.junit.Test

class AutoMemoryGroundTest {
    private val user = "Saya suka kopi tubruk tanpa gula. Saya sedang membuat perangkat bernama BF01."
    @Test fun acceptsNearVerbatim() {
        assertTrue(AutoMemory.grounded("Suka kopi tubruk tanpa gula", user))
        assertTrue(AutoMemory.grounded("Sedang membuat perangkat bernama BF01", user))
    }
    @Test fun rejectsInventedName() { assertFalse(AutoMemory.grounded("Dipanggil Alexto oleh teman", user)) }
    @Test fun rejectsWrongName() { assertFalse(AutoMemory.grounded("Sedang membuat perangkat bernama BF02", user)) }
    @Test fun rejectsInventedFact() { assertFalse(AutoMemory.grounded("Tinggal di Surabaya bersama istri", user)) }
    @Test fun promptForbidsNames() { assertTrue(AutoMemory.PROMPT.contains("Kalau ragu, jangan simpan apa pun")) }
}
