package id.yanuar.bf01.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.ble.BfProtocol
import id.yanuar.bf01.ble.BleManager
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot JVM (Robolectric + Roborazzi), Samsung A53: 411x914 dp @ 420 dpi ≈ 1080x2400. Hasil: docs/archive/screens_pf/<n>_<layar>_<dark|light>.png */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest(private val screen: Screen, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun data() = listOf(Screen.Beranda, Screen.Ngobrol, Screen.Tugas, Screen.Catatan, Screen.Pengaturan, Screen.File)
            .flatMap { listOf(arrayOf<Any>(it, true), arrayOf<Any>(it, false)) }
    }

    private fun sampleHub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_secure", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.deviceName = "BF-01-4E27"
        prefs.lastLat = -6.20876; prefs.lastLon = 106.84559; prefs.lastLocTime = 1792990800000
        prefs.setWifi(0, "WiFi-Rumah", "rahasia123"); prefs.setWifi(1, "Hotspot-Yanuar", "rahasia456")
        prefs.xaiKey = "test-key"
        val hub = Hub(ctx, prefs)
        hub.ble.preview(BleManager.State.READY, BfProtocol.Info(fw = "0.1.0", batt = 78, sd = true, sdFreeMb = 28_412, rec = false,
            paired = true, wifi = true, wifiOn = false, warm = false, state = "idle"))
        val db = hub.db
        db.tasks().forEach { db.deleteTask(it.id) }; db.clearChat(); db.memories().forEach { db.deleteMemory(it.id) }
        db.upsertNote("note_0921.wav", "Ide konten minggu depan: video behind the scene bikin BF, terus tanya pendapat tim soal warna case.",
            "Ide video behind the scene + diskusi warna case", "ide,konten")
        db.upsertNote("note_0815.wav", "Jangan lupa beli baterai LiPo 400 mAh dan cek ukuran rongga casing.", "Beli baterai, cek rongga casing", "belanja")
        db.upsertNote("note_0730.wav")
        val wav = java.io.File(ctx.filesDir, "rec_test.wav").apply { writeBytes(id.yanuar.bf01.util.Audio.wav(ShortArray(1600))) }
        db.setNoteAudio("note_0921.wav", wav.absolutePath); db.setNoteAudio("note_0815.wav", wav.absolutePath)
        db.setNoteType("VN_20261024_090000.wav", "TASK")
        db.upsertVoiceTask("VN_20261024_090000.wav", "Telepon vendor casing", "Besok pagi telepon vendor casing, tanya harga cetak 50 unit.")
        db.setNoteType("VN_20261024_093000.wav", "NOTE"); db.setNoteType("VN_20261024_094500.wav", "NOTE")
        prefs.lastRecSync = 1792980000000
        db.addTask("Cetak ulang casing v2"); db.addTask("Ukur board pakai jangka sorong"); db.addTask("Kirim brief ke tim UI")
        db.setDone(db.addTask("Beli kabel USB-C"), true)
        db.addChat("user", "BF, hari ini aku ada jadwal apa aja?")
        db.addChat("assistant", "Jam sebelas ada meeting Kalsi, Mas Yan. Sorenya kosong, pas buat ngoprek BF!")
        db.addChat("user", "Ingetin aku beli baterai ya")
        db.addChat("assistant", "Siap Mas Yan, nanti aku ingetin pas jam pulang kantor.")
        db.addMemory("Mas Yan suka kopi susu gula aren"); db.addMemory("Sedang membuat perangkat BF-01")
        db.addMemory("Mas Yan biasa olahraga lari pagi tiap Sabtu", "chat", auto = true); db.addMemory("Rencana cetak 50 unit casing BF bulan depan", "note", auto = true)
        db.addUsage(sttSec = 754.0, ttsSec = 512.0, chat = 38)
        hub.log("BF connected, syncing..."); hub.log("Sync done"); hub.log("Event: Media(cmd=next)")
        return hub
    }

    @Test fun capture() {
        val hub = sampleHub()
        val n = listOf(Screen.Beranda, Screen.Ngobrol, Screen.Tugas, Screen.Catatan, Screen.Pengaturan, Screen.File).indexOf(screen) + 1
        captureRoboImage("../docs/archive/screens_pf/${n}_${screen.name}_${if (dark) "dark" else "light"}.png") { PocketFriendTheme(dark) { App(hub, screen) } }
    }

    /** Target utama: Samsung Galaxy A53 (1080x2400, 411x914 dp, ~420 dpi). Cek fontScale 1.0 / 1.15 / 1.3.
     *  Layar panjang (Pengaturan) dirender setinggi isi supaya semua bagian kelihatan. */
    @Test fun captureA53() {
        if (!dark) return
        val extra = if (screen == Screen.Pengaturan) listOf(Screen.SetAi, Screen.SetSuara, Screen.SetJaringan, Screen.SetIzin, Screen.SetPerangkat,
            Screen.SetPemakaian, Screen.Pairing, Screen.Kartu, Screen.Menu, Screen.File) else emptyList()
        for (fs in listOf(1.0f, 1.15f, 1.3f)) for (sc in listOf(screen) + extra) {
            org.robolectric.RuntimeEnvironment.setQualifiers(if (sc in tall) "w411dp-h1900dp-420dpi" else "w411dp-h914dp-420dpi")
            org.robolectric.RuntimeEnvironment.setFontScale(fs)
            val hub = sampleHub()
            captureRoboImage("../docs/archive/screens_pf/a53/fs${(fs * 100).toInt()}_${sc.name}.png") { PocketFriendTheme(dark) { App(hub, sc) } }
            if (sc == Screen.SetAi) {
                hub.prefs.llmProvider = "qwen"; hub.prefs.qwenKey = "test-key"
                captureRoboImage("../docs/archive/screens_pf/a53/fs${(fs * 100).toInt()}_SetAi_Qwen.png") { PocketFriendTheme(dark) { App(hub, sc) } }
                hub.prefs.llmProvider = "grok"
            }
        }
        org.robolectric.RuntimeEnvironment.setFontScale(1f)
    }
    private val tall = setOf(Screen.SetAi, Screen.SetSuara, Screen.SetJaringan)

    /** Update firmware: BF lama (perlu kabel), siap, mengunduh, gagal, selesai. */
    @Test fun captureFirmware() {
        if (!dark || screen != Screen.Pengaturan) return
        val sha = id.yanuar.bf01.fw.FirmwareImage.KNOWN.getValue(id.yanuar.bf01.fw.FirmwareImage.LATEST)
        val img = id.yanuar.bf01.fw.FirmwareImage.Parsed(2_626_528, sha, id.yanuar.bf01.fw.FirmwareImage.LATEST, "xiaozhi", null)
        val F = id.yanuar.bf01.svc.FwUpdate.Ui(phase = id.yanuar.bf01.svc.FwUpdate.Phase.Ready, fileName = "xiaozhi.bin", image = img)
        for (fs in listOf(1.0f, 1.15f, 1.3f)) {
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h1300dp-420dpi")
            org.robolectric.RuntimeEnvironment.setFontScale(fs)
            val n = "fs${(fs * 100).toInt()}"
            val hub = sampleHub()
            fun info(fw: String, ota: Boolean, batt: Int = 78) = BfProtocol.Info(fw = fw, batt = batt, sd = true, sdFreeMb = 28_412, rec = false,
                paired = true, wifi = true, wifiOn = false, warm = false, state = "idle", ota = ota, usb = false)
            hub.ble.preview(BleManager.State.READY, info("2.5.3", false))
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_Lama.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
            hub.ble.preview(BleManager.State.READY, info("0.3.0", true))
            hub.fwUpdate.preview(F)
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_Siap.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
            hub.fwUpdate.preview(F.copy(image = img.copy(sha256 = "0".repeat(64))))
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_BelumVerif.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
            hub.fwUpdate.preview(F.copy(phase = id.yanuar.bf01.svc.FwUpdate.Phase.Downloading, progress = 45, message = "BF is downloading from the phone…"))
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_Mengunduh.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
            hub.fwUpdate.preview(F.copy(phase = id.yanuar.bf01.svc.FwUpdate.Phase.Failed, progress = 60, message = "Download interrupted. Keeping the old firmware"))
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_Gagal.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
            hub.fwUpdate.preview(F.copy(phase = id.yanuar.bf01.svc.FwUpdate.Phase.Done, progress = 100, message = "BF is now v0.3.1"))
            captureRoboImage("../docs/archive/screens_pf/a53/${n}_Firmware_Selesai.png") { PocketFriendTheme(dark) { App(hub, Screen.SetFirmware) } }
        }
        org.robolectric.RuntimeEnvironment.setFontScale(1f)
    }

    /** ASK BF kosong (ajakan) dan status pil putus/menyambung, di tiga skala huruf. */
    @Test fun captureStates() {
        if (!dark || screen != Screen.Ngobrol) return
        for (fs in listOf(1.0f, 1.15f, 1.3f)) {
            org.robolectric.RuntimeEnvironment.setFontScale(fs)
            val hub = sampleHub(); hub.db.clearChat()
            captureRoboImage("../docs/archive/screens_pf/a53/fs${(fs * 100).toInt()}_Ngobrol_Kosong.png") { PocketFriendTheme(dark) { App(hub, Screen.Ngobrol) } }
            hub.ble.preview(BleManager.State.OFF, null)
            captureRoboImage("../docs/archive/screens_pf/a53/fs${(fs * 100).toInt()}_Tugas_Menyambung.png") { PocketFriendTheme(dark) { App(hub, Screen.Tugas) } }
            hub.prefs.linkEnabled = false; hub.linked.value = false
            captureRoboImage("../docs/archive/screens_pf/a53/fs${(fs * 100).toInt()}_Tugas_Off.png") { PocketFriendTheme(dark) { App(hub, Screen.Tugas) } }
        }
        org.robolectric.RuntimeEnvironment.setFontScale(1f)
    }

    /** Sakelar sambungan: Beranda + Pengaturan saat diputus manual. */
    @Test fun captureDisconnected() {
        if (!dark || screen !in listOf(Screen.Beranda, Screen.Pengaturan)) return
        val hub = sampleHub()
        hub.prefs.linkEnabled = false; hub.linked.value = false
        hub.ble.preview(BleManager.State.OFF, null)
        captureRoboImage("../docs/archive/screens_pf/link_off_${screen.name}.png") { PocketFriendTheme(dark) { App(hub, screen) } }
    }

    /** v0.3.1: ASK BF, Home, status sheet from the top, File & Data, Settings (A53, dark). */
    @Test fun captureV031() {
        if (!dark || screen != Screen.Beranda) return
        org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        val hub = sampleHub()
        captureRoboImage("../docs/archive/screens_pf/v031/AskBF.png") { PocketFriendTheme(dark) { App(hub, Screen.Ngobrol) } }
        val empty = sampleHub(); empty.db.clearChat()
        captureRoboImage("../docs/archive/screens_pf/v031/AskBF_empty.png") { PocketFriendTheme(dark) { App(empty, Screen.Ngobrol) } }
        captureRoboImage("../docs/archive/screens_pf/v031/Home.png") { PocketFriendTheme(dark) { App(hub, Screen.Beranda) } }
        captureRoboImage("../docs/archive/screens_pf/v031/StatusSheet.png") { PocketFriendTheme(dark) { App(hub, Screen.Beranda, sheetOpen = true) } }
        captureRoboImage("../docs/archive/screens_pf/v031/FileData.png") { PocketFriendTheme(dark) { App(hub, Screen.File) } }
        captureRoboImage("../docs/archive/screens_pf/v031/Settings.png") { PocketFriendTheme(dark) { App(hub, Screen.Pengaturan) } }
        val off = sampleHub(); off.prefs.linkEnabled = false; off.linked.value = false; off.ble.preview(BleManager.State.OFF, null)
        captureRoboImage("../docs/archive/screens_pf/v031/StatusSheet_off.png") { PocketFriendTheme(dark) { App(off, Screen.Beranda, sheetOpen = true) } }
    }

    /** v0.3.2: Recordings, Ask AI bubble, Notes with play, To-do with voice task, TODAY pill, AI name (A53, fontScale 1.0 & 1.3). */
    @Test fun captureV032() {
        if (screen != Screen.Beranda) return
        val mode = if (dark) "dark" else "light"
        for (fs in listOf(1.0f, 1.3f)) {
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
            org.robolectric.RuntimeEnvironment.setFontScale(fs)
            val tag = "fs${(fs * 100).toInt()}_$mode"
            val hub = sampleHub()
            captureRoboImage("/workspace/pf_v032/${tag}_Home.png") { PocketFriendTheme(dark) { App(hub, Screen.Beranda) } }
            captureRoboImage("/workspace/pf_v032/${tag}_AskAI.png") { PocketFriendTheme(dark) { App(hub, Screen.Ngobrol) } }
            captureRoboImage("/workspace/pf_v032/${tag}_Notes.png") { PocketFriendTheme(dark) { App(hub, Screen.Catatan) } }
            captureRoboImage("/workspace/pf_v032/${tag}_Todo.png") { PocketFriendTheme(dark) { App(hub, Screen.Tugas) } }
            hub.recordings.refreshCount()
            captureRoboImage("/workspace/pf_v032/${tag}_Recordings.png") { PocketFriendTheme(dark) { App(hub, Screen.File) } }
            val named = sampleHub(); named.db.clearChat(); named.prefs.aiName = "Kiko"
            captureRoboImage("/workspace/pf_v032/${tag}_AskAI_empty_named.png") { PocketFriendTheme(dark) { App(named, Screen.Ngobrol) } }
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h1500dp-420dpi")
            captureRoboImage("/workspace/pf_v032/${tag}_SetAi.png") { PocketFriendTheme(dark) { App(named, Screen.SetAi) } }
            named.prefs.aiName = "BF"
        }
    }

    /** v0.3.3: Home (pill TODAY), Settings → AI (toggle ingatan otomatis). Dropdown memori: lihat V033ShotTest. */
    @Test fun captureV033() {
        if (screen != Screen.Beranda) return
        val mode = if (dark) "dark" else "light"
        for (fs in listOf(1.0f, 1.3f)) {
            org.robolectric.RuntimeEnvironment.setFontScale(fs)
            val tag = "fs${(fs * 100).toInt()}_$mode"
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
            val hub = sampleHub()
            captureRoboImage("/workspace/pf_v033/${tag}_Home.png") { PocketFriendTheme(dark) { App(hub, Screen.Beranda) } }
            org.robolectric.RuntimeEnvironment.setQualifiers("w411dp-h1500dp-420dpi")
            captureRoboImage("/workspace/pf_v033/${tag}_SetAi.png") { PocketFriendTheme(dark) { App(hub, Screen.SetAi) } }
        }
    }
}
