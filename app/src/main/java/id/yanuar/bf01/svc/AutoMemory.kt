package id.yanuar.bf01.svc

import id.yanuar.bf01.ai.ChatTemperature
import id.yanuar.bf01.ai.chatProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ingatan otomatis (0.3.3). Satu panggilan LLM singkat per sesi chat (setelah diam IDLE_MS atau saat keluar dari Ask AI)
 * dan per catatan suara NOTE yang sudah ditranskrip. Hasil: maks 3 fakta tahan lama tentang pengguna, disimpan auto=1.
 * Duplikat dibuang dengan kemiripan teks ternormalisasi (provider kita tidak punya embedding murah yang sama untuk Grok & Qwen).
 */
class AutoMemory(private val hub: Hub) {
    private var idle: Job? = null
    /** v0.3.9: fakta yang baru saja disimpan (untuk chip "Saved to memory" + Undo). */
    data class Saved(val ids: List<Long>, val facts: List<String>)
    val lastSaved = MutableStateFlow<Saved?>(null)
    fun undoLast() { lastSaved.value?.ids?.forEach { hub.db.deleteMemory(it) }; lastSaved.value = null; hub.revision.value++ }
    fun dismissLast() { lastSaved.value = null }
    private val lock = Mutex()

    fun onChatActivity() {
        if (!hub.prefs.autoMemory) return
        idle?.cancel(); idle = hub.scope.launch { delay(IDLE_MS); safeChat() }
    }
    /** Dipanggil saat pengguna meninggalkan layar Ask AI. */
    fun flush() { idle?.cancel(); if (hub.prefs.autoMemory) hub.scope.launch { safeChat() } }
    private suspend fun safeChat() { runCatching { fromChat() }.onFailure { if (it is CancellationException) throw it } }

    fun fromNote(text: String) { if (hub.prefs.autoMemory) hub.scope.launch { runCatching { extract(text.take(MAX_INPUT), "note") } } }

    suspend fun fromChat() = lock.withLock {
        val msgs = hub.db.chatAfter(hub.prefs.memChatMark)
        val user = msgs.filter { it.role == "user" }
        if (msgs.isEmpty()) return@withLock
        if (user.sumOf { it.text.length } < MIN_USER_CHARS) { hub.prefs.memChatMark = msgs.last().id; return@withLock }
        // v0.3.9: HANYA pesan pengguna. Balasan AI tidak boleh menjadi sumber fakta (dulu AI mengarang "a wrong name").
        val convo = user.joinToString("\n") { it.text.take(400) }.takeLast(MAX_INPUT)
        runCatching { extract(convo, "chat") }
            .onSuccess { hub.prefs.memChatMark = msgs.last().id }
            .onFailure { hub.log("Auto memory failed: ${it.message}") }
    }

    private suspend fun extract(input: String, source: String) = withContext(Dispatchers.IO) {
        val reply = withContext(ChatTemperature(0.0)) { chatProvider(hub.prefs).chat(PROMPT, emptyList(), input) }
        hub.db.addUsage(chat = 1)
        val existing = hub.db.memories().map { it.text }.toMutableList()
        val ids = mutableListOf<Long>(); val facts = mutableListOf<String>()
        for (f in parseFacts(reply)) {
            if (isSensitive(f) || existing.any { similar(it, f) } || !grounded(f, input)) continue
            ids += hub.db.addMemory(f, source, auto = true); facts += f; existing += f
        }
        if (ids.isNotEmpty()) { hub.db.capMemories(CAP); hub.revision.value++; lastSaved.value = Saved(ids, facts); hub.log("Auto memory: +${ids.size} from $source") }
    }

