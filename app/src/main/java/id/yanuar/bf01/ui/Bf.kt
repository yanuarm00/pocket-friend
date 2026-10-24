package id.yanuar.bf01.ui

import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.*

/* ---------- Huruf titik 5x7 (sama dengan device v7, scripts/ds.py) ---------- */
private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf("01110","10001","10001","10001","10001","10001","01110"),
    '1' to listOf("0010","0110","0010","0010","0010","0010","0111"),
    '2' to listOf("01110","10001","00001","00110","01000","10000","11111"),
    '3' to listOf("11110","00001","00001","01110","00001","00001","11110"),
    '4' to listOf("00010","00110","01010","10010","11111","00010","00010"),
    '5' to listOf("11111","10000","11110","00001","00001","10001","01110"),
    '6' to listOf("00110","01000","10000","11110","10001","10001","01110"),
    '7' to listOf("11111","00001","00010","00100","01000","01000","01000"),
    '8' to listOf("01110","10001","10001","01110","10001","10001","01110"),
    '9' to listOf("01110","10001","10001","01111","00001","00001","01110"),
    'A' to listOf("01110","10001","10001","11111","10001","10001","10001"),
    'B' to listOf("11110","10001","10001","11110","10001","10001","11110"),
    'C' to listOf("01110","10001","10000","10000","10000","10001","01110"),
    'D' to listOf("11110","10001","10001","10001","10001","10001","11110"),
    'E' to listOf("11111","10000","10000","11110","10000","10000","11111"),
    'F' to listOf("11111","10000","10000","11110","10000","10000","10000"),
    'G' to listOf("01110","10001","10000","10111","10001","10001","01110"),
    'H' to listOf("10001","10001","10001","11111","10001","10001","10001"),
    'I' to listOf("1","1","1","1","1","1","1"),
    'J' to listOf("00111","00010","00010","00010","00010","10010","01100"),
    'K' to listOf("10001","10010","10100","11000","10100","10010","10001"),
    'L' to listOf("10000","10000","10000","10000","10000","10000","11111"),
    'M' to listOf("10001","11011","10101","10101","10001","10001","10001"),
    'N' to listOf("10001","11001","11001","10101","10011","10011","10001"),
    'O' to listOf("01110","10001","10001","10001","10001","10001","01110"),
    'P' to listOf("11110","10001","10001","11110","10000","10000","10000"),
    'Q' to listOf("01110","10001","10001","10001","10101","10010","01101"),
    'R' to listOf("11110","10001","10001","11110","10100","10010","10001"),
    'S' to listOf("01111","10000","10000","01110","00001","00001","11110"),
    'T' to listOf("11111","00100","00100","00100","00100","00100","00100"),
    'U' to listOf("10001","10001","10001","10001","10001","10001","01110"),
    'V' to listOf("10001","10001","10001","10001","10001","01010","00100"),
    'W' to listOf("10001","10001","10001","10101","10101","10101","01010"),
    'X' to listOf("10001","10001","01010","00100","01010","10001","10001"),
    'Y' to listOf("10001","10001","01010","00100","00100","00100","00100"),
    'Z' to listOf("11111","00001","00010","00100","01000","10000","11111"),
    '.' to listOf("00","00","00","00","00","11","11"),
    ':' to listOf("0","1","1","0","1","1","0"),
    '*' to listOf("00000","00000","10101","01110","10101","00000","00000"),
    '-' to listOf("000","000","000","111","000","000","000"),
    '%' to listOf("11001","11010","00010","00100","01000","01011","10011"),
    '!' to listOf("1","1","1","1","1","0","1"),
    '?' to listOf("01110","10001","00001","00110","00100","00000","00100"),
    '/' to listOf("00001","00010","00010","00100","01000","01000","10000"),
    '>' to listOf("00100","00110","11111","10110","10100","10000","10000"),
    '\'' to listOf("1","1","0","0","0","0","0"),
    ',' to listOf("0","0","0","0","0","1","1"),
    '(' to listOf("01","10","10","10","10","10","01"),
    ')' to listOf("10","01","01","01","01","01","10"),
    ' ' to listOf("0","0","0","0","0","0","0")
)

