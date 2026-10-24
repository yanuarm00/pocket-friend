package id.yanuar.bf01.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import id.yanuar.bf01.R
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.svc.Hub

/** Status sambungan BF untuk pil di header. */
enum class BfStatus(@androidx.annotation.StringRes val label: Int, val mood: BfMood, val filled: Boolean) {
    On(R.string.pill_on, BfMood.Senyum, true), Menyambung(R.string.pill_connecting, BfMood.Datar, false), Off(R.string.pill_off, BfMood.Sedih, false)
}

@Composable fun rememberBfStatus(hub: Hub): BfStatus {
    val linked by hub.linked.collectAsState(); val st by hub.ble.state.collectAsState()
    return when { !linked -> BfStatus.Off; st == BleManager.State.READY -> BfStatus.On; else -> BfStatus.Menyambung }
}

/** Pil status mengambang di header: ON = blok solid + BF senyum, OFF = garis luar + BF sedih, menyambung = garis luar berdenyut. */
@Composable fun StatusPill(status: BfStatus, compact: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val on = status == BfStatus.On
    val ink = if (on) cs.inverseOnSurface else cs.onSurface
    val pulse = if (status == BfStatus.Menyambung) rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.45f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value else 1f
    val label = stringResource(status.label); val cd = stringResource(R.string.status_cd, label)
    Surface(onClick = onClick, shape = CircleShape, color = if (on) cs.inverseSurface else Color.Transparent,
        border = if (on) null else BorderStroke(1.5.dp, if (status == BfStatus.Off) cs.outline else cs.onSurfaceVariant),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = cd }) {
        Row(Modifier.padding(start = 10.dp, end = if (compact) 10.dp else 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BfFace(24.dp, status.mood, status.filled, Modifier.alpha(pulse), color = ink)
            if (!compact) { Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1) }
        }
    }
}

/** Status panel that drops down from the top, under the header pill. Closes on swipe up or a tap outside. */
@Composable fun StatusSheet(hub: Hub, status: BfStatus, visible: Boolean, dismiss: () -> Unit) {
    val info by hub.ble.info.collectAsState(); val linked by hub.linked.collectAsState()
    val cs = MaterialTheme.colorScheme
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = dismiss)
            .semantics { contentDescription = "status_scrim" })
    }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) { drag = 0f }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.animation.AnimatedVisibility(visible, Modifier.fillMaxWidth(),
        enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut()) {
        Surface(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, start = 12.dp, end = 12.dp)
            .graphicsLayer { translationY = drag }
            .draggable(rememberDraggableState { d -> drag = (drag + d).coerceAtMost(0f) }, Orientation.Vertical,
                onDragStopped = { if (drag < -with(density) { 56.dp.toPx() }) dismiss() else drag = 0f })
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BfFace(52.dp, status.mood, status.filled, color = cs.onSurface)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        DotText(stringResource(when (status) { BfStatus.On -> R.string.status_connected; BfStatus.Menyambung -> R.string.status_connecting; BfStatus.Off -> R.string.status_disconnected }).uppercase(), Dot.S, 1.1f)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(when (status) { BfStatus.On -> R.string.status_on_note; BfStatus.Menyambung -> R.string.status_connecting_note
                            BfStatus.Off -> R.string.status_off_note }), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                val b = info?.batt?.takeIf { it >= 0 && status == BfStatus.On }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.battery), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(b?.let { "$it%" } ?: "–", style = MaterialTheme.typography.titleLarge)
                }
                info?.takeIf { status == BfStatus.On }?.let { Text(stringResource(R.string.firmware_x, it.fw), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(stringResource(R.string.reconnect), Modifier.weight(1f), primary = status != BfStatus.On, icon = Sym.Bluetooth) { hub.reconnect(); dismiss() }
                    OutlinedButton({ hub.disconnectAll(); dismiss() }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = linked) {
                        Icon(Sym.BluetoothOff, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.disconnect), maxLines = 1) }
                }
                // Grab handle: swipe up to close.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(cs.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)) }
            }
        }
    }
}

/** Catatan bantu seragam: ⓘ + teks kecil abu, maks 2 baris. [detail] panjang dibuka di sheet. */
@Composable fun HelpNote(text: String, detail: String? = null, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (detail != null) Modifier.clickable { open = true } else Modifier).padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top) {
        Icon(Sym.Info, null, Modifier.padding(top = 1.dp).size(16.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) { Spacer(Modifier.width(8.dp)); Text("Details", style = MaterialTheme.typography.labelMedium, color = cs.onSurface) }
    }
    if (open && detail != null) InfoSheet(text, detail) { open = false }
}

/** Tombol ⓘ yang membuka sheet berisi penjelasan. */
@Composable fun InfoButton(title: String, detail: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton({ open = true }) { Icon(Sym.Info, "Info: $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (open) InfoSheet(title, detail) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun InfoSheet(title: String, detail: String, dismiss: () -> Unit) {
    ModalBottomSheet(dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Sym.Info, null); Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium) }
            Text(detail, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Baris kategori berikon (Setelan & sub-halaman). */
@Composable fun NavRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = sub?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Box(Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onSecondaryContainer) } },
        trailingContent = { Icon(Sym.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp))
}

/** Kartu berisi daftar NavRow dengan pemisah. */
@Composable fun NavCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}
@Composable fun NavDivider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
