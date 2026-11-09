package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** v0.4.0 bottom bar (mockup v5.13): dark pill with Home / Notes / To-do / Settings + a round orange "Ask BF" face button. */
@Composable fun PopNavBar(screen: Screen, isTab: Boolean, go: (Screen) -> Unit) {
    val items = listOf(Triple(Screen.Beranda, Sym.Home, "Home"), Triple(Screen.Catatan, Sym.Notes, "Notes"),
        Triple(Screen.Tugas, Sym.CheckBox, "To-do"), Triple(Screen.Pengaturan, Sym.Person, "Settings"))
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(64.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Pop.Ink),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (s, icon, label) ->
                val sel = s == screen || (!isTab && s == Screen.Pengaturan)
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Tab) { go(s) }.semantics { contentDescription = label; selected = sel },
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(26.dp), tint = if (sel) Pop.Yellow else Color(0xFFA3A0AE))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val on = screen == Screen.Ngobrol
        Box(Modifier.size(72.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Pop.Orange).clickable(role = Role.Tab) { go(Screen.Ngobrol) }
            .semantics { contentDescription = "Ask"; selected = on }, contentAlignment = Alignment.Center) {
            BfFace(30.dp, BfMood.Senyum, true, color = if (on) Color.White else Pop.Ink)
        }
    }
}
