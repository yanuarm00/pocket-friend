package id.yanuar.bf01.svc

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import id.yanuar.bf01.data.Note
import java.io.File

/** v0.3.9.9.0: share or save a voice note (WAV + transcript). Recordings live in app-private filesDir/recordings. */
object NoteExport {
    const val FOLDER = "PocketFriend"
    const val SAVED_MSG = "Saved to Download/$FOLDER"

    fun audioFile(n: Note): File? = n.localPath.takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.isFile && it.length() > 44 }

    fun text(n: Note): String = buildString {
        if (n.summary.isNotBlank()) { appendLine("Summary: ${n.summary.trim()}"); appendLine() }
        append(n.transcript.trim().ifBlank { "(no transcript yet)" })
    }

    fun baseName(n: Note): String = n.file.substringAfterLast('/').removeSuffix(".wav").replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "note_${n.id}" }

    fun authority(ctx: Context) = ctx.packageName + ".apkfiles"

    /** Audio + text in one share. Some apps (e.g. WhatsApp) keep only the file. */
    fun shareAudioIntent(ctx: Context, n: Note): Intent? {
        val f = audioFile(n) ?: return null
        val uri = FileProvider.getUriForFile(ctx, authority(ctx), f)
        return Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text(n)).putExtra(Intent.EXTRA_SUBJECT, baseName(n))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun shareTextIntent(n: Note): Intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text(n)).putExtra(Intent.EXTRA_SUBJECT, baseName(n))

    /** Android 9 and older need WRITE_EXTERNAL_STORAGE; 10+ use MediaStore without permission. */
    fun needsPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Saves <name>.wav (if the audio is on the phone) and <name>.txt. Returns the names written. */
    fun saveToDownloads(ctx: Context, n: Note): List<String> {
        val base = baseName(n); val out = ArrayList<String>()
        val audio = audioFile(n)
        if (audio != null) { write(ctx, "$base.wav", "audio/wav") { o -> audio.inputStream().use { it.copyTo(o) } }; out += "$base.wav" }
        write(ctx, "$base.txt", "text/plain") { it.write(text(n).toByteArray()) }; out += "$base.txt"
        return out
    }

    private fun write(ctx: Context, name: String, mime: String, body: (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val r = ctx.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw java.io.IOException("Could not create $name")
            try {
                (r.openOutputStream(uri) ?: throw java.io.IOException("Could not open $name")).use(body)
                r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { runCatching { r.delete(uri, null, null) }; throw e }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
            File(dir, name).outputStream().use(body)
        }
    }
}
