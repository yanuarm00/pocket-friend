package id.yanuar.bf01.ui

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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun BerandaScreen(hub: Hub) {
    val st by hub.ble.state.collectAsState(); val info by hub.ble.info.collectAsState(); val log by hub.log.collectAsState()
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current; val rev by hub.revision.collectAsState()
    // Baca DB & kalender saat komposisi pertama (belum ada di layar saat frame pertama = stutter).
    val a = rememberLoaded(hub, "beranda", rev) { id.yanuar.bf01.svc.CalendarSource.todayLines(ctx) to hub.db.tasks().filter { !it.done } }
    val agenda = a?.first; val tasks = a?.second
    val today = (agenda ?: emptyList()) + (tasks ?: emptyList()).map { "• ${it.title}" }
    val now = remember { Date() }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BubbleCard(Modifier.fillMaxWidth(), tail = 22.dp) {
            DotText(fmtClock.format(now), Dot.XL, 2.6f)
            Spacer(Modifier.height(16.dp))
            DotPill("TODAY", head = false)  // tanpa kepala tak terlihat: teks benar-benar di tengah
            Spacer(Modifier.height(12.dp))
            if (today.isEmpty()) Text("Nothing today. Take it easy.", style = MaterialTheme.typography.bodyLarge)
            today.take(4).forEach { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 3.dp)) }
            if (today.size > 4) Text("+${today.size - 4} more", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DotText(fmtDate.format(now), Dot.S, 1.05f, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.weight(1f)); BatteryDots(info?.batt?.takeIf { it >= 0 })
            }
        }
        val ready = st == BleManager.State.READY
        val linked by hub.linked.collectAsState()
        Section("Device") {
            val batt = rememberBattery(hub, ready)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bs = rememberBfStatus(hub); BfFace(48.dp, bs.mood, bs.filled, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DotText(linkLabel(linked, st), Dot.S, 1.1f)
                    Text(batt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            HelpNote(if (!linked) "BF stays disconnected until you tap Connect."
                else if (ready) "BF is ready to chat." else "Waiting for BF. Press PWR on BF if it is asleep.")
            info?.let { i -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({}, { Text("FW ${i.fw}") })
                AssistChip({}, { Text(if (i.sd) "Memory card ${"%.1f".format(i.sdFreeMb / 1024f)} GB free" else "No memory card") })
                AssistChip({}, { Text(if (i.wifiOn) "Wi-Fi on" else "Wi-Fi off") })
            } }
            if (linked) HelpNote(if (hub.server.clients > 0) "Phone server: BF is chatting" else "Phone server ready on port 8000")
            LinkToggle(hub, linked)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(stringResource(R.string.home_sync), Modifier.weight(1f), enabled = ready, icon = Sym.CloudSync) { scope.launch { hub.syncAll() } }
                ActionButton(stringResource(R.string.home_reload), Modifier.weight(1f), enabled = linked, icon = Sym.Refresh) { scope.launch { hub.ble.refreshInfo() } }
            }
            if (linked && !ready) HelpNote("Sync is available once BF is connected.")
        }
        if (hub.findPhone.ringing) Button({ hub.findPhone.stop() }, Modifier.fillMaxWidth()) { Text("Stop phone ringing") }
        Section("Log") { log.takeLast(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@SuppressLint("MissingPermission")
@Composable fun PairingScreen(hub: Hub, done: () -> Unit) {
    val ctx = LocalContext.current; var code by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }; var msg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        @Suppress("DEPRECATION")
        val sr: ScanResult? = r.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        val dev = sr?.device ?: return@rememberLauncherForActivityResult
        hub.prefs.deviceAddress = dev.address; hub.prefs.deviceName = sr.scanRecord?.deviceName ?: ""
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

@Composable fun CatatanScreen(hub: Hub) {
    val rev by hub.revision.collectAsState(); val notes = rememberLoaded(hub, "notes", rev) { hub.db.notes() }
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // Filter hanya dihitung saat input/kumpulan data berubah, bukan tiap ketikan karakter lain direkomposisi.
    val shown = remember(notes, q) { (notes ?: emptyList()).filter { it.type != "TASK" }.filter { q.isBlank() || (it.transcript + it.summary + it.tags + it.file).contains(q, true) } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.extraLarge,
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null) }, placeholder = { Text("Search notes") })
        HelpNote("${shown.size} notes · voice notes from BF show up here.", "Recordings saved as NOTE land here with a transcript and short summary. TASK recordings go to To-do instead. The audio stays on BF too.")
        if (shown.isEmpty()) Text("No notes yet.")
        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shown, key = { it.id }) { n ->
                BubbleCard(Modifier.fillMaxWidth(), tail = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotText(fmtNote.format(Date(n.created)), Dot.S, 0.95f)
                        Spacer(Modifier.weight(1f)); Text(n.file, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (n.summary.isNotBlank()) Text(n.summary, style = MaterialTheme.typography.titleMedium)
                        }
                        if (n.localPath.isNotEmpty()) PlayButton(n.localPath)
                    }
                    Text(n.transcript.ifBlank { if (n.localPath.isEmpty()) "(audio still on BF, syncs soon)" else "(transcribing…)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (n.tags.isNotBlank()) { Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
                            DotPill(t, head = false) } } }
                }
            }
        }
    }
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
    fun add() { if (t.isNotBlank()) { hub.db.addTask(t.trim()); t = ""; adding = false; r++ } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // Agenda hari ini: baris biasa (bukan pil yang terlihat seperti tombol) + chip izin yang memang tombol.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.CalendarToday, null, tint = cs.onSurfaceVariant); Spacer(Modifier.width(10.dp))
                    Text(if (agenda.isNullOrEmpty()) "Today · empty" else "Today · ${agenda.size} events", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Spacer(Modifier.width(12.dp)) }
                    if (!calOk) AssistChip({ calLauncher.launch(android.Manifest.permission.READ_CALENDAR) }, { Text("Allow calendar") },
                        leadingIcon = { Icon(Sym.Lock, null, Modifier.size(18.dp)) })
                }
                agenda?.forEach { a -> Row(Modifier.padding(start = 34.dp)) {
                    Text(a.substringBefore(' '), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                    Text(a.substringAfter(' '), style = MaterialTheme.typography.bodyLarge) } }
            }
            if (adding) OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text("New task") }, singleLine = true, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                trailingIcon = { IconButton({ add() }, enabled = t.isNotBlank()) { Icon(Sym.Add, "Add") } })
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(cs.surfaceContainerHigh)) {
                Row(Modifier.padding(start = 22.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Task", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${tasks?.count { it.done } ?: 0}/${tasks?.size ?: 0}", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                    InfoButton("Task tips", "• Swipe a task left to delete it. An Undo button appears right after.\n" +
                        "• You can also add one by voice: hold the button on BF, speak, then choose TASK.\n" +
                        "• Send to BF sends your events, tasks and reminders for the next 48 hours, because the phone can't reach BF while it sleeps.")
                }
                if (tasks?.isEmpty() == true) Text("No tasks yet. Tap + Task to add one.", style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                tasks?.forEach { k -> key(k.id) {
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v == SwipeToDismissBoxValue.EndToStart) {
                            pending.add(k.id)
                            scope.launch {
                                var undo = false
                                try { undo = snack.showSnackbar("Task deleted", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed }
                                finally { if (!undo) hub.db.deleteTask(k.id); pending.remove(k.id); r++ }
                            }
                            true
                        } else false })
                    SwipeToDismissBox(state, enableDismissFromStartToEnd = false, backgroundContent = {
                        Box(Modifier.fillMaxSize().background(cs.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Sym.Delete, "Delete", tint = cs.onErrorContainer) } }) {
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { hub.db.setDone(k.id, !k.done); r++ }
                            .padding(start = 10.dp, end = 22.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(k.done, { hub.db.setDone(k.id, it); r++ })
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(k.title, style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (k.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    color = if (k.done) cs.onSurfaceVariant else cs.onSurface)
                                if (k.detail.isNotBlank()) Text(k.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                } }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton("Send to BF", Modifier, icon = Sym.Sync) {
                    scope.launch { hub.syncTime(); hub.ble.write(BfProtocol.NOTIFY, BfProtocol.agenda(agenda ?: emptyList())); hub.pushTasks(); hub.pushAlarms()
                        sentAt = fmtClock.format(Date()) } }
                if (sentAt.isNotEmpty()) { Spacer(Modifier.width(12.dp))
                    Text("last $sentAt", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        ExtendedFloatingActionButton(text = { Text("Task") }, icon = { Icon(Sym.Add, null) },
            onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd))
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
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val cs = MaterialTheme.colorScheme; val ai = hub.prefs.aiName
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
        LaunchedEffect(chat?.size, pending, busy, err) { listScroll.animateScrollTo(listScroll.maxValue) }
        Column(Modifier.weight(1f).verticalScroll(listScroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (empty) Spacer(Modifier.height(28.dp))
            // Greeting speech bubble (always with a tail); the persona & memory dropdown sits inside at its bottom.
            BubbleCard(Modifier.fillMaxWidth(), tail = 18.dp) {
                if (empty) {
                    Text(stringResource(R.string.ask_hello), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ask_intro, ai), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.15f))
                    Spacer(Modifier.height(4.dp))
                }
                val rot by androidx.compose.animation.core.animateFloatAsState(if (more) 180f else 0f, label = "chev")
                val ink = LocalContentColor.current
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { more = !more }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(ink.copy(alpha = 0.08f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Sym.Person, null, Modifier.size(20.dp), tint = ink) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ask_persona_title, ai), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.ask_persona_sub, if (hub.prefs.llmProvider == "qwen") "Qwen" else "Grok", ai),
                            style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
                    }
                    Icon(Sym.ExpandMore, stringResource(if (more) R.string.collapse else R.string.expand), Modifier.graphicsLayer { rotationZ = rot }, tint = ink)
                }
                androidx.compose.animation.AnimatedVisibility(more) {
                    // Inside the bubble the colors are inverted, so fields use the opposite scheme.
                    MaterialTheme(colorScheme = if (LocalBubble.current.container == androidx.compose.ui.graphics.Color.White) PfLight else PfDark) {
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
                            OutlinedTextField(mem, { mem = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ask_memory_add)) }, shape = MaterialTheme.shapes.large,
                                trailingIcon = { IconButton({ if (mem.isNotBlank()) { hub.db.addMemory(mem.trim()); mem = ""; r++ } }, enabled = mem.isNotBlank()) { Icon(Sym.Add, stringResource(R.string.add)) } })
                            OutlinedButton({ confirmClear = true }, Modifier.heightIn(min = 48.dp), enabled = !chat.isNullOrEmpty()) {
                                Icon(Sym.DeleteSweep, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ask_clear)) }
                        }
                    }
                }
            }
            if (empty) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    saran.forEach { s -> val t = stringResource(s); SuggestionChip({ send(t) }, { Text(t, Modifier.padding(vertical = 8.dp)) }, Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            chat?.forEach { c ->
                ChatBubble(c.text, fromBf = c.role != "user",
                    header = (if (c.role == "user") "Mas Yan" else ai) + " · " + fmtClock.format(Date(c.time)))
            }
            if (pending.isNotEmpty()) ChatBubble(pending, fromBf = false, header = "Mas Yan" + if (err.isNotEmpty()) " · not sent" else "")
            if (busy) ThinkingBubble(ai)
            if (err.isNotEmpty() && !busy) ChatErrorCard(err, onRetry = { send(pending) }, onDismiss = { msg = pending; pending = ""; err = "" })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(msg, { msg = it }, Modifier.weight(1f).focusRequester(focus), placeholder = { Text(stringResource(R.string.ask_hint, ai)) }, shape = MaterialTheme.shapes.extraLarge, maxLines = 3)
            Spacer(Modifier.width(10.dp))
            // Not focusable, so tapping Send keeps the keyboard and focus on the text field.
            FloatingActionButton({ send(msg) }, Modifier.focusProperties { canFocus = false }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
        }
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
@Composable fun ChatErrorCard(text: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = cs.errorContainer, contentColor = cs.onErrorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Filled.Warning, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("No reply", style = MaterialTheme.typography.titleSmall) }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onRetry, Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Sym.RestartAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Retry", maxLines = 1) }
                OutlinedButton(onDismiss, Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onErrorContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.onErrorContainer.copy(alpha = 0.5f))) { Text("Edit", maxLines = 1) }
            }
        }
    }
}

