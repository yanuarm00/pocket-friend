package id.yanuar.bf01.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Note(val id: Long, val file: String, val created: Long, val transcript: String, val summary: String, val tags: String,
                val type: String = "NOTE", val localPath: String = "", val processed: Boolean = false)
data class Task(val id: Long, val title: String, val due: Long, val done: Boolean, val detail: String = "")
data class ChatMsg(val id: Long, val role: String, val text: String, val time: Long)
data class Memory(val id: Long, val text: String, val source: String = "manual", val auto: Boolean = false, val created: Long = 0)

/**
 * Database lokal (SQLite polos, tanpa Room supaya build sederhana).
 * Kolom `embedding` (BLOB float32) disiapkan untuk pencarian makna di catatan & ingatan.
 * TODO: isi embedding lewat API embedding (belum dipilih; cek dokumen xAI) lalu cari dengan cosine similarity.
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "bf01.db", null, 3) {
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
        upgradeTo2(db); upgradeTo3(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { if (o < 2) upgradeTo2(db); if (o < 3) upgradeTo3(db) }
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
    fun setNoteType(file: String, type: String) { upsertNote(file); writableDatabase.update("notes", ContentValues().apply { put("type", type) }, "file=?", arrayOf(file)) }
    fun setNoteAudio(file: String, path: String) = writableDatabase.update("notes", ContentValues().apply { put("local_path", path) }, "file=?", arrayOf(file))
    fun setNoteProcessed(file: String) = writableDatabase.update("notes", ContentValues().apply { put("processed", 1) }, "file=?", arrayOf(file))
    /** Tugas dari rekaman TASK: satu tugas per file (ditandai note_file). Kembalikan id tugas. */
    fun upsertVoiceTask(file: String, title: String, detail: String = ""): Long {
        val db = writableDatabase
        val id = db.rawQuery("SELECT id FROM tasks WHERE note_file=?", arrayOf(file)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        val cv = ContentValues().apply { put("title", title); put("detail", detail); put("note_file", file) }
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
    fun addTask(title: String, due: Long = 0) = writableDatabase.insert("tasks", null, ContentValues().apply { put("title", title); put("due", due) })
    fun setDone(id: Long, done: Boolean) = writableDatabase.update("tasks", ContentValues().apply { put("done", if (done) 1 else 0) }, "id=?", arrayOf("$id"))
    fun deleteTask(id: Long) = writableDatabase.delete("tasks", "id=?", arrayOf("$id"))

    fun chat(limit: Int = 200): List<ChatMsg> = readableDatabase.rawQuery("SELECT id,role,text,time FROM chat ORDER BY id DESC LIMIT $limit", null).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) } }.reversed() // sudah terurut dari yang terbaru; reversed() = urut naik (hemat sort ulang)
    fun addChat(role: String, text: String) = writableDatabase.insert("chat", null, ContentValues().apply { put("role", role); put("text", text); put("time", System.currentTimeMillis()) })
    /** Pangkas riwayat supaya tabel chat tidak tumbuh selamanya (simpan 500 pesan terakhir). */
    fun trimChat(keep: Int = 500) = writableDatabase.execSQL("DELETE FROM chat WHERE id NOT IN (SELECT id FROM chat ORDER BY id DESC LIMIT ?)", arrayOf(keep))
    fun clearChat() = writableDatabase.delete("chat", null, null)

    /** Riwayat `n` pesan terakhir (TIDAK termasuk pesan terbaru yang baru disimpan), urut naik — langsung dari SQL, hemat memori. */
    fun chatHistory(n: Int): List<ChatMsg> = readableDatabase.rawQuery(
        "SELECT id,role,text,time FROM (SELECT id,role,text,time FROM chat ORDER BY id DESC LIMIT ? OFFSET 1) ORDER BY id", arrayOf("$n")).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) } }

    fun memories(): List<Memory> = readableDatabase.rawQuery("SELECT id,text,source,auto,created FROM memories ORDER BY id", null).use { c ->
        buildList { while (c.moveToNext()) add(Memory(c.getLong(0), c.getString(1), c.getString(2) ?: "manual", c.getInt(3) != 0, c.getLong(4))) } }
    fun addMemory(t: String, source: String = "manual", auto: Boolean = false) = writableDatabase.insert("memories", null, ContentValues().apply {
        put("text", t); put("source", source); put("auto", if (auto) 1 else 0); put("created", System.currentTimeMillis()) })
    /** Batas jumlah ingatan: yang auto paling lama dibuang dulu; ingatan manual tidak pernah dibuang otomatis. */
    fun capMemories(max: Int) {
        val n = readableDatabase.rawQuery("SELECT COUNT(*) FROM memories", null).use { it.moveToFirst(); it.getInt(0) }
        if (n > max) writableDatabase.execSQL("DELETE FROM memories WHERE id IN (SELECT id FROM memories WHERE auto=1 ORDER BY created, id LIMIT ${n - max})")
    }
    /** Pesan chat setelah id tertentu (urut naik), untuk diringkas jadi ingatan. */
    fun chatAfter(id: Long, limit: Int = 40): List<ChatMsg> = readableDatabase.rawQuery(
        "SELECT id,role,text,time FROM (SELECT id,role,text,time FROM chat WHERE id>? ORDER BY id DESC LIMIT $limit) ORDER BY id", arrayOf("$id")).use { c ->
        buildList { while (c.moveToNext()) add(ChatMsg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) } }
    fun deleteMemory(id: Long) = writableDatabase.delete("memories", "id=?", arrayOf("$id"))

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
}
