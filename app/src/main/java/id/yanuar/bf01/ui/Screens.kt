package id.yanuar.bf01.ui
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async

import androidx.compose.foundation.lazy.items
import android.annotation.SuppressLint
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.shape.CircleShape
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import id.yanuar.bf01.ai.UsageReports
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import id.yanuar.bf01.R
import id.yanuar.bf01.ai.ModeCepat
import id.yanuar.bf01.ai.QwenStt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.BfService
import id.yanuar.bf01.svc.Hub
import id.yanuar.bf01.svc.PairingReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

private val fmt = SimpleDateFormat("d MMM HH.mm", Locale("id", "ID"))
private val fmtClock = SimpleDateFormat("HH.mm", Locale.US)
private val fmtDate = SimpleDateFormat("EEE, dd/MM/yy", Locale.US)
private val fmtNote = SimpleDateFormat("dd/MM HH.mm", Locale.US)


@SuppressLint("MissingPermission")
@Composable fun PairingScreen(hub: Hub, done: () -> Unit) {
    val ctx = LocalContext.current; var code by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }; var msg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        @Suppress("DEPRECATION")
        val sr: ScanResult? = r.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        val dev = sr?.device ?: return@rememberLauncherForActivityResult
        hub.prefs.deviceAddress = dev.address; if (hub.prefs.deviceName.isBlank()) hub.prefs.deviceName = sr.scanRecord?.deviceName ?: ""
        if (Build.VERSION.SDK_INT >= 31) runCatching { ctx.getSystemService(CompanionDeviceManager::class.java).startObservingDevicePresence(dev.address) }
        PairingReceiver.pendingCode = runCatching { BfProtocol.passkeyString(code) }.getOrNull()
        hub.ble.bond(dev.address)
        hub.prefs.linkEnabled = true; hub.linked.value = true; BfService.start(ctx); hub.ble.connect(dev.address)
        msg = "Picked ${hub.prefs.deviceName}. If Android asks for a PIN/passkey, type ${PairingReceiver.pendingCode ?: "00 + code"}."
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("1. Turn on BF and open its Pairing screen. It shows a 4-digit code.")
        Text("2. Enter the code here, then pick BF-01.")
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Code on BF's screen") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (code.length == 4) Text("Passkey: ${BfProtocol.passkeyString(code)}")
        Spacer(Modifier.height(8.dp))
        Button(enabled = code.length == 4, onClick = {
            val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
            val req = AssociationRequest.Builder().addDeviceFilter(BluetoothLeDeviceFilter.Builder()
                .setNamePattern(Pattern.compile("BF-01-.*")).build()).setSingleDevice(false).build()
            @Suppress("DEPRECATION")
            cdm.associate(req, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(s: IntentSender) { launcher.launch(IntentSenderRequest.Builder(s).build()) }
                override fun onFailure(e: CharSequence?) { msg = "Search failed: $e" }
            }, null)
        }) { Text("Find & connect BF-01") }
        if (msg.isNotEmpty()) Text(msg)
        Spacer(Modifier.height(12.dp))
        SendToBf(hub)
        OutlinedButton(done, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun CatatanScreen(hub: Hub) {
    val rev by hub.revision.collectAsState(); val notes = rememberLoaded(hub, "notes", rev) { hub.db.notes() }
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // Filter hanya dihitung saat input/kumpulan data berubah, bukan tiap ketikan karakter lain direkomposisi.
    val shown = remember(notes, q) { (notes ?: emptyList()).filter { it.type == "NOTE" }.filter { q.isBlank() || (it.transcript + it.summary + it.tags + it.file).contains(q, true) } }
    // v0.3.9.8.9: tap a card to read the whole transcript (cards stay at 3 lines)
    var openFile by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val openNote = openFile?.let { f -> notes?.firstOrNull { it.file == f } }
    if (openNote != null) {
        androidx.activity.compose.BackHandler { openFile = null }
        NoteDetail(hub, openNote) { openFile = null }; return
    }
    val st by hub.recordings.status.collectAsState(); var how by remember { mutableStateOf(false) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }; var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val busy = st.phase == id.yanuar.bf01.svc.RecordingSync.Phase.Downloading || st.phase == id.yanuar.bf01.svc.RecordingSync.Phase.Transcribing || st.phase == id.yanuar.bf01.svc.RecordingSync.Phase.Connecting
    val dev = hub.prefs.deviceName.ifBlank { "BF" }; val seen = remember { hub.prefs.lastRecSync }
    val rows = shown.filter { n -> when (filter) { 1 -> n.localPath.isNotEmpty(); 2 -> n.localPath.isEmpty(); 3 -> n.tags.contains("idea", true); else -> true } }.map { n ->
        val tag = n.tags.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() }
        NoteRowUi(n.file, n.summary.ifBlank { n.transcript.take(40).ifBlank { "Voice note" } },
            if (n.transcript.isBlank() && n.localPath.isEmpty()) "Still on $dev · syncs soon" else fmtClock.format(Date(n.created)) + (tag?.let { " · #$it" } ?: ""),
            dayLabel(n.created), isNew = !n.processed || n.created > seen, transcribing = n.transcript.isBlank() && n.localPath.isNotEmpty(), playable = n.localPath.isNotEmpty())
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        NotesListContent(rows, q, { q = it }, filter, { filter = it }, if (busy) st.message.ifBlank { "Syncing from $dev…" } else null, st.done, st.total, playing,
            onOpen = { openFile = it }, onPlay = { f -> val n = notes?.firstOrNull { it.file == f } ?: return@NotesListContent
                player?.release(); player = null
                if (playing == f) playing = null else { playing = f
                    player = runCatching { android.media.MediaPlayer().apply { setDataSource(n.localPath); setOnCompletionListener { it.release(); player = null; playing = null }; prepare(); start() } }.getOrNull()
                    if (player == null) playing = null } },
            onShowHow = { how = true }, syncLine = { SyncStatusLine(hub) }, onOpenSearch = { hub.openScreen?.invoke(Screen.Search) })
        Spacer(Modifier.height(PopNavClearance))
    }
    if (how) AlertDialog({ how = false }, title = { Text("Save a note") }, text = { Text("Hold the top button on $dev, speak, then let go and pick NOTE. It shows up here with a transcript once $dev syncs.") },
        confirmButton = { TextButton({ how = false }) { Text("Got it") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TugasScreen(hub: Hub) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val rev by hub.revision.collectAsState(); var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var calOk by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calOk = it; r++ }
    // Muat data di latar belakang (IO) supaya buka tab & mencentang tugas tidak terasa jeda.
    val d = rememberLoaded(hub, "tugas", rev, r) { hub.db.tasks() to id.yanuar.bf01.svc.CalendarSource.todayLines(ctx) }
    val agenda = d?.second
    // Tugas yang baru digeser (menunggu "Undo"): disembunyikan dulu, dihapus dari DB setelah snackbar selesai.
    val pending = remember { mutableStateListOf<Long>() }
    val tasks = d?.first?.filter { it.id !in pending }
    var t by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }; var adding by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sentAt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val cs = MaterialTheme.colorScheme
    // v0.4.0: to-dos sync to the device the moment they are saved; offline they wait for it.
    val bleSt by hub.ble.state.collectAsState(); val devName = hub.prefs.deviceName.ifBlank { "BF" }
    var lastPush by remember { mutableLongStateOf(0L) }
    fun pushNow() { if (hub.ble.state.value == BleManager.State.READY) hub.scope.launch { runCatching { if (hub.pushTasks(force = true)) { hub.pushAlarms(); lastPush = System.currentTimeMillis() } } } }
    fun add() { if (t.isNotBlank()) { hub.db.addTask(t.trim()); t = ""; adding = false; r++; hub.revision.value++; pushNow() } }
    val jobs = remember { mutableMapOf<Long, kotlinx.coroutines.Job>() }
    fun del(id: Long) { pending.add(id); jobs[id] = scope.launch { kotlinx.coroutines.delay(5000); hub.db.deleteTask(id); pending.remove(id); jobs.remove(id); r++; hub.revision.value++
        hub.scope.launch { runCatching { hub.pushTasks(force = true) } } } }
    fun undo(id: Long) { jobs.remove(id)?.cancel(); pending.remove(id) }
    var openId by androidx.compose.runtime.saveable.rememberSaveable { mutableLongStateOf(0L) }
    var editing by remember { mutableStateOf(false) }
    val all = d?.first
    val online = bleSt == BleManager.State.READY
    val openTask = all?.firstOrNull { it.id == openId }
    if (openTask != null) {
        androidx.activity.compose.BackHandler { openId = 0L }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TodoDetailContent(openTask.title, if (openTask.detail.isNotBlank()) "From voice note" else "Added on phone", openTask.detail,
                if (openTask.due > 0) hub.whenLabel(openTask.due) else "No time set", "Doesn't repeat",
                if (openTask.due > 0) "At time · $devName says it" else "No reminder", devName,
                if (online && lastPush > 0) "Synced ${agoLabel(lastPush)}" else if (online) "Synced" else "Waiting for $devName", "", openTask.done,
                back = { openId = 0L }, onEdit = { t = openTask.title; editing = true },
                onToggle = { hub.db.setDone(openTask.id, !openTask.done); r++; hub.revision.value++; pushNow() },
                onDelete = { del(openTask.id); openId = 0L })
        }
        if (editing) {
            var showOn by remember { mutableStateOf(true) }
            var repeat by remember { mutableStateOf(hub.prefs.taskRepeat(openTask.id)) }
            var days by remember { mutableStateOf(setOf(0,1,2,3,4)) }
            var sheet by remember { mutableStateOf(false) }
            val dueLab = if (openTask.due > 0) java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.US).format(java.util.Date(openTask.due)) else "Not set"
            val timeLab = if (openTask.due > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(openTask.due)) else "—"
            val repLab = when (repeat) { "daily" -> "Every day"; "weekdays" -> "Weekdays"; "custom" -> "On these days"; "monthly" -> "Every month"; else -> "Never" }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TodoEditContent(t.ifBlank { openTask.title }, { t = it }, dueLab, timeLab, if (openTask.due > 0) "At time" else "None", repLab, showOn, devName,
                    onDue = {}, onTime = {}, onRemind = {}, onRepeat = { sheet = true }, onShow = { showOn = it },
                    onSave = {
                        val title = t.trim().ifBlank { openTask.title }
                        hub.db.renameTask(openTask.id, title); hub.prefs.setTaskRepeat(openTask.id, repeat)
                        r++; hub.revision.value++; pushNow(); t = ""; editing = false
                    },
                    onDelete = { del(openTask.id); openId = 0L; editing = false },
                    onClose = { t = ""; editing = false })
            }
            if (sheet) androidx.compose.ui.window.Dialog({ sheet = false }) {
                TodoRepeatSheet(repeat, days, { repeat = it }, { days = it }, onSave = { sheet = false }, onDismiss = { sheet = false })
            }
            return
        }
        return
    }
    val now = System.currentTimeMillis(); val endDay = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59) }.timeInMillis
    val rows = (all ?: emptyList()).map { k -> TodoRowUi(k.id, k.title, if (k.done) "Done" else if (k.due > 0) hub.whenLabel(k.due) else "", k.done,
        synced = online && lastPush > 0, deleting = k.id in pending) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TodoListContent(rows, today = (all ?: emptyList()).count { !it.done && (it.due == 0L || it.due <= endDay) },
                reminders = (all ?: emptyList()).count { !it.done && it.due > now }, done = (all ?: emptyList()).count { it.done },
                onAdd = { adding = true }, onToggle = { id -> all?.firstOrNull { it.id == id }?.let { hub.db.setDone(id, !it.done); r++; hub.revision.value++; pushNow() } },
                onOpen = { openId = it }, onUndo = { undo(it) }, devName = devName) {
                if (adding) OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text("New to-do") }, singleLine = true, shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                    trailingIcon = { IconButton({ add() }, enabled = t.isNotBlank()) { Icon(Sym.Add, "Add") } })
            }
            // Today's calendar + manual send, kept from v0.4.1.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.CalendarToday, null, tint = cs.onSurfaceVariant); Spacer(Modifier.width(10.dp))
                    Text(if (agenda.isNullOrEmpty()) "Calendar today · empty" else "Calendar today · ${agenda.size} events", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (!calOk) AssistChip({ calLauncher.launch(android.Manifest.permission.READ_CALENDAR) }, { Text("Allow calendar") }, leadingIcon = { Icon(Sym.Lock, null, Modifier.size(18.dp)) })
                }
                agenda?.forEach { a -> Row(Modifier.padding(start = 34.dp)) {
                    Text(a.substringBefore(' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                    Text(a.substringAfter(' '), style = MaterialTheme.typography.bodyLarge) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ scope.launch { hub.syncTime(); hub.ble.write(BfProtocol.NOTIFY, BfProtocol.agenda(agenda ?: emptyList())); hub.pushTasks(); hub.pushAlarms(); sentAt = fmtClock.format(Date()) } }) {
                        Icon(Sym.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Send to $devName now") }
                    Text(if (sentAt.isNotEmpty()) "last $sentAt" else if (online) "On $devName" else "Waiting for $devName", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(PopNavClearance))
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = PopNavClearance))
    }
}

private val saran = listOf(R.string.ask_s1, R.string.ask_s2, R.string.ask_s3)

/** Satu baris ingatan: penanda "auto" kecil untuk yang dibuat otomatis; geser ke samping atau ketuk ikon hapus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MemoryRow(m: id.yanuar.bf01.data.Memory, onDelete: () -> Unit) {
    val st = rememberSwipeToDismissBoxState(confirmValueChange = { if (it != SwipeToDismissBoxValue.Settled) { onDelete(); true } else false })
    SwipeToDismissBox(st, backgroundContent = {}) {
        Row(Modifier.fillMaxWidth().background(LocalBubble.current.container), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                if (m.auto) Text(stringResource(R.string.ask_memory_auto, if (m.source == "note") stringResource(R.string.ask_memory_from_note) else stringResource(R.string.ask_memory_from_chat)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onDelete) { Icon(Sym.Delete, stringResource(R.string.ask_memory_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable fun NgobrolScreen(hub: Hub) {
    DisposableEffect(Unit) { onDispose { hub.autoMemory.flush() } }
    var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }; val rev by hub.revision.collectAsState()
    // Chat and memories load once per data change, not on every recomposition.
    val d = rememberLoaded(hub, "ngobrol", r, rev) { hub.db.chat() to hub.db.memories() }
    val chat = d?.first; val mems = d?.second
    var persona by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(hub.prefs.persona) }; var mem by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var msg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }; var confirmMem by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme; val ai = hub.prefs.aiName; val ctxChat = LocalContext.current
    // pending = the message waiting for a reply (or that failed); err = plain-English reason, shown with Retry.
    var pending by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var err by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }; val listScroll = rememberScrollState()
    fun send(text: String) { val m = text.trim(); if (m.isNotEmpty() && !busy) { msg = ""; busy = true; pending = m; err = ""
        runCatching { focus.requestFocus() }
        scope.launch {
            try { hub.chatText(m); pending = "" }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { err = id.yanuar.bf01.ai.friendlyError(e) }
            finally { busy = false; r++ } } } }
    val empty = chat?.isEmpty() == true && !busy && pending.isEmpty()
    Column(Modifier.fillMaxSize()) {
        var memOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
        AskHeader(ai, persona, { hub.openScreen?.invoke(Screen.AiChats) }) { more = !more }
        Spacer(Modifier.height(10.dp))
        RemembersCard(ai, mems?.map { it.text } ?: emptyList(), memOpen, { memOpen = !memOpen }) { more = !more }
        Spacer(Modifier.height(6.dp))
        MemorySavedChip(hub)
        LaunchedEffect(chat?.size, pending, busy, err) { listScroll.animateScrollTo(listScroll.maxValue) }
        Column(Modifier.weight(1f).verticalScroll(listScroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (more) Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), color = cardColor()) { Column(Modifier.padding(14.dp)) {
                androidx.compose.animation.AnimatedVisibility(more) {
                    // Inside the bubble the colors are inverted, so fields use the opposite scheme.
                    run {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.ask_persona), style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(persona, { persona = it }, Modifier.fillMaxWidth(), minLines = 3, shape = MaterialTheme.shapes.large)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(stringResource(R.string.save), Modifier.weight(1f), icon = Sym.Save) { hub.prefs.persona = persona }
                                OutlinedButton({ persona = Prefs.DEFAULT_PERSONA; hub.prefs.persona = persona }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Icon(Sym.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ask_default), maxLines = 1) } }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.ask_memory), style = MaterialTheme.typography.labelLarge)
                            if (mems.isNullOrEmpty()) HelpNote(stringResource(R.string.ask_memory_empty, ai))
                            mems?.forEach { m -> key(m.id) { MemoryRow(m) { hub.db.deleteMemory(m.id); r++ } } }
                            if (!mems.isNullOrEmpty()) TextButton({ confirmMem = true }) { Text("Clear all memory") }
                            if (confirmMem) AlertDialog(onDismissRequest = { confirmMem = false },
                                title = { Text("Clear all memory?") }, text = { Text("$ai will forget everything saved about you. This can't be undone.") },
                                confirmButton = { TextButton({ hub.db.clearMemories(); hub.autoMemory.dismissLast(); confirmMem = false; r++ }) { Text("Clear all") } },
                                dismissButton = { TextButton({ confirmMem = false }) { Text(stringResource(R.string.cancel)) } })
                            OutlinedTextField(mem, { mem = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ask_memory_add)) }, shape = MaterialTheme.shapes.large,
                                trailingIcon = { IconButton({ if (mem.isNotBlank()) { hub.db.addMemory(mem.trim()); mem = ""; r++ } }, enabled = mem.isNotBlank()) { Icon(Sym.Add, stringResource(R.string.add)) } })
                            OutlinedButton({ confirmClear = true }, Modifier.heightIn(min = 48.dp), enabled = !chat.isNullOrEmpty()) {
                                Icon(Sym.DeleteSweep, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ask_clear)) }
                        }
                    }
                }
            }
            }
            if (empty) AskLanding(listOf(androidx.compose.material.icons.Icons.Default.Search to "What did I say about the trip budget?", Sym.AutoAwesome to "Summarize today's notes",
                Sym.Notifications to "Remind me to call the team at 2", Sym.CheckCircle to "What's left on my to-do?")) { send(it) }
            var lastUser = ""
            chat?.forEach { c ->
                ChatBubble(c.text, fromBf = c.role != "user",
                    header = (if (c.role == "user") (hub.prefs.cardName.trim().split(" ").firstOrNull()?.ifBlank { null } ?: "You") else ai) + (if (c.src == "bf") " · from BF" else "") + " · " + fmtClock.format(Date(c.time)))
                if (c.role == "user") lastUser = c.text
                else if (lastUser.isNotBlank() && id.yanuar.bf01.ai.PlaceSearch.isPlaceQuery(lastUser)) { val q = id.yanuar.bf01.ai.PlaceSearch.query(lastUser)
                    OutlinedButton({ id.yanuar.bf01.ai.PlaceSearch.open(ctxChat, q, hub.prefs.lastLat, hub.prefs.lastLon) }, Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Place, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Search in Google Maps: $q", maxLines = 1) }
                    lastUser = "" }
            }
            if (pending.isNotEmpty()) ChatBubble(pending, fromBf = false, header = (hub.prefs.cardName.trim().split(" ").firstOrNull()?.ifBlank { null } ?: "You") + if (err.isNotEmpty()) " · not sent" else "")
            if (busy) ThinkingBubble(ai)
            if (err.isNotEmpty() && !busy) ChatErrorCard(err, id.yanuar.bf01.ai.chatLabel(hub.prefs), onRetry = { send(pending) }, onDismiss = { msg = pending; pending = ""; err = "" })
        }
        Spacer(Modifier.height(12.dp))
        AskInput(msg, { msg = it }, "Ask anything…", { send(msg) })
        Spacer(Modifier.height(PopNavClearance))
    }
    if (confirmClear) AlertDialog({ confirmClear = false }, icon = { Icon(Sym.DeleteSweep, null) }, title = { Text(stringResource(R.string.ask_clear_q)) },
        text = { Text(stringResource(R.string.ask_clear_note, ai)) },
        confirmButton = { TextButton({ hub.db.clearChat(); r++; confirmClear = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

/** "BF is thinking…" bubble with animated dots while the AI answers. */
@Composable fun ThinkingBubble(ai: String) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "think")
    val n by t.animateFloat(0f, 3.99f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200)), label = "dots")
    ChatBubble("$ai is thinking" + ".".repeat(n.toInt()), fromBf = true, header = ai)
}

