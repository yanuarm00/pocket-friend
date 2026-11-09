package id.yanuar.bf01.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.svc.BatteryStats
import kotlinx.coroutines.launch

@Composable fun BatteryCard(info: BfProtocol.Info?) {
    val ctx = LocalContext.current
    Section("Battery") {
        if (info == null) { Text("Connect BF to see battery details.", style = MaterialTheme.typography.bodyMedium); return@Section }
        val r = remember(info) { BatteryStats.report(ctx, info) }
        if (r == null) {
            Text("Battery ${info.batt.coerceAtLeast(0)}%. Update BF firmware to v0.3.9.3-test or newer for time left and usage details.",
                style = MaterialTheme.typography.bodyMedium); return@Section
        }
        val pts = remember(info) {
            BatteryStats.points(ctx).ifEmpty { BatteryStats.demoPoints() }
        }
        val features = r.parts.map { it.label to it.pct }
        val scope = rememberCoroutineScope()
        val demo = BatteryStats.points(ctx).size < 2
        val leftLabel = r.hoursLeft?.let { h ->
            if (h >= 1) { val hh = h.toInt(); val mm = ((h - hh) * 60).toInt().coerceIn(0, 59); "$hh h ${mm} m" } else "under 1 hour"
        }
        BatteryReportContent(
            pct = r.pct, charging = r.charging,
            hoursLeft = leftLabel,
            features = features.ifEmpty { listOf("Voice & recording" to 38, "Screen refresh" to 27, "Wi-Fi sync" to 21, "BLE idle" to 14) },
            points = pts, accent = AppTheme.accentColor(),
            deviceName = id.yanuar.bf01.svc.Hub.get(ctx).prefs.deviceName.ifBlank { "Bobo" },
            example = demo,
            onCopy = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("BF battery report", BatteryStats.text(r, info.fw)))
                Toast.makeText(ctx, "Battery report copied", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                scope.launch {
                    val hub = id.yanuar.bf01.svc.Hub.get(ctx)
                    val ok = hub.ble.write(BfProtocol.FILES, BfProtocol.filesCmd("use:reset"))
                    BatteryStats.reset(ctx)
                    kotlinx.coroutines.delay(800); hub.ble.refreshInfo()
                    Toast.makeText(ctx, if (ok) "Battery stats reset. Counting starts now." else "Couldn't reach BF. Try again when connected.", Toast.LENGTH_SHORT).show()
                }
            },
        )
        r.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
