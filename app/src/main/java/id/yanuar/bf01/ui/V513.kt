package id.yanuar.bf01.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send

/* v0.4.2: screens from the v5.13 mockups (handoff/v040_round2/devy_app/pop_OG_v5_13_*). Stateless contents so they can be
   screenshot-tested; the *Screen functions in Screens.kt feed them real data. */

private val Card22 = RoundedCornerShape(22.dp)
@Composable private fun dark() = MaterialTheme.colorScheme.background.let { it.red * 0.3f + it.green * 0.59f + it.blue * 0.11f < 0.4f }
@Composable internal fun cardColor() = if (dark()) Pop.NightCard else Color.White
@Composable internal fun inkCard() = if (dark()) Color(0xFF3A3D43) else Pop.Ink

@Composable fun PfHeader(title: String, sub: String, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.4).sp))
            Text(sub, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }

@Composable fun PfBackHeader(title: String, sub: String, back: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cardColor()).clickable(onClick = back), contentAlignment = Alignment.Center) {
            Icon(Sym.ArrowBack, "Back", Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }

@Composable fun PfSearch(value: String, onChange: (String) -> Unit, hint: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(cardColor()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.material.icons.Icons.Default.Search, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(Pop.Orange), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable fun PfChip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (selected) inkCard() else cardColor(); val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(15.dp), tint = fg); Spacer(Modifier.width(5.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable fun SectionCap(t: String) = Text(t.uppercase(), Modifier.padding(start = 2.dp, top = 6.dp),
    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable fun Tag(t: String, bg: Color = Pop.Yellow, fg: Color = Pop.Ink) = Text(t, Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 1.dp),
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg)

@Composable fun WaveBars(seed: String, color: Color, modifier: Modifier = Modifier) = Row(modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
    var h = seed.hashCode()
    repeat(14) { h = h * 1103515245 + 12345; val f = 0.3f + ((h ushr 16) and 0xff) / 255f * 0.7f
        Box(Modifier.width(4.dp).fillMaxHeight(f).clip(CircleShape).background(color)) }
}

@Composable fun PfOrangeButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(Pop.Orange, Color.White)) {
        Text(text, fontWeight = FontWeight.Bold) }

/* ---------------- Notes ---------------- */
data class NoteRowUi(val file: String, val title: String, val sub: String, val day: String, val isNew: Boolean = false,
                     val transcribing: Boolean = false, val playable: Boolean = false)

@Composable fun NotesListContent(rows: List<NoteRowUi>, q: String, onQ: (String) -> Unit, filter: Int, onFilter: (Int) -> Unit,
                                 syncLabel: String?, syncDone: Int, syncTotal: Int, playing: String?, onOpen: (String) -> Unit, onPlay: (String) -> Unit,
                                 onShowHow: () -> Unit = {}, syncLine: @Composable () -> Unit = {}, onOpenSearch: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Notes", if (rows.isEmpty() && q.isEmpty() && filter == 0) "Everything BF heard." else "Everything BF heard.")
        if (rows.isEmpty() && q.isEmpty() && filter == 0 && syncLabel == null) { EmptyState(bfBadge = true, icon = null, title = "No notes yet",
            body = "Hold the button on BF to save\nyour first note.", action = "Show me how", onAction = onShowHow); return@Column }
        if (onOpenSearch != null) {
            Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(cardColor()).clickable(onClick = onOpenSearch).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("Search notes, transcripts, to-dos…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else PfSearch(q, onQ, "Search notes, transcripts, to-dos…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PfChip("All", filter == 0) { onFilter(0) }; PfChip("Voice", filter == 1, Sym.Mic) { onFilter(1) }
            PfChip("Text", filter == 2) { onFilter(2) }; PfChip("Ideas", filter == 3) { onFilter(3) }
        }
        if (syncLabel != null) Surface(shape = Card22, color = inkCard(), contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.CloudSync, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                    Text(syncLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (syncTotal > 0) Text("$syncDone of $syncTotal", style = MaterialTheme.typography.titleSmall, color = Pop.Yellow)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator({ if (syncTotal > 0) syncDone / syncTotal.toFloat() else 0.3f }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Pop.Orange, trackColor = Color.White.copy(alpha = 0.18f), drawStopIndicator = {})
            }
        } else syncLine()
        if (rows.isEmpty()) Text("Nothing matches.", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        var lastDay = ""
        rows.forEach { r ->
            if (r.day != lastDay) { SectionCap(r.day); lastDay = r.day }
            val hl = r.transcribing
            Surface(Modifier.fillMaxWidth().clickable { onOpen(r.file) }, shape = Card22, color = if (hl) Pop.Yellow else cardColor(), shadowElevation = if (hl) 0.dp else 1.dp,
                contentColor = if (hl) Pop.Ink else cs.onSurface) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isPlaying = playing == r.file
                    Box(Modifier.size(38.dp).clip(CircleShape).background(when { hl -> Color(0x22FF6A1A).copy(alpha = 0.25f); r.isNew -> Color(0xFFF2D6C8); else -> cs.surfaceVariant.copy(alpha = if (dark()) 1f else 0.6f) })
                        .clickable(enabled = r.playable) { onPlay(r.file) }, contentAlignment = Alignment.Center) {
                        Icon(if (hl) Sym.Edit else if (isPlaying) Sym.Pause else Sym.Play, if (isPlaying) "Pause" else "Play", Modifier.size(18.dp),
                            tint = if (hl) Pop.Ink else if (r.isNew) Pop.OrangeDeep else cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hl) "Transcribing…" else r.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            if (r.isNew) { Spacer(Modifier.width(8.dp)); Tag("NEW") }
                        }
                        Text(r.sub, style = MaterialTheme.typography.bodyMedium, color = if (hl) Pop.Ink.copy(alpha = 0.7f) else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    WaveBars(r.file, if (hl) Pop.OrangeDeep.copy(alpha = 0.7f) else Color(0xFFF2C744))
                }
            }
        }
    }
}

@Composable fun EmptyState(bfBadge: Boolean, icon: ImageVector?, title: String, body: String, action: String, onAction: () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(if (bfBadge) inkCard() else Pop.Yellow), contentAlignment = Alignment.Center) {
                if (bfBadge) Text("bf", color = Color.White, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                else if (icon != null) Icon(icon, null, Modifier.size(40.dp), tint = Pop.Ink)
            }
            if (bfBadge) Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(3.dp).clip(CircleShape).background(Pop.Orange))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        PfOrangeButton(action, onClick = onAction)
    }

/* ---------------- To-do ---------------- */
data class TodoRowUi(val id: Long, val title: String, val sub: String, val done: Boolean, val synced: Boolean = false, val deleting: Boolean = false)

@Composable fun TodoListContent(rows: List<TodoRowUi>, today: Int, reminders: Int, done: Int, onAdd: () -> Unit, onToggle: (Long) -> Unit,
                                onOpen: (Long) -> Unit, onUndo: (Long) -> Unit, devName: String, extra: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme; val left = rows.count { !it.done && !it.deleting }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PfHeader("To-do", if (rows.isEmpty()) "Nothing yet." else "$left left today.") {
            if (rows.isNotEmpty()) Box(Modifier.size(52.dp).clip(CircleShape).background(inkCard()).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(Sym.Add, "Add a to-do", tint = Pop.Yellow, modifier = Modifier.size(28.dp)) }
        }
        if (rows.isEmpty()) { EmptyState(false, Sym.CheckBox, "All clear", "Tell $devName \u201cremind me to…\u201d\nor add one here.", "+ Add a to-do", onAdd); extra(); return@Column }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(Modifier.weight(1f), "$today", "Today", Pop.Yellow, Pop.Ink)
            Stat(Modifier.weight(1f), "$reminders", "Reminders", inkCard(), Color.White)
            Stat(Modifier.weight(1f), "$done", "Done", Pop.Orange, Color.White)
        }
        Spacer(Modifier.height(2.dp))
        rows.forEach { r ->
            val faded = r.done || r.deleting
            Surface(Modifier.fillMaxWidth().clickable { onOpen(r.id) }, shape = Card22, color = if (r.deleting) cs.surfaceVariant.copy(alpha = 0.6f) else cardColor(), shadowElevation = if (faded) 0.dp else 1.dp) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (r.done) Pop.Yellow.copy(alpha = 0.6f) else Color.Transparent)
                        .border(2.dp, if (r.done) Pop.Yellow else if (r.deleting) cs.outlineVariant else Pop.Orange, RoundedCornerShape(7.dp)).clickable { onToggle(r.id) })
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (faded) TextDecoration.LineThrough else null, color = if (faded) cs.onSurfaceVariant else cs.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!faded && r.sub.isNotEmpty()) { Icon(Sym.Notifications, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(4.dp)) }
                            Text(if (r.deleting) "Deleting · syncing to $devName" else r.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                            if (r.synced && !faded) { Spacer(Modifier.width(6.dp)); Tag("✓ Synced to $devName", Color(0xFFDDF3E2), Color(0xFF2E7D46)) }
                        }
                    }
                    if (r.deleting) Text("Undo", Modifier.clickable { onUndo(r.id) }.padding(8.dp), color = Pop.Orange, fontWeight = FontWeight.Bold)
                }
            }
        }
        Surface(shape = Card22, color = Pop.Yellow, contentColor = Pop.Ink) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) { Text("bf", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Tip: hold the BF screen on a task to mark it done.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        extra()
    }
}