/** Visible error under the chat, with Retry. */
@Composable fun ChatErrorCard(text: String, who: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = cs.errorContainer, contentColor = cs.onErrorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Warning, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("No reply from $who", style = MaterialTheme.typography.titleSmall) }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onRetry, Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Sym.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Retry", maxLines = 1) }
                OutlinedButton(onDismiss, Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onErrorContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.onErrorContainer.copy(alpha = 0.5f))) { Text("Edit", maxLines = 1) }
            }
        }
    }
}

/** "Test key" and "Test speech" buttons + one result line (Connected / Heard ... / failed with reason). */
@Composable fun TestKeyRow(hub: Hub, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); var busy by remember { mutableStateOf(false) }; var res by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var which by remember { mutableStateOf("") }
    ActionButton(if (busy && which == "key") "Testing…" else "Test key", enabled = !busy, icon = Sym.SmartToy) { busy = true; which = "key"; res = null
        scope.launch { val r = id.yanuar.bf01.ai.testKey(hub.prefs)
            res = r.fold({ true to it }, { false to "Failed · " + id.yanuar.bf01.ai.friendlyError(it) +
                if (hub.prefs.llmProvider == "qwen") "\nTried: " + id.yanuar.bf01.ai.QwenUrl.host(hub.prefs.qwenBaseUrl) else "" }); busy = false; onDone() } }
    ActionButton(if (busy && which == "speech") "Listening…" else "Test speech", enabled = !busy, icon = androidx.compose.material.icons.Icons.Filled.PlayArrow) { busy = true; which = "speech"; res = null
        scope.launch { val r = hub.testSpeech(); res = r.ok to r.text; busy = false; onDone() } }
    res?.let { (ok, t) -> Text(t, style = MaterialTheme.typography.bodyMedium,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
}

@Composable fun MenuScreen(hub: Hub) {
    var items by remember { mutableStateOf(hub.prefs.menuOrder.split(",").filter { it.isNotBlank() }) }
    val all = Prefs.DEFAULT_MENU.split(",")
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HelpNote("Set what BF's menu shows and in what order (3 pages, 2 buttons per page).")
        items.forEachIndexed { i, s -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}. $s", Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            IconButton({ if (i > 0) items = items.toMutableList().also { java.util.Collections.swap(it, i, i - 1) } }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
            IconButton({ if (i < items.size - 1) items = items.toMutableList().also { java.util.Collections.swap(it, i, i + 1) } }, enabled = i < items.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
            IconButton({ items = items - s }) { Icon(Icons.Default.Close, "Hide") } } }
        (all - items.toSet()).forEach { s -> OutlinedButton({ items = items + s }) { Text("+ $s") } }
        ActionButton("Save", primary = true, icon = Sym.Save) { hub.prefs.menuOrder = items.joinToString(",") }
        HelpNote("The new order is saved on the phone; it goes to BF in the next firmware.")
    }
}

