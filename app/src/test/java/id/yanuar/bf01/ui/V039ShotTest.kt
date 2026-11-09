package id.yanuar.bf01.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.fw.FirmwareImage
import id.yanuar.bf01.svc.AutoMemory
import id.yanuar.bf01.svc.FwUpdate
import id.yanuar.bf01.svc.Hub
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.9: update firmware via Bluetooth, chip memori, kartu bantuan sambung, auto-open + cek Maps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1300dp-420dpi")
class V039ShotTest {
    private fun hub(ready: Boolean = true): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_v039", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.deviceName = "BF-01-4E27"
        val h = Hub(ctx, prefs)
        h.ble.preview(if (ready) BleManager.State.READY else BleManager.State.SCANNING, BfProtocol.Info(fw = "0.3.8.3-test", batt = 78, sd = true, sdFreeMb = 28_412, rec = false,
            paired = true, wifi = true, wifiOn = false, warm = false, state = "idle"))
        return h
    }
    private fun out(n: String) = "build/roborazzi/$n.png"
    private val img = FirmwareImage.Parsed(2_746_560, FirmwareImage.KNOWN.getValue("0.3.9-test"), "0.3.9-test", "xiaozhi", null)

    @Test fun firmware() {
        RuntimeEnvironment.setFontScale(1.15f)
        val h = hub()
        val base = FwUpdate.Ui(phase = FwUpdate.Phase.Ready, fileName = "BF01-firmware.bin", image = img, viaBle = true)
        h.fwUpdate.preview(base)
        captureRoboImage(out("01_Firmware_Siap")) { PocketFriendTheme(true) { App(h, Screen.SetFirmware) } }
        h.fwUpdate.preview(base.copy(phase = FwUpdate.Phase.Sending, progress = 42, message = "Sending over Bluetooth… 1.15 / 2.75 MB"))
        captureRoboImage(out("02_Firmware_Mengirim")) { PocketFriendTheme(true) { App(h, Screen.SetFirmware) } }
        h.fwUpdate.preview(base.copy(phase = FwUpdate.Phase.Failed, progress = 60, message = "Bluetooth was quiet for too long. Keeping the old firmware"))
        captureRoboImage(out("03_Firmware_Gagal")) { PocketFriendTheme(true) { App(h, Screen.SetFirmware) } }
        h.fwUpdate.preview(base.copy(phase = FwUpdate.Phase.Done, progress = 100, message = "BF is now v0.3.9-test"))
        captureRoboImage(out("04_Firmware_Selesai")) { PocketFriendTheme(true) { App(h, Screen.SetFirmware) } }
    }

    @Test fun memoryChip() {
        val h = hub(); h.db.clearChat()
        h.db.addChat("user", "Aku suka kopi susu gula aren"); h.db.addChat("assistant", "Siap, dicatat!")
        h.autoMemory.lastSaved.value = AutoMemory.Saved(listOf(1L), listOf("Suka kopi susu gula aren"))
        captureRoboImage(out("05_Memori_Chip")) { PocketFriendTheme(true) { App(h, Screen.Ngobrol) } }
    }

    @Test fun connectHelp() {
        val h = hub(); h.reconnectFails.value = 2
        captureRoboImage(out("07_Perangkat_Bantuan_Sambung")) { PocketFriendTheme(true) { App(h, Screen.SetPerangkat) } }
    }

    @Test fun autoOpenAndMaps() {
        val h = hub()
        h.lastMapsUpdate.value = 1792990800000 to "250M Belok kiri ke Jl. Sudirman"
        h.lastSentToBf.value = 1792990801000 to "NL|250M|Jl. Sudirman|Belok kiri ke Jl. Sudirman|12 menit|14.35"
        captureRoboImage(out("08_Musik_Maps_AutoOpen_Cek")) { PocketFriendTheme(true) { App(h, Screen.SetIzin) } }
    }
}
