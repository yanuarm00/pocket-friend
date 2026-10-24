package id.yanuar.bf01.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Foreground service tipe connectedDevice: memegang koneksi BLE + server xiaozhi. */
class BfService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop_find") { Hub.get(this).findPhone.stop(); return START_STICKY }
        val hub = Hub.get(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "BF-01 connection", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BF-01").setContentText("Keeping the connection to BF").setOngoing(true)
            .addAction(0, "Disconnect", PendingIntent.getService(this, 2, Intent(this, BfService::class.java).setAction(ACTION_OFF),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        // startForeground wajib dipanggil dulu (aturan startForegroundService), baru berhenti kalau diputus.
        if (intent?.action == ACTION_OFF) { hub.disconnectAll(); return START_NOT_STICKY }
        if (!hub.prefs.linkEnabled) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        hub.start()
        return START_STICKY
    }

    companion object {
        const val CH = "bf"
        const val ACTION_OFF = "link_off"
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, BfService::class.java))
    }
}
