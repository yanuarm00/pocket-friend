package id.yanuar.bf01.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.data.Note
import id.yanuar.bf01.svc.Hub
import id.yanuar.bf01.svc.NoteExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure helpers for the v0.4.0 note detail (unit-tested). */
object NoteText {
    data class Line(val atMs: Long, val text: String)

    /** "0:42", "12:05" */
    fun clock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "${s / 60}:${"%02d".format(s % 60)}" }

    /** Spoken-length estimate when the audio is not on the phone (about 150 words a minute). */
    fun estimateMs(transcript: String): Long = (words(transcript) * 400L).coerceAtLeast(if (transcript.isBlank()) 0 else 1000)
    fun words(t: String) = t.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /** Sentence lines with a start time proportional to their place in the text (transcripts have no word timings). */
    fun lines(transcript: String, durationMs: Long): List<Line> {
        val parts = transcript.trim().split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val total = parts.sumOf { it.length }.coerceAtLeast(1); var acc = 0
        return parts.map { p -> Line(durationMs * acc / total, p).also { acc += p.length } }
    }
    fun currentLine(lines: List<Line>, posMs: Long): Int = lines.indexOfLast { it.atMs <= posMs }

    private val cue = Regex("\\b(i need to|need to|have to|remember to|don't forget to|dont forget to|must|should|todo|to-do|to do:)\\b\\s*", RegexOption.IGNORE_CASE)
    private val verb = Regex("^(call|buy|email|book|send|pay|pick up|bring|text|book|check|fix|order|write|finish|cancel|renew)\\b", RegexOption.IGNORE_CASE)

    /** To-do candidates spoken in a note: sentences with a cue ("need to …", "remember to …") or that start with an action verb. */
    fun todos(transcript: String): List<String> = lines(transcript, 0).mapNotNull { l ->
        val s = l.text.trimEnd('.', '!', ' ')
        val m = cue.find(s)
        val t = when { m != null -> s.substring(m.range.last + 1); verb.containsMatchIn(s) -> s; else -> null } ?: return@mapNotNull null
        t.split(Regex(",? (and then|then|because|so that) ")).first().trim().trimEnd(',', '.').takeIf { it.length in 3..80 }?.replaceFirstChar { it.uppercase() }
    }.distinct().take(6)

    /** Default title: the summary's first sentence, else the transcript's, else the date. */
    fun defaultTitle(n: Note): String = (n.summary.ifBlank { n.transcript }).trim().split(Regex("(?<=[.!?])\\s")).firstOrNull()
        ?.trim()?.trimEnd('.')?.take(48)?.takeIf { it.isNotBlank() } ?: "Voice note"
}

/** Note titles live on the phone only (BF has no title field). */
object NoteTitles {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("note_titles", Context.MODE_PRIVATE)
    fun get(ctx: Context, n: Note) = sp(ctx).getString(n.file, null) ?: NoteText.defaultTitle(n)
    fun set(ctx: Context, n: Note, t: String) = sp(ctx).edit().putString(n.file, t.trim()).apply()
}

private val fmtDetail = SimpleDateFormat("EEE d MMM · HH.mm", Locale.US)

