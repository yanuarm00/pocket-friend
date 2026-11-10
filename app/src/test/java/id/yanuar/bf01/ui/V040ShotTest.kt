package id.yanuar.bf01.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Note
import id.yanuar.bf01.svc.RecordingSync
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.4.0 (mockup v5.13): Home online/offline, note detail, settings, quiet sync line, pill nav. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp-420dpi")
class V040ShotTest {
    private val home = HomeState("Good morning, Alex", "Small steps still count.", "Pip", online = true, connecting = false,
        battery = "Battery 82%", lastSeen = "Last seen · 5 min ago", network = "Wi-Fi · Home 5", date = "Tue, 3 Nov", weather = "29° · Partly cloudy",
        tasks = 4, reminders = 2, next = "15:00 · Call the dentist", whereLine = "Last seen at home · 5 min ago", hasLocation = true,
        recentTitle = "Weekend plan", recentSub = "2 min ago · 0:48", recentNew = true,
        asked = "What time is the market open on Sunday?", reminderRang = "Water the plants · 08:00")
    private fun shot(f: String, dark: Boolean, c: @androidx.compose.runtime.Composable () -> Unit) = captureRoboImage("build/roborazzi/$f") {
        PocketFriendTheme(dark) { Surface { Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { c() } } } }

    @Test fun homeOnlineLight() = shot("01_home_online_light.png", false) { HomeContent(home, animate = false) }
    @Test fun homeOnlineDark() = shot("02_home_online_dark.png", true) { HomeContent(home, animate = false) }
    @Test fun homeOffline() = shot("03_home_offline_light.png", false) {
        HomeContent(home.copy(online = false, battery = "82% · last seen", network = "Not connected"), animate = false) }

    private val text = "Okay so for the weekend. I need to buy flowers for Sam's birthday. Remember to call the dentist about Tuesday. " +
        "The market opens at seven, so we leave early. Book a table for four on Saturday night."
    private val note = Note(1, "VN_1.wav", 1793260800000, text, "Weekend errands: flowers, dentist and a dinner table.", "")
    @Test fun noteDetailPlaying() = shot("04_note_detail_playing.png", false) {
        NoteDetailContent(note, "Weekend plan", playing = true, posMs = 9000, durMs = 24000, hasAudio = true, todosAdded = false) }
    @Test fun noteDetailDark() = shot("05_note_detail_dark.png", true) {
        NoteDetailContent(note, "Weekend plan", playing = false, posMs = 0, durMs = 24000, hasAudio = false, todosAdded = true) }

    @androidx.compose.runtime.Composable private fun settings() = SettingsContent("Alex", "Name, avatar, Say Hi · backup on", {},
        device = listOf(SetRow(Sym.Edit, "Device name", "Pip", {}),
            SetRow(Sym.Apps, "Character", "Menu, faces & order on Pip", {}, accent = Pop.Orange, deviceThumb = true),
            SetRow(Sym.Link, "Connection", "Wi-Fi \u201cHome 5\u201d", {}, chip = "Help", onChip = {}),
            SetRow(Sym.Download, "Software update", "Pip 1.0.3 · 1.1.0 available", {}, chip = "New", chipYellow = true),
            SetRow(Sym.LightMode, "Sleep & wake", "Sleep after 60 s · quiet 22:00–6:00", {}, accent = Pop.Yellow)),
        app = listOf(SetRow(Sym.AutoAwesome, "AI & voice", "Grok · voice eve", {}),
            SetRow(Sym.Notifications, "Notifications", "Music, Maps & what Pip may show", {}),
            SetRow(Sym.Palette, "Theme", null, {}, swatches = true),
            SetRow(Sym.Shield, "Privacy & data", "Export, recordings, memory card", {})))
    @Test fun settingsLight() = shot("06_settings_light.png", false) { settings() }
    @Test fun settingsDark() = shot("07_settings_dark.png", true) { settings() }

    @Test fun syncLineAndNav() = shot("08_sync_nav.png", false) {
        androidx.compose.foundation.layout.Column {
            SyncStatusRow(RecordingSync.Phase.Done, "", 1793260800000 - 300_000, now = 1793260800000) {}
            SyncStatusRow(RecordingSync.Phase.Failed, "Device went to sleep", 0) {}
            PopNavBar(Screen.Catatan, true) {}
        } }
}
