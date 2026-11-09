package id.yanuar.bf01.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The QR as BF draws it (firmware yan_draw_hi_qr): error correction H, versions 1..10 (max 119 bytes),
 * [m] screen pixels per module on the 200 px panel, a 37x37 px clear square in the middle with the profile logo on it.
 */
object BfQr {
    const val CLEAR = 37          // clear square (px), firmware: cx-18 .. cx+18
    class Code(val n: Int, val m: Int, private val mod: BooleanArray) {
        operator fun get(x: Int, y: Int) = mod[y * n + x]
        /** Side in BF pixels. */
        val side get() = n * m
    }

    fun encode(text: String): Code? {
        if (text.isEmpty()) return null
        val bm = runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 0)) }.getOrNull() ?: return null
        val n = bm.width
        if (n > 57) return null                               // version 10 is the most BF draws
        return Code(n, maxOf(2, 184 / (n + 4)), BooleanArray(n * n) { bm[it % n, it / n] })
    }

    /** Pixels of the logo [key], 40x40, centre at (20,20); a letter badge key is "#K". Unknown keys draw the link icon. */
    fun logo(key: String): BooleanArray = LogoBits.pixels(key) ?: LogoBits.pixels("link")!!

    /** The picture BF shows, with a 4-module quiet zone, as pixels (true = black). Used to check scanning in tests. */
    fun bitmap(text: String, logoKey: String): Array<BooleanArray>? {
        val c = encode(text) ?: return null
        val q = 4 * c.m; val s = c.side; val size = s + 2 * q
        val img = Array(size) { BooleanArray(size) }
        for (y in 0 until c.n) for (x in 0 until c.n) if (c[x, y])
            for (j in 0 until c.m) for (i in 0 until c.m) img[q + y * c.m + j][q + x * c.m + i] = true
        val cx = q + s / 2; val cy = q + s / 2
        for (y in cy - 18..cy + 18) for (x in cx - 18..cx + 18) img[y][x] = false
        val lg = logo(logoKey)
        for (y in 0 until LogoBits.SIZE) for (x in 0 until LogoBits.SIZE) if (lg[y * LogoBits.SIZE + x]) {
            val px = cx - 20 + x; val py = cy - 20 + y
            if (px in 0 until size && py in 0 until size) img[py][px] = true
        }
        return img
    }
}

/** A logo drawn from the BF firmware bitmaps (black on white), tinted [ink]. */
@Composable
fun LogoIcon(key: String, size: Dp = 28.dp, ink: Color = Color.Black) {
    val px = remember(key) { BfQr.logo(key) }
    Canvas(Modifier.size(size)) { drawLogo(px, ink, Offset.Zero, this.size.width / 36f, 2) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLogo(px: BooleanArray, ink: Color, origin: Offset, u: Float, crop: Int) {
    val sz = LogoBits.SIZE
    for (y in crop until sz - crop) {
        var x = crop
        while (x < sz - crop) {
            if (!px[y * sz + x]) { x++; continue }
            var e = x; while (e < sz - crop && px[y * sz + e]) e++
            drawRect(ink, origin + Offset((x - crop) * u, (y - crop) * u), Size((e - x) * u + 0.6f, u + 0.6f))
            x = e
        }
    }
}

/** QR preview for a profile: same code and logo as BF shows. [logoKey] = logo, "" = plain code. */
@Composable
fun QrPreview(text: String, size: Dp = 200.dp, logoKey: String = "") {
    val code = remember(text) { BfQr.encode(text) }
    val logo = remember(logoKey) { if (logoKey.isEmpty()) null else BfQr.logo(logoKey) }
    Canvas(Modifier.size(size)) {
        drawRect(Color.White)
        code ?: return@Canvas
        val w = this.size.width
        val quiet = 4f / (code.n + 8)                      // BF: 4 modules of white around the code
        val side = w * (1 - 2 * quiet); val o = w * quiet
        val cell = side / code.n
        for (y in 0 until code.n) for (x in 0 until code.n) if (code[x, y])
            drawRect(Color.Black, Offset(o + x * cell, o + y * cell), Size(cell + 0.5f, cell + 0.5f))
        if (logo != null) {
            val u = side / code.side                          // one BF pixel
            val c = Offset(o + (code.side / 2) * u, o + (code.side / 2) * u)
            drawRect(Color.White, c - Offset(18 * u, 18 * u), Size(37 * u, 37 * u))
            drawLogo(logo, Color.Black, c - Offset(18 * u, 18 * u), u, 2)
        }
    }
}