/** Ukuran huruf titik device (satuan px layar device 240 lebar → dipakai sebagai dp x skala). */
enum class Dot(val gw: Float, val h: Float, val t: Float) { XL(17.5f, 22f, 6f), L(12.5f, 16f, 5.5f), M(12f, 15f, 4.5f), S(11.2f, 13f, 4.5f) }

/** Titik-titik yang sudah dipetakan ke koordinat dp (x, y) + lebar total teks. */
private class DotLayout(val totalW: Float, val xs: FloatArray, val ys: FloatArray) {
    val count get() = xs.size
}

/** Cache layout lintas-recomposition (key: teks+ukuran+scale), LRU kecil agar hemat memori. */
private val dotLayoutCache = object : LinkedHashMap<String, DotLayout>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DotLayout>?) = size > 256
}

/** Ambil (dan bila perlu hitung) layout yang sudah di-cache. Tersinkronisasi karena cache dipakai lintas thread. */
private fun dotLayout(text: String, size: Dot, scale: Float): DotLayout =
    synchronized(dotLayoutCache) { dotLayoutCache.getOrPut("$text|${size.name}|$scale") { computeDotLayout(text, size, scale) } }

private fun computeDotLayout(text: String, size: Dot, scale: Float): DotLayout {
    val s = text.uppercase().filter { GLYPHS.containsKey(it) }
    val gw = size.gw * scale; val h = size.h * scale; val tr = size.t * scale
    val d = gw / 4.9f * 0.98f; val px = (gw - d) / 4f; val py = (h - d) / 6f
    var n = 0
    for (c in s) for (row in GLYPHS[c]!!) for (b in row) if (b == '1') n++
    val xs = FloatArray(n); val ys = FloatArray(n)
    var i = 0; var x = 0f; var total = 0f
    for ((idx, c) in s.withIndex()) {
        val rows = GLYPHS[c]!!
        val w = (rows[0].length - 1) * px + d
        for ((r, row) in rows.withIndex()) for ((col, b) in row.withIndex()) if (b == '1') {
            xs[i] = x + col * px + d / 2; ys[i] = r * py + d / 2; i++
        }
        x += w; total += w
        if (idx < s.length - 1) { x += tr; total += tr }
    }
    return DotLayout(total, xs, ys)
}

/** Teks dot-matrix. [scale] memperbesar ukuran device (mis. 2.4 untuk jam besar di HP). */
@Composable
fun DotText(text: String, size: Dot = Dot.M, scale: Float = 1f, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    val lay = remember(text, size, scale) { dotLayout(text, size, scale) }
    val radiusDp = size.gw * scale / 4.9f * 0.98f / 2f
    Canvas(modifier.size(lay.totalW.dp, (size.h * scale).dp)) {
        val rad = radiusDp * density
        for (i in 0 until lay.count) drawCircle(color, rad, Offset(lay.xs[i] * density, lay.ys[i] * density))
    }
}

/* ---------- Kepala BF: ikon FILLED (siluet blok, mata & mulut = garis outline yang dilubangi) ----------
 * PLACEHOLDER. Path dihasilkan scripts/bf_icon_solid.py; ganti SOLID di sini (dan drawable ic_launcher_*) saat karakter final. */
