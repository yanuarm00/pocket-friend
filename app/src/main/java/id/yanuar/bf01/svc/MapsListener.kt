package id.yanuar.bf01.svc

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import id.yanuar.bf01.ble.BfProtocol
import kotlinx.coroutines.launch

/**
 * Hanya memproses notifikasi paket Google Maps (navigasi). Notifikasi lain diabaikan & tidak dicatat.
 * Kalau format Maps tidak dikenali 5x berturut-turut, fitur navigasi dimatikan dengan sopan (prefs.navEnabled=false).
 * Juga dipakai untuk akses MediaSession (judul lagu + perintah dari BF).
 */
class MapsListener : NotificationListenerService() {
    private val hub by lazy { Hub.get(this) }
    private var badCount = 0
    private var controller: MediaController? = null

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(this, MapsListener::class.java)
        runCatching { msm.addOnActiveSessionsChangedListener({ attach(it) }, cn); attach(msm.getActiveSessions(cn)) }
        hub.mediaCommand = { cmd -> controller?.transportControls?.let { t -> when (cmd) {
            "prev" -> t.skipToPrevious(); "next" -> t.skipToNext()
            "playpause" -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play() } } }
        // Maps may already be navigating when access is granted / the service restarts.
        runCatching { activeNotifications?.filter { it.packageName == MAPS }?.forEach { onNotificationPosted(it) } }
    }
    override fun onListenerDisconnected() { hub.mediaCommand = null }

    private fun attach(list: List<MediaController>?) {
        controller?.unregisterCallback(cb)
        // Prefer the session that is actually playing, not just the first one.
        controller = (list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull())?.also { it.registerCallback(cb); pushMedia(it) }
    }
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(m: MediaMetadata?) { controller?.let { pushMedia(it) } }
        override fun onPlaybackStateChanged(s: PlaybackState?) { controller?.let { pushMedia(it) } }
    }
    private fun pushMedia(c: MediaController) {
        if (!hub.prefs.musicEnabled) return
        val m = c.metadata ?: return
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        hub.sendLive(BfProtocol.music(title, artist, playing), nav = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS || !hub.prefs.navEnabled) return
        // Unknown Maps notifications (e.g. "Rerouting…") are skipped; navigation is never switched off silently.
        val p = parse(sbn.notification) ?: run { if (++badCount == 5) hub.log("Google Maps notification format not recognised"); return }
        badCount = 0
        hub.sendLive(p, nav = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS) { hub.sendLive(BfProtocol.navigation("-", "", "", "", "", ""), nav = true); hub.lastNav = null }
    }

    /**
     * [BELUM DICEK] Format notifikasi navigasi Maps tidak resmi & bisa berubah.
     * Asumsi: title = jarak ("250 m"), text = instruksi/jalan ("Belok kiri ke Jl. Sudirman"), subText = sisa/ETA.
     */
    private fun parse(n: Notification): ByteArray? {
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val distRe = Regex("""(\d+[.,]?\d*)\s*(km|m|mi|ft)\b""", RegexOption.IGNORE_CASE)
        val dist = distRe.find(title)?.value ?: distRe.find(text)?.value ?: return null
        val street = text.substringAfter(" ke ", text).substringAfter(" onto ", text)
        val eta = Regex("""\b\d{1,2}[.:]\d{2}\b""").find(sub)?.value ?: ""
        return BfProtocol.navigation(BfProtocol.arrowFor(text + " " + title), dist.replace(" ", "").uppercase(), street, text, sub.substringBefore("·").trim(), eta)
    }

    companion object { const val MAPS = "com.google.android.apps.maps" }
}