@Composable fun FileScreen(hub: Hub) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current; val cs = MaterialTheme.colorScheme
    val st by hub.recordings.status.collectAsState(); val ble by hub.ble.state.collectAsState(); val rev by hub.revision.collectAsState()
    LaunchedEffect(rev) { hub.recordings.refreshCount() }
    var files by remember { mutableStateOf(listOf<BfProtocol.FileEntry>()) }
    var adv by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val busy = st.phase in setOf(id.yanuar.bf01.svc.RecordingSync.Phase.Connecting, id.yanuar.bf01.svc.RecordingSync.Phase.Downloading, id.yanuar.bf01.svc.RecordingSync.Phase.Transcribing)
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BubbleCard(Modifier.fillMaxWidth(), tail = 14.dp) {
            Text(if (st.notOnPhone == 0) stringResource(R.string.rec_status_none) else stringResource(R.string.rec_status_n, st.notOnPhone),
                style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            val last = hub.prefs.lastRecSync
            Text(if (last == 0L) stringResource(R.string.rec_never) else stringResource(R.string.rec_last, fmtNote.format(Date(last))),
                style = MaterialTheme.typography.bodyMedium)
            if (st.message.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(st.message, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(14.dp))
            SyncStatusLine(hub)
        }
        HelpNote(stringResource(R.string.rec_how), stringResource(R.string.rec_how_detail))
        val rot by androidx.compose.animation.core.animateFloatAsState(if (adv) 180f else 0f, label = "adv")
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { adv = !adv }.heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rec_advanced), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Sym.ExpandMore, stringResource(if (adv) R.string.collapse else R.string.expand), Modifier.graphicsLayer { rotationZ = rot })
        }
        androidx.compose.animation.AnimatedVisibility(adv) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section(stringResource(R.string.file_tf)) {
                    HelpNote(stringResource(R.string.file_tf_desc))
                    ActionButton(stringResource(R.string.file_tf_read), icon = Sym.Refresh, enabled = ble == BleManager.State.READY) {
                        scope.launch { files = hub.ble.read(BfProtocol.FILES)?.let { BfProtocol.parseFiles(String(it)) } ?: emptyList() } }
                    files.forEach { f -> Text("${f.name} · ${f.bytes / 1024} KB · ${if (f.synced) "on phone" else "not on phone yet"}",
                        style = MaterialTheme.typography.bodyMedium) }
                }
                Section(stringResource(R.string.file_export)) {
                    HelpNote(stringResource(R.string.file_export_desc))
                    val title = stringResource(R.string.file_export)
                    ActionButton(stringResource(R.string.file_export_md), icon = androidx.compose.material.icons.Icons.Default.Share) {
                        val md = buildString { appendLine("# BF-01 notes"); hub.db.notes().forEach { appendLine("\n## ${it.file} (${it.type})\n${it.summary}\n\n${it.transcript}") }
                            appendLine("\n# To-do"); hub.db.tasks().forEach { appendLine("- [${if (it.done) "x" else " "}] ${it.title}") } }
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_TEXT, md), title))
                    }
                }
            }
        }
    }
}