private const val SOLID = "M3.960 0.003 L3.322 0.031 L2.845 0.124 L2.612 0.193 L2.164 0.375 L1.740 0.614 L1.539 0.754 L1.162 1.074 L0.816 1.455 L0.664 1.660 L0.406 2.095 L0.210 2.559 L0.135 2.800 L0.034 3.299 L0.009 3.555 L0.000 22.443 L0.081 23.641 L0.316 24.791 L0.696 25.882 L1.209 26.903 L1.845 27.844 L2.593 28.694 L3.443 29.442 L4.384 30.078 L5.405 30.591 L6.495 30.971 L7.645 31.206 L8.844 31.287 L20.153 31.287 L21.351 31.206 L22.501 30.971 L23.592 30.591 L24.613 30.078 L25.553 29.442 L26.403 28.694 L27.151 27.844 L27.787 26.903 L28.300 25.882 L28.680 24.791 L28.915 23.641 L28.996 22.443 L28.996 11.134 L28.915 9.936 L28.680 8.786 L28.300 7.695 L27.787 6.674 L27.151 5.733 L26.403 4.884 L25.553 4.135 L24.613 3.499 L23.592 2.986 L22.501 2.607 L21.351 2.371 L20.153 2.290 L9.313 2.290 L5.239 0.279 L4.823 0.137 L4.396 0.045 Z M5.543 17.367 L5.557 12.229 L5.640 11.733 L5.751 11.366 L5.935 10.951 L6.201 10.525 L6.411 10.264 L6.647 10.025 L6.904 9.811 L7.181 9.623 L7.476 9.463 L7.785 9.332 L8.104 9.233 L8.433 9.165 L8.766 9.130 L9.101 9.127 L9.435 9.158 L9.764 9.221 L10.085 9.316 L10.396 9.442 L10.692 9.597 L10.972 9.782 L11.269 10.025 L11.505 10.264 L11.715 10.525 L11.958 10.908 L12.103 11.210 L12.233 11.571 L12.312 11.897 L12.359 12.229 L12.359 17.677 L12.276 18.173 L12.165 18.540 L11.981 18.955 L11.715 19.381 L11.390 19.764 L10.972 20.124 L10.546 20.390 L9.973 20.628 L9.483 20.741 L8.983 20.782 L8.481 20.748 L7.943 20.628 L7.370 20.390 L6.944 20.124 L6.526 19.764 L6.201 19.381 L5.882 18.849 L5.697 18.382 L5.584 17.892 Z M12.896 17.392 L12.910 12.229 L12.993 11.733 L13.104 11.366 L13.288 10.951 L13.485 10.621 L13.687 10.354 L13.914 10.107 L14.164 9.884 L14.434 9.686 L14.723 9.516 L15.027 9.375 L15.343 9.264 L15.669 9.185 L16.001 9.138 L16.336 9.124 L16.670 9.143 L17.001 9.195 L17.326 9.278 L17.640 9.394 L17.942 9.539 L18.229 9.713 L18.496 9.915 L18.825 10.228 L19.039 10.485 L19.253 10.805 L19.408 11.101 L19.572 11.524 L19.655 11.849 L19.712 12.229 L19.712 17.677 L19.629 18.173 L19.518 18.540 L19.334 18.955 L19.137 19.285 L18.825 19.678 L18.365 20.095 L17.942 20.367 L17.438 20.590 L16.836 20.741 L16.336 20.782 L15.834 20.748 L15.296 20.628 L14.829 20.443 L14.393 20.193 L14.000 19.881 L13.583 19.421 L13.288 18.955 L13.088 18.494 L12.937 17.892 Z M9.528 24.552 L9.012 24.113 L8.878 23.970 L8.774 23.804 L8.705 23.620 L8.670 23.329 L8.723 23.040 L8.803 22.861 L8.917 22.702 L9.141 22.512 L9.316 22.425 L9.505 22.374 L9.799 22.368 L10.081 22.448 L10.251 22.546 L10.687 22.921 L11.019 23.140 L11.371 23.311 L11.738 23.433 L12.115 23.506 L12.499 23.530 L12.882 23.506 L13.259 23.433 L13.626 23.311 L13.978 23.140 L14.310 22.921 L14.828 22.493 L15.007 22.413 L15.198 22.368 L15.394 22.362 L15.587 22.395 L15.771 22.464 L15.937 22.568 L16.080 22.702 L16.194 22.861 L16.301 23.135 L16.325 23.427 L16.292 23.620 L16.223 23.804 L16.056 24.045 L15.575 24.471 L14.910 24.911 L14.319 25.188 L13.703 25.384 L13.199 25.481 L12.562 25.530 L11.924 25.498 L11.294 25.384 L10.557 25.139 L9.972 24.846 Z M7.543 12.551 L7.559 17.575 L7.626 17.844 L7.710 18.034 L7.864 18.264 L8.008 18.415 L8.231 18.580 L8.481 18.699 L8.750 18.766 L8.958 18.782 L9.234 18.755 L9.499 18.674 L9.685 18.580 L9.908 18.415 L10.052 18.264 L10.206 18.034 L10.312 17.778 L10.357 17.575 L10.373 17.355 L10.357 12.331 L10.265 11.998 L10.171 11.812 L10.052 11.642 L9.908 11.491 L9.744 11.363 L9.563 11.260 L9.369 11.185 L9.097 11.131 L8.819 11.131 L8.547 11.185 L8.291 11.291 L8.061 11.445 L7.864 11.642 L7.710 11.872 L7.604 12.128 L7.559 12.331 Z M14.912 12.331 L14.896 17.355 L14.912 17.575 L14.957 17.778 L15.063 18.034 L15.217 18.264 L15.414 18.461 L15.644 18.615 L15.900 18.721 L16.172 18.775 L16.450 18.775 L16.722 18.721 L16.916 18.646 L17.097 18.543 L17.261 18.415 L17.405 18.264 L17.524 18.094 L17.618 17.908 L17.710 17.575 L17.726 12.551 L17.710 12.331 L17.665 12.128 L17.559 11.872 L17.405 11.642 L17.261 11.491 L17.038 11.326 L16.852 11.232 L16.587 11.151 L16.311 11.124 L16.103 11.140 L15.834 11.207 L15.584 11.326 L15.361 11.491 L15.217 11.642 L15.063 11.872 L14.979 12.062 Z"
private const val HW = 28.996f; private const val HH = 31.287f
private fun solidNodes() = androidx.compose.ui.graphics.vector.PathParser().parsePathString(SOLID).toNodes()

