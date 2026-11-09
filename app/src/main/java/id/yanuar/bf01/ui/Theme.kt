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
 * Pocket Friend v0.4.0 "Pop" theme (mockup v5.13): cream background, white cards, orange #FF6A1A accent,
 * yellow #FFE27A highlight, charcoal #2B2D31 ink. Light + dark. Dynamic color stays OFF.
 */
val Lexend = FontFamily(
    Font(R.font.lexend_300, FontWeight.Light), Font(R.font.lexend_400, FontWeight.Normal),
    Font(R.font.lexend_500, FontWeight.Medium), Font(R.font.lexend_600, FontWeight.SemiBold),
)
val DmSans = FontFamily(
    Font(R.font.dmsans_400, FontWeight.Normal), Font(R.font.dmsans_500, FontWeight.Medium),
    Font(R.font.dmsans_700, FontWeight.Bold), Font(R.font.dmsans_800, FontWeight.ExtraBold),
)

/** Pop palette, shared by screens that need a fixed brand colour (cards, chips). */
object Pop {
    val Orange = Color(0xFFFF6A1A); val OrangeDeep = Color(0xFFC24E0E); val Yellow = Color(0xFFFFE27A)
    val Ink = Color(0xFF2B2D31); val Cream = Color(0xFFF6F5F2); val Grey = Color(0xFF7D8288); val Mist = Color(0xFFE9EAEC)
    val Red = Color(0xFFD9442B); val Led = Color(0xFF3DDC6A); val NightBg = Color(0xFF1C1E21); val NightCard = Color(0xFF2B2D31)
}

private val Ink = Pop.Ink; private val Paper = Color(0xFFFFFFFF)

val PfDark = darkColorScheme(
    primary = Pop.Orange, onPrimary = Paper, primaryContainer = Color(0xFF4A2A18), onPrimaryContainer = Color(0xFFFFD8C2),
    secondary = Pop.Yellow, onSecondary = Ink, secondaryContainer = Color(0xFF3A3D43), onSecondaryContainer = Paper,
    tertiary = Pop.Yellow, onTertiary = Ink, tertiaryContainer = Color(0xFF4A4222), onTertiaryContainer = Pop.Yellow,
    background = Pop.NightBg, onBackground = Paper, surface = Pop.NightBg, onSurface = Paper,
    surfaceVariant = Color(0xFF33363B), onSurfaceVariant = Color(0xFFA3A0AE),
    surfaceContainerLowest = Color(0xFF16181B), surfaceContainerLow = Color(0xFF24262A), surfaceContainer = Pop.NightCard,
    surfaceContainerHigh = Color(0xFF33363B), surfaceContainerHighest = Color(0xFF3A3D43),
    outline = Color(0xFF8C8F96), outlineVariant = Color(0xFF3D4046),
    inverseSurface = Paper, inverseOnSurface = Ink, inversePrimary = Pop.OrangeDeep,
    error = Color(0xFFFF7A62), onError = Ink, errorContainer = Color(0xFF5A231A), onErrorContainer = Color(0xFFFFD3C9), scrim = Color.Black,
)

val PfLight = lightColorScheme(
    primary = Pop.Orange, onPrimary = Paper, primaryContainer = Color(0xFFFFE3D2), onPrimaryContainer = Pop.OrangeDeep,
    secondary = Ink, onSecondary = Paper, secondaryContainer = Pop.Yellow, onSecondaryContainer = Ink,
    tertiary = Pop.OrangeDeep, onTertiary = Paper, tertiaryContainer = Pop.Yellow, onTertiaryContainer = Ink,
    background = Pop.Cream, onBackground = Ink, surface = Pop.Cream, onSurface = Ink,
    surfaceVariant = Pop.Mist, onSurfaceVariant = Pop.Grey,
    surfaceContainerLowest = Paper, surfaceContainerLow = Paper, surfaceContainer = Paper,
    surfaceContainerHigh = Paper, surfaceContainerHighest = Pop.Mist,
    outline = Color(0xFFA3A0AE), outlineVariant = Color(0xFFDCDDE0),
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Pop.Yellow,
    error = Pop.Red, onError = Paper, errorContainer = Color(0xFFFBE0DA), onErrorContainer = Color(0xFF7A1E10), scrim = Color.Black,
)

private fun t(size: Int, w: FontWeight, line: Int, ls: Double = 0.0) =
    TextStyle(fontFamily = DmSans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = ls.sp)

val PfType = Typography(
    displayLarge = t(57, FontWeight.Normal, 64), displayMedium = t(45, FontWeight.Normal, 52), displaySmall = t(36, FontWeight.Normal, 44),
    headlineLarge = t(32, FontWeight.ExtraBold, 38, -0.5), headlineMedium = t(28, FontWeight.ExtraBold, 34, -0.4), headlineSmall = t(24, FontWeight.Bold, 30),
    titleLarge = t(22, FontWeight.Bold, 28), titleMedium = t(16, FontWeight.Bold, 22), titleSmall = t(14, FontWeight.Bold, 20),
    bodyLarge = t(16, FontWeight.Normal, 24, 0.2), bodyMedium = t(14, FontWeight.Normal, 20, 0.2), bodySmall = t(12, FontWeight.Normal, 16, 0.2),
    labelLarge = t(14, FontWeight.Bold, 20, 0.2), labelMedium = t(12, FontWeight.Medium, 16, 0.6), labelSmall = t(11, FontWeight.Medium, 16, 0.6),
)

/** Warna "gelembung": kebalikan dari latar (putih di tema gelap, hitam di tema terang). */
data class Bubble(val container: Color, val content: Color)
val LocalBubble = staticCompositionLocalOf { Bubble(Paper, Ink) }

@Composable
fun PocketFriendTheme(dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
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