@Composable private fun Stat(m: Modifier, n: String, label: String, bg: Color, fg: Color) =
    Surface(m.fillMaxHeight(), shape = RoundedCornerShape(22.dp), color = bg, contentColor = fg) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

@Composable fun TodoDetailContent(title: String, from: String, quote: String, next: String, repeat: String, remind: String, devName: String, synced: String,
                                  history: String, done: Boolean, back: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit,
                                  onNext: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PfBackHeader("To-do", from, back) { Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceVariant.copy(alpha = 0.6f)).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
            Icon(Sym.Edit, "Edit", Modifier.size(16.dp)) } }
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 2.dp).size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (done) Pop.Yellow else Color.Transparent).border(2.dp, cs.onSurface, RoundedCornerShape(7.dp)).clickable(onClick = onToggle))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), textDecoration = if (done) TextDecoration.LineThrough else null)
                    if (quote.isNotBlank()) Text("\u201c…$quote\u201d", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        SectionCap("Details")
        DetailRow(Sym.CalendarToday, Pop.Yellow, Pop.Ink, "Next", next, onClick = onNext)
        DetailRow(Sym.Refresh, Pop.Orange, Color.White, "Repeat", repeat)
        DetailRow(Sym.Notifications, cs.surfaceVariant, cs.onSurface, "Remind", remind)
        DetailRow(Sym.DeveloperBoard, cs.surfaceVariant, cs.onSurface, "On $devName", synced, tag = if (synced.startsWith("Synced")) "Synced" else null)
        if (history.isNotBlank()) { SectionCap("History"); Text(history, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp)) }
        Spacer(Modifier.height(6.dp))
        PfOrangeButton(if (done) "Mark not done" else "Mark done", Modifier.fillMaxWidth(), onToggle)
        TextButton(onDelete, Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Delete", color = Pop.Red, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun DetailRow(icon: ImageVector, bg: Color, fg: Color, title: String, sub: String, tag: String? = null, onClick: (() -> Unit)? = null) =
    Surface(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, bg, fg, 40.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tag != null) Tag(tag, Color(0xFFDDF3E2), Color(0xFF2E7D46))
            else if (onClick != null) Icon(Sym.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

/* ---------------- Ask BF ---------------- */
/** Short persona chip label — never mid-word truncate. */
fun shortPersonaChip(raw: String, fallback: String = "Calm buddy"): String {
    val line = raw.lineSequence().firstOrNull()?.trim().orEmpty()
    if (line.isBlank()) return fallback
    // Prefer a known short vibe label if the saved persona is a long system prompt.
    if (line.length > 22 || line.startsWith("You are", true) || line.startsWith("Kamu ", true)) return fallback
    if (line.length <= 16) return line
    val cut = line.take(16)
    val at = cut.lastIndexOf(' ')
    return if (at >= 6) cut.substring(0, at) else fallback
}

@Composable fun AskHeader(aiName: String, persona: String, onHistory: () -> Unit, onPersona: () -> Unit) {
    val chip = shortPersonaChip(persona)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
            Text("bf", color = Color.White, fontWeight = FontWeight.Bold)
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(7.dp).clip(CircleShape).background(Pop.Orange))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Ask $aiName", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Searches your notes first · on phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(cardColor()).clickable(onClick = onHistory), contentAlignment = Alignment.Center) {
            Icon(Sym.RestartAlt, "Chat history", Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(CircleShape).background(Color(0xFFFFE3D2)).clickable(onClick = onPersona)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Sym.AutoAwesome, null, Modifier.size(14.dp), tint = Pop.OrangeDeep)
            Spacer(Modifier.width(4.dp))
            Text(chip, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Pop.OrangeDeep, maxLines = 1)
        }
    }
}