/** "Test key" button + result line (Connected / failed with reason). */
@Composable fun TestKeyRow(hub: Hub, onDone: () -> Unit) {
    val scope = rememberCoroutineScope(); var busy by remember { mutableStateOf(false) }; var res by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    ActionButton(if (busy) "Testing…" else "Test key", enabled = !busy, icon = Sym.SmartToy) { busy = true; res = null
        scope.launch { val r = id.yanuar.bf01.ai.testKey(hub.prefs)
            res = r.fold({ true to it }, { false to "Failed · " + id.yanuar.bf01.ai.friendlyError(it) +
                if (hub.prefs.llmProvider == "qwen") "\nTried: " + id.yanuar.bf01.ai.QwenUrl.host(hub.prefs.qwenBaseUrl) else "" }); busy = false; onDone() } }
    res?.let { (ok, t) -> Text(t, style = MaterialTheme.typography.bodyMedium,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
}

@Composable fun KartuScreen(hub: Hub) {
    val p = hub.prefs; val scope = rememberCoroutineScope()
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.cardName) }; var title by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.cardTitle) }
    var phone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.cardPhone) }; var msg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.cardMsg) }; var status by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    fun save() { p.cardName = name; p.cardTitle = title; p.cardPhone = phone; p.cardMsg = msg }
    val link = "https://wa.me/${phone.filter(Char::isDigit)}?text=${Uri.encode(msg)}"
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Name") })
        OutlinedTextField(title, { title = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Job title") })
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("WhatsApp number (62…)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(msg, { msg = it }, Modifier.fillMaxWidth(), label = { Text("Opening message") })
        QrPreview(link); Text(link, style = MaterialTheme.typography.bodySmall)
        ActionButton("Save & send", primary = true, icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send) { save(); scope.launch { status = if (hub.sendNameCard()) "Sent to BF" else "Failed: BF is not connected" } }
        Text(status)
    }
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
            ActionButton(stringResource(R.string.rec_sync), primary = true, enabled = ble == BleManager.State.READY && !busy, icon = Sym.CloudSync) {
                scope.launch { hub.recordings.run(manual = true) } }
            if (ble != BleManager.State.READY) HelpNote("Sync is available once BF is connected.")
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
@Composable fun PlayButton(path: String) {
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release(); player = null } }
    val playing = player != null
    FilledTonalIconButton({
        if (playing) { player?.release(); player = null }
        else player = runCatching { android.media.MediaPlayer().apply { setDataSource(path); setOnCompletionListener { it.release(); player = null }; prepare(); start() } }.getOrNull()
    }, Modifier.size(48.dp)) { Icon(if (playing) androidx.compose.material.icons.Icons.Default.Close else androidx.compose.material.icons.Icons.Default.PlayArrow, if (playing) "Stop" else "Play") }
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
@Composable fun PengaturanScreen(hub: Hub, open: (Screen) -> Unit = {}) {
    val p = hub.prefs; val ctx = LocalContext.current
    val linked by hub.linked.collectAsState(); val paused by hub.forwardPaused.collectAsState(); val st by hub.ble.state.collectAsState()
    val usage = rememberLoaded(hub, "pemakaian") { hub.db.usageThisMonth() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WheresMineCard(hub)
        NotifAccessCard()
        NavCard {
            NavRow(Sym.SmartToy, "AI provider", (if (p.llmProvider == "qwen") "Qwen · " + (if (p.qwenKey.isBlank()) "no key yet" else "key saved")
                else "Grok · " + (if (p.xaiKey.isBlank()) "no key yet" else "key saved"))) { open(Screen.SetAi) }
            NavDivider()
            NavRow(Sym.RecordVoiceOver, "BF & voice", "Sleep ${p.sleepSec} s · quiet hours ${p.quietStart}–${p.quietEnd}") { open(Screen.SetSuara) }
            NavDivider()
            NavRow(Sym.Bluetooth, "Connection & network", "${linkLabel(linked, st).lowercase().replaceFirstChar { it.uppercase() }} · Wi-Fi, phone server") { open(Screen.SetJaringan) }
            NavDivider()
            NavRow(Sym.Notifications, "Music, Maps & alerts", "What BF can show from your phone") { open(Screen.SetIzin) }
            NavDivider()
            NavRow(Sym.DeveloperBoard, "Device", "Pairing, name card, BF menu, files") { open(Screen.SetPerangkat) }
            NavDivider()
            NavRow(Sym.BarChart, "Usage & about", "${usage?.let { "%.0f".format(it.first + it.second) } ?: "–"} min of audio this month · v${appVersion(ctx)}") { open(Screen.SetPemakaian) }
        }
    }
}

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
                Button({ val on = !ringing; ringing = on; scope.launch { hub.ble.write(BfProtocol.FIND, BfProtocol.find(on)) } }, Modifier.cell(1f).heightIn(min = 48.dp),
                    colors = bc, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(if (ringing) Sym.StopCircle else Sym.NotificationsActive, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (ringing) "Stop" else "Ring", maxLines = 1) }
                Button({ openMaps(ctx, p.lastLat, p.lastLon) }, Modifier.cell(1.25f).heightIn(min = 48.dp), enabled = has, colors = bc,
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
                else Text("Region: ${id.yanuar.bf01.ai.QwenUrl.regionName(norm)}\nChat & speech: $norm/chat/completions", style = MaterialTheme.typography.bodySmall)
                HelpNote(if (custom) "A workspace URL is used exactly as entered. The key must come from that workspace." else "The key must match the region. Test key finds the right region by itself.")
                ModelPicker(qm) { qm = it; p.qwenModel = it }
                HelpNote("Speech recognition: ${QwenStt.MODEL} (Indonesian).")
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
                OutlinedButton({ p.rotateToken(); r++ }, Modifier.cell(1f).heightIn(min = 48.dp)) {
                    Icon(Sym.Key, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("New token", maxLines = 1) }
            }
            HelpNote("After changing the token, send it again from Pairing.")
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
    SubPage {
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
    SubPage {
        NavCard {
            NavRow(Sym.SystemUpdate, stringResource(Screen.SetFirmware.title), "BF ${id.yanuar.bf01.fw.FirmwareImage.displayVersion(info?.fw)} · latest v${id.yanuar.bf01.fw.FirmwareImage.LATEST}") { open(Screen.SetFirmware) }
            NavDivider()
            NavRow(Sym.Bluetooth, stringResource(Screen.Pairing.title), "Connect a new BF & send Wi-Fi") { open(Screen.Pairing) }
            NavDivider()
            NavRow(Sym.Person, stringResource(Screen.Kartu.title), "WhatsApp QR on BF's screen") { open(Screen.Kartu) }
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
@Composable fun ModelPicker(current: String, models: List<id.yanuar.bf01.ai.AiProviders.Model> = id.yanuar.bf01.ai.AiProviders.QWEN_MODELS, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val list = if (models.any { it.id == current }) models else models + id.yanuar.bf01.ai.AiProviders.Model(current, "$current (saved)")
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(list.first { it.id == current }.label, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true,
            label = { Text("Chat model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, shape = MaterialTheme.shapes.large)
        ExposedDropdownMenu(open, { open = false }) {
            list.forEach { m -> DropdownMenuItem({ Column { Text(m.label); Text(m.id, style = MaterialTheme.typography.bodySmall) } }, { pick(m.id); open = false }) }
        }
    }
}