/** Putar/stop rekaman di HP. */
@Composable fun PlayButton(path: String, small: Boolean = false) {
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release(); player = null } }
    val playing = player != null
    FilledTonalIconButton({
        if (playing) { player?.release(); player = null }
        else player = runCatching { android.media.MediaPlayer().apply { setDataSource(path); setOnCompletionListener { it.release(); player = null }; prepare(); start() } }.getOrNull()
    }, Modifier.size(if (small) 36.dp else 48.dp)) { Icon(if (playing) Sym.StopCircle else androidx.compose.material.icons.Icons.Default.PlayArrow, if (playing) "Stop" else "Play", Modifier.size(if (small) 20.dp else 24.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
fun linkLabel(linked: Boolean, st: BleManager.State) = when {
    !linked -> "DISCONNECTED"; st == BleManager.State.READY -> "CONNECTED"; else -> "CONNECTING"
}

fun notifAccessGranted(ctx: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

/** Onboarding card: music (JAM ON) and Maps (TAKE ME) need notification access. Status refreshes when coming back from Settings. */
@Composable fun NotifAccessCard() {
    val ctx = LocalContext.current; var ok by remember { mutableStateOf(notifAccessGranted(ctx)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ok = notifAccessGranted(ctx) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = if (ok) cs.surfaceContainerHigh else cs.errorContainer,
        contentColor = if (ok) cs.onSurface else cs.onErrorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.Notifications, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Text(if (ok) "Notification access: on" else "Allow notification access", style = MaterialTheme.typography.titleSmall) }
            Text(if (ok) "JAM ON shows your music and TAKE ME shows Google Maps directions on BF."
                else "Needed for music (JAM ON) and Google Maps directions (TAKE ME) on BF. Only music and Maps are read.",
                style = MaterialTheme.typography.bodyMedium)
            if (!ok) Button({ ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Open settings", maxLines = 1) }
        }
    }
}

/** Sakelar besar Sambungkan/Putuskan (tersimpan, tetap berlaku setelah app ditutup / HP restart). */
@Composable fun LinkToggle(hub: Hub, linked: Boolean) {
    if (linked) OutlinedButton({ hub.disconnectAll() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Disconnect") }
    else Button({ hub.reconnect() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Connect to BF") }
}

/** Beranda Setelan: kartu WHERE'S MINE + 6 kategori berikon (sub-halaman). */
@Composable fun PengaturanScreen(hub: Hub, open: (Screen) -> Unit = {}, onStatus: () -> Unit = {}) {
    val p = hub.prefs; val ctx = LocalContext.current
    val linked by hub.linked.collectAsState(); val st by hub.ble.state.collectAsState(); val info by hub.ble.info.collectAsState()
    val fails by hub.reconnectFails.collectAsState(); val remoteFw by hub.fwUpdate.remote.collectAsState()
    var help by remember { mutableStateOf(false) }; var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val usage = rememberLoaded(hub, "pemakaian") { hub.db.usageThisMonth() }
    LaunchedEffect(Unit) { hub.fwUpdate.checkLatest() }
    val ready = st == id.yanuar.bf01.ble.BleManager.State.READY || st == id.yanuar.bf01.ble.BleManager.State.CONNECTED
    val neverPaired = p.deviceAddress.isNullOrBlank()
    val name = p.deviceName.ifBlank { "BF" }
    val owner = p.cardName.trim().split(" ").firstOrNull().orEmpty().lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "You" }
    val fwHave = id.yanuar.bf01.fw.FirmwareImage.displayVersion(info?.fw)
    val fwAvail = remoteFw?.version ?: id.yanuar.bf01.fw.FirmwareImage.LATEST
    val fwNew = info?.fw != null && !fwHave.contains(fwAvail)
    val ssid = p.wifi(0).first
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (neverPaired) Section("Start here") {
            Text("$name hasn't been connected to this phone yet. Turn it on, hold it near the phone, then tap the button below.", style = MaterialTheme.typography.bodyLarge)
            ActionButton("Connect $name", primary = true, icon = Sym.Bluetooth) { open(Screen.Pairing) }
        }
        // v0.4.0 (mockup v5.13 · 08): profile card, DEVICE and APP groups.
        var renaming by remember { mutableStateOf(false) }; var theming by remember { mutableStateOf(false) }
        val rev by hub.revision.collectAsState(); val shownName = remember(rev) { p.deviceName.ifBlank { "BF" } }
        if (renaming) RenameDeviceDialog(shownName, { DeviceRename.apply(hub, it); renaming = false }) { renaming = false }
        if (theming) ThemeDialog(AppTheme.mode, { AppTheme.set(it) }) { theming = false }
        SettingsContent(owner, "Name, avatar, Say Hi · backup on", { open(Screen.Kartu) },
            device = listOf(
                SetRow(Sym.Edit, "Device name", shownName, { renaming = true }),
                SetRow(Sym.Apps, "Character", "Menu, faces & order on $name", { open(Screen.Menu) }, accent = Pop.Orange, deviceThumb = true),
                SetRow(Sym.Link, "Connection", if (ready) (if (ssid.isNotBlank()) "Wi-Fi \u201c$ssid\u201d" else "Bluetooth · connected") else linkLabel(linked, st),
                    { open(Screen.SetJaringan) }, chip = "Help", onChip = { help = !help }),
                SetRow(Sym.Download, "Software update", "$name $fwHave" + if (fwNew) " · $fwAvail available" else " · up to date", { open(Screen.SetFirmware) },
                    chip = if (fwNew) "New" else null, chipYellow = true),
                SetRow(Sym.LightMode, "Sleep & wake", "Sleep after ${p.sleepSec} s · quiet ${p.quietStart}:00–${p.quietEnd}:00", { open(Screen.SetSuara) }, accent = Pop.Yellow),
            ),
            app = listOf(
                SetRow(Sym.AutoAwesome, "AI & voice", (if (p.llmProvider == "qwen") "Qwen" else "Grok") + " · voice ${p.ttsVoice}" + if ((if (p.llmProvider == "qwen") p.qwenKey else p.xaiKey).isBlank()) " · no key yet" else "", { open(Screen.SetAi) }),
                SetRow(Sym.Notifications, "Notifications", "Music, Maps & what $name may show", { open(Screen.SetIzin) }),
                SetRow(Sym.BarChart, "Usage", "Features, battery & AI cost", { open(Screen.Usage) }),
                SetRow(Sym.Palette, "Theme", null, { open(Screen.ThemeBoard) }, swatches = true),
                SetRow(Sym.Shield, "Privacy & data", "Export, clear, audio & chats", { open(Screen.Privacy) }),
            ),
            online = ready,
            connecting = st == id.yanuar.bf01.ble.BleManager.State.CONNECTING || st == id.yanuar.bf01.ble.BleManager.State.SCANNING,
            onStatus = onStatus,
        )
        if (!neverPaired && (fails >= 2 || help)) ConnectHelpCard(hub)
        if (!neverPaired && !ready) ActionButton("Reconnect", primary = true, icon = Sym.Refresh) { hub.reconnect() }
        AppUpdateCard(always = true)
        // 3b) AI usage (tokens, estimated cost, monthly projection)
        val aiDays = rememberLoaded(hub, "aiusage") { loadAiDays(hub) }
        NavCard {
            val sub = aiDays?.let { d -> UsageReports.build(d, java.time.LocalDate.now(), id.yanuar.bf01.ai.AiPricing.decode(p.aiPriceOverrides), p.idrPerUsd.toDouble()).month
                .let { m -> if (m.calls == 0) "Tokens, estimated cost and monthly projection" else "This month ≈ ${UsageReports.rupiah(m.usd * p.idrPerUsd)} · ${UsageReports.tokens(m.tokens)} tokens (estimate)" } } ?: "Tokens, estimated cost and monthly projection"
            NavRow(Sym.BarChart, stringResource(Screen.AiUsage.title), sub) { open(Screen.AiUsage) }
        }
        // 5) Pengaturan lainnya (tertutup)
        NavCard {
            NavRow(Sym.ExpandMore, "More settings", if (more) "Tap to close" else "Usage & about, battery, phone link") { more = !more }
            if (more) {
                NavDivider()
                NavRow(Sym.Notifications, "Music, Maps & notifications", "What BF may show") { open(Screen.SetIzin) }
                NavRow(Sym.Bluetooth, "$name isn't connecting", "Reconnect tips") { open(Screen.ConnHelp) }
                NavDivider()
                NavRow(Sym.RecordVoiceOver, "BF & sound", "Sleep ${p.sleepSec} s · quiet hours ${p.quietStart}–${p.quietEnd}") { open(Screen.SetSuara) }
                NavDivider()
                NavRow(Sym.Bluetooth, "Connection & network", "Wi-Fi, phone server") { open(Screen.SetJaringan) }
                NavDivider()
                NavRow(Sym.Person, stringResource(Screen.Kartu.title), "QR codes with logos on BF") { open(Screen.Kartu) }
                NavDivider()
                NavRow(Sym.DeveloperBoard, stringResource(Screen.Menu.title), "BF menu items & order") { open(Screen.Menu) }
                NavDivider()
                NavRow(Sym.Dns, stringResource(Screen.File.title), "Sync recordings, memory card, export") { open(Screen.File) }
                NavDivider()
                NavRow(Sym.BarChart, "Usage & about", "${usage?.let { "%.0f".format(it.first + it.second) } ?: "–"} audio min this month · v${appVersion(ctx)}") { open(Screen.SetPemakaian) }
            }
        }
        if (more) {
            if (!neverPaired) { Section("$name link") { RestartLine(info, hub); LinkToggle(hub, linked) }
                BatteryCard(info); DiagnostikCard(hub, info); WaktuTidurCard(hub) }
            WheresMineCard(hub)
        }
        NotifAccessCard()
        VersionFooter()
        Spacer(Modifier.height(PopNavClearance))
    }
}

/** App version from BuildConfig, e.g. "Pocket Friend v0.3.7". */
const val APP_VERSION_LABEL = "Pocket Friend v" + id.yanuar.bf01.BuildConfig.VERSION_NAME
@Composable fun VersionFooter(small: Boolean = false) =
    Text(APP_VERSION_LABEL, Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

fun appVersion(ctx: android.content.Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

/** Buka lokasi di Google Maps; kalau Maps tidak ada, pakai geo: (app peta lain). */
fun openMaps(ctx: android.content.Context, lat: Double, lon: Double) {
    val maps = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")).setPackage("com.google.android.apps.maps")
    try { ctx.startActivity(maps) } catch (_: android.content.ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(BF)"))) }
    }
}

@Composable fun WheresMineCard(hub: Hub) {
    val p = hub.prefs; val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val st by hub.ble.state.collectAsState()
    var ringing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme; val has = !p.lastLat.isNaN()
    Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = cs.inverseSurface, contentColor = cs.inverseOnSurface) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BfHead(30.dp, color = cs.inverseOnSurface); Spacer(Modifier.width(14.dp))
                DotText("WHERE'S MINE", Dot.L, 1.1f, color = cs.inverseOnSurface)
            }
            Text(if (has) "Last seen · ${fmt.format(Date(p.lastLocTime))}" else "No saved location yet", style = MaterialTheme.typography.bodyLarge)
            if (st != BleManager.State.READY) Row(verticalAlignment = Alignment.Top) {
                Icon(Sym.Info, null, Modifier.size(16.dp).padding(top = 1.dp)); Spacer(Modifier.width(8.dp))
                Text("BF is asleep: it only rings once awake (PWR).", style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            val bc = ButtonDefaults.buttonColors(containerColor = cs.surface, contentColor = cs.onSurface, disabledContainerColor = cs.surface.copy(alpha = 0.4f),
                disabledContentColor = cs.onSurface.copy(alpha = 0.5f))
            AdaptiveRow {
                Button({ val on = !ringing; ringing = on; if (on) hub.db.addFeatureUse(id.yanuar.bf01.svc.Feature.WHERES_MINE); scope.launch { hub.ble.write(BfProtocol.FIND, BfProtocol.find(on)) } }, Modifier.cell(1f).heightIn(min = 48.dp),
                    colors = bc, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(if (ringing) Sym.StopCircle else Sym.NotificationsActive, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (ringing) "Stop" else "Ring", maxLines = 1) }
                Button({ hub.db.addFeatureUse(id.yanuar.bf01.svc.Feature.WHERES_MINE); openMaps(ctx, p.lastLat, p.lastLon) }, Modifier.cell(1.25f).heightIn(min = 48.dp), enabled = has, colors = bc,
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Sym.LocationOn, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Open in Maps", maxLines = 1) }
            }
        }
    }
}

@Composable internal fun SubPage(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)

@Composable fun SetAiScreen(hub: Hub) {
    val p = hub.prefs; var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var xk by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val tick = r  // baca r supaya halaman dirender ulang setelah prefs berubah
    SubPage {
        Section(stringResource(R.string.ai_name)) {
            var nm by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.aiName) }
            OutlinedTextField(nm, { nm = it.take(16); if (it.isNotBlank()) p.aiName = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.ai_name)) }, placeholder = { Text("BF") }, shape = MaterialTheme.shapes.large)
            HelpNote(stringResource(R.string.ai_name_desc))
        }
        Section(stringResource(R.string.ask_memory)) {
            Toggle(stringResource(R.string.auto_mem), p.autoMemory, stringResource(R.string.auto_mem_desc, p.aiName)) { p.autoMemory = it; r++ }
        }
        val shown = id.yanuar.bf01.ai.AiProviders.visible
        if (shown.size > 1) Section("Provider") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                shown.forEachIndexed { i, sp ->
                    SegmentedButton(p.llmProvider == sp.id, { p.llmProvider = sp.id; r++ }, SegmentedButtonDefaults.itemShape(i, shown.size),
                        colors = SegmentedButtonDefaults.colors(inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent)) {
                        Text(sp.label, maxLines = 1, softWrap = false) } }
            }
            HelpNote("One choice for everything: speech recognition, chat, and BF's voice.")
        }
        if (p.llmProvider == "qwen") {
            var qk by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
            var qb by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.qwenBaseUrl) }; var qm by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.qwenModel) }
            Section("Qwen API key") {
                Text(if (p.qwenKey.isBlank()) "Not set" else "Saved encrypted (…${p.qwenKey.takeLast(4)})")
                OutlinedTextField(qk, { qk = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("API key (DashScope)") },
                    visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.large)
                ActionButton("Save", enabled = qk.isNotBlank(), icon = Sym.Save) { p.qwenKey = qk.trim(); p.llmProvider = "qwen"; qk = ""; r++ }
                if (p.secureWasReset && p.qwenKey.isBlank()) Text("Your saved key couldn't be read on this phone. Please re-enter your key.", color = MaterialTheme.colorScheme.error)
                if (p.qwenKey.isNotBlank()) TestKeyRow(hub) { qb = p.qwenBaseUrl; r++ }
            }
            Section("Region & model") {
                val custom = !id.yanuar.bf01.ai.QwenUrl.isPreset(qb)
                fun setBase(v: String) { qb = v; p.qwenBaseUrl = v; if (!id.yanuar.bf01.ai.QwenUrl.isPreset(v) && v.isNotBlank()) p.qwenCustomUrl = p.qwenBaseUrl }
                val nb = id.yanuar.bf01.ai.QwenUrl.normalize(qb)
                val opts = listOf("Global" to (nb == Prefs.QWEN_BASE_INTL), "China" to (nb == Prefs.QWEN_BASE_CN), "Custom" to custom)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { opts.forEachIndexed { i, (label, on) ->
                    SegmentedButton(on, { when (i) { 0 -> setBase(Prefs.QWEN_BASE_INTL); 1 -> setBase(Prefs.QWEN_BASE_CN); else -> if (!custom) { qb = p.qwenCustomUrl; if (qb.isNotBlank()) p.qwenBaseUrl = qb } } },
                        SegmentedButtonDefaults.itemShape(i, 3), icon = {},
                        colors = SegmentedButtonDefaults.colors(inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent)) { Text(label, maxLines = 1, softWrap = false) } } }
                OutlinedTextField(qb, { setBase(it) }, Modifier.fillMaxWidth(), label = { Text(if (custom) "Base URL (paste your workspace URL)" else "Base URL") },
                    minLines = 2, shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                val norm = id.yanuar.bf01.ai.QwenUrl.normalize(qb)
                if (norm == null) Text(if (qb.isBlank()) "Paste a Base URL, or pick a region." else "That doesn't look like a web address.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else Text("Region: ${id.yanuar.bf01.ai.QwenUrl.regionName(norm)}\nChat: $norm/chat/completions", style = MaterialTheme.typography.bodySmall)
                HelpNote(if (custom) "A workspace URL is used exactly as entered. The key must come from that workspace." else "The key must match the region. Test key finds the right region by itself.")
                ModelPicker(qm) { qm = it; p.qwenModel = it }
                var am by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.qwenAsrModel) }
                ModelPicker(am, id.yanuar.bf01.ai.AiProviders.QWEN_ASR_MODELS, "Speech model") { am = it; p.qwenAsrModel = it }
                HelpNote("Speech is recognised in Indonesian. Both models need to be active on your Alibaba account.")
            }
        } else {
            var model by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.chatModel) }
            Section("xAI API key") {
                Text(if (p.xaiKey.isBlank()) "Not set" else "Saved encrypted (…${p.xaiKey.takeLast(4)})")
                OutlinedTextField(xk, { xk = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("xAI API key") },
                    visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.large)
                ActionButton("Save", enabled = xk.isNotBlank(), icon = Sym.Save) { p.xaiKey = xk.trim(); p.llmProvider = "grok"; xk = ""; r++ }
                if (p.secureWasReset && p.xaiKey.isBlank()) Text("Your saved key couldn't be read on this phone. Please re-enter your key.", color = MaterialTheme.colorScheme.error)
                if (p.xaiKey.isNotBlank()) TestKeyRow(hub) { r++ }
            }
            Section("Model") {
                OutlinedTextField(model, { model = it; p.chatModel = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Grok chat model") }, shape = MaterialTheme.shapes.large)
            }
        }
    }
}

