package id.yanuar.bf01.svc

import id.yanuar.bf01.ai.chatProvider
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
        val convo = msgs.joinToString("\n") { (if (it.role == "user") "U: " else "A: ") + it.text.take(400) }.takeLast(MAX_INPUT)
        runCatching { extract(convo, "chat") }
            .onSuccess { hub.prefs.memChatMark = msgs.last().id }
            .onFailure { hub.log("Auto memory failed: ${it.message}") }
    }

    private suspend fun extract(input: String, source: String) = withContext(Dispatchers.IO) {
        val reply = chatProvider(hub.prefs).chat(PROMPT, emptyList(), input)
        hub.db.addUsage(chat = 1)
        val existing = hub.db.memories().map { it.text }.toMutableList()
        var added = 0
        for (f in parseFacts(reply)) {
            if (isSensitive(f) || existing.any { similar(it, f) }) continue
            hub.db.addMemory(f, source, auto = true); existing += f; added++
        }
        if (added > 0) { hub.db.capMemories(CAP); hub.revision.value++; hub.log("Auto memory: +$added from $source") }
    }

    companion object {
        const val IDLE_MS = 3 * 60_000L
        const val CAP = 200
        const val MAX_INPUT = 3000
        const val MIN_USER_CHARS = 20
        val PROMPT = "Dari teks berikut, ambil maks 3 fakta TAHAN LAMA tentang pengguna (Mas Yan): preferensi, kebiasaan, rencana, orang, proyek. " +
            "Abaikan obrolan sesaat, pertanyaan umum, dan data rahasia (kunci API, kata sandi, PIN, OTP, nomor kartu/rekening). " +
            "Tiap fakta satu kalimat pendek bahasa Indonesia. Balas HANYA JSON: {\"facts\":[\"...\"]} atau {\"facts\":[]}."

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
