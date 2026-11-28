package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.svc.Hub
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* v0.4.12: "To sort" = recordings BF saved as Later (no choice made on the device). Keep as note / Make a to-do / Ask. */

data class ToSortItem(val file: String, val whenLabel: String, val text: String, val busy: Boolean = false)

/** Orange card at the top of Notes. */
@Composable fun ToSortBanner(count: Int, onOpen: () -> Unit) =
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(22.dp), color = Pop.Orange, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("To sort · $count", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                Text(if (count == 1) "1 voice note is waiting for you." else "$count voice notes are waiting for you.", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Sym.ChevronRight, null, Modifier.size(24.dp))
        }
    }

@Composable fun ToSortContent(items: List<ToSortItem>, onKeep: (String) -> Unit, onTodo: (String) -> Unit, onAsk: (String) -> Unit, message: String? = null) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("To sort", if (items.isEmpty()) "All sorted." else "Pick what each voice note becomes.")
        if (message != null) Text(message, color = Pop.Red, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) Text("Nothing here. When you don't pick To-do, Ask or Notes on BF, the recording waits here.", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        items.forEach { it ->
            val ready = it.text.isNotBlank() && !it.text.startsWith("(")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = cardColor(), shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(it.whenLabel, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Text(if (it.text.isBlank()) "Transcribing…" else it.text, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (it.text.isNotBlank() || it.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortButton("Keep as note", Pop.Ink, Color.White, !it.busy, Modifier.weight(1f)) { onKeep(it.file) }
                        SortButton("Make a to-do", Pop.Orange, Color.White, !it.busy && ready, Modifier.weight(1f)) { onTodo(it.file) }
                        SortButton("Ask", Pop.Yellow, Pop.Ink, !it.busy && ready, Modifier.weight(0.6f)) { onAsk(it.file) }
                    }
                    if (it.busy) Text("Working…", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun SortButton(text: String, bg: Color, fg: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.heightIn(min = 48.dp).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.35f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }

@Composable fun ToSortScreen(hub: Hub) {
    val rev by hub.revision.collectAsState(); val scope = rememberCoroutineScope()
    val notes = rememberLoaded(hub, "tosort", rev) { hub.db.notes().filter { it.type == BfProtocol.TYPE_UNSORTED } }
    var busyFile by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val items = (notes ?: emptyList()).map { ToSortItem(it.file, dayLabel(it.created) + " · " + clock.format(Date(it.created)), it.transcript, busyFile == it.file) }
    fun run(file: String, what: suspend () -> Unit) { if (busyFile != null) return; busyFile = file; err = null
        scope.launch { try { what() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { err = "Couldn't finish: " + id.yanuar.bf01.ai.friendlyError(e) } finally { busyFile = null; hub.revision.value++ } } }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ToSortContent(items, onKeep = { f -> hub.keepAsNote(f) }, onTodo = { f -> run(f) { hub.makeTodoFromNote(f) } },
            onAsk = { f -> run(f) { hub.askFromNote(f) } }, message = err)
        Spacer(Modifier.height(PopNavClearance))
    }
}
