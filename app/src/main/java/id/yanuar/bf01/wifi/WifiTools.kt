package id.yanuar.bf01.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/** A nearby network, merged across access points with the same name. */
data class Nearby(val ssid: String, val level: Int, val secured: Boolean, val has24: Boolean) {
    val only5 get() = !has24
}

object WifiTools {
    /** Tests can inject a fake scan list. */
    var scanOverride: (() -> List<Nearby>)? = null
    var ssidOverride: (() -> String?)? = null

    fun hasLocation(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun wm(ctx: Context) = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** SSID the phone is on now, or null (not on Wi-Fi, no location permission, or location turned off). */
    @SuppressLint("MissingPermission") @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        ssidOverride?.let { return it() }
        if (!hasLocation(ctx)) return null
        val s = runCatching { wm(ctx).connectionInfo?.ssid }.getOrNull()?.removeSurrounding("\"") ?: return null
        return s.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != WifiManager.UNKNOWN_SSID }
    }

    @Suppress("DEPRECATION") fun startScan(ctx: Context) = runCatching { wm(ctx).startScan() }.getOrDefault(false)

    /** Nearby Wi-Fi, strongest first, one row per name. */
    @SuppressLint("MissingPermission") @Suppress("DEPRECATION")
    fun nearby(ctx: Context): List<Nearby> {
        scanOverride?.let { return it() }
        if (!hasLocation(ctx)) return emptyList()
        val raw = runCatching { wm(ctx).scanResults }.getOrNull().orEmpty()
        return merge(raw.map { Triple(it.SSID ?: "", it.level, it.frequency) to it.capabilities.orEmpty() })
    }

    /** Pure merge step (unit-tested): ((ssid, dBm, MHz), capabilities). */
    fun merge(raw: List<Pair<Triple<String, Int, Int>, String>>): List<Nearby> =
        raw.filter { it.first.first.isNotBlank() }.groupBy { it.first.first }.map { (ssid, aps) ->
            Nearby(ssid, aps.maxOf { it.first.second },
                aps.any { c -> listOf("WPA", "WEP", "SAE", "PSK", "EAP").any { it in c.second.uppercase() } },
                aps.any { it.first.third in 2400..2500 })
        }.sortedByDescending { it.level }

    /** Best effort: is this phone hosting a hotspot right now? null = can't tell. */
    fun hotspotOn(ctx: Context): Boolean? = runCatching {
        val m = WifiManager::class.java.getDeclaredMethod("isWifiApEnabled"); m.isAccessible = true; m.invoke(wm(ctx)) as Boolean
    }.getOrNull()

    /** Reads a Wi-Fi QR from a picked image (e.g. a screenshot). */
    fun qrFromImage(ctx: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        return qrFromBitmap(bmp)
    }

    fun qrFromBitmap(bmp: Bitmap): String? {
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return qrFromPixels(px, bmp.width, bmp.height)
    }

    fun qrFromPixels(px: IntArray, w: Int, h: Int): String? {
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
        val src = RGBLuminanceSource(w, h, px)
        for (s in listOf(src, src.invert())) for (bin in listOf(HybridBinarizer(s), GlobalHistogramBinarizer(s)))
            runCatching { return MultiFormatReader().decode(BinaryBitmap(bin), hints).text }
        return null
    }
}
