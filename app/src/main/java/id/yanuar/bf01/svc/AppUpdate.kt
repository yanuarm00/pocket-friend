package id.yanuar.bf01.svc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Update aplikasi Pocket Friend dari rilis GitHub pocket-friend (manifest PocketFriend.json). */
object AppUpdate {
    const val MANIFEST = "https://github.com/yanuarm00/pocket-friend/releases/latest/download/PocketFriend.json"
    const val APK_URL = "https://github.com/yanuarm00/pocket-friend/releases/latest/download/PocketFriend.apk"
    private const val AUTO_EVERY_MS = 4 * 3600_000L

    data class Remote(val versionCode: Int, val versionName: String, val size: Long, val sha256: String, val notes: String, val url: String)

    sealed interface Ui {
        data object Idle : Ui
        data object Checking : Ui
        data object UpToDate : Ui
        data class Available(val r: Remote) : Ui
        data class Downloading(val r: Remote, val done: Long, val total: Long) : Ui
        data class NeedPermission(val r: Remote, val file: File) : Ui
        data class Ready(val r: Remote, val file: File) : Ui
        data class Failed(val msg: String) : Ui
    }

    val state = MutableStateFlow<Ui>(Ui.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun parse(text: String, fallbackUrl: String = APK_URL): Remote? = runCatching {
        val j = org.json.JSONObject(text)
        val sha = j.getString("sha256").lowercase(); require(sha.length == 64)
        Remote(j.getInt("versionCode"), j.getString("versionName"), j.getLong("size"), sha, j.optString("notes"), j.optString("url", fallbackUrl).ifBlank { fallbackUrl })
    }.getOrNull()

    internal fun prefs(ctx: Context) = ctx.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    fun lastCheck(ctx: Context): Long = prefs(ctx).getLong("last_check", 0)
    fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0)

    /** Dipanggil saat app dibuka: cek paling sering tiap 4 jam. */
    fun autoCheck(ctx: Context) { if (System.currentTimeMillis() - lastCheck(ctx) > AUTO_EVERY_MS) check(ctx, quiet = true) }

    /** Reads the release manifest. Throws on network problems or an unreadable manifest. Used by the screen and by the daily worker. */
    fun fetchRemote(): Remote {
        val c = (URL(MANIFEST).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 15000; instanceFollowRedirects = true }
        try {
            if (c.responseCode == 404) throw Exception("No update info on the server yet.")
            return parse(c.inputStream.bufferedReader().readText()) ?: throw Exception("Couldn't read the update info.")
        } finally { c.disconnect() }
    }

    /** True only for a newer build that the user was not already notified about. */
    fun shouldNotify(remoteCode: Int, currentCode: Long, lastNotifiedCode: Int): Boolean = remoteCode > currentCode && remoteCode > lastNotifiedCode

    fun check(ctx: Context, quiet: Boolean = false) {
        val s = state.value; if (s is Ui.Checking || s is Ui.Downloading) return
        val app = ctx.applicationContext
        if (!quiet) state.value = Ui.Checking
        scope.launch {
            try {
                val r = fetchRemote()
                prefs(app).edit().putLong("last_check", System.currentTimeMillis()).apply()
                state.value = if (r.versionCode > currentCode(app)) Ui.Available(r) else if (quiet) Ui.Idle else Ui.UpToDate
            } catch (e: Exception) {
                state.value = if (quiet) Ui.Idle else Ui.Failed(friendly(e))
            }
        }
    }

    fun download(ctx: Context, r: Remote) {
        if (state.value is Ui.Downloading) return
        val app = ctx.applicationContext
        state.value = Ui.Downloading(r, 0, r.size)
        scope.launch {
            try {
                val dir = File(app.cacheDir, "apk").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "PocketFriend-${r.versionName}.apk")
                val c = (URL(r.url).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 30000; instanceFollowRedirects = true }
                val md = MessageDigest.getInstance("SHA-256"); var done = 0L; var lastUi = 0L
                c.inputStream.use { inp -> f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (done - lastUi > 128 * 1024) { lastUi = done; state.value = Ui.Downloading(r, done, r.size) } }
                } }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (sha != r.sha256) { f.delete(); throw Exception("The download is incomplete (security check failed). Try again.") }
                state.value = Ui.Ready(r, f)
                install(app, r, f)
            } catch (e: Exception) { state.value = Ui.Failed(friendly(e)) }
        }
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Buka installer Android; kalau izin "sumber ini" belum ada, tampilkan penjelasan dulu. */
    fun install(ctx: Context, r: Remote, f: File) {
        if (!canInstall(ctx)) { state.value = Ui.NeedPermission(r, f); return }
        state.value = Ui.Ready(r, f)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".apkfiles", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openBrowser(ctx: Context) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    fun dismiss() { state.value = Ui.Idle }

    internal fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Your phone seems to be offline."
        is java.net.SocketTimeoutException -> "The internet is too slow, so the check stopped."
        else -> e.message?.takeIf { it.any { c -> c == ' ' } && !it.contains("Exception") } ?: "Problem reaching the update server."
    }
}
