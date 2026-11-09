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
        val n = sbn.notification
        // Hanya notifikasi navigasi yang sedang berjalan (bukan info lalu lintas dsb.)
        val navLike = n.category == Notification.CATEGORY_NAVIGATION || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!navLike) return
        val fields = texts(n)
        val nav = parseNav(fields)
        hub.lastMapsUpdate.value = System.currentTimeMillis() to (nav?.let { "${it.distance} ${it.instruction}".trim() } ?: "not recognised: " + fields.joinToString(" | ").take(120))
        // Tidak dikenali -> dilewati (mis. "Rerouting…"). Navigasi TIDAK pernah dimatikan diam-diam.
        if (nav == null) { if (++badCount == 5) hub.log("Google Maps notification format not recognised"); return }
        badCount = 0
        hub.sendLive(BfProtocol.navigation(BfProtocol.arrowFor(nav.instruction).let { if (it == "-") "S" else it }, nav.distance, nav.street, nav.instruction, nav.remain, nav.eta), nav = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS && (sbn.notification.category == Notification.CATEGORY_NAVIGATION || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)) {
            hub.sendLive(BfProtocol.navigation("-", "", "", "", "", ""), nav = true); hub.lastNav = null }
    }

    /** Semua teks notifikasi: extras + tickerText, lalu (bila kosong) teks dari RemoteViews kustom Maps. */
    private fun texts(n: Notification): List<String> {
        val e = n.extras
        val out = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
            .mapNotNull { e.getCharSequence(it)?.toString() }.toMutableList()
        n.tickerText?.toString()?.let { out += it }
        if (out.none { it.isNotBlank() }) for (rv in listOfNotNull(n.bigContentView, n.contentView)) out += remoteTexts(rv)
        return out.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Teks yang dipasang lewat setTextViewText di RemoteViews (refleksi; aman bila gagal). */
    private fun remoteTexts(rv: android.widget.RemoteViews): List<String> = runCatching {
        val f = android.widget.RemoteViews::class.java.getDeclaredField("mActions").apply { isAccessible = true }
        (f.get(rv) as? List<*>).orEmpty().mapNotNull { a ->
            a ?: return@mapNotNull null
            var c: Class<*>? = a.javaClass; var v: Any? = null
            while (c != null && v == null) { v = c.declaredFields.firstOrNull { it.name == "value" || it.name == "mValue" }?.let { it.isAccessible = true; it.get(a) }; c = c.superclass }
            (v as? CharSequence)?.toString()
        }
    }.getOrDefault(emptyList())

    data class Nav(val distance: String, val instruction: String, val street: String, val remain: String, val eta: String)

    companion object {
        const val MAPS = "com.google.android.apps.maps"
        private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
        private val TIME = Regex("""\b([01]?\d|2[0-3])[.:]([0-5]\d)\b""")
        private val DUR = Regex("""(\d+\s*(?:jam|j|hr|h)\s*)?\d+\s*(?:menit|mnt|min)\b""", RegexOption.IGNORE_CASE)

        /**
         * v0.3.9: tidak bergantung bahasa. Jarak dari regex angka+satuan; instruksi = teks apa adanya (baris non-jarak
         * pertama); ETA = jam "HH:MM"/"HH.MM" mana pun; sisa = durasi "x menit"/"x min". Null hanya bila tak ada teks berguna.
         */
        fun parseNav(fields: List<String>): Nav? {
            val f = fields.map { it.trim() }.filter { it.isNotEmpty() }
            if (f.isEmpty()) return null
            val distLine = f.firstOrNull { DIST.containsMatchIn(it) }
            val distance = distLine?.let { DIST.find(it)!!.let { m -> m.groupValues[1].replace(',', '.') + m.groupValues[2].lowercase() } }?.uppercase() ?: ""
            val isMeta = { s: String -> (TIME.containsMatchIn(s) || DUR.containsMatchIn(s)) && s != distLine }
            var instruction = f.firstOrNull { it != distLine && !isMeta(it) && !DIST.matches(it.trim()) } ?: ""
            if (instruction.isEmpty() && distLine != null) instruction = distLine.replace(DIST, "").trim(' ', '-', '·', ',')
            val meta = f.filter { it != instruction && isMeta(it) }.joinToString(" ")
            val eta = TIME.find(meta)?.let { "%02d.%s".format(it.groupValues[1].toInt(), it.groupValues[2]) } ?: ""
            val remain = DUR.find(meta)?.value?.trim() ?: ""
            if (distance.isEmpty() && instruction.isEmpty()) return null
            val street = Regex("""\b(?:ke|menuju|onto|toward|towards|ke arah)\s+(.+)$""", RegexOption.IGNORE_CASE).find(instruction)?.groupValues?.get(1)?.trim() ?: instruction
            return Nav(distance, instruction, street, remain, eta)
        }
    }
}
