package id.yanuar.bf01.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(val id: Long, val file: String, val created: Long, val transcript: String, val summary: String, val tags: String,
                val type: String = "NOTE", val localPath: String = "", val processed: Boolean = false)
data class Task(val id: Long, val title: String, val due: Long, val done: Boolean, val detail: String = "")
data class ChatMsg(val id: Long, val role: String, val text: String, val time: Long, val src: String = "app")
data class Memory(val id: Long, val text: String, val source: String = "manual", val auto: Boolean = false, val created: Long = 0)

/**
 * Database lokal (SQLite polos, tanpa Room supaya build sederhana).
 * Kolom `embedding` (BLOB float32) disiapkan untuk pencarian makna di catatan & ingatan.
 * TODO: isi embedding lewat API embedding (belum dipilih; cek dokumen xAI) lalu cari dengan cosine similarity.
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "bf01.db", null, 6) {
    init {
        // WAL: baca & tulis tidak saling memblokir (UI tetap mulus saat service menulis dari BLE/HTTP).
        setWriteAheadLoggingEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, file TEXT UNIQUE, created INTEGER, transcript TEXT DEFAULT '', summary TEXT DEFAULT '', tags TEXT DEFAULT '', embedding BLOB)")
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY, title TEXT, due INTEGER DEFAULT 0, done INTEGER DEFAULT 0, cal_event_id INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE chat(id INTEGER PRIMARY KEY, role TEXT, text TEXT, time INTEGER)")
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY, text TEXT, embedding BLOB)")
        db.execSQL("CREATE TABLE usage(month TEXT PRIMARY KEY, stt_sec REAL DEFAULT 0, tts_sec REAL DEFAULT 0, chat_calls INTEGER DEFAULT 0)")
        // Index untuk query yang jalan tiap buka layar / sinkron.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_created ON notes(created DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_id ON chat(id)")
        upgradeTo2(db); upgradeTo3(db); upgradeTo4(db); upgradeTo5(db); upgradeTo6(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { if (o < 2) upgradeTo2(db); if (o < 3) upgradeTo3(db); if (o < 4) upgradeTo4(db); if (o < 5) upgradeTo5(db); if (o < 6) upgradeTo6(db) }
    /** v6 (app 0.4.0): what each AI call was for (old rows stay empty and are read by kind), and one row per feature use for the Usage page. */
    private fun upgradeTo6(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_calls ADD COLUMN purpose TEXT DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS feature_uses(id INTEGER PRIMARY KEY, t INTEGER, feature TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feature_uses_t ON feature_uses(t)")
    }
    /** v5 (app 0.3.9.8.4): one row per AI call that reported usage (tokens or audio seconds). Separate from the older `usage` month totals. */
    private fun upgradeTo5(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ai_calls(id INTEGER PRIMARY KEY, t INTEGER, provider TEXT, model TEXT, kind TEXT, in_tok INTEGER DEFAULT 0, out_tok INTEGER DEFAULT 0, audio_sec REAL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_calls_t ON ai_calls(t)")
    }
    /** v4 (app 0.3.9.5): asal pesan chat — "app" (diketik di HP) atau "bf" (ditanya lewat BF). */
    private fun upgradeTo4(db: SQLiteDatabase) { db.execSQL("ALTER TABLE chat ADD COLUMN src TEXT DEFAULT 'app'") }
    /** v3 (app 0.3.3): ingatan otomatis — asal (manual/chat/note), penanda auto, waktu. */
    private fun upgradeTo3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE memories ADD COLUMN source TEXT DEFAULT 'manual'")
        db.execSQL("ALTER TABLE memories ADD COLUMN auto INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE memories ADD COLUMN created INTEGER DEFAULT 0")
    }
    /** v2 (app 0.3.2): jenis rekaman, file audio di HP, status transkrip; detail tugas (transkrip suara). */
    private fun upgradeTo2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT DEFAULT 'NOTE'")
        db.execSQL("ALTER TABLE notes ADD COLUMN local_path TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN processed INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN detail TEXT DEFAULT ''")
        db.execSQL("ALTER TABLE tasks ADD COLUMN note_file TEXT DEFAULT ''")
    }

    fun notes(): List<Note> = readableDatabase.rawQuery("SELECT id,file,created,transcript,summary,tags,type,local_path,processed FROM notes ORDER BY created DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Note(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4), c.getString(5),
            c.getString(6) ?: "NOTE", c.getString(7) ?: "", c.getInt(8) != 0)) } }
    fun note(file: String): Note? = notes().firstOrNull { it.file == file }
    fun setNoteType(file: String, type: String) { upsertNote(file); if (type in setOf("NOTE", "PENDING") && note(file)?.type in setOf("TASK", "AI", "AIR", "AIREPLY")) return; writableDatabase.update("notes", ContentValues().apply { put("type", type) }, "file=?", arrayOf(file)) }
    fun setNoteAudio(file: String, path: String) = writableDatabase.update("notes", ContentValues().apply { put("local_path", path) }, "file=?", arrayOf(file))
    fun setNoteProcessed(file: String) = writableDatabase.update("notes", ContentValues().apply { put("processed", 1) }, "file=?", arrayOf(file))
    /** Tugas dari rekaman TASK: satu tugas per file (ditandai note_file). Kembalikan id tugas. */
    /** v0.3.9.7: recordings whose to-do was deleted; a later sync must not bring it back. */
    private val tomb = ctx.getSharedPreferences("bf01_tomb", Context.MODE_PRIVATE)
    fun isTombstoned(file: String) = tomb.getBoolean(file, false)
    fun upsertVoiceTask(file: String, title: String, detail: String = "", due: Long? = null): Long {
        if (isTombstoned(file)) return -1
        val db = writableDatabase
        val id = db.rawQuery("SELECT id FROM tasks WHERE note_file=?", arrayOf(file)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        val cv = ContentValues().apply { put("title", title); put("detail", detail); put("note_file", file); due?.let { put("due", it) } }
        return if (id >= 0) { db.update("tasks", cv, "id=?", arrayOf("$id")); id } else db.insert("tasks", null, cv)
    }
    fun upsertNote(file: String, transcript: String? = null, summary: String? = null, tags: String? = null) {
        val db = writableDatabase
        db.insertWithOnConflict("notes", null, ContentValues().apply { put("file", file); put("created", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE)
        val cv = ContentValues(); transcript?.let { cv.put("transcript", it) }; summary?.let { cv.put("summary", it) }; tags?.let { cv.put("tags", it) }
        if (cv.size() > 0) db.update("notes", cv, "file=?", arrayOf(file))
    }

    /** Removes the note for a recording deleted on BF. Returns rows removed. */
    fun deleteNoteByFile(file: String): Int = writableDatabase.delete("notes", "file=?", arrayOf(file))
    fun tasks(): List<Task> = readableDatabase.rawQuery("SELECT id,title,due,done,detail FROM tasks ORDER BY done, due", null).use { c ->
        buildList { while (c.moveToNext()) add(Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getString(4) ?: "")) } }
    fun addTask(title: String, due: Long = 0) = also { addFeatureUse(id.yanuar.bf01.svc.Feature.TODO) }.writableDatabase.insert("tasks", null, ContentValues().apply { put("title", title); put("due", due) })
    fun setDone(id: Long, done: Boolean) = writableDatabase.update("tasks", ContentValues().apply { put("done", if (done) 1 else 0) }, "id=?", arrayOf("$id"))
    fun renameTask(id: Long, title: String) = writableDatabase.update("tasks", ContentValues().apply { put("title", title) }, "id=?", arrayOf(id.toString()))
    fun setTaskDue(id: Long, due: Long) = writableDatabase.update("tasks", ContentValues().apply { put("due", due) }, "id=?", arrayOf(id.toString()))
    fun setTaskDetail(id: Long, detail: String) = writableDatabase.update("tasks", ContentValues().apply { put("detail", detail) }, "id=?", arrayOf(id.toString()))
    fun deleteTask(id: Long): Int {
        readableDatabase.rawQuery("SELECT note_file FROM tasks WHERE id=?", arrayOf("$id")).use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { tomb.edit().putBoolean(it, true).apply() } }
        return writableDatabase.delete("tasks", "id=?", arrayOf("$id"))
    }
    /** AI chats started on BF live in the chat table with src "ai:<thread>". */
    fun aiThread(tid: String): List<ChatMsg> = readableDatabase.rawQuery("SELECT id,role,text,time,src FROM chat WHERE src=? ORDER BY id", arrayOf("ai:$tid")).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4))) } }
    fun aiThreads(): List<Pair<String, ChatMsg>> = readableDatabase.rawQuery(
        "SELECT c.id,c.role,c.text,c.time,c.src FROM chat c JOIN (SELECT MAX(id) m FROM chat WHERE src LIKE 'ai:%' GROUP BY src) x ON c.id=x.m ORDER BY c.id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(4).removePrefix("ai:") to ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4))) } }

    fun chat(limit: Int = 200): List<ChatMsg> = readableDatabase.rawQuery("SELECT id,role,text,time,src FROM chat WHERE IFNULL(src,'app') NOT LIKE 'ai:%' ORDER BY id DESC LIMIT $limit", null).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4) ?: "app")) } }.reversed() // sudah terurut dari yang terbaru; reversed() = urut naik (hemat sort ulang)
    fun addChat(role: String, text: String, src: String = "app") = writableDatabase.insert("chat", null, ContentValues().apply { put("role", role); put("text", text); put("time", System.currentTimeMillis()); put("src", src) })
    /** Pangkas riwayat supaya tabel chat tidak tumbuh selamanya (simpan 500 pesan terakhir). */
    fun trimChat(keep: Int = 500) = writableDatabase.execSQL("DELETE FROM chat WHERE IFNULL(src,'app') NOT LIKE 'ai:%' AND id NOT IN (SELECT id FROM chat ORDER BY id DESC LIMIT ?)", arrayOf(keep))
    fun clearChat() = writableDatabase.delete("chat", null, null)

    /** Riwayat `n` pesan terakhir (dari HP maupun BF), urut naik — langsung dari SQL, hemat memori.
     *  v0.3.9.5: dulu OFFSET 1 membuang balasan terakhir, padahal riwayat diambil SEBELUM pesan baru disimpan. */
    fun chatHistory(n: Int): List<ChatMsg> = readableDatabase.rawQuery(
        "SELECT id,role,text,time FROM (SELECT id,role,text,time FROM chat WHERE IFNULL(src,'app') NOT LIKE 'ai:%' ORDER BY id DESC LIMIT ?) ORDER BY id", arrayOf("$n")).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) } }

    fun memories(): List<Memory> = readableDatabase.rawQuery("SELECT id,text,source,auto,created FROM memories ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Memory(c.getLong(0), c.getString(1), c.getString(2) ?: "manual", c.getInt(3) != 0, c.getLong(4))) } }
    /** v0.3.9.5: naik setiap memori berubah (cache prompt). */
    @Volatile var memRev = 0
    fun addMemory(t: String, source: String = "manual", auto: Boolean = false) = run { memRev++; writableDatabase }.insert("memories", null, ContentValues().apply {
        put("text", t); put("source", source); put("auto", if (auto) 1 else 0); put("created", System.currentTimeMillis()) })
    /** Batas jumlah ingatan: yang auto paling lama dibuang dulu; ingatan manual tidak pernah dibuang otomatis. */
    fun capMemories(max: Int) {
        memRev++
        val n = readableDatabase.rawQuery("SELECT COUNT(*) FROM memories", null).use { it.moveToFirst(); it.getInt(0) }
        if (n > max) writableDatabase.execSQL("DELETE FROM memories WHERE id IN (SELECT id FROM memories WHERE auto=1 ORDER BY created, id LIMIT ${n - max})")
    }
    /** Pesan chat setelah id tertentu (urut naik), untuk diringkas jadi ingatan. */
    fun chatAfter(id: Long, limit: Int = 40): List<ChatMsg> = readableDatabase.rawQuery(
        "SELECT id,role,text,time FROM (SELECT id,role,text,time FROM chat WHERE id>? ORDER BY id DESC LIMIT $limit) ORDER BY id", arrayOf("$id")).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) } }
    fun clearMemories() = run { memRev++; writableDatabase }.delete("memories", null, null)
    fun deleteMemory(id: Long) = run { memRev++; writableDatabase }.delete("memories", "id=?", arrayOf("$id"))

    // ---- Meter pemakaian (menit audio), tanpa harga ----
    private fun month() = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    fun addUsage(sttSec: Double = 0.0, ttsSec: Double = 0.0, chat: Int = 0) {
        val m = month()
        writableDatabase.execSQL("INSERT OR IGNORE INTO usage(month) VALUES(?)", arrayOf(m))
        writableDatabase.execSQL("UPDATE usage SET stt_sec=stt_sec+?, tts_sec=tts_sec+?, chat_calls=chat_calls+? WHERE month=?", arrayOf(sttSec, ttsSec, chat, m))
    }
    /** (menit STT, menit TTS, jumlah panggilan chat) bulan ini. */
    fun usageThisMonth(): Triple<Double, Double, Int> = readableDatabase.rawQuery("SELECT stt_sec,tts_sec,chat_calls FROM usage WHERE month=?", arrayOf(month())).use { c ->
        if (c.moveToFirst()) Triple(c.getDouble(0) / 60, c.getDouble(1) / 60, c.getInt(2)) else Triple(0.0, 0.0, 0) }

    // ---- AI usage per call (tokens / audio seconds), for the AI usage screen ----
    fun addAiCall(c: id.yanuar.bf01.ai.AiCall) {
        writableDatabase.insert("ai_calls", null, ContentValues().apply {
            put("t", c.time); put("provider", c.provider); put("model", c.model); put("kind", c.kind)
            put("in_tok", c.inTokens); put("out_tok", c.outTokens); put("audio_sec", c.audioSec); put("purpose", c.use) })
    }
    /** Every AI call since [sinceMs] with its use (for the Usage page). */
    fun aiCallsSince(sinceMs: Long): List<id.yanuar.bf01.ai.AiCall> = readableDatabase.rawQuery(
        "SELECT t, provider, model, kind, in_tok, out_tok, audio_sec, purpose FROM ai_calls WHERE t>=? ORDER BY t", arrayOf("$sinceMs")).use { c ->
        buildList { while (c.moveToNext()) add(id.yanuar.bf01.ai.AiCall(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
            c.getLong(4), c.getLong(5), c.getDouble(6), id.yanuar.bf01.ai.AiUse.of(c.getString(7), c.getString(3) ?: ""))) } }
    /** v0.4.0: count one use of a feature (see [id.yanuar.bf01.svc.Feature]). Never throws. */
    fun addFeatureUse(feature: String, t: Long = System.currentTimeMillis()) { runCatching {
        writableDatabase.insert("feature_uses", null, ContentValues().apply { put("t", t); put("feature", feature) }) } }
    fun featureUsesSince(sinceMs: Long): List<Pair<Long, String>> = readableDatabase.rawQuery(
        "SELECT t, feature FROM feature_uses WHERE t>=?", arrayOf("$sinceMs")).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
    /** Daily sums (local time) per provider/model/kind since [sinceMs]. */
    fun aiUsageDays(sinceMs: Long): List<id.yanuar.bf01.ai.DayRow> = readableDatabase.rawQuery(
        "SELECT strftime('%Y-%m-%d', t/1000, 'unixepoch', 'localtime') d, provider, model, kind, COUNT(*), SUM(in_tok), SUM(out_tok), SUM(audio_sec) " +
            "FROM ai_calls WHERE t>=? GROUP BY d, provider, model, kind ORDER BY d", arrayOf("$sinceMs")).use { c ->
        buildList { while (c.moveToNext()) add(id.yanuar.bf01.ai.DayRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getLong(5), c.getLong(6), c.getDouble(7))) } }
    fun resetAiUsage() = writableDatabase.delete("ai_calls", null, null)
}
