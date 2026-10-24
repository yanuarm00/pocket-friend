package id.yanuar.bf01.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/** Pengaturan biasa + rahasia (API key, token, sandi Wi-Fi) di EncryptedSharedPreferences (kunci di Android Keystore). */
/** `secureOverride` hanya untuk tes JVM (Robolectric tidak punya Keystore). */
class Prefs(ctx: Context, secureOverride: SharedPreferences? = null) {
    private val plain: SharedPreferences = ctx.getSharedPreferences("bf01", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = secureOverride ?: EncryptedSharedPreferences.create(
        ctx, "bf01_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    var xaiKey: String get() = secure.getString("xai_key", "") ?: ""; set(v) = secure.edit().putString("xai_key", v.trim()).apply()
    /** Key Qwen (Alibaba Cloud Model Studio / DashScope), terpisah dari key xAI. Terikat ke region tempat key dibuat. */
    var qwenKey: String get() = secure.getString("qwen_key", "") ?: ""; set(v) = secure.edit().putString("qwen_key", v.trim()).apply()
    /** Penyedia AI: "grok" (xAI) atau "qwen" (DashScope), dipakai untuk STT, chat, dan TTS. */
    var llmProvider: String get() = plain.getString("llm", "grok") ?: "grok"; set(v) = plain.edit().putString("llm", v).apply()
    var qwenBaseUrl: String get() = plain.getString("qwen_base", QWEN_BASE_INTL) ?: QWEN_BASE_INTL; set(v) = plain.edit().putString("qwen_base", v.trim().trimEnd('/')).apply()
    var qwenModel: String get() = plain.getString("qwen_model", QWEN_MODEL) ?: QWEN_MODEL; set(v) = plain.edit().putString("qwen_model", v.trim()).apply()
    /** Token WebSocket; dibuat acak sekali, dikirim ke BF lewat Provision. */
    val wsToken: String get() = secure.getString("ws_token", null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.also { secure.edit().putString("ws_token", it).apply() }
    fun rotateToken() = secure.edit().remove("ws_token").apply()

    fun wifi(i: Int) = Pair(plain.getString("wifi${i}_ssid", "") ?: "", secure.getString("wifi${i}_pass", "") ?: "")
    fun setWifi(i: Int, ssid: String, pass: String) { plain.edit().putString("wifi${i}_ssid", ssid).apply(); secure.edit().putString("wifi${i}_pass", pass).apply() }

    /** false = pengguna menekan "Putuskan": tidak ada BLE, server, layanan, maupun sambung-ulang otomatis (bertahan sampai "Sambungkan"). */
    var linkEnabled: Boolean get() = plain.getBoolean("link_on", true); set(v) = plain.edit().putBoolean("link_on", v).commit().let {}
    /** Jeda: tetap tersambung, tapi notifikasi Maps/musik tidak diteruskan ke BF. */
    var forwardPaused: Boolean get() = plain.getBoolean("fwd_pause", false); set(v) = plain.edit().putBoolean("fwd_pause", v).apply()

    var deviceAddress: String? get() = plain.getString("addr", null); set(v) = plain.edit().putString("addr", v).apply()
    var deviceName: String get() = plain.getString("dname", "") ?: ""; set(v) = plain.edit().putString("dname", v).apply()
    var serverHost: String get() = plain.getString("host", "192.168.43.1") ?: "192.168.43.1"; set(v) = plain.edit().putString("host", v).apply()

    var sttProvider: String get() = plain.getString("stt", "xai") ?: "xai"; set(v) = plain.edit().putString("stt", v).apply()
    var ttsProvider: String get() = plain.getString("tts", "xai") ?: "xai"; set(v) = plain.edit().putString("tts", v).apply()
    var chatModel: String get() = plain.getString("model", "grok-4-fast") ?: "grok-4-fast"; set(v) = plain.edit().putString("model", v).apply()
    var ttsVoice: String get() = plain.getString("voice", "eve") ?: "eve"; set(v) = plain.edit().putString("voice", v).apply()
    var modeCepat: Boolean get() = plain.getBoolean("fast", false); set(v) = plain.edit().putBoolean("fast", v).apply()
    var sleepSec: Int get() = plain.getInt("sleep", 60); set(v) = plain.edit().putInt("sleep", v.coerceIn(15, 3600)).apply()
    var quietStart: Int get() = plain.getInt("q_start", 22); set(v) = plain.edit().putInt("q_start", v).apply()
    var quietEnd: Int get() = plain.getInt("q_end", 6); set(v) = plain.edit().putInt("q_end", v).apply()
    /** Plafon bulanan dalam MENIT AUDIO (bukan uang; harga tidak di-hardcode). 0 = tanpa batas. */
    var monthlyCapMin: Int get() = plain.getInt("cap", 300); set(v) = plain.edit().putInt("cap", v).apply()
    var navEnabled: Boolean get() = plain.getBoolean("nav", true); set(v) = plain.edit().putBoolean("nav", v).apply()

    /** Nama panggilan AI (default "BF"), dipakai di UI app dan di prompt persona. */
    var aiName: String get() = (plain.getString("ai_name", "BF") ?: "BF").ifBlank { "BF" }; set(v) = plain.edit().putString("ai_name", v.trim().take(16)).apply()
    /** Waktu sinkron rekaman terakhir yang berhasil (epoch ms), 0 = belum pernah. */
    var autoMemory: Boolean get() = plain.getBoolean("auto_mem", true); set(v) = plain.edit().putBoolean("auto_mem", v).apply()
    var memChatMark: Long get() = plain.getLong("mem_mark", 0); set(v) = plain.edit().putLong("mem_mark", v).apply()
    var lastRecSync: Long get() = plain.getLong("rec_sync", 0); set(v) = plain.edit().putLong("rec_sync", v).apply()
    var persona: String get() = plain.getString("persona", DEFAULT_PERSONA) ?: DEFAULT_PERSONA; set(v) = plain.edit().putString("persona", v).apply()

    var cardName: String get() = plain.getString("c_name", "YANUAR") ?: "YANUAR"; set(v) = plain.edit().putString("c_name", v).apply()
    var cardTitle: String get() = plain.getString("c_title", "CREATIVE DIRECTOR") ?: ""; set(v) = plain.edit().putString("c_title", v).apply()
    var cardPhone: String get() = plain.getString("c_phone", "6287853038787") ?: ""; set(v) = plain.edit().putString("c_phone", v).apply()
    var cardMsg: String get() = plain.getString("c_msg", "Halo saya ada keinginan untuk diskusi") ?: ""; set(v) = plain.edit().putString("c_msg", v).apply()

    /** Urutan menu BF, dipisah koma. [BELUM ADA DI FIRMWARE] belum ada karakteristik untuk mengirimnya. */
    var menuOrder: String get() = (plain.getString("menu", DEFAULT_MENU) ?: DEFAULT_MENU).let { if (it == OLD_MENU) DEFAULT_MENU else it }; set(v) = plain.edit().putString("menu", v).apply()

    var lastLat: Double get() = plain.getString("lat", "")?.toDoubleOrNull() ?: Double.NaN; set(v) = plain.edit().putString("lat", v.toString()).apply()
    var lastLon: Double get() = plain.getString("lon", "")?.toDoubleOrNull() ?: Double.NaN; set(v) = plain.edit().putString("lon", v.toString()).apply()
    var lastLocTime: Long get() = plain.getLong("loc_t", 0); set(v) = plain.edit().putLong("loc_t", v).apply()

    companion object {
        // Sumber: https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope (dibaca 23 Okt 2026).
        // Domain lama ini masih berlaku; domain per-workspace https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1
        // disarankan docs dan bisa diisi manual di Pengaturan.
        const val QWEN_BASE_INTL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" // Singapura (internasional)
        const val QWEN_BASE_CN = "https://dashscope.aliyuncs.com/compatible-mode/v1"        // Beijing (China)
        const val QWEN_MODEL = "qwen-plus"
        const val DEFAULT_PERSONA = "Kamu BF, sahabat dekat yang lincah dan ceria. Panggil pengguna \"Mas Yan\". " +
            "Bicara bahasa Indonesia santai seperti teman akrab. Jawab singkat, 1 sampai 3 kalimat, tanpa daftar atau emoji."
        /** Menu v7: 6 item, 3 halaman × 2 tombol. */
        const val DEFAULT_MENU = "SAY HI,TO-DO,MY NOTES,JAM ON,TAKE ME,TWEAKS"
        const val OLD_MENU = "Talk,Notes,Tasks,Calendar,Music,Maps,Name Card,Settings"
    }
}