@Composable fun SetSuaraScreen(hub: Hub) {
    val p = hub.prefs; val scope = rememberCoroutineScope(); var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    SubPage {
        Section("BF's voice") {
            if (p.llmProvider == "qwen") HelpNote("Google's Indonesian voice on the phone.", "Qwen TTS voices don't support Indonesian yet, so BF uses Google's id-ID voice on the phone.")
            else {
                var voice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.ttsVoice) }
                key(r) { Toggle("Voice from xAI", p.ttsProvider == "xai", sub = "Off = Google's Indonesian voice on the phone") { p.ttsProvider = if (it) "xai" else "system"; r++ } }
                if (p.ttsProvider == "xai") OutlinedTextField(voice, { voice = it; p.ttsVoice = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("xAI voice (voice_id)") }, shape = MaterialTheme.shapes.large)
            }
            if (ModeCepat.IMPLEMENTED) key(r) { Toggle("Fast mode (realtime)", p.modeCepat) { p.modeCepat = it; r++ } }
            else Toggle("Fast mode (realtime)", false, sub = "Soon", enabled = false) {}
        }
        Section("Sleep") {
            var sl by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.sleepSec.toString()) }
            OutlinedTextField(sl, { sl = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Sleep after idle (seconds)") },
                supportingText = { Text("15 to 3600 seconds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large)
            ActionButton("Send to BF", icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send) { p.sleepSec = sl.toIntOrNull() ?: 60; sl = p.sleepSec.toString()
                scope.launch { hub.ble.write(BfProtocol.NOTIFY, BfProtocol.sleepTimeout(p.sleepSec)) } }
        }
        Section("Quiet hours") {
            var qs by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.quietStart.toString()) }; var qe by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.quietEnd.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(qs, { qs = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("From") },
                    suffix = { Text(".00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large)
                OutlinedTextField(qe, { qe = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("To") },
                    suffix = { Text(".00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large) }
            ActionButton("Save", icon = Sym.Save) { p.quietStart = (qs.toIntOrNull() ?: 22) % 24; p.quietEnd = (qe.toIntOrNull() ?: 6) % 24 }
        }
    }
}

@Composable fun SetJaringanScreen(hub: Hub) {
    val p = hub.prefs; var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val linked by hub.linked.collectAsState(); val st by hub.ble.state.collectAsState()
    SubPage {
        Section("BF connection") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { DotText(linkLabel(linked, st), Dot.S, 1.1f) }
                Switch(linked, { if (it) hub.reconnect() else hub.disconnectAll() })
            }
            HelpNote("Off = Bluetooth & Wi-Fi to BF are cut and won't reconnect automatically.")
        }
        WifiSlot(hub, 0); WifiSlot(hub, 1)
        SendToBf(hub)
        Section("Server on the phone") {
            var host by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.serverHost) }
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Phone IP as seen by BF") }, shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Sym.Dns, null) })
            HelpNote("On the phone hotspot it is often 192.168.43.1, but can differ.", "When BF uses this phone's hotspot, the IP is often 192.168.43.1, but can differ. Check Android Settings › Mobile hotspot.")
            AdaptiveRow {
                ActionButton("Save", Modifier.cell(1f), icon = Sym.Save) { p.serverHost = host.trim() }
                OutlinedButton({ p.rotateToken(); p.clearPairing(); p.openPairing(); r++ }, Modifier.cell(1f).heightIn(min = 48.dp)) {
                    Icon(Sym.Key, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("New token", maxLines = 1) }
            }
            if (p.pairedDeviceId.isNotEmpty()) HelpNote("Paired BF Device-Id: ${p.pairedDeviceId}")
            HelpNote("After changing the token, send it again from Pairing. That also re-opens device pairing for 10 minutes.")
        }
    }
}

@Composable fun SetIzinScreen(hub: Hub) {
    val p = hub.prefs; val ctx = LocalContext.current; var r by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val linked by hub.linked.collectAsState(); val paused by hub.forwardPaused.collectAsState()
    var notif by remember { mutableStateOf(notifAccessGranted(ctx)) }; var dnd by remember { mutableStateOf(dndAccessGranted(ctx)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { notif = notifAccessGranted(ctx); dnd = dndAccessGranted(ctx) } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    val dev = p.deviceName.ifBlank { "BF" }
    SubPage {
        NotificationsContent(dev,
            listOf(Triple("Reminders", "To-dos that are due", p.nReminders), Triple("$dev needs you", "When BF waits for an answer", p.nNeedsYou), Triple("Sync finished", "New notes arrived", p.nSyncDone)),
            { i, v -> when (i) { 0 -> p.nReminders = v; 1 -> p.nNeedsYou = v; else -> p.nSyncDone = v }; r++ },
            listOf(Triple("Say it out loud", "BF speaks reminders", p.nSpeak), Triple("Quiet during night sleep", "No voice in quiet hours", p.nQuietNight)),
            { i, v -> when (i) { 0 -> p.nSpeak = v; else -> p.nQuietNight = v }; r++ })
        Section("Live check") {
            val mu by hub.lastMapsUpdate.collectAsState(); val sent by hub.lastSentToBf.collectAsState()
            val fmt = { t: Long -> java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(t)) }
            Text("Notification access: " + if (notif) "on" else "OFF (BF can't see Maps or music)", style = MaterialTheme.typography.bodyMedium)
            Text("Last Maps update: " + (mu?.let { fmt(it.first) + " · " + it.second } ?: "none yet"), style = MaterialTheme.typography.bodyMedium)
            Text("Last sent to BF: " + (sent?.let { fmt(it.first) + " · " + it.second.replace('|', ' ').take(60) } ?: "none yet"), style = MaterialTheme.typography.bodyMedium)
        }
        Section("Auto-open on BF") {
            var ao by remember { mutableStateOf(p.autoOpen) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Open JAM ON / TAKE ME automatically", Modifier.weight(1f))
                Switch(ao, { ao = it; p.autoOpen = it; hub.scope.launch { hub.pushAutoOpen() } })
            }
            HelpNote("BF shows the music page when a song starts, and the map page when Google Maps navigation starts. It never interrupts a recording, a chat with AI, or a reminder.")
        }
        Section("Permissions") {
            HelpNote("Android asks for these once. Tap an item to open its system setting.")
            PermRow("Notification access", "Lets BF show the song playing (JAM ON) and Google Maps directions (TAKE ME).", notif) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            PermRow("Do Not Disturb access", "Lets Find Phone ring even when the phone is on silent.", dnd) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }
        Section("Show on BF") {
            key(r) {
                Toggle("Music on JAM ON", p.musicEnabled, sub = if (notif) "Song title, artist and play/pause from BF" else "Needs notification access", enabled = notif) { p.musicEnabled = it; r++ }
                Toggle("Maps directions on TAKE ME", p.navEnabled, sub = if (notif) "Next turn, distance and arrival time from Google Maps" else "Needs notification access", enabled = notif) { p.navEnabled = it; r++ }
            }
            Toggle("Pause both for now", paused, sub = if (linked) "Stops sending music and directions until you turn this off" else "Connect BF first", enabled = linked) { hub.setForwardPaused(it) }
        }
    }
}

fun dndAccessGranted(ctx: android.content.Context): Boolean =
    (ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted

/** Permission row: title, one-line purpose, On/Off status, opens the system setting. */
@Composable fun PermRow(title: String, why: String, granted: Boolean, open: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = open).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Surface(shape = CircleShape, color = if (granted) cs.primary else cs.errorContainer, contentColor = if (granted) cs.onPrimary else cs.onErrorContainer) {
            Text(if (granted) "On" else "Off", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
    }
}

/** Battery text: live value, else last known, else "Unknown". Saves the live value. */
@Composable fun rememberBattery(hub: Hub, ready: Boolean): String {
    val info by hub.ble.info.collectAsState()
    val live = info?.batt?.takeIf { it >= 0 && ready }
    LaunchedEffect(live) { live?.let { hub.prefs.lastBatt = it } }
    return when { live != null -> "Battery $live%"; hub.prefs.lastBatt >= 0 -> "Battery ${hub.prefs.lastBatt}% (last known)"; else -> "Battery unknown" }
}

@Composable fun SetPerangkatScreen(hub: Hub, open: (Screen) -> Unit) {
    val info by hub.ble.info.collectAsState()
    val fails by hub.reconnectFails.collectAsState(); var help by remember { mutableStateOf(false) }
    val remoteFw by hub.fwUpdate.remote.collectAsState()
    LaunchedEffect(Unit) { hub.fwUpdate.checkLatest() }
    SubPage {
        if (fails >= 2 || help) ConnectHelpCard(hub)
        TextButton({ help = !help }) { Text("Trouble connecting?") }
        NavCard {
            NavRow(Sym.SystemUpdate, stringResource(Screen.SetFirmware.title), "BF ${id.yanuar.bf01.fw.FirmwareImage.displayVersion(info?.fw)} · available v${remoteFw?.version ?: id.yanuar.bf01.fw.FirmwareImage.LATEST}") { open(Screen.SetFirmware) }
            NavDivider()
            NavRow(Sym.Bluetooth, stringResource(Screen.Pairing.title), "Connect a new BF & send Wi-Fi") { open(Screen.Pairing) }
            NavDivider()
            NavRow(Sym.Person, stringResource(Screen.Kartu.title), "QR codes with logos on BF") { open(Screen.Kartu) }
            NavDivider()
            NavRow(Sym.DeveloperBoard, stringResource(Screen.Menu.title), "BF menu items & order") { open(Screen.Menu) }
            NavDivider()
            NavRow(Sym.Dns, stringResource(Screen.File.title), "Sync recordings, memory card, export") { open(Screen.File) }
        }
    }
}

@Composable fun SetPemakaianScreen(hub: Hub) {
    val p = hub.prefs; val ctx = LocalContext.current; val info by hub.ble.info.collectAsState()
    val usage = rememberLoaded(hub, "pemakaian") { hub.db.usageThisMonth() } ?: Triple(0.0, 0.0, 0)
    val (stt, tts, calls) = usage
    SubPage {
        Section("Usage this month") {
            Text("STT ${"%.1f".format(stt)} min · TTS ${"%.1f".format(tts)} min · $calls chats", style = MaterialTheme.typography.bodyLarge)
            var cap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.monthlyCapMin.toString()) }
            OutlinedTextField(cap, { cap = it.filter(Char::isDigit); p.monthlyCapMin = cap.toIntOrNull() ?: 0 }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Monthly audio limit (minutes)") }, supportingText = { Text("0 = no limit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large)
        }
        Section("About") {
            Text("Pocket Friend v${appVersion(ctx)}", style = MaterialTheme.typography.bodyLarge)
            Text("BF firmware ${info?.fw ?: "–"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Baris tombol yang otomatis jadi tumpuk vertikal saat huruf diperbesar (fontScale > 1.1), supaya label tidak terpotong. */
interface CellScope { fun Modifier.cell(weight: Float): Modifier }
@Composable fun AdaptiveRow(content: @Composable CellScope.() -> Unit) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { val sc = object : CellScope { override fun Modifier.cell(weight: Float) = fillMaxWidth() }; sc.content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { val rs = this
        val sc = object : CellScope { override fun Modifier.cell(weight: Float) = with(rs) { weight(weight) } }; sc.content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ModelPicker(current: String, models: List<id.yanuar.bf01.ai.AiProviders.Model> = id.yanuar.bf01.ai.AiProviders.QWEN_MODELS, title: String = "Chat model", pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val list = if (models.any { it.id == current }) models else models + id.yanuar.bf01.ai.AiProviders.Model(current, "$current (saved)")
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(list.first { it.id == current }.label, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true,
            label = { Text(title) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, shape = MaterialTheme.shapes.large)
        ExposedDropdownMenu(open, { open = false }) {
            list.forEach { m -> DropdownMenuItem({ Column { Text(m.label); Text(m.id, style = MaterialTheme.typography.bodySmall) } }, { pick(m.id); open = false }) }
        }
    }
}


/** v0.3.9: "Saved to memory: …" dengan Undo, supaya pengguna selalu tahu apa yang diingat. */
@Composable fun MemorySavedChip(hub: Hub) {
    val saved by hub.autoMemory.lastSaved.collectAsState()
    val s = saved ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Saved to memory: " + s.facts.joinToString("; "), Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton({ hub.autoMemory.undoLast() }) { Text("Undo") }
            TextButton({ hub.autoMemory.dismissLast() }) { Text("OK") }
        }
    }
}


const val CONNECT_HELP = "Can't connect? On BF: open TWEAKS, hold Forget phone, swipe right to confirm. Then tap Reconnect and enter the PAIR code shown on BF."

/** v0.3.9: bantuan bila bond BF dan HP tidak cocok lagi (Reconnect gagal terus). */
@Composable fun ConnectHelpCard(hub: Hub) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(CONNECT_HELP, style = MaterialTheme.typography.bodyMedium)
            ActionButton("Reconnect", Modifier.fillMaxWidth(), primary = true, icon = Sym.Refresh) { hub.reconnect() }
        }
    }
}


/** v0.3.9.5: grup "Koneksi" ringkas: Bluetooth ✓ / Wi-Fi ✗ (menggantikan chip Wi-Fi on/off). */
@Composable fun KoneksiGroup(hub: Hub, ready: Boolean, info: BfProtocol.Info?) {
    val cs = MaterialTheme.colorScheme
    @Composable fun item(ok: Boolean, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) androidx.compose.material.icons.Icons.Filled.CheckCircle else androidx.compose.material.icons.Icons.Filled.Clear,
            if (ok) "on" else "off", Modifier.size(18.dp), tint = if (ok) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        item(ready, "Bluetooth")
        item(info?.wifiOn == true || hub.server.clients > 0, "Wi-Fi")
    }
}

/** Baris "Last restart" dengan tombol Salin (kembali tampil sejak v0.3.9.5). */
@Composable fun RestartLine(info: BfProtocol.Info?, hub: Hub? = null) {
    val line = info?.let { BfProtocol.lastRestartText(it) } ?: return
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val ctx = LocalContext.current
        TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(buildString {
            appendLine(line)
            appendLine("Details (why/task/addr/breadcrumb): ${info.crashInfo.ifBlank { "-" }}")
            if (info.lastCrash.isNotBlank()) appendLine("Crash: ${info.lastCrash}")
            appendLine("Last AI start: ${info.aiSrc.ifBlank { "-" }}")
            hub?.lastDeviceAiError?.value?.let { (t, m) -> appendLine("Last AI error (${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t))}): $m") }
            append("fw ${info.fw} · app ${appVersion(ctx)}")
        })) }) { Text("Copy") }
    }
}

