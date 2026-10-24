package id.yanuar.bf01.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.svc.BfService
import id.yanuar.bf01.R
import androidx.compose.ui.res.stringResource
import id.yanuar.bf01.svc.Hub

enum class Screen(@androidx.annotation.StringRes val title: Int) {
    Beranda(R.string.nav_home), Pairing(R.string.t_pairing), Catatan(R.string.nav_notes), Tugas(R.string.t_tasks), Ngobrol(R.string.nav_ask),
    Kartu(R.string.t_card), Menu(R.string.t_menu), File(R.string.t_file), Pengaturan(R.string.nav_settings),
    SetAi(R.string.t_ai), SetSuara(R.string.t_voice), SetJaringan(R.string.t_network), SetIzin(R.string.t_perms),
    SetPerangkat(R.string.t_device), SetPemakaian(R.string.t_usage), SetFirmware(R.string.t_firmware)
}

/** Tumpukan layar sederhana untuk tombol/gesture kembali.
 *  - Sub-layar (Pairing, Kartu, Menu, File) kembali ke layar sebelumnya.
 *  - Tab selain Beranda kembali ke Beranda.
 *  - Hanya dari Beranda app keluar (canGoBack = false). */
class NavStack(start: Screen) {
    val stack = mutableStateListOf(start).also { if (start != Screen.Beranda) it.add(0, Screen.Beranda) }
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    /** Pindah tab: tumpukan jadi [Beranda, tab]. */
    fun tab(s: Screen) { stack.clear(); stack.add(Screen.Beranda); if (s != Screen.Beranda) stack.add(s) }
    /** Buka sub-layar di atas layar sekarang. */
    fun open(s: Screen) { if (current != s) stack.add(s) }
    fun back(): Boolean { if (!canGoBack) return false; stack.removeAt(stack.lastIndex); return true }

    companion object {
        /** Simpan tumpukan saat rotasi / proses dimatikan sistem. */
        val Saver: androidx.compose.runtime.saveable.Saver<NavStack, List<String>> = androidx.compose.runtime.saveable.Saver(
            save = { it.stack.map(Screen::name) },
            restore = { names -> NavStack(Screen.Beranda).apply { stack.clear(); stack.addAll(names.map(Screen::valueOf)) } })
    }
}

/** Data layar yang dimuat di latar (IO), dengan nilai awal dari cache Hub supaya scroll tidak terpotong ke atas saat kembali. */
@Suppress("UNCHECKED_CAST")
@Composable fun <T : Any> rememberLoaded(hub: Hub, key: String, vararg keys: Any?, load: () -> T): T? {
    var v by remember { mutableStateOf(hub.uiCache[key] as T?) }
    LaunchedEffect(*keys) {
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { load() }
        hub.uiCache[key] = r; v = r
    }
    return v
}

class MainActivity : ComponentActivity() {
    private val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it[if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.ACCESS_FINE_LOCATION] == true && Hub.get(this).prefs.linkEnabled)
                BfService.start(this)
        }
        launcher.launch(perms)
        val hub = Hub.get(this)
        enableEdgeToEdge()
        setContent { PocketFriendTheme(dark = true) { App(hub) } }
    }
}

/** 5 tab utama Pocket Friend (bottom navigation M3). Judul dot-matrix di header bersama. */
private val tabs = listOf(Screen.Beranda to R.string.nav_home, Screen.Ngobrol to R.string.nav_ask, Screen.Tugas to R.string.nav_todo,
    Screen.Catatan to R.string.nav_notes, Screen.Pengaturan to R.string.nav_settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(hub: Hub, initial: Screen? = null, sheetOpen: Boolean = false) {
    val nav = androidx.compose.runtime.saveable.rememberSaveable(saver = NavStack.Saver) { NavStack(initial ?: if (hub.prefs.deviceAddress == null) Screen.Pairing else Screen.Beranda) }
    val screen = nav.current
    // Tiap layar punya penyimpanan state sendiri (scroll, isian yang belum disimpan, bagian yang dibuka).
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    // Gesture/tombol kembali (predictive back aktif lewat manifest). Di Beranda tidak ditangkap -> app keluar.
    androidx.activity.compose.BackHandler(enabled = nav.canGoBack) { nav.back() }
    val isTab = tabs.any { it.first == screen }
    var sheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(sheetOpen) }
    androidx.activity.compose.BackHandler(enabled = sheet) { sheet = false }
    val status = rememberBfStatus(hub)
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                if (isTab) DotText(stringResource(screen.title).uppercase(), Dot.L, 1.2f)
                else Text(stringResource(screen.title), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }, navigationIcon = { if (!isTab) IconButton({ nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { StatusPill(status, compact = !isTab) { sheet = true }; Spacer(Modifier.width(12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { (s, label) ->
                    val sel = s == screen || (!isTab && s == Screen.Pengaturan)
                    NavigationBarItem(sel, { nav.tab(s) }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip) }, icon = {
                        when (s) {
                            Screen.Ngobrol -> BfNavIcon()
                            else -> Icon(when (s) { Screen.Beranda -> Icons.Default.Home; Screen.Tugas -> Icons.Default.CheckCircle
                                Screen.Catatan -> Icons.Default.Edit; else -> Icons.Default.Settings }, null)
                        } })
                }
            }
        }) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp)) {
            holder.SaveableStateProvider(screen.name) { when (screen) {
                Screen.Beranda -> BerandaScreen(hub)
                Screen.Pairing -> PairingScreen(hub) { if (!nav.back()) nav.tab(Screen.Beranda) }
                Screen.Catatan -> CatatanScreen(hub)
                Screen.Tugas -> TugasScreen(hub)
                Screen.Ngobrol -> NgobrolScreen(hub)
                Screen.Kartu -> KartuScreen(hub)
                Screen.Menu -> MenuScreen(hub)
                Screen.File -> FileScreen(hub)
                Screen.Pengaturan -> PengaturanScreen(hub) { nav.open(it) }
                Screen.SetAi -> SetAiScreen(hub)
                Screen.SetSuara -> SetSuaraScreen(hub)
                Screen.SetJaringan -> SetJaringanScreen(hub)
                Screen.SetIzin -> SetIzinScreen(hub)
                Screen.SetPerangkat -> SetPerangkatScreen(hub) { nav.open(it) }
                Screen.SetFirmware -> FirmwareScreen(hub)
                Screen.SetPemakaian -> SetPemakaianScreen(hub)
            } }
        }
    }
    StatusSheet(hub, status, sheet) { sheet = false }
    }
}

@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content() } }
}

@Composable fun <T> SimpleList(items: List<T>, row: @Composable (T) -> Unit) {
    LazyColumn { items(items) { Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { row(it) } } }
}

@Composable fun Toggle(label: String, value: Boolean, sub: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled) { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp)); Switch(value, onChange, enabled = enabled) }
}

/** Tombol aksi konsisten: tonal, pil, ikon + label pendek, teks tidak terpotong. */
@Composable fun ActionButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), primary: Boolean = false, enabled: Boolean = true,
                             icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    val pad = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    if (primary) Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, contentPadding = pad, content = content)
    else FilledTonalButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, contentPadding = pad, content = content)
}
