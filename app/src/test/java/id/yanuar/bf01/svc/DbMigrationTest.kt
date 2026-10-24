package id.yanuar.bf01.svc

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Db
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DbMigrationTest {
    @Test fun v1To2KeepsDataAndAddsColumns() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("bf01.db")
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("bf01.db").apply { parentFile?.mkdirs() }, null).use { d ->
            d.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, file TEXT UNIQUE, created INTEGER, transcript TEXT DEFAULT '', summary TEXT DEFAULT '', tags TEXT DEFAULT '', embedding BLOB)")
            d.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY, title TEXT, due INTEGER DEFAULT 0, done INTEGER DEFAULT 0, cal_event_id INTEGER DEFAULT 0)")
            d.execSQL("CREATE TABLE chat(id INTEGER PRIMARY KEY, role TEXT, text TEXT, time INTEGER)")
            d.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY, text TEXT, embedding BLOB)")
            d.execSQL("CREATE TABLE usage(month TEXT PRIMARY KEY, stt_sec REAL DEFAULT 0, tts_sec REAL DEFAULT 0, chat_calls INTEGER DEFAULT 0)")
            d.execSQL("INSERT INTO notes(file,created,transcript) VALUES('VN_old.wav',1,'lama')")
            d.execSQL("INSERT INTO tasks(title) VALUES('lama')")
            d.version = 1
        }
        val db = Db(ctx)
        val n = db.notes().single()
        assertEquals("lama", n.transcript); assertEquals("NOTE", n.type); assertEquals("", n.localPath); assertFalse(n.processed)
        val id = db.upsertVoiceTask("VN_t.wav", "Voice task", "")
        assertEquals(id, db.upsertVoiceTask("VN_t.wav", "Beli baterai", "beli baterai lipo"))
        assertEquals(2, db.tasks().size)
        assertEquals("beli baterai lipo", db.tasks().first { it.id == id }.detail)
        db.close()
    }
}
