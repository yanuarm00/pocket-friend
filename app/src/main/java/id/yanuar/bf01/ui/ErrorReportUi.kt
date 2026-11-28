package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.yanuar.bf01.BuildConfig
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.fw.FirmwareImage
import id.yanuar.bf01.svc.ErrorReport
import id.yanuar.bf01.svc.Hub
import kotlinx.coroutines.launch

/** Stateless body, so it can be screenshot-tested. */
@Composable fun ErrorReportContent(report: String, canRefresh: Boolean, onRefresh: () -> Unit, onShare: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PfHeader("Error report", "Read it first. Share only if you want.")
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = cardColor(), shadowElevation = 1.dp) {
            SelectionContainer {
                Text(report, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
        PfOrangeButton("Share report", Modifier.fillMaxWidth(), onShare)
        if (canRefresh) PfChip("Refresh from BF", false, Sym.Refresh, onRefresh)
    }
}

@Composable fun ErrorReportScreen(hub: Hub) {
    val ctx = LocalContext.current
    val info by hub.ble.info.collectAsState(); val st by hub.ble.state.collectAsState()
    val ready = st == BleManager.State.READY
    LaunchedEffect(ready) { if (ready) hub.ble.refreshInfo() }
    val report = remember(info) {
        ErrorReport.build(info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, android.os.Build.VERSION.RELEASE ?: "?", android.os.Build.VERSION.SDK_INT,
            info?.fw?.let { FirmwareImage.displayVersion(it) })
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ErrorReportContent(report, ready, { hub.scope.launch { hub.ble.refreshInfo() } }, {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_SUBJECT, "Pocket Friend error report"); putExtra(android.content.Intent.EXTRA_TEXT, report) }
            ctx.startActivity(android.content.Intent.createChooser(send, "Share error report"))
        })
        Spacer(Modifier.height(PopNavClearance))
    }
}