/** Live state of a note's player; position is polled while playing. */
class NotePlayer(private val path: String?) {
    var player: android.media.MediaPlayer? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    private fun ensure(): android.media.MediaPlayer? = player ?: path?.let { p -> runCatching {
        android.media.MediaPlayer().apply { setDataSource(p); prepare(); setOnCompletionListener { playing = false; pos = 0 } } }.getOrNull() }
        ?.also { player = it; dur = it.duration.toLong() }
    fun toggle() { val p = ensure() ?: return; if (playing) { p.pause(); playing = false } else { p.start(); playing = true } }
    fun seek(ms: Long) { val p = ensure() ?: return; p.seekTo(ms.toInt()); pos = ms; if (!playing) { p.start(); playing = true } }
    fun tick() { player?.let { if (playing) pos = it.currentPosition.toLong() } }
    fun release() { player?.release(); player = null; playing = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NoteDetail(hub: Hub, n: Note, back: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val audio = remember(n.localPath) { NoteExport.audioFile(n) }
    val pl = remember(n.file, audio) { NotePlayer(audio?.path) }
    DisposableEffect(pl) { onDispose { pl.release() } }
    LaunchedEffect(pl.playing) { while (pl.playing) { pl.tick(); delay(200) } }
    LaunchedEffect(audio) { if (audio != null) withContext(Dispatchers.IO) { runCatching {
        android.media.MediaMetadataRetriever().run { setDataSource(audio.path); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong().also { release() } } }
        .getOrNull()?.let { if (pl.dur == 0L) pl.dur = it } } }
    var title by remember(n.file) { mutableStateOf(NoteTitles.get(ctx, n)) }
    var added by remember(n.file) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var shareMode by remember { mutableIntStateOf(0) }
    fun save() = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { NoteExport.saveToDownloads(ctx, n) } }
        snack.showSnackbar(r.fold({ if (audio != null) NoteExport.SAVED_MSG else NoteExport.SAVED_MSG + " (text only, audio not on phone)" }, { "Couldn't save: ${it.message ?: "unknown error"}" }))
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) save() else scope.launch { snack.showSnackbar("Storage permission is needed to save on this Android version") } }
    fun share(i: Intent?) { if (i == null) scope.launch { snack.showSnackbar("Audio isn't on the phone yet.") }
        else runCatching { ctx.startActivity(Intent.createChooser(i, "Share note")) }.onFailure { scope.launch { snack.showSnackbar("No app to share with") } } }
    fun doShare() {
        when (shareMode) {
            0 -> share(NoteExport.shareTextIntent(n))
            1, 2 -> share(NoteExport.shareAudioIntent(ctx, n) ?: NoteExport.shareTextIntent(n))
        }
        sheet = false
    }
    Box(Modifier.fillMaxSize()) {
        NoteDetailContent(n, title, pl.playing, pl.pos, if (pl.dur > 0) pl.dur else NoteText.estimateMs(n.transcript), audio != null, added,
            back = back, onTitle = { title = it; NoteTitles.set(ctx, n, it) }, onDelete = { confirmDelete = true },
            onPlay = { pl.toggle() }, onSeek = { pl.seek(it) },
            onShare = { sheet = true }, onDownload = {
                if (NoteExport.needsPermission() && androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    perm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save() },
            onAddAll = { list -> scope.launch { withContext(Dispatchers.IO) { list.forEach { hub.db.addTask(it) } }; hub.revision.value++; added = true
                snack.showSnackbar("Added ${list.size} to-dos") } },
            onSaveTranscript = { t -> scope.launch { withContext(Dispatchers.IO) { hub.db.upsertNote(n.file, transcript = t) }; hub.revision.value++ } },
            shareMenu = {})
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
        if (sheet) {
            ModalBottomSheet(onDismissRequest = { sheet = false }) {
                NoteShareSheet(title, audio != null, shareMode, { shareMode = it },
                    onShareApp = { doShare() },
                    onCopy = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("note", NoteExport.text(n)))
                        sheet = false
                        scope.launch { snack.showSnackbar("Copied") }
                    },
                    onDismiss = { sheet = false })
            }
        }
    }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete this note?") },
        text = { Text("It is removed from the phone. The recording on the device is not touched.") },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { withContext(Dispatchers.IO) { hub.db.deleteNoteByFile(n.file) }; hub.revision.value++; back() } }) { Text("Delete", color = Pop.Red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@Composable fun NoteDetailContent(
    n: Note, title: String, playing: Boolean, posMs: Long, durMs: Long, hasAudio: Boolean, todosAdded: Boolean,
    back: () -> Unit = {}, onTitle: (String) -> Unit = {}, onDelete: () -> Unit = {}, onPlay: () -> Unit = {}, onSeek: (Long) -> Unit = {},
    onShare: () -> Unit = {}, onDownload: () -> Unit = {}, onAddAll: (List<String>) -> Unit = {}, onSaveTranscript: (String) -> Unit = {},
    shareMenu: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    var editTitle by remember { mutableStateOf(false) }
    var titleDraft by remember(title) { mutableStateOf(title) }
    var editText by remember { mutableStateOf(false) }
    var textDraft by remember(n.transcript) { mutableStateOf(n.transcript) }
    val lines = remember(n.transcript, durMs) { NoteText.lines(n.transcript, durMs) }
    val todos = remember(n.transcript) { NoteText.todos(n.transcript) }
    val cur = if (playing || posMs > 0) NoteText.currentLine(lines, posMs) else -1
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Sym.ArrowBack, "Back", cs.surfaceContainerHigh, cs.onSurface, back)
            Spacer(Modifier.weight(1f))
            RoundIcon(Sym.Delete, "Delete note", Pop.Red.copy(alpha = .14f), Pop.Red, onDelete)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editTitle) OutlinedTextField(titleDraft, { titleDraft = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.headlineSmall,
                trailingIcon = { TextButton({ editTitle = false; if (titleDraft.isNotBlank()) onTitle(titleDraft) }) { Text("Save") } })
            else Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { editTitle = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp)); Icon(Sym.Edit, "Edit title", Modifier.size(20.dp), tint = cs.onSurfaceVariant)
            }
            Text(fmtDetail.format(Date(n.created)) + " · " + NoteText.clock(durMs), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        // Player: grey play button that turns orange only while playing; progress "0:42 / 2:14"
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (hasAudio) Pop.Orange else cs.surfaceContainerHighest)
                        .clickable(enabled = hasAudio, onClick = onPlay), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Sym.Pause else Sym.Play, if (playing) "Pause" else "Play",
                            Modifier.size(30.dp), tint = if (hasAudio) Color.White else cs.onSurfaceVariant.copy(alpha = .5f))
                    }
                    Spacer(Modifier.width(14.dp))
                    Waveform(n.file, if (durMs > 0) posMs.toFloat() / durMs else 0f, playing, Modifier.weight(1f).height(40.dp))
                }
                Slider(if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f, { onSeek((it * durMs).toLong()) }, enabled = hasAudio,
                    colors = SliderDefaults.colors(thumbColor = Pop.Orange, activeTrackColor = Pop.Orange, inactiveTrackColor = Pop.Orange.copy(alpha = 0.25f)))
                Row { Text("${NoteText.clock(posMs)} / ${NoteText.clock(durMs)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (!hasAudio) Text("Audio still on device", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                Button(onShare, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(Pop.Orange, Color.White)) {
                    Icon(Sym.Share, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Share", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                shareMenu()
            }
            OutlinedButton(onDownload, Modifier.weight(1f).height(52.dp), border = BorderStroke(1.5.dp, cs.outline)) {
                Icon(Sym.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Download", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
        }
        if (n.summary.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("SUMMARY"); Text(n.summary, style = MaterialTheme.typography.bodyLarge)
        }
        if (todos.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("TO-DOS FROM THIS NOTE"); Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(CircleShape).background(Pop.Yellow).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("${todos.size}", color = Pop.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                TextButton({ onAddAll(todos) }, enabled = !todosAdded) { Text(if (todosAdded) "Added" else "Add all", color = if (todosAdded) cs.onSurfaceVariant else Pop.Orange, fontWeight = FontWeight.Bold) }
            }
            todos.forEach { t -> Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (todosAdded) Sym.CheckCircle else Sym.CheckBox, null, Modifier.size(20.dp), tint = if (todosAdded) Pop.Led else cs.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp)); Text(t, style = MaterialTheme.typography.bodyLarge) } } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("TRANSCRIPT"); Spacer(Modifier.weight(1f))
                if (editText) { TextButton({ editText = false; textDraft = n.transcript }) { Text("Cancel") }
                    TextButton({ editText = false; onSaveTranscript(textDraft.trim()) }) { Text("Save", color = Pop.Orange, fontWeight = FontWeight.Bold) } }
                else TextButton({ editText = true }, enabled = n.transcript.isNotBlank()) { Icon(Sym.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
            }
            if (editText) OutlinedTextField(textDraft, { textDraft = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), textStyle = MaterialTheme.typography.bodyLarge)
            else if (lines.isEmpty()) Text("(no transcript yet)", color = cs.onSurfaceVariant)
            else lines.forEachIndexed { i, l ->
                val on = i == cur
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Pop.Yellow else Color.Transparent)
                    .clickable(enabled = hasAudio) { onSeek(l.atMs) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(NoteText.clock(l.atMs), Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium, color = if (on) Pop.Ink else cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(l.text, style = MaterialTheme.typography.bodyLarge, color = if (on) Pop.Ink else cs.onSurface)
                }
            }
        }
    }
}

