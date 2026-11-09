package id.yanuar.bf01.svc

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class ManifestTest {
    @Test fun parses() {
        val r = parseManifest("""{"version":"0.3.9-test","size":2742016,"sha256":"${"A".repeat(64)}"}""")!!
        assertEquals("0.3.9-test", r.version); assertEquals(2742016L, r.size); assertEquals("a".repeat(64), r.sha256)
        assertTrue(r.url.endsWith("/releases/latest/download/BF01-firmware.bin"))
    }
    @Test fun rejectsBad() { assertNull(parseManifest("""{"version":"x","size":1,"sha256":"abc"}""")); assertNull(parseManifest("nope")) }
}