@Composable fun RemembersCard(aiName: String, items: List<String>, open: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) =
    Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Neurology, null, Modifier.size(20.dp), tint = Pop.OrangeDeep); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("$aiName remembers", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${items.size} things · used to answer better", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Edit", Modifier.clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 6.dp), color = Pop.Orange, fontWeight = FontWeight.Bold)
                Icon(Sym.ExpandMore, null)
            }
            if (open && items.isNotEmpty()) Text(
                items.joinToString(" · "),
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)).padding(12.dp),
                style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable fun AskLanding(suggestions: List<Pair<ImageVector, String>>, onPick: (String) -> Unit) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Ask me anything.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text("I'll look in your notes and to-dos first.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        suggestions.forEach { (ic, t) ->
            Surface(Modifier.fillMaxWidth().clickable { onPick(t) }, shape = RoundedCornerShape(18.dp), color = cardColor(), shadowElevation = 1.dp) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, Modifier.size(16.dp), tint = Pop.Orange); Spacer(Modifier.width(10.dp))
                    Text(t, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
            }
        }
    }

@Composable fun AskInput(value: String, onChange: (String) -> Unit, hint: String, onSend: () -> Unit, modifier: Modifier = Modifier) =
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val cs = MaterialTheme.colorScheme
        Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape).background(cardColor()).padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant)
            BasicTextField(value, onChange, maxLines = 3, textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(Pop.Orange), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(52.dp).clip(CircleShape).background(Pop.Orange).clickable(onClick = onSend), contentAlignment = Alignment.Center) {
            Icon(if (value.isBlank()) Sym.Mic else androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
    }

/* ---------------- Chat history ---------------- */
data class ChatRowUi(val id: String, val title: String, val sub: String, val time: String, val day: String, val unread: Boolean = false)

@Composable fun ChatHistoryContent(rows: List<ChatRowUi>, q: String, onQ: (String) -> Unit, back: () -> Unit, onOpen: (String) -> Unit, onClear: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PfBackHeader("Chat history", "Ask BF · kept on this phone", back) {
            if (onClear != null) Text("Clear all", Modifier.clickable(onClick = onClear).padding(8.dp), color = Pop.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
        PfSearch(q, onQ, "Search chats")
        if (rows.isEmpty()) Text(if (q.isEmpty()) "No chats yet. Tap the top button on BF, or ask here." else "Nothing matches.", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        var last = ""
        rows.forEach { r ->
            if (r.day != last) { SectionCap(r.day); last = r.day }
            Surface(Modifier.fillMaxWidth().clickable { onOpen(r.id) }, shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Sym.Chat, cs.surfaceVariant.copy(alpha = 0.6f), cs.onSurface, 38.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            if (r.unread) { Spacer(Modifier.width(6.dp)); Tag("NEW") } }
                        Text(r.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(r.time, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}



/* ---------------- Search (v5.13) ---------------- */
@Composable fun SearchContent(q: String, onQ: (String) -> Unit, filter: Int, onFilter: (Int) -> Unit,
                              recent: List<String>, onRecent: (String) -> Unit, onClearRecent: (String) -> Unit,
                              results: List<Pair<String, String>>, onOpen: (String) -> Unit, back: () -> Unit,
                              suggestions: List<String> = listOf("this week", "#work", "to-dos")) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cardColor()).clickable(onClick = back), contentAlignment = Alignment.Center) {
                Icon(Sym.ArrowBack, "Back", Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { PfSearch(q, onQ, "Search notes…") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PfChip("All", filter == 0) { onFilter(0) }
            PfChip("Voice notes", filter == 1, Sym.Mic) { onFilter(1) }
            PfChip("Text", filter == 2) { onFilter(2) }
        }
        if (q.isBlank()) {
            if (recent.isNotEmpty()) {
                SectionCap("Recent searches")
                recent.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(Card22).background(cardColor()).clickable { onRecent(r) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Sym.RestartAlt, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                        Text(r, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("✕", Modifier.clickable { onClearRecent(r) }.padding(6.dp), color = cs.onSurfaceVariant)
                    }
                }
            }
            Text("Try", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { t -> PfChip(t, false) { onQ(t); onRecent(t) } }
            }
        } else if (results.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing found", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Text("Try another word, or a #tag.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            SectionCap("${results.size} results")
            results.forEach { (id, line) ->
                Surface(Modifier.fillMaxWidth().clickable { onOpen(id) }, shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                    Text(line, Modifier.padding(14.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/* ---------------- Say Hi (v5.13) ---------------- */
data class SayHiRow(val id: Int, val label: String, val sub: String, val logo: String)

@Composable fun SayHiContent(devName: String, rows: List<SayHiRow>, selected: Int, qr: @Composable () -> Unit,
                             onSelect: (Int) -> Unit, onEdit: (Int) -> Unit, onAdd: () -> Unit, onShow: () -> Unit, back: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfBackHeader("Say Hi", "QR profiles people scan from $devName", back)
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) { qr() }
                Spacer(Modifier.height(12.dp))
                val cur = rows.getOrNull(selected)
                Text(if (cur != null) "On $devName now: ${cur.label}" else "Pick a profile to show on $devName",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PfOrangeButton("Show on $devName", Modifier.fillMaxWidth(), onShow)
            }
        }
        SectionCap("My profiles")
        rows.forEach { r ->
            val on = r.id == selected
            Surface(Modifier.fillMaxWidth().clickable { onSelect(r.id) }, shape = Card22, color = cardColor(),
                border = if (on) BorderStroke(2.dp, Pop.Orange) else null, shadowElevation = if (on) 0.dp else 1.dp) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoChip(r.logo); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) Pop.Orange else cs.outlineVariant, CircleShape)
                        .background(if (on) Pop.Orange else Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    Icon(Sym.Edit, "Edit", Modifier.size(18.dp).clickable { onEdit(r.id) }, tint = cs.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onAdd, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) {
            Icon(Sym.Add, null); Spacer(Modifier.width(8.dp)); Text("+ Add profile", fontWeight = FontWeight.Bold) }
    }
}

/* ---------------- Firmware update (v5.13) ---------------- */
@Composable fun FwUpdateContent(devName: String, installed: String, available: String?, sizeLabel: String?, notes: List<String>,
                                batt: Int?, phase: String, progress: Float, steps: List<Pair<String, Boolean>>,
                                onUpdate: () -> Unit, onCheck: () -> Unit, busy: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Software Update", "$devName · BF-01")
        Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Pop.Mist), contentAlignment = Alignment.Center) {
                    Icon(Sym.DeveloperBoard, null, tint = Pop.Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(devName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Installed: $installed", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                if (batt != null && batt >= 0) Tag("$batt%", Pop.Yellow, Pop.Ink)
            }
        }
        if (busy || phase.isNotBlank()) {
            Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(phase.ifBlank { "Working…" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Pop.Orange, trackColor = Pop.Mist, drawStopIndicator = {})
                    steps.forEach { (label, done) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(if (done) Pop.Orange else Color.Transparent)
                                .border(2.dp, if (done) Pop.Orange else cs.outlineVariant, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (done) cs.onSurface else cs.onSurfaceVariant)
                        }
                    }
                }
            }
        } else if (available != null) {
            Surface(shape = Card22, color = cardColor(), shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New version $available" + (sizeLabel?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    notes.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            PfOrangeButton("Update $devName", Modifier.fillMaxWidth(), onUpdate)
            TextButton(onCheck, Modifier.fillMaxWidth()) { Text("Check again", color = Pop.Orange, fontWeight = FontWeight.Bold) }
        } else {
            Text("$devName is up to date.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            TextButton(onCheck, Modifier.fillMaxWidth()) { Text("Check again", color = Pop.Orange, fontWeight = FontWeight.Bold) }
        }
    }
}

/* ---------------- Onboarding (v5.13) ---------------- */
@Composable fun OnboardWelcome(onStart: () -> Unit, onHave: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(Pop.Ink), contentAlignment = Alignment.Center) {
            Text("☺", color = Pop.Yellow, style = MaterialTheme.typography.displaySmall) }
        Text("Meet your pocket friend.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text("Talk, note and remember, right from your pocket.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        listOf(Sym.Mic to "Voice notes that sort themselves", Sym.Notifications to "Reminders that ring on the device", Sym.CloudSync to "Works offline, syncs later").forEach { (ic, t) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(ic, Pop.Yellow, Pop.Ink, 40.dp); Spacer(Modifier.width(12.dp))
                Text(t, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Get started", Modifier.fillMaxWidth(), onStart)
        TextButton(onHave, Modifier.fillMaxWidth()) { Text("I already have one", color = Pop.Orange, fontWeight = FontWeight.Bold) }
    }

@Composable fun OnboardName(devName: String, onName: (String) -> Unit, onNext: () -> Unit) {
    var n by remember { mutableStateOf(devName) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Name your friend", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Text("This shows on the device and in the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        PfSearch(n, { n = it; onName(it) }, "e.g. Alex's BF")
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Continue", Modifier.fillMaxWidth()) { onName(n.trim().ifBlank { "BF" }); onNext() }
    }
}

@Composable fun OnboardDone(devName: String, onHome: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(36.dp)).background(Pop.Mist), contentAlignment = Alignment.Center) {
            Icon(Sym.DeveloperBoard, null, Modifier.size(64.dp), tint = Pop.Ink) }
        Text("$devName is ready.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text("Say hi! Hold the button and talk.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        listOf("Hold to record a note", "\"Remind me at 2\" sets a reminder").forEach { t ->
            Surface(shape = RoundedCornerShape(18.dp), color = cardColor(), shadowElevation = 1.dp) {
                Text(t, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall) }
        }
        Spacer(Modifier.weight(1f))
        PfOrangeButton("Go to Home", Modifier.fillMaxWidth(), onHome)
    }

/** "TODAY" / "YESTERDAY" / "MON" / "3 NOV" for list section headers. */
fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val z = java.util.Calendar.getInstance(); z.timeInMillis = now
    z.set(java.util.Calendar.HOUR_OF_DAY, 0); z.set(java.util.Calendar.MINUTE, 0); z.set(java.util.Calendar.SECOND, 0); z.set(java.util.Calendar.MILLISECOND, 0)
    val d0 = z.timeInMillis; val day = 86_400_000L
    return when { ms >= d0 -> "Today"; ms >= d0 - day -> "Yesterday"
        ms >= d0 - 6 * day -> java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(java.util.Date(ms))
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(java.util.Date(ms)) }
}
