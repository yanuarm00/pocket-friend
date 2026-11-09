package id.yanuar.bf01.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.R
import kotlinx.coroutines.delay

/**
 * v0.4.0: the ONE place that knows which picture shows the device.
 * To swap the art later (e.g. a new character), replace the `device_art_*` PNGs in res/drawable-nodpi
 * or point these entries at new drawables. Nothing else in the app references the images directly.
 */
object DeviceArt {
    enum class Pose { Happy, Offline, Search, Blank }
    @DrawableRes fun res(pose: Pose, blink: Boolean = false): Int = when (pose) {
        Pose.Happy -> if (blink) R.drawable.device_art_happy_blink else R.drawable.device_art_happy
        Pose.Search -> if (blink) R.drawable.device_art_search_blink else R.drawable.device_art_search
        Pose.Offline -> R.drawable.device_art_offline
        Pose.Blank -> R.drawable.device_art_blank
    }
    /** Poses that have a blink frame. */
    fun blinks(pose: Pose) = pose == Pose.Happy || pose == Pose.Search
}

/**
 * Device art with a gentle float + sway, and a separate floor shadow that shrinks/fades while the device rises.
 * [animate] = false gives a still frame (screenshot tests).
 */
@Composable fun FloatingDevice(pose: DeviceArt.Pose, size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val inf = rememberInfiniteTransition(label = "device")
    val lift by if (animate) inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lift") else remember { mutableFloatStateOf(0f) }
    val sway by if (animate) inf.animateFloat(-2.2f, 2.2f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway") else remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableStateOf(false) }
    if (animate && DeviceArt.blinks(pose)) LaunchedEffect(pose) { while (true) { delay(3600); blink = true; delay(140); blink = false } }
    val rise = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.06f).toPx() }
    Box(modifier.size(size, size * 1.1f), contentAlignment = Alignment.TopCenter) {
        // shadow: its own motion (scale + alpha), not glued to the device transform
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.02f).size(size * 0.62f, size * 0.09f)
            .graphicsLayer { val k = 1f - 0.18f * lift; scaleX = k; scaleY = k; alpha = 0.55f - 0.25f * lift }
            .clip(CircleShape).background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        Image(painterResource(DeviceArt.res(pose, blink)), null, Modifier.size(size)
            .graphicsLayer { translationY = -rise * lift; rotationZ = sway })
    }
}
