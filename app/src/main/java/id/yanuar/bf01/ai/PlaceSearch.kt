package id.yanuar.bf01.ai

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Pertanyaan soal tempat (makan, kafe, bengkel, "dekat sini"…) -> tombol "Cari di Google Maps" supaya hasilnya nyata, bukan karangan AI. */
object PlaceSearch {
    private val words = listOf("makan", "makanan", "kuliner", "resto", "restoran", "rumah makan", "warung", "kafe", "cafe", "kopi", "coffee",
        "bakso", "sate", "soto", "mie", "nasi", "seafood", "tempat", "lokasi", "alamat", "dekat", "terdekat", "sekitar", "di mana", "dimana",
        "bengkel", "apotek", "apotik", "klinik", "rumah sakit", "dokter", "atm", "bank", "spbu", "pom bensin", "hotel", "penginapan",
        "mall", "toko", "minimarket", "indomaret", "alfamart", "pasar", "salon", "barbershop", "masjid", "gereja", "taman", "wisata", "parkir")
    private val re = Regex("\\b(" + words.joinToString("|") { Regex.escape(it) } + ")\\b", RegexOption.IGNORE_CASE)

    fun isPlaceQuery(text: String) = re.containsMatchIn(text)

    /** Kata kunci pencarian dari pertanyaan: buang kata tanya/sapaan supaya Maps mengerti. */
    fun query(text: String): String {
        val stop = setOf("apa", "ada", "yang", "enak", "bagus", "rekomendasi", "rekomendasikan", "dong", "donk", "ya", "sih", "kah", "di", "mana",
            "dimana", "kasih", "tahu", "tau", "tolong", "carikan", "cari", "saya", "aku", "mau", "ingin", "pengen", "pingin", "bf", "mas", "sini", "sekitar",
            "dekat", "terdekat", "deket", "tempat", "nggak", "gak", "gk", "tidak", "atau", "dan", "buat", "untuk", "the", "nih", "lagi", "bisa")
        val w = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotBlank() && it !in stop }
        return w.take(6).joinToString(" ").ifBlank { text.take(60) }
    }

    fun open(ctx: Context, q: String, lat: Double, lon: Double) {
        val enc = Uri.encode(q)
        val geo = if (!lat.isNaN()) Uri.parse("geo:$lat,$lon?q=$enc") else Uri.parse("geo:0,0?q=$enc")
        val i = Intent(Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.recoverCatching {
            val near = if (!lat.isNaN()) "&center=$lat,$lon" else ""
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$enc$near")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Volatile private var cityCache: Triple<Double, Double, String>? = null
    /** Kota perkiraan dari lokasi terakhir (Geocoder di HP), atau null. Jangan panggil di main thread. */
    fun city(ctx: Context, lat: Double, lon: Double): String? {
        if (lat.isNaN()) return null
        cityCache?.let { if (Math.abs(it.first - lat) < 0.05 && Math.abs(it.second - lon) < 0.05) return it.third }
        return runCatching {
            @Suppress("DEPRECATION")
            val a = android.location.Geocoder(ctx, java.util.Locale("id")).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
            listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea).distinct().joinToString(", ").ifBlank { null }
        }.getOrNull()?.also { cityCache = Triple(lat, lon, it) }
    }

    const val RULES = "\nATURAN KEJUJURAN (wajib):\n" +
        "- Jangan pernah mengarang nama tempat, restoran, alamat, nomor telepon, link, harga, jam buka, rating, atau fakta lain.\n" +
        "- Kamu tidak punya data internet atau peta yang langsung (live). Kalau ditanya tempat makan, kafe, bengkel, atau tempat lain di sekitar, " +
        "katakan dengan jujur bahwa kamu tidak bisa memastikan tempat yang benar-benar ada, lalu beri saran jenis makanan/tempat secara umum dan " +
        "ask Alex to tap tombol \"Search in Google Maps\" di aplikasi.\n" +
        "- Hanya sebut nama tempat kalau Alex sendiri yang menyebutnya.\n" +
        "- Kalau tidak tahu atau tidak yakin, bilang tidak tahu. Lebih baik jujur daripada terdengar pintar."
}
