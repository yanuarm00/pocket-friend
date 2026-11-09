package id.yanuar.bf01.svc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 0.3.9.8.7: temporary Android LocalOnlyHotspot so BF can reach the phone when there is no shared Wi-Fi.
 * SSID/password are random per start, kept only in memory and forgotten when [Session.close] runs.
 */
object PhoneHotspot {
    const val NEED_WIFI = "BF needs Wi-Fi: join the same Wi-Fi or turn on hotspot"
    private const val CHANNEL = "bf_wifi"
    private const val NOTIF_ID = 7302

    class Session(val ssid: String, val pass: String, private val res: WifiManager.LocalOnlyHotspotReservation) : AutoCloseable {
        override fun close() { runCatching { res.close() } }
    }

    /** Why the hotspot can't start, or null when it can. */
    fun blocker(ctx: Context): String? {
        fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33) {
            if (!has(Manifest.permission.NEARBY_WIFI_DEVICES)) return "Nearby Wi-Fi devices permission is off"
        } else {
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return "Location permission is off"
            val lm = ctx.getSystemService(LocationManager::class.java)
            if (lm != null && !lm.isLocationEnabled) return "Location is off"
        }
        return null
    }

    /** Starts the hotspot; returns the session, or throws with a plain-English reason. */
    @Suppress("DEPRECATION", "MissingPermission")
    suspend fun start(ctx: Context): Session {
        blocker(ctx)?.let { throw Exception(it) }
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: throw Exception("No Wi-Fi on this phone")
        return suspendCancellableCoroutine { c ->
            val cb = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    val (s, p) = if (Build.VERSION.SDK_INT >= 30) {
                        val cfg = r.softApConfiguration
                        (cfg.ssid ?: "") to (cfg.passphrase ?: "")
                    } else { val cfg = r.wifiConfiguration; (cfg?.SSID ?: "") to (cfg?.preSharedKey ?: "") }
                    val sess = Session(s.trim('"'), p.trim('"'), r)
                    if (!c.isActive) { sess.close(); return }
                    if (sess.ssid.isEmpty()) { sess.close(); c.resumeWith(Result.failure(Exception("Hotspot gave no name"))); return }
                    c.resume(sess)
                }
                override fun onFailed(reason: Int) {
                    if (c.isActive) c.resumeWith(Result.failure(Exception(failText(reason))))
                }
            }
            try { wm.startLocalOnlyHotspot(cb, Handler(Looper.getMainLooper())) }
            catch (e: Exception) { if (c.isActive) c.resumeWith(Result.failure(Exception("Hotspot not allowed: ${e.message}"))) }
        }
    }

    fun failText(reason: Int) = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Phone hotspot is busy (turn off your own hotspot/tethering)"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Hotspot is blocked on this phone"
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No free Wi-Fi channel for the hotspot"
        else -> "Hotspot failed ($reason)"
    }

    fun notifyNeedWifi(ctx: Context, detail: String) {
        runCatching {
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "BF Wi-Fi", NotificationManager.IMPORTANCE_DEFAULT))
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                android.app.PendingIntent.getActivity(ctx, 4, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT) }
            val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("BF needs Wi-Fi").setContentText("Join the same Wi-Fi or turn on hotspot")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$NEED_WIFI.\n$detail"))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
            nm.notify(NOTIF_ID, n)
        }
    }
}
