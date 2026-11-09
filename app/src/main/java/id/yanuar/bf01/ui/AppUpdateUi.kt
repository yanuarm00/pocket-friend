package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.yanuar.bf01.svc.AppUpdate
import id.yanuar.bf01.svc.AppUpdate.Ui

/** Kartu update aplikasi. [always] = tampilkan juga saat tidak ada apa-apa (halaman Pengaturan). */
@Composable fun AppUpdateCard(always: Boolean = false) {
    val ctx = LocalContext.current; val s by AppUpdate.state.collectAsState()
    // Kembali dari layar izin "sumber ini": lanjutkan install otomatis.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) (AppUpdate.state.value as? Ui.NeedPermission)?.let { if (AppUpdate.canInstall(ctx)) AppUpdate.install(ctx, it.r, it.file) } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val show = always || s is Ui.Available || s is Ui.Downloading || s is Ui.NeedPermission || s is Ui.Ready || s is Ui.Failed
    if (!show) return
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val u = s) {
                is Ui.Available -> {
                    Text("App update available: v${u.r.versionName}", style = MaterialTheme.typography.titleMedium)
                    if (u.r.notes.isNotBlank()) Text(u.r.notes, style = MaterialTheme.typography.bodyMedium)
                    Text("About ${u.r.size / 1_000_000} MB. Your data stays safe.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ AppUpdate.download(ctx, u.r) }) { Text("Update") }
                        TextButton({ AppUpdate.dismiss() }) { Text("Later") }
                    }
                }
                is Ui.Downloading -> {
                    Text("Mengunduh v${u.r.versionName}…", style = MaterialTheme.typography.titleMedium)
                    val f = if (u.total > 0) (u.done.toFloat() / u.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                    Text("${(f * 100).toInt()}% · please keep the app open", style = MaterialTheme.typography.bodySmall)
                }
                is Ui.NeedPermission -> {
                    Text("One more step", style = MaterialTheme.typography.titleMedium)
                    Text("Android needs permission for Pocket Friend to install its own updates. You only do this once. " +
                        "Tap \"Allow\", turn on \"Allow from this source\", then come back here.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ AppUpdate.openInstallPermission(ctx) }) { Text("Allow") }
                        TextButton({ AppUpdate.openBrowser(ctx) }) { Text("Use Chrome instead") }
                    }
                }
                is Ui.Ready -> {
                    Text("Ready to install v${u.r.versionName}", style = MaterialTheme.typography.titleMedium)
                    Text("If the install window doesn't appear, tap Install.", style = MaterialTheme.typography.bodyMedium)
                    Button({ AppUpdate.install(ctx, u.r, u.file) }) { Text("Install") }
                }
                is Ui.Failed -> {
                    Text("Update failed", style = MaterialTheme.typography.titleMedium)
                    Text(u.msg, style = MaterialTheme.typography.bodyMedium)
                    Text("Try again, or download it with Chrome.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ AppUpdate.check(ctx) }) { Text("Try again") }
                        TextButton({ AppUpdate.openBrowser(ctx) }) { Text("Open in Chrome") }
                    }
                }
                else -> {
                    Text("App update", style = MaterialTheme.typography.titleMedium)
                    Text("Current version: v${id.yanuar.bf01.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    val last = AppUpdate.lastCheck(ctx)
                    Text("Last checked: " + if (last == 0L) "never" else java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("id")).format(java.util.Date(last)),
                        style = MaterialTheme.typography.bodySmall)
                    when (u) {
                        is Ui.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
                        is Ui.UpToDate -> Text("The app is up to date.", style = MaterialTheme.typography.bodySmall)
                        else -> {}
                    }
                    Button({ AppUpdate.check(ctx) }, enabled = u !is Ui.Checking) { Text("Check for app update") }
                }
            }
        }
    }
}