/** Diagnostik: restart terakhir lengkap, error AI terakhir dari BF, status server di HP. */
@Composable fun DiagnostikCard(hub: Hub, info: BfProtocol.Info?) {
    val aiErr by hub.lastDeviceAiError.collectAsState()
    Section("Diagnostics") {
        if (info == null) Text("Connect BF to see restart info.", style = MaterialTheme.typography.bodyMedium)
        else {
            RestartLine(info, hub)
            if (info.crashInfo.isNotBlank()) Text("Details: ${info.crashInfo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        aiErr?.let { (t, m) -> Text("Last AI chat from BF failed (${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t))}): $m",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(if (hub.server.clients > 0) "AI link on phone: BF is chatting" else "AI link on phone: ready (${hub.prefs.serverHost})",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** v0.3.9.5: Hemat baterai / Waktu tidur — kembali ke Home, tidur layar, mati otomatis. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun WaktuTidurCard(hub: Hub) {
    val p = hub.prefs; val scope = rememberCoroutineScope(); var r by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf("") }; var savedOk by remember { mutableStateOf(true) }
    fun send() = scope.launch {
        saved = "Sending…"; savedOk = true
        val ack = async { kotlinx.coroutines.withTimeoutOrNull(6_000) { hub.setAck.first { s -> s == "I" } } }
        kotlinx.coroutines.yield()
        if (!hub.sendPowerTimes()) { ack.cancel(); saved = "Not saved: BF isn't connected."; savedOk = false; return@launch }
        if (ack.await() != null) { saved = "Saved to BF ✓"; savedOk = true }
        else { saved = "BF didn't confirm. Update BF's firmware or reconnect, then try again."; savedOk = false }
    }
    @OptIn(ExperimentalLayoutApi::class) @Composable fun pick(title: String, opts: List<Pair<Int, String>>, cur: Int, set: (Int) -> Unit) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { (v, l) -> FilterChip(cur == v, { set(v); r++; send() }, { Text(l) }) }
        }
    }
    Section("Sleep timer") {
        key(r) {
            pick("Back to Home after idle", listOf(5 to "5 s", 10 to "10 s", 20 to "20 s", 30 to "30 s"), p.homeIdleSec) { p.homeIdleSec = it }
            pick("Screen sleeps after", listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min"), p.sleepSec) { p.sleepSec = it }
            pick("Auto power off while asleep", listOf(0 to "Never", 15 to "15 min", 30 to "30 min", 60 to "60 min"), p.autoOffMin) { p.autoOffMin = it }
        }
        if (saved.isNotEmpty()) Text(saved, style = MaterialTheme.typography.bodyMedium,
            color = if (savedOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        HelpNote("Sleeping sooner saves battery. Auto power off saves the most, but BF needs the PWR button to turn back on and reminders won't ring while off.")
    }
}

/** v0.3.9.7: AI chats started on BF, as WhatsApp-style threads with your voice transcribed. */
@Composable fun AiChatsScreen(hub: Hub) {
    val rev by hub.revision.collectAsState()
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val threads = rememberLoaded(hub, "aithreads", rev) { hub.db.aiThreads() }
    if (open.isNotEmpty()) {
        val msgs = rememberLoaded(hub, "aithread_$open", rev, open) { hub.db.aiThread(open) }
        androidx.activity.compose.BackHandler { open = "" }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PfBackHeader("Chat", "Ask BF · kept on this phone", { open = "" })
            msgs?.forEach { m -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.role == "user") Arrangement.End else Arrangement.Start) {
                Card(Modifier.widthIn(max = 300.dp), colors = CardDefaults.cardColors(if (m.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        if (m.role == "user") Text("🎤 Voice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.text, style = MaterialTheme.typography.bodyLarge)
                        Text(fmtClock.format(Date(m.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    } } } }
        }
        return
    }
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val rows = (threads ?: emptyList()).filter { q.isBlank() || it.second.text.contains(q, true) }.map { (tid, last) ->
        ChatRowUi(tid, last.text.take(32).ifBlank { "Chat" }, (if (last.role == "user") "You: " else "BF: ") + last.text, fmtClock.format(Date(last.time)), dayLabel(last.time), hub.aiUnread(tid)) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ChatHistoryContent(rows, q, { q = it }, { hub.openScreen?.invoke(Screen.Ngobrol) }, { open = it }, null)
        Spacer(Modifier.height(24.dp))
    }
}
@Composable fun PrivacyScreen(hub: Hub) {
    val rev by hub.revision.collectAsState()
    val notes = rememberLoaded(hub, "priv_n", rev) { hub.db.notes().count { it.type == "NOTE" } } ?: 0
    val todos = rememberLoaded(hub, "priv_t", rev) { hub.db.tasks().size } ?: 0
    val chats = rememberLoaded(hub, "priv_c", rev) { hub.db.chat().size } ?: 0
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        PrivacyContent(notes, todos, chats, "-", hub.prefs.keepAudio, hub.prefs.autoDeleteChats,
            { hub.prefs.keepAudio = it }, { hub.prefs.autoDeleteChats = it },
            onExport = {
                val body = hub.db.notes().joinToString("\n\n") { id.yanuar.bf01.svc.NoteExport.text(it) }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, body.ifBlank { "No notes yet." })
                }
                ctx.startActivity(android.content.Intent.createChooser(send, "Export my data"))
            },
            onClear = { confirm = true })
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Clear data?") },
        text = { Text("Deletes notes, to-dos and chats on this phone. BF recordings stay on the device.") },
        confirmButton = { TextButton({ hub.db.clearChat(); confirm = false }) { Text("Clear chats", color = Pop.Red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@Composable fun ConnHelpScreen(hub: Hub) {
    val info by hub.ble.info.collectAsState()
    val dev = hub.prefs.deviceName.ifBlank { "BF" }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ConnHelpContent(dev, "Recently" + (info?.let { " · battery ${it.batt}%" } ?: ""),
            onReconnect = { hub.reconnect() },
            onRing = { hub.scope.launch { hub.ble.write(id.yanuar.bf01.ble.BfProtocol.FIND, id.yanuar.bf01.ble.BfProtocol.find(true)) } },
            onMaps = {})
    }
}

@Composable fun ThemeBoardScreen(hub: Hub) {
    var mode by remember { mutableStateOf(hub.prefs.themeMode) }
    var accent by remember { mutableStateOf(hub.prefs.accent) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ThemeBoardContent(mode, accent, {
            mode = it; AppTheme.set(it)
        }, {
            accent = it; AppTheme.pickAccent(it)
        })
    }
}

@Composable fun SearchScreen(hub: Hub, back: () -> Unit) {
    val rev by hub.revision.collectAsState()
    val notes = rememberLoaded(hub, "search_notes", rev) { hub.db.notes() }
    val tasks = rememberLoaded(hub, "search_tasks", rev) { hub.db.tasks() }
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(hub.prefs.plainRecent()) }
    fun saveRecent(term: String) {
        val t = term.trim(); if (t.isEmpty()) return
        recent = (listOf(t) + recent.filter { !it.equals(t, true) }).take(8)
        hub.prefs.setPlainRecent(recent)
    }
    val results = remember(notes, tasks, q, filter) {
        if (q.isBlank()) emptyList() else {
            val qq = q.trim()
            buildList {
                if (filter != 2) notes.orEmpty().filter { it.type == "NOTE" }.forEach { n ->
                    val hay = n.summary + n.transcript + n.tags + n.file
                    if (hay.contains(qq, true)) add("note:${n.file}" to (n.summary.ifBlank { n.transcript }.ifBlank { n.file }.take(80)))
                }
                if (filter == 0) tasks.orEmpty().forEach { t ->
                    if (t.title.contains(qq, true) || t.detail.contains(qq, true)) add("task:${t.id}" to t.title)
                }
            }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SearchContent(q, { q = it }, filter, { filter = it }, recent, { q = it; saveRecent(it) },
            { t -> recent = recent.filter { it != t }; hub.prefs.setPlainRecent(recent) }, results,
            onOpen = { id -> when {
                id.startsWith("note:") -> hub.openScreen?.invoke(Screen.Catatan) // detail via notes tab for now
                id.startsWith("task:") -> hub.openScreen?.invoke(Screen.Tugas)
            } }, back = back)
    }
}

@Composable fun OnboardScreen(hub: Hub, done: () -> Unit) {
    var step by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(hub.prefs.deviceName.ifBlank { "BF" }) }
    var face by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var tries by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(2) }
    var aiFace by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("☻") }
    var bt by remember { mutableStateOf(true) }; var notif by remember { mutableStateOf(true) }; var mic by remember { mutableStateOf(true) }
    val wifi = hub.prefs.wifi(0).first.ifBlank { "Choose Wi-Fi" }
    when (step) {
        0 -> OnboardWelcome(onStart = { step = 1 }, onHave = { hub.prefs.onboarded = true; done() })
        1 -> OnboardSearching(onCancel = { step = 0 })
        2 -> OnboardPairCode("472", "Signal good · ready", onPair = { step = 3 }, onBack = { step = 1 })
        3 -> OnboardCharacter(face, { face = it }, onContinue = { step = 4 },
            onPhoto = { step = 30 }, onAi = { step = 31 })
        30 -> CharacterPhotoFlow(onPick = {}, onContinue = { step = 4 }, onBack = { step = 3 })
        31 -> CharacterAiFlow(tries, aiFace,
            onTry = { if (tries > 0) { tries -= 1; aiFace = listOf("☺", "☻", "◉", "◈").random() } },
            onUse = { step = 4 }, onBack = { step = 3 })
        4 -> OnboardName(name, { name = it }, onNext = { hub.prefs.deviceName = name.trim().ifBlank { "BF" }; step = 5 })
        5 -> OnboardWifiPerms(wifi, bt, notif, mic, onWifi = { hub.openScreen?.invoke(Screen.SetJaringan) },
            onToggle = { label, v -> when (label) { "Bluetooth" -> bt = v; "Notifications" -> notif = v; "Microphone" -> mic = v } },
            onContinue = { step = 6 })
        else -> OnboardDone(hub.prefs.deviceName.ifBlank { "BF" }) { hub.prefs.onboarded = true; done() }
    }
    if (step == 1) LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); step = 2 }
}