/** BF head [width] wide, solid and smiling (avatars, chat). Same drawing as [BfFace]. */
@Composable
fun BfHead(width: Dp, modifier: Modifier = Modifier, color: Color = androidx.compose.material3.LocalContentColor.current) =
    BfFace(width, BfMood.Senyum, true, modifier, color)

/** BF glyph for the bottom navigation: 24dp box, 20dp tall glyph like the Material icons next to it. */
@Composable
fun BfNavIcon(color: Color = androidx.compose.material3.LocalContentColor.current) =
    androidx.compose.foundation.layout.Box(Modifier.size(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        BfFace((20f * HW / HH).dp, BfMood.Senyum, true, color = color) }

/** Stroke weight in dp for a BF head [width] wide: one visual weight everywhere, thinner only at icon sizes. */
fun bfStroke(width: Dp): Dp = (width.value * 0.075f).coerceIn(1.5f, 2.2f).dp

/* ---------- Kepala BF dengan ekspresi (pil status) ---------- */
/** Kartu M3 berbentuk gelembung putih (di tema gelap). */
@Composable
fun BubbleCard(modifier: Modifier = Modifier, tail: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val b = LocalBubble.current
    Card(modifier, shape = BubbleShape(radius = 26.dp, tailH = tail), colors = CardDefaults.cardColors(containerColor = b.container, contentColor = b.content)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 22.dp + tail), content = content)
    }
}

/** Baterai 5 titik seperti footer device. */
@Composable
fun BatteryDots(percent: Int?, color: Color = LocalContentColor.current, dot: Dp = 9.dp) {
    val on = if (percent == null) 0 else ((percent + 10) / 20).coerceIn(if (percent > 0) 1 else 0, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(dot * 0.45f)) {
        repeat(5) { i -> Canvas(Modifier.size(dot)) {
            if (i < on) drawCircle(color) else drawCircle(color, style = Stroke(1.2.dp.toPx()))
        } }
    }
}

/** Pil hitam kecil seperti "TODAY (6)" di rev1. */
@Composable
fun DotPill(text: String, modifier: Modifier = Modifier, head: Boolean = true) {
    val b = LocalBubble.current
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = b.content, contentColor = b.container) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (head) { BfHead(20.dp, color = b.content); Spacer(Modifier.width(10.dp)) }
            DotText(text, Dot.S, 0.9f, color = b.container)
        }
    }
}

/** Chat ala WhatsApp/daisyUI `chat-start`/`chat-end` (warna v1): BF kiri (gelembung putih, ekor kiri-bawah, avatar kepala BF),
 *  Mas Yan kanan (abu gelap, ekor kanan-bawah), header nama · jam. */
