package id.yanuar.bf01.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.R

/**
 * Pocket Friend — tema Material 3 monokrom mengikuti UI device v7.
 * Dynamic color sengaja MATI. Gelap = utama (dasar hitam, kartu gelembung putih); terang = versi terbalik.
 */
val Lexend = FontFamily(
    Font(R.font.lexend_300, FontWeight.Light), Font(R.font.lexend_400, FontWeight.Normal),
    Font(R.font.lexend_500, FontWeight.Medium), Font(R.font.lexend_600, FontWeight.SemiBold),
)

private val Ink = Color(0xFF000000); private val Paper = Color(0xFFFFFFFF)

val PfDark = darkColorScheme(
    primary = Paper, onPrimary = Ink, primaryContainer = Paper, onPrimaryContainer = Ink,
    secondary = Color(0xFFD9D9D9), onSecondary = Ink, secondaryContainer = Color(0xFF2B2B2B), onSecondaryContainer = Paper,
    tertiary = Paper, onTertiary = Ink, tertiaryContainer = Color(0xFF3A3A3A), onTertiaryContainer = Paper,
    background = Ink, onBackground = Paper, surface = Ink, onSurface = Paper,
    surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Ink, surfaceContainerLow = Color(0xFF0E0E0E), surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C), surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFF3D3D3D),
    inverseSurface = Paper, inverseOnSurface = Ink, inversePrimary = Ink,
    error = Paper, onError = Ink, errorContainer = Paper, onErrorContainer = Ink, scrim = Ink,
)

val PfLight = lightColorScheme(
    primary = Ink, onPrimary = Paper, primaryContainer = Ink, onPrimaryContainer = Paper,
    secondary = Color(0xFF262626), onSecondary = Paper, secondaryContainer = Color(0xFFE4E4E4), onSecondaryContainer = Ink,
    tertiary = Ink, onTertiary = Paper, tertiaryContainer = Color(0xFFD4D4D4), onTertiaryContainer = Ink,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF424242),
    surfaceContainerLowest = Paper, surfaceContainerLow = Color(0xFFF6F6F6), surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA), surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF737373), outlineVariant = Color(0xFFC6C6C6),
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Paper,
    error = Ink, onError = Paper, errorContainer = Ink, onErrorContainer = Paper, scrim = Ink,
)

private fun t(size: Int, w: FontWeight, line: Int, ls: Double = 0.0) =
    TextStyle(fontFamily = Lexend, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = ls.sp)

val PfType = Typography(
    displayLarge = t(57, FontWeight.Normal, 64), displayMedium = t(45, FontWeight.Normal, 52), displaySmall = t(36, FontWeight.Normal, 44),
    headlineLarge = t(32, FontWeight.Medium, 40), headlineMedium = t(28, FontWeight.Medium, 36), headlineSmall = t(24, FontWeight.Medium, 32),
    titleLarge = t(22, FontWeight.Medium, 28), titleMedium = t(16, FontWeight.Medium, 24, 0.15), titleSmall = t(14, FontWeight.Medium, 20, 0.1),
    bodyLarge = t(16, FontWeight.Normal, 24, 0.2), bodyMedium = t(14, FontWeight.Normal, 20, 0.2), bodySmall = t(12, FontWeight.Light, 16, 0.3),
    labelLarge = t(14, FontWeight.SemiBold, 20, 0.6), labelMedium = t(12, FontWeight.Medium, 16, 0.6), labelSmall = t(11, FontWeight.Medium, 16, 0.6),
)

/** Warna "gelembung": kebalikan dari latar (putih di tema gelap, hitam di tema terang). */
data class Bubble(val container: Color, val content: Color)
val LocalBubble = staticCompositionLocalOf { Bubble(Paper, Ink) }

@Composable
fun PocketFriendTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (dark) PfDark else PfLight
    androidx.compose.runtime.CompositionLocalProvider(LocalBubble provides Bubble(scheme.inverseSurface, scheme.inverseOnSurface)) {
        MaterialTheme(colorScheme = scheme, typography = PfType, shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        ), content = content)
    }
}
