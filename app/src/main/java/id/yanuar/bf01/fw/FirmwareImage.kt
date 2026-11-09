package id.yanuar.bf01.fw

import java.io.InputStream
import java.security.MessageDigest

/**
 * Pemeriksaan file firmware BF-01 untuk update lewat app (OTA app-only, BUKAN file merged).
 *  - Header image ESP: byte 0 = 0xE9.
 *  - esp_app_desc_t di offset 32 (24 B header image + 8 B header segmen): magic 0xABCD5432 (LE),
 *    version di +16 (32 B), project_name di +48 (32 B).
 *  - Ukuran maks = partisi ota_0/ota_1 (0x2F0000) dari partitions/v2/8m.csv.
 */
object FirmwareImage {
    const val MAX_SIZE = 0x2F0000L
    const val MIN_OTA_FW = "0.3.0"   // firmware pertama yang menerima "ota:start" lewat BLE
    const val MIN_BLE_OTA_FW = "0.3.9"  // firmware pertama yang menerima data firmware lewat BLE

    /** Firmware resmi yang dikenal app ini (versi -> SHA-256 file xiaozhi.bin di rilis GitHub). */
    val KNOWN = linkedMapOf(
        "0.3.0" to "522bca1cea102cbf19a5ab7162b8dd03407b4f08cb6c1c90c7d36def59217873",
        "0.3.1" to "1d0bee704b5fef4895a600b58bb9c791b3387c9b59d0e5e39dd0c73efa5a7b3e",
        // test build (branch test/qwen-bot): HTTP file session for recording sync
        "0.3.4-test" to "994f1add918b4b4613b9de3839616373a4bf3cc0b7151af06caad120d8ef7fbd",
        // v0.3.9-test: pertama yang menerima firmware lewat Bluetooth (ota:ble)
        "0.3.9-test" to "434f914fa6aef1f1b8f6870e8cb102a1892b9768bcde63f62ba08cf36d3beb46",
        "0.3.9.1-test" to "881fd391b45ba8d3f2db2f513897e1d1635e7239c729d502a838ba7f8c95280d",
        "0.3.9.2-test" to "9de48f1874e1fa3ed297e48a161619da9eeb1d96a1c9b4abaaa9bbc0491f4a95",
        "0.3.9.3-test" to "bb760fb5416d4802f05c7941decfd36dec2ef83f9b111ed93f8733305c21ce95",
        "0.3.9.4-test" to "47ee867285734af3eda7831f44eccf50ad0991253a233701c34ea60af4424ebc",
        "0.3.9.5-test" to "74712874b09340226daaf61091598d11d7f3e8dcafbc541c6337c0d8cc37581e",
        "0.3.9.6-test" to "b148c1944cd5a81ebba834dfb867e8e5723cc6ac6e04e622390a8a6603b996c6",
        "0.3.9.7-test" to "3f7f8dcfc3e06634db68c2b7f46f7a7aa89a7b6349e6f1550332fc877c6c792c",
        "0.3.9.8-test" to "af8a93e4251b4d58214df6f70607ee18ae8285382670fb429a9f4d325e6b0b12",
        "0.3.9.8.1-test" to "c6e21a09968d2fc2ce1bccebebfc4d590e50ab2bcbb451874c69fc52762aabac",
        "0.3.9.8.2-test" to "bdc1bc891b416146b936d8dab3cebead55ee30169aa0d10fa276284af11182bd",
        "0.3.9.8.6-test" to "e75602c3be17637630f584cefeb29f3f06c26fd587ca945386de68faf82890a8",
        "0.3.9.8.7-test" to "5707c414a8f16f88b3ba1b3b65121f29e8def83faea752c36f92a514031bf511",
    )
    val LATEST: String get() = KNOWN.keys.last()

    data class Parsed(val size: Long, val sha256: String, val version: String?, val project: String?, val error: String?) {
        val ok get() = error == null
        val knownVersion: String? get() = KNOWN.entries.firstOrNull { it.value == sha256 }?.key
    }

    fun parse(input: InputStream): Parsed {
        val md = MessageDigest.getInstance("SHA-256")
        val head = ByteArray(128); var headLen = 0; var size = 0L
        val buf = ByteArray(64 * 1024)
        input.use { s ->
            while (true) {
                val n = s.read(buf); if (n < 0) break
                if (headLen < head.size) { val c = minOf(n, head.size - headLen); System.arraycopy(buf, 0, head, headLen, c); headLen += c }
                md.update(buf, 0, n); size += n
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        fun cstr(off: Int) = if (headLen >= off + 32) String(head, off, 32, Charsets.US_ASCII).substringBefore('\u0000').takeIf { it.isNotBlank() } else null
        val magicOk = headLen >= 36 && (head[32].toInt() and 0xff) == 0x32 && (head[33].toInt() and 0xff) == 0x54 &&
            (head[34].toInt() and 0xff) == 0xCD && (head[35].toInt() and 0xff) == 0xAB
        val err = when {
            size == 0L -> "Empty file"
            (head[0].toInt() and 0xff) != 0xE9 -> "Not an ESP32 firmware file"
            size > MAX_SIZE -> "Too big for the OTA partition (probably the merged file for cable flashing)"
            !magicOk -> "No version info found in the file"
            else -> null
        }
        return Parsed(size, sha, if (magicOk) cstr(48) else null, if (magicOk) cstr(80) else null, err)
    }

    /** Ambil SHA-256 untuk [fileName] dari isi SHA256SUMS ("<hex>  <nama>"), atau satu-satunya hash bila hanya satu baris. */
    fun shaFromSums(text: String, fileName: String = "xiaozhi.bin"): String? {
        val rows = text.lines().mapNotNull { l -> Regex("^([0-9a-fA-F]{64})\\s+\\*?(\\S+)").find(l.trim())?.let { it.groupValues[1].lowercase() to it.groupValues[2] } }
        rows.firstOrNull { it.second.endsWith(fileName) }?.let { return it.first }
        // v0.3.9: build test memakai nama bf01-vX-OTA.bin / -app-0x20000.bin (isinya sama)
        rows.firstOrNull { it.second.endsWith("-OTA.bin") || it.second.endsWith("-app-0x20000.bin") }?.let { return it.first }
        val bare = Regex("\\b[0-9a-fA-F]{64}\\b").findAll(text).map { it.value.lowercase() }.toList()
        return if (rows.isEmpty() && bare.size == 1) bare[0] else null
    }

    /** "fw" dari BLE Info. Firmware lama (v0.1–v0.2.x) melapor versi xiaozhi "2.5.3" -> tampil "v0.2.x (old)". */
    fun displayVersion(fw: String?): String = when {
        fw.isNullOrBlank() -> "unknown"
        fw.startsWith("2.") || fw.startsWith("1.") -> "v0.2.x (old)"
        else -> "v$fw"
    }
    fun supportsAppOta(fw: String?, otaFlag: Boolean): Boolean = otaFlag || (fw != null && !fw.startsWith("2.") && !fw.startsWith("1.") && compare(fw, MIN_OTA_FW) >= 0)

    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }; val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 }); if (d != 0) return d }
        return 0
    }
}
