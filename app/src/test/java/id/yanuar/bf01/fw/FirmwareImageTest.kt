package id.yanuar.bf01.fw

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class FirmwareImageTest {
    private fun fakeImage(ver: String = "0.3.0", magic: Int = 0xE9): ByteArray {
        val b = ByteArray(4096)
        b[0] = magic.toByte()
        // esp_app_desc_t di offset 32: magic 0xABCD5432 (LE), versi di +16, project di +48
        b[32] = 0x32; b[33] = 0x54; b[34] = 0xCD.toByte(); b[35] = 0xAB.toByte()
        ver.toByteArray().copyInto(b, 48); "xiaozhi".toByteArray().copyInto(b, 80)
        return b
    }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun parsesVersionAndSha() {
        val b = fakeImage()
        val p = FirmwareImage.parse(b.inputStream())
        assertTrue(p.error, p.ok)
        assertEquals("0.3.0", p.version); assertEquals("xiaozhi", p.project)
        assertEquals(sha(b), p.sha256); assertEquals(4096L, p.size)
        assertNull(p.knownVersion)
    }
    @Test fun rejectsNonFirmware() {
        assertFalse(FirmwareImage.parse(fakeImage(magic = 0x00).inputStream()).ok)
        assertFalse(FirmwareImage.parse("hello".toByteArray().inputStream()).ok)
    }
    @Test fun knownShaIsReal() {
        val s = FirmwareImage.KNOWN.getValue("0.3.0")
        assertTrue(Regex("^[0-9a-f]{64}$").matches(s))
    }
    @Test fun sums() {
        val s = "fc58fb61175904b82d84bbeb25b1552a84077ebac89b4585300f9f80eb2d2ebc  bf01-firmware-merged-v0.3.0.bin\n" +
                "522bca1cea102cbf19a5ab7162b8dd03407b4f08cb6c1c90c7d36def59217873  xiaozhi.bin\n"
        assertEquals("522bca1cea102cbf19a5ab7162b8dd03407b4f08cb6c1c90c7d36def59217873", FirmwareImage.shaFromSums(s))
        assertNull(FirmwareImage.shaFromSums("nothing here"))
    }
    @Test fun versions() {
        assertEquals("v0.2.x (old)", FirmwareImage.displayVersion("2.5.3"))
        assertEquals("v0.3.0", FirmwareImage.displayVersion("0.3.0"))
        assertFalse(FirmwareImage.supportsAppOta("2.5.3", false))
        assertTrue(FirmwareImage.supportsAppOta("0.3.0", false))
        assertTrue(FirmwareImage.supportsAppOta("2.5.3", true))
        assertTrue(FirmwareImage.compare("0.10.0", "0.3.0") > 0)
    }
}
