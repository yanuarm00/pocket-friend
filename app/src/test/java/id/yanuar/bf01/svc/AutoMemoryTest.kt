package id.yanuar.bf01.svc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Db
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoMemoryTest {
    @Test fun parsesFactsJson() {
        val f = AutoMemory.parseFacts("```json\n{\"facts\":[\"Mas Yan suka kopi susu\",\"Rencana ke Bandung bulan depan\",\"x\",\"A\",\"Punya kucing bernama Oyen\",\"Fakta keempat\"]}\n```")
        assertEquals(listOf("Mas Yan suka kopi susu", "Rencana ke Bandung bulan depan", "Punya kucing bernama Oyen"), f)
        assertTrue(AutoMemory.parseFacts("{\"facts\":[]}").isEmpty())
        assertTrue(AutoMemory.parseFacts("maaf tidak ada").isEmpty())
        assertEquals(1, AutoMemory.parseFacts("[\"Suka nasi goreng pedas\"]").size)
    }
    @Test fun filtersSecrets() {
        assertTrue(AutoMemory.isSensitive("API key Qwen-nya sk-abc123def456ghi789"))
        assertTrue(AutoMemory.isSensitive("Kata sandi wifi rumah adalah melati99"))
        assertTrue(AutoMemory.isSensitive("Password email: hunter2"))
        assertTrue(AutoMemory.isSensitive("Nomor kartu 4111 1111 1111 1111"))
        assertTrue(AutoMemory.isSensitive("PIN ATM 123456"))
        assertFalse(AutoMemory.isSensitive("Mas Yan suka kopi susu gula aren"))
        assertFalse(AutoMemory.isSensitive("Rencana cetak 50 unit casing tahun 2027"))
        assertFalse(AutoMemory.isSensitive("Pindah kantor ke Depok bulan Desember"))
    }
    @Test fun nearDuplicates() {
        assertTrue(AutoMemory.similar("Mas Yan suka kopi susu gula aren", "Suka kopi susu gula aren"))
        assertTrue(AutoMemory.similar("Sedang membuat perangkat BF-01", "Mas Yan sedang membuat perangkat BF-01."))
        assertFalse(AutoMemory.similar("Mas Yan suka kopi susu gula aren", "Mas Yan suka teh tarik"))
        assertFalse(AutoMemory.similar("Lari pagi tiap Sabtu", "Rencana cetak 50 unit casing"))
    }
    @Test fun capDropsOldestAutoOnly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); ctx.deleteDatabase("bf01.db")
        val db = Db(ctx)
        db.addMemory("manual satu")
        repeat(5) { db.addMemory("auto $it", "chat", auto = true) }
        db.addMemory("manual dua")
        db.capMemories(4)
        assertEquals(listOf("manual satu", "auto 3", "auto 4", "manual dua"), db.memories().map { it.text })
        assertTrue(db.memories()[1].auto); assertEquals("chat", db.memories()[1].source); assertTrue(db.memories()[1].created > 0)
        db.addChat("user", "a"); val id = db.chat(1).first().id; db.addChat("assistant", "b"); db.addChat("user", "c")
        assertEquals(listOf("b", "c"), db.chatAfter(id).map { it.text })
        db.close()
    }
    @Test fun migrationV2toV3KeepsMemories() {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); ctx.deleteDatabase("bf01.db")
        val f = ctx.getDatabasePath("bf01.db"); f.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            d.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, file TEXT UNIQUE, created INTEGER, transcript TEXT DEFAULT '', summary TEXT DEFAULT '', tags TEXT DEFAULT '', embedding BLOB, type TEXT DEFAULT 'NOTE', local_path TEXT DEFAULT '', processed INTEGER DEFAULT 0)")
            d.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY, title TEXT, due INTEGER DEFAULT 0, done INTEGER DEFAULT 0, cal_event_id INTEGER DEFAULT 0, detail TEXT DEFAULT '', note_file TEXT DEFAULT '')")
            d.execSQL("CREATE TABLE chat(id INTEGER PRIMARY KEY, role TEXT, text TEXT, time INTEGER)")
            d.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY, text TEXT, embedding BLOB)")
            d.execSQL("CREATE TABLE usage(month TEXT PRIMARY KEY, stt_sec REAL DEFAULT 0, tts_sec REAL DEFAULT 0, chat_calls INTEGER DEFAULT 0)")
            d.execSQL("INSERT INTO memories(text) VALUES ('suka kopi'),('punya kucing')")
            d.version = 2
        }
        val db = Db(ctx)
        val m = db.memories()
        assertEquals(listOf("suka kopi", "punya kucing"), m.map { it.text })
        assertTrue(m.all { !it.auto && it.source == "manual" })
        db.addMemory("auto baru", "chat", auto = true); assertTrue(db.memories().last().auto)
        db.close()
    }
}
