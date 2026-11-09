package id.yanuar.bf01.svc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.yanuar.bf01.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Once a day (about), Android runs this when the phone has internet and the battery is not low.
 * It reads the same PocketFriend.json as the Update card. If a newer version exists and the user was not told yet,
 * it posts one "New version available" notification. Tapping it opens the app on the existing Update card.
 * It never downloads or installs anything by itself.
 */
class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        return try {
            val r = withContext(Dispatchers.IO) { AppUpdate.fetchRemote() }
            val p = AppUpdate.prefs(app)
            if (AppUpdate.shouldNotify(r.versionCode, AppUpdate.currentCode(app), p.getInt(KEY_NOTIFIED, 0))) {
                // Android 13+: without the notification permission nothing is shown (and we do not mark it as notified,
                // so it shows later if the user allows notifications). The Update card in the app still appears on open.
                if (canNotify(app)) { notify(app, r); p.edit().putInt(KEY_NOTIFIED, r.versionCode).apply() }
            }
            Result.success()
        } catch (e: Exception) { if (runAttemptCount < 2) Result.retry() else Result.success() }  // offline: try again at the next run
    }

    companion object {
        const val WORK = "app_update_check"
        const val CHANNEL = "app_update"
        const val EXTRA_OPEN_UPDATE = "open_update"
        private const val KEY_NOTIFIED = "notified_code"
        private const val NOTIF_ID = 20

        /** Safe to call often: KEEP leaves the existing schedule alone. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

        private fun notify(ctx: Context, r: AppUpdate.Remote) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("New version available").setContentText("Pocket Friend ${r.versionName} is ready. Tap to update.")
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
            nm.notify(NOTIF_ID, n)
        }
    }
}
