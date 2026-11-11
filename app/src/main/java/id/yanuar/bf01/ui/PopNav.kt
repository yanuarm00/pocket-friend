package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Floating pill + Ask FAB over the page background. Blur/glass lives only
 * inside the clipped CircleShape pill — never a full-width band or fade.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable fun PopNavBar(screen: Screen, isTab: Boolean, hazeState: HazeState? = null, go: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Beranda, Sym.Home, "Home"),
        Triple(Screen.Catatan, Sym.Notes, "Notes"),
        Triple(Screen.Tugas, Sym.CheckBox, "To-do"),
        Triple(Screen.Pengaturan, Sym.Person, "Settings"),
    )
    val style = HazeMaterials.ultraThin(Color(0xFF1C1E21))
    val frost = if (hazeState != null) {
        Modifier.hazeEffect(state = hazeState, style = style)
    } else Modifier
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(64.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(CircleShape)
                .then(frost)
                .background(Color(0x551C1E21))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (s, icon, label) ->
                val sel = s == screen || (!isTab && s == Screen.Pengaturan)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Tab) { go(s) }
                        .semantics { contentDescription = label; selected = sel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(26.dp), tint = if (sel) Pop.Yellow else Color(0xFFD0CDD8))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val on = screen == Screen.Ngobrol
        Box(
            Modifier.size(72.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Pop.Orange)
                .clickable(role = Role.Tab) { go(Screen.Ngobrol) }
                .semantics { contentDescription = "Ask"; selected = on },
            contentAlignment = Alignment.Center,
        ) {
            BfFace(30.dp, BfMood.Senyum, true, color = if (on) Color.White else Pop.Ink)
        }
    }
}

val PopNavClearance = 96.dp