@Composable private fun SectionLabel(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)

@Composable internal fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: Color, fg: Color, onClick: () -> Unit) =
    Box(Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(24.dp), tint = fg) }

/** Decorative waveform seeded by the file name; bars left of the playhead are filled. */
@Composable private fun Waveform(seed: String, progress: Float, playing: Boolean, modifier: Modifier) {
    val bars = remember(seed) { val r = java.util.Random(seed.hashCode().toLong()); List(36) { .25f + r.nextFloat() * .75f } }
    // Design: warm orange played bars; soft peach unplayed (never black/grey).
    val on = Pop.Orange; val off = Pop.Orange.copy(alpha = 0.28f)
    Canvas(modifier) {
        val w = size.width / bars.size
        bars.forEachIndexed { i, h -> val bh = size.height * h
            drawRoundRect(if ((i + .5f) / bars.size <= progress) on else off, Offset(i * w + w * .2f, (size.height - bh) / 2), Size(w * .6f, bh), CornerRadius(w)) }
    }
}

/** v0.4.0 quiet sync status: no manual Sync button. "Synced · 5 min ago", progress while working, Retry only on failure. */
@Composable fun SyncStatusLine(hub: Hub) {
    val st by hub.recordings.status.collectAsState(); val scope = rememberCoroutineScope()
    val rev by hub.revision.collectAsState()
    val last = remember(rev, st.phase) { hub.prefs.lastRecSync }
    SyncStatusRow(st.phase, st.message, last) { scope.launch { hub.recordings.run(manual = true) } }
}

@Composable fun SyncStatusRow(phase: id.yanuar.bf01.svc.RecordingSync.Phase, message: String, last: Long, now: Long = System.currentTimeMillis(), retry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val failed = phase == id.yanuar.bf01.svc.RecordingSync.Phase.Failed
    val busy = phase == id.yanuar.bf01.svc.RecordingSync.Phase.Connecting || phase == id.yanuar.bf01.svc.RecordingSync.Phase.Downloading || phase == id.yanuar.bf01.svc.RecordingSync.Phase.Transcribing
    val text = when {
        failed -> "Sync failed" + (message.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        busy -> message.ifBlank { "Syncing…" }
        last == 0L -> "Not synced yet"
        else -> "Synced · " + agoLabel(last, now)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Pop.Orange)
        else Icon(if (failed) Sym.Error else Sym.CheckCircle, null, Modifier.size(16.dp), tint = if (failed) Pop.Red else cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = if (failed) Pop.Red else cs.onSurfaceVariant, maxLines = 2)
        if (failed) TextButton(retry) { Text("Retry", color = Pop.Orange, fontWeight = FontWeight.Bold) }
    }
}
