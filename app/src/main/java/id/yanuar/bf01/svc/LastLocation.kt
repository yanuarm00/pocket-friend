package id.yanuar.bf01.svc

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import id.yanuar.bf01.data.Prefs

/** Simpan lokasi HP sebagai "lokasi terakhir BF" (saat BLE putus & berkala). Tanpa Google Play Services. */
object LastLocation {
    @SuppressLint("MissingPermission")
    fun save(ctx: Context, prefs: Prefs) = runCatching {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return@runCatching
        prefs.lastLat = best.latitude; prefs.lastLon = best.longitude; prefs.lastLocTime = System.currentTimeMillis()
    }
}
