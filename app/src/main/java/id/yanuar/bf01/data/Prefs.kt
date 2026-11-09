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
    /** true when the encrypted store could not be opened/decrypted (e.g. restored from a backup or keystore lost) and was reset:
     *  saved keys are gone and the user must re-enter them. */
    var secureWasReset = false; private set
    private val secure: SharedPreferences = secureOverride ?: openSecure(ctx)
    private fun openSecure(ctx: Context): SharedPreferences {
        fun make() = EncryptedSharedPreferences.create(ctx, "bf01_secure",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        return try { make() } catch (e: Exception) {
            secureWasReset = true; ctx.deleteSharedPreferences("bf01_secure"); make() }
    }
    private fun secret(k: String): String = try { secure.getString(k, "") ?: "" } catch (e: Exception) { secureWasReset = true; "" }

    var xaiKey: String get() = secret("xai_key"); set(v) = secure.edit().putString("xai_key", v.trim()).apply()
    /** Key Qwen (Alibaba Cloud Model Studio / DashScope), terpisah dari key xAI. Terikat ke region tempat key dibuat. */
    var qwenKey: String get() = secret("qwen_key"); set(v) = secure.edit().putString("qwen_key", v.trim()).apply()
    /** Penyedia AI: "grok" (xAI) atau "qwen" (DashScope), dipakai untuk STT, chat, dan TTS. */
    /** If never chosen and only a Qwen key exists, Qwen is the active provider. */
    var llmProvider: String get() = (plain.getString("llm", null) ?: (if (qwenKey.isNotBlank() && xaiKey.isBlank()) "qwen" else "grok"))
        .let { if (id.yanuar.bf01.ai.AiProviders.isVisible(it)) it else id.yanuar.bf01.ai.AiProviders.visible.first().id }; set(v) = plain.edit().putString("llm", v).apply()
    var qwenBaseUrl: String get() = plain.getString("qwen_base", QWEN_BASE_INTL) ?: QWEN_BASE_INTL; set(v) = plain.edit().putString("qwen_base", id.yanuar.bf01.ai.QwenUrl.normalize(v) ?: QWEN_BASE_INTL).apply()
    /** Last custom (workspace) Base URL, so the Custom chip can bring it back. */
    var qwenCustomUrl: String get() = plain.getString("qwen_custom", "") ?: ""; set(v) = plain.edit().putString("qwen_custom", v).apply()
    var qwenAsrModel: String
        get() { val m = plain.getString("qwen_asr", QWEN_ASR) ?: QWEN_ASR
            return if (m in OLD_DEFAULT_ASR && !plain.getBoolean("qwen_asr_picked", false)) QWEN_ASR else m }
        set(v) = plain.edit().putString("qwen_asr", v.trim()).putBoolean("qwen_asr_picked", true).apply()
    var qwenModel: String
        get() { val m = plain.getString("qwen_model", QWEN_MODEL) ?: QWEN_MODEL
            return if (m in OLD_DEFAULT_MODELS && !plain.getBoolean("qwen_model_picked", false)) QWEN_MODEL else m }
        set(v) = plain.edit().putString("qwen_model", v.trim()).putBoolean("qwen_model_picked", true).apply()
    /** Token WebSocket; dibuat acak sekali, dikirim ke BF lewat Provision. */
    val wsToken: String get() = secure.getString("ws_token", null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.also { secure.edit().putString("ws_token", it).apply() }
    fun rotateToken() = secure.edit().remove("ws_token").apply()
    /** Wi-Fi MAC (Device-Id) of the paired BF; empty until first successful WS after BLE provision. */
    var pairedDeviceId: String get() = plain.getString("paired_did", "") ?: ""; set(v) = plain.edit().putString("paired_did", v.trim().lowercase()).apply()
    /** Epoch ms until which a new Device-Id may be accepted (opened by BLE provision / "New token"). */
    var pairingOpenUntil: Long get() = plain.getLong("pair_until", 0); set(v) = plain.edit().putLong("pair_until", v).apply()
    fun openPairing(ms: Long = 10 * 60_000L) { pairingOpenUntil = System.currentTimeMillis() + ms }
    fun pairingOpen(now: Long = System.currentTimeMillis()) = now < pairingOpenUntil
    fun clearPairing() { pairedDeviceId = ""; pairingOpenUntil = 0 }

    fun wifi(i: Int) = Pair(plain.getString("wifi${i}_ssid", "") ?: "", secure.getString("wifi${i}_pass", "") ?: "")
    fun setWifi(i: Int, ssid: String, pass: String) { plain.edit().putString("wifi${i}_ssid", ssid).apply(); secure.edit().putString("wifi${i}_pass", pass).apply() }

    /** false = pengguna menekan "Putuskan": tidak ada BLE, server, layanan, maupun sambung-ulang otomatis (bertahan sampai "Sambungkan"). */
    var linkEnabled: Boolean get() = plain.getBoolean("link_on", true); set(v) = plain.edit().putBoolean("link_on", v).commit().let {}
    /** Jeda: tetap tersambung, tapi notifikasi Maps/musik tidak diteruskan ke BF. */
    var forwardPaused: Boolean get() = plain.getBoolean("fwd_pause", false); set(v) = plain.edit().putBoolean("fwd_pause", v).apply()

    var deviceAddress: String? get() = plain.getString("addr", null); set(v) = plain.edit().putString("addr", v).apply()
    /** v0.4.0: "system" | "light" | "dark". */
    var themeMode: String get() = plain.getString("theme", "system") ?: "system"; set(v) = plain.edit().putString("theme", v).apply()
    var accent: String get() = plain.getString("accent", "orange") ?: "orange"; set(v) = plain.edit().putString("accent", v).apply()
    var deviceName: String get() = plain.getString("dname", "") ?: ""; set(v) = plain.edit().putString("dname", v).apply()
    /** false until the v5.13 onboarding finishes (or user taps “I already have one”). */
    /** Fresh installs see onboarding; existing paired installs are treated as already onboarded. */
    var onboarded: Boolean
        get() = if (!plain.contains("onboarded") && !plain.getString("addr", null).isNullOrEmpty()) true
                else plain.getBoolean("onboarded", false)
        set(v) { plain.edit().putBoolean("onboarded", v).apply() }
    fun plainRecent(): List<String> = (plain.getString("search_recent", "") ?: "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
    fun setPlainRecent(l: List<String>) = plain.edit().putString("search_recent", l.take(8).joinToString("|")).apply()
    fun taskRepeat(id: Long): String = plain.getString("trep_$id", "never") ?: "never"
    fun setTaskRepeat(id: Long, v: String) = plain.edit().putString("trep_$id", v).apply()
    var keepAudio: Boolean get() = plain.getBoolean("keep_audio", true); set(v) = plain.edit().putBoolean("keep_audio", v).apply()
    var autoDeleteChats: Boolean get() = plain.getBoolean("auto_del_chat", false); set(v) = plain.edit().putBoolean("auto_del_chat", v).apply()
    var nReminders: Boolean get() = plain.getBoolean("n_rem", true); set(v) = plain.edit().putBoolean("n_rem", v).apply()
    var nNeedsYou: Boolean get() = plain.getBoolean("n_need", true); set(v) = plain.edit().putBoolean("n_need", v).apply()
    var nSyncDone: Boolean get() = plain.getBoolean("n_sync", false); set(v) = plain.edit().putBoolean("n_sync", v).apply()
    var nSpeak: Boolean get() = plain.getBoolean("n_speak", true); set(v) = plain.edit().putBoolean("n_speak", v).apply()
    var nQuietNight: Boolean get() = plain.getBoolean("n_quiet", true); set(v) = plain.edit().putBoolean("n_quiet", v).apply()
    var serverHost: String get() = plain.getString("host", "192.168.43.1") ?: "192.168.43.1"; set(v) = plain.edit().putString("host", v).apply()

    var sttProvider: String get() = plain.getString("stt", "xai") ?: "xai"; set(v) = plain.edit().putString("stt", v).apply()
    var ttsProvider: String get() = plain.getString("tts", "xai") ?: "xai"; set(v) = plain.edit().putString("tts", v).apply()
    // "grok-4-fast" was retired by xAI on 15 May 2026 (redirects to grok-4.3); old saved value is migrated.
    var chatModel: String get() = (plain.getString("model", GROK_MODEL) ?: GROK_MODEL).let { if (it.isBlank() || it.startsWith("grok-4-fast")) GROK_MODEL else it }; set(v) = plain.edit().putString("model", v).apply()
    var ttsVoice: String get() = plain.getString("voice", "eve") ?: "eve"; set(v) = plain.edit().putString("voice", v).apply()
    var modeCepat: Boolean get() = plain.getBoolean("fast", false); set(v) = plain.edit().putBoolean("fast", v).apply()
    var sleepSec: Int get() = plain.getInt("sleep", 60); set(v) = plain.edit().putInt("sleep", v.coerceIn(15, 3600)).apply()
    /** v0.3.9.5: kembali ke Home setelah diam (detik) dan mati otomatis saat tidur (menit, 0 = tidak pernah). */
    var homeIdleSec: Int get() = plain.getInt("home_idle", 10); set(v) = plain.edit().putInt("home_idle", v.coerceIn(5, 30)).apply()
    var autoOffMin: Int get() = plain.getInt("auto_off", 0); set(v) = plain.edit().putInt("auto_off", v.coerceIn(0, 240)).apply()
    var quietStart: Int get() = plain.getInt("q_start", 22); set(v) = plain.edit().putInt("q_start", v).apply()
    var quietEnd: Int get() = plain.getInt("q_end", 6); set(v) = plain.edit().putInt("q_end", v).apply()
    /** Plafon bulanan dalam MENIT AUDIO (bukan uang; harga tidak di-hardcode). 0 = tanpa batas. */
    var monthlyCapMin: Int get() = plain.getInt("cap", 300); set(v) = plain.edit().putInt("cap", v).apply()
    /** AI usage screen: edited prices (JSON model -> [in/1M, out/1M, per min] in USD) and the IDR rate per USD. */
    var aiPriceOverrides: String get() = plain.getString("ai_prices", "{}") ?: "{}"; set(v) = plain.edit().putString("ai_prices", v).apply()
    var idrPerUsd: Float get() = plain.getFloat("idr_rate", id.yanuar.bf01.ai.AiPricing.DEFAULT_IDR.toFloat()); set(v) = plain.edit().putFloat("idr_rate", v).apply()
    var musicEnabled: Boolean get() = plain.getBoolean("music", true); set(v) = plain.edit().putBoolean("music", v).apply()
    /** Last battery % seen from BF (-1 = never), shown while disconnected. */
    var lastBatt: Int get() = plain.getInt("last_batt", -1); set(v) = plain.edit().putInt("last_batt", v).apply()
    var navEnabled: Boolean get() = plain.getBoolean("nav", true); set(v) = plain.edit().putBoolean("nav", v).apply()

    /** Nama panggilan AI (default "BF"), dipakai di UI app dan di prompt persona. */
    var aiName: String get() = (plain.getString("ai_name", "BF") ?: "BF").ifBlank { "BF" }; set(v) = plain.edit().putString("ai_name", v.trim().take(16)).apply()
    /** Waktu sinkron rekaman terakhir yang berhasil (epoch ms), 0 = never. */
    /** v0.3.9: BF membuka JAM ON / TAKE ME otomatis saat musik mulai / navigasi Maps mulai. */
    var autoOpen: Boolean get() = plain.getBoolean("auto_open", true); set(v) = plain.edit().putBoolean("auto_open", v).apply()
    var autoMemory: Boolean get() = plain.getBoolean("auto_mem", true); set(v) = plain.edit().putBoolean("auto_mem", v).apply()
    var memChatMark: Long get() = plain.getLong("mem_mark", 0); set(v) = plain.edit().putLong("mem_mark", v).apply()
    var lastRecSync: Long get() = plain.getLong("rec_sync", 0); set(v) = plain.edit().putLong("rec_sync", v).apply()
    var persona: String get() = (plain.getString("persona", DEFAULT_PERSONA) ?: DEFAULT_PERSONA).let { if (it.startsWith("Kamu BF, sahabat dekat")) DEFAULT_PERSONA else it }; set(v) = plain.edit().putString("persona", v).apply()

    var cardName: String get() = plain.getString("c_name", "Alex") ?: "Alex"; set(v) = plain.edit().putString("c_name", v).apply()
    var cardTitle: String get() = plain.getString("c_title", "") ?: ""; set(v) = plain.edit().putString("c_title", v).apply()
    var cardPhone: String get() = plain.getString("c_phone", "") ?: ""; set(v) = plain.edit().putString("c_phone", v).apply()
    var cardMsg: String get() = plain.getString("c_msg", "Hi, I'd like to have a chat") ?: ""; set(v) = plain.edit().putString("c_msg", v).apply()

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
        const val QWEN_MODEL = "qwen-flash-character"
        /** Former defaults: if one is saved but the user never picked it, move to the new default. */
        val OLD_DEFAULT_MODELS = setOf("qwen-plus", "qwen3.8-flash")
        const val QWEN_ASR = "qwen-audio-3.1-asr-flash"
        val OLD_DEFAULT_ASR = setOf("qwen-audio-3.0-asr-flash", "qwen3-asr-flash")
        const val GROK_MODEL = "grok-4.3"
        const val DEFAULT_PERSONA = "You are BF, a lively, cheerful close friend. Call the user \"Alex\". " +
            "Always reply in the same language the user speaks, casual like a close friend. Keep answers short, 1 to 3 sentences, no lists or emoji."
        /** Menu v7: 6 item, 3 halaman × 2 tombol. */
        const val DEFAULT_MENU = "SAY HI,TO-DO,MY NOTES,JAM ON,TAKE ME,TWEAKS"
        const val OLD_MENU = "Talk,Notes,Tasks,Calendar,Music,Maps,Name Card,Settings"
    }
    /**
     * Hi! profile on BF (v0.3.9.8.5: one list for everything, replaces the old Name card + Hi! profiles).
     * type = [ProfileTypes] key; value = number / username / address / link (type dependent);
     * name = name shown on BF; msg = opening message (WhatsApp only); icon = logo chosen for Other / Link ("" = automatic).
     */
    data class Profile(val type: String, val value: String, val name: String = "", val msg: String = "", val icon: String = "")
    internal fun parse(json: String): List<Profile> = runCatching { val a = org.json.JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Profile(o.getString("t"), o.getString("v"), o.optString("n", ""), o.optString("m", ""), o.optString("i", "")) } } }.getOrDefault(emptyList())
    /**
     * Profiles in the new list ("profiles2"). On the first read after the update the old data is moved over without loss:
     * the WhatsApp Name card becomes the first WhatsApp profile (number, opening message, name), the old Hi! profiles follow.
     * The old settings (Name, Job title, number, message, old list) stay stored untouched.
     */
    fun profiles(): List<Profile> {
        plain.getString("profiles2", null)?.let { return parse(it) }
        val l = ProfileTypes.migrate(plain.getString("profiles", "[]") ?: "[]", cardName, cardPhone, cardMsg)
        setProfiles(l)
        return l
    }
    fun setProfiles(l: List<Profile>) = plain.edit().putString("profiles2", org.json.JSONArray(l.map { org.json.JSONObject().put("t", it.type).put("v", it.value).put("n", it.name).put("m", it.msg).put("i", it.icon) }).toString()).apply()
}