@Composable
fun ChatBubble(text: String, fromBf: Boolean, header: String? = null) {
    val c = MaterialTheme.colorScheme; val b = LocalBubble.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromBf) Arrangement.Start else Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        if (fromBf) { Surface(Modifier.size(34.dp), shape = androidx.compose.foundation.shape.CircleShape, color = c.surfaceContainerHighest) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { BfHead(18.dp, color = c.onSurface) } }
            Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = if (fromBf) androidx.compose.ui.Alignment.Start else androidx.compose.ui.Alignment.End) {
            header?.let { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant) }
            Surface(Modifier.widthIn(max = 290.dp), shape = BubbleShape(radius = 20.dp, tailW = 9.dp, tailH = 9.dp, tailLeft = fromBf),
                color = if (fromBf) b.container else c.surfaceContainerHighest, contentColor = if (fromBf) b.content else c.onSurface) {
                Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + 9.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/* ---------- Kepala BF dengan ekspresi (pil status) ---------- */


/* ---------- Kartu gelembung bicara, ekor di kiri-bawah (rev1) ---------- */
class BubbleShape(private val radius: Dp = 22.dp, private val tailW: Dp = 22.dp, private val tailH: Dp = 18.dp, private val tailLeft: Boolean = true) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val r = radius.toPx(); val tw = tailW.toPx(); val th = tailH.toPx()
        val b = size.height - th
        val w = size.width
        val p = Path().apply {
            moveTo(0f, r); arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(w - r, 0f); arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            if (tailLeft) {
                lineTo(w, b - r); arcTo(Rect(w - 2 * r, b - 2 * r, w, b), 0f, 90f, false)
                lineTo(tw + r * 0.3f, b); lineTo(0f, size.height)   // ekor kiri-bawah (identitas BF / chat-start)
            } else {
                lineTo(w, size.height); lineTo(w - tw - r * 0.3f, b)  // ekor kanan-bawah (chat-end)
                lineTo(r, b); arcTo(Rect(0f, b - 2 * r, 2 * r, b), 90f, 90f, false)
            }
            close()
        }
        Outline.Generic(p)
    }
}

enum class BfMood { Senyum, Datar, Sedih }

private val outerPath: Path by lazy { PathParser().parsePathString(SOLID.substringBefore(" Z") + " Z").toPath() }

/** Kepala BF selebar [width]. [filled] = blok solid (mata & mulut tembus), false = garis luar saja. [mood] mengatur mulut. */
@Composable
fun BfFace(width: Dp, mood: BfMood, filled: Boolean, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(width, width * (HH / HW)).graphicsLayerOffscreen()) {
        val k = size.width / HW
        // Stroke set in dp and converted to path units, so it does not grow or shrink with the head.
        val line = bfStroke(width).toPx() / k
        scale(k, k, pivot = Offset.Zero) {
            if (filled) drawPath(outerPath, color) else drawPath(outerPath, color, style = Stroke(line, join = StrokeJoin.Round))
            val feat = if (filled) Color.Black else color
            val blend = if (filled) BlendMode.Clear else BlendMode.SrcOver
            for (cx in floatArrayOf(8.95f, 16.32f)) drawRoundRect(feat, Offset(cx - 2.4f, 10.1f), Size(4.8f, 9.7f), CornerRadius(2.4f),
                style = Stroke(line), blendMode = blend)
            val m = Path().apply {
                when (mood) {
                    BfMood.Senyum -> { moveTo(9.5f, 23.2f); quadraticTo(12.5f, 26.4f, 15.5f, 23.2f) }
                    BfMood.Sedih -> { moveTo(9.5f, 25.4f); quadraticTo(12.5f, 22.2f, 15.5f, 25.4f) }
                    BfMood.Datar -> { moveTo(9.8f, 24.2f); lineTo(15.2f, 24.2f) }
                }
            }
            drawPath(m, feat, style = Stroke(line, cap = StrokeCap.Round), blendMode = blend)
        }
    }
}

private fun Modifier.graphicsLayerOffscreen() = this.then(Modifier.graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen))
