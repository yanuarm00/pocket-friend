package id.yanuar.bf01.svc

import android.content.Context
import android.provider.CalendarContract
import id.yanuar.bf01.ble.BfProtocol
import java.text.SimpleDateFormat
import java.util.*

/** Agenda dari CalendarContract (READ_CALENDAR). */
object CalendarSource {
    data class Ev(val title: String, val begin: Long, val end: Long, val location: String)

    fun events(ctx: Context, from: Long, to: Long): List<Ev> = runCatching {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to) }.build()
        ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
            null, null, CalendarContract.Instances.BEGIN)?.use { c ->
            buildList { while (c.moveToNext()) if (c.getInt(4) == 0) add(Ev(c.getString(0) ?: "(tanpa judul)", c.getLong(1), c.getLong(2), c.getString(3) ?: "")) }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private val hm = SimpleDateFormat("HH.mm", Locale("id", "ID"))
    fun todayLines(ctx: Context): List<String> {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val start = c.timeInMillis
        return events(ctx, start, start + 86_400_000L).map { "${hm.format(Date(it.begin))} ${it.title}" }
    }
    /** Alarm untuk 48 jam ke depan (dikirim sebelum BF tidur, karena BLE mati saat tidur). */
    fun upcomingAlarms(ctx: Context, extra: List<BfProtocol.Alarm> = emptyList()): List<BfProtocol.Alarm> {
        val now = System.currentTimeMillis()
        return (events(ctx, now, now + 48 * 3_600_000L).map {
            BfProtocol.Alarm(it.begin / 1000 - 10 * 60, it.title, "${hm.format(Date(it.begin))}-${hm.format(Date(it.end))}")
        }.filter { it.epochUtc * 1000 > now } + extra).sortedBy { it.epochUtc }.take(16)
    }
}
