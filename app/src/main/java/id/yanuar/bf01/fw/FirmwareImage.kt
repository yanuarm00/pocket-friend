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

    /** Firmware resmi yang dikenal app ini (versi -> SHA-256 file xiaozhi.bin di rilis GitHub). */
    val KNOWN = linkedMapOf(
        "0.3.0" to "522bca1cea102cbf19a5ab7162b8dd03407b4f08cb6c1c90c7d36def59217873",
        "0.3.1" to "1d0bee704b5fef4895a600b58bb9c791b3387c9b59d0e5e39dd0c73efa5a7b3e",
        // test build (branch test/qwen-bot): HTTP file session for recording sync
        "0.3.4-test" to "994f1add918b4b4613b9de3839616373a4bf3cc0b7151af06caad120d8ef7fbd",
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
