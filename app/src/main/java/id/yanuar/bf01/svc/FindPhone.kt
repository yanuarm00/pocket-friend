package id.yanuar.bf01.svc

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager

/** Cari HP: bunyi lewat stream ALARM supaya tembus mode senyap. DND: perlu akses kebijakan notifikasi. */
class FindPhone(private val ctx: Context) {
    private var mp: MediaPlayer? = null
    private var savedVol = -1
    private var savedFilter = -1

    fun start() {
        if (mp != null) return
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            savedFilter = nm.currentInterruptionFilter
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
        savedVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(ctx, uri); isLooping = true; prepare(); start()
        }
    }

    @android.annotation.SuppressLint("WrongConstant")
    fun stop() {
        mp?.runCatching { stop(); release() }; mp = null
        val am = ctx.getSystemService(AudioManager::class.java)
        if (savedVol >= 0) am.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0); savedVol = -1
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (savedFilter > 0 && nm.isNotificationPolicyAccessGranted) runCatching { nm.setInterruptionFilter(savedFilter) }; savedFilter = -1
    }
    val ringing get() = mp != null
}