    companion object {
        const val IDLE_MS = 3 * 60_000L
        const val CAP = 200
        const val MAX_INPUT = 3000
        const val MIN_USER_CHARS = 20
        val PROMPT = "Teks berikut HANYA berisi pesan yang ditulis pengguna sendiri. Ambil maks 3 fakta yang pengguna nyatakan SECARA EKSPLISIT tentang dirinya " +
            "(preferensi, kebiasaan, rencana, proyek). Tulis sedekat mungkin dengan kata-kata pengguna; JANGAN menambah, menyimpulkan, atau menebak. " +
            "JANGAN pernah membuat atau menambahkan nama orang yang tidak tertulis persis di teks. Abaikan pertanyaan, perintah, obrolan sesaat, " +
            "dan data rahasia (kunci API, kata sandi, PIN, OTP, nomor kartu/rekening). Kalau ragu, jangan simpan apa pun. " +
            "Balas HANYA JSON: {\"facts\":[\"...\"]} atau {\"facts\":[]}."

        /**
         * v0.3.9: fakta harus berpijak pada teks pengguna. Setiap kata berhuruf kapital (nama) wajib muncul di teks,
         * dan ≥80% kata inti fakta harus ada di teks. Mencegah AI mengarang nama/fakta.
         */
        fun grounded(fact: String, userText: String): Boolean {
            val text = userText.lowercase()
            val names = Regex("\\b[A-Z][A-Za-z0-9]+").findAll(fact).filter { it.range.first > 0 }.map { it.value }
            if (names.any { it.lowercase() !in text }) return false
            val core = tokens(fact); if (core.isEmpty()) return false
            val src = tokens(userText)
            val hit = core.count { c -> c in src || text.contains(c) }
            return hit >= kotlin.math.ceil(core.size * 0.8)
        }

        fun parseFacts(s: String): List<String> {
            val arr = runCatching {
                val i = s.indexOf('{'); val j = s.lastIndexOf('}')
                if (i >= 0 && j > i) JSONObject(s.substring(i, j + 1)).optJSONArray("facts") else JSONArray(s.substring(s.indexOf('['), s.lastIndexOf(']') + 1))
            }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it).trim().trim('-', ' ', '"') }
                .filter { it.length in 6..200 }.distinct().take(3)
        }

        private val SECRET = listOf(
            Regex("(?i)\\b(sk|xai|pk|rk|ghp|gho|AIza)[-_][A-Za-z0-9_-]{8,}"),
            Regex("(?i)(api[ _-]?key|token|password|passwd|kata ?sandi|sandi|pin|otp|cvv|cvc)\\b"),
            Regex("\\b[A-Za-z0-9_-]{32,}\\b"),
        )
        private val DIGITS = Regex("(?:\\d[ -]?){13,19}")
        fun isSensitive(s: String): Boolean = SECRET.any { it.containsMatchIn(s) } ||
            DIGITS.findAll(s).any { m -> m.value.filter(Char::isDigit).let { it.length in 13..19 && luhn(it) } || m.value.filter(Char::isDigit).length >= 16 }
        private fun luhn(d: String) = d.reversed().mapIndexed { i, c -> val v = c - '0'; if (i % 2 == 1) (v * 2).let { if (it > 9) it - 9 else it } else v }.sum() % 10 == 0

        private val STOP = setOf("yang", "dan", "di", "ke", "dari", "itu", "ini", "untuk", "dengan", "suka", "mas", "yan", "pengguna", "adalah", "akan", "sedang", "ingin", "punya", "a", "the")
        fun tokens(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 1 && it !in STOP }
            .map { it.removePrefix("meng").removePrefix("mem").removePrefix("me").removeSuffix("nya") }.toSet()
        /** Mirip bila himpunan kata inti tumpang tindih ≥ 0.6 (Jaccard) atau satu memuat yang lain. */
        fun similar(a: String, b: String): Boolean {
            val x = tokens(a); val y = tokens(b); if (x.isEmpty() || y.isEmpty()) return a.trim().equals(b.trim(), true)
            val inter = (x intersect y).size.toDouble()
            return inter / (x union y).size >= 0.6 || inter / minOf(x.size, y.size) >= 0.85
        }
    }
}
