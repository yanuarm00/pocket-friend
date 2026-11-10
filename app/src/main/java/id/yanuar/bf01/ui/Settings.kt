package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image

/** One Settings row (mockup v5.13 screen 08). */
data class SetRow(val icon: ImageVector, val title: String, val sub: String?, val onClick: () -> Unit,
                  val chip: String? = null, val chipYellow: Boolean = false, val accent: Color? = null, val deviceThumb: Boolean = false,
                  val swatches: Boolean = false, val onChip: (() -> Unit)? = null)

@Composable fun SettingsContent(
    profileName: String, profileSub: String, onProfile: () -> Unit,
    device: List<SetRow>, app: List<SetRow>,
    online: Boolean = false, connecting: Boolean = false, onStatus: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Single page title + LED (no scaffold TopAppBar title on Settings).
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            LedChip(online, connecting, onStatus)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pop.Ink).clickable(onClick = onProfile).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Image(painterResource(DeviceArt.res(DeviceArt.Pose.Happy, false)), null, Modifier.size(46.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profileName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profileSub, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Sym.ChevronRight, null, tint = Pop.Yellow)
        }
        Group("DEVICE"); device.forEach { SettingRow(it) }
        Group("APP"); app.forEach { SettingRow(it) }
    }
}

@Composable private fun Group(t: String) = Text(t, Modifier.padding(top = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)

@Composable private fun SettingRow(r: SetRow) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLowest, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = r.onClick).heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(r.accent ?: cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(r.icon, null, Modifier.size(22.dp), tint = if (r.accent == Pop.Orange) Color.White else if (r.accent != null) Pop.Ink else cs.onSurface) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                r.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (r.deviceThumb) Image(painterResource(DeviceArt.res(DeviceArt.Pose.Happy, false)), null, Modifier.size(36.dp))
            r.chip?.let { c -> Box(Modifier.clip(CircleShape).background(if (r.chipYellow) Pop.Yellow else cs.surfaceContainerHigh)
                .then(if (r.onChip != null) Modifier.clickable(onClick = r.onChip) else Modifier).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(c, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (r.chipYellow) Pop.Ink else cs.onSurface) } }
            if (r.swatches) { Text(AppTheme.label(), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) { listOf(Pop.Orange, Pop.Ink, Pop.Yellow).forEach { Box(Modifier.size(16.dp).clip(CircleShape).background(it)) } } }
            Spacer(Modifier.width(6.dp)); Icon(Sym.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
    }
}
