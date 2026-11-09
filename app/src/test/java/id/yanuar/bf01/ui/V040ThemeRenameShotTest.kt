package id.yanuar.bf01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.4.0: Settings rendered through the new theme option, plus the theme picker and the rename dialog. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp-420dpi")
class V040ThemeRenameShotTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = AppTheme.set("system")

    private fun settings() = @androidx.compose.runtime.Composable {
        SettingsContent("Alex", "Name, avatar, Say Hi · backup on", {},
            device = listOf(SetRow(Sym.Edit, "Device name", "Pip", {}),
                SetRow(Sym.Apps, "Character", "Menu, faces & order on Pip", {}, accent = Pop.Orange, deviceThumb = true),
                SetRow(Sym.Link, "Connection", "Wi-Fi \u201cHome 5\u201d", {}, chip = "Help", onChip = {})),
            app = listOf(SetRow(Sym.AutoAwesome, "AI & voice", "Grok · voice eve", {}),
                SetRow(Sym.Palette, "Theme", null, {}, swatches = true)))
    }
    private fun show(mode: String, f: String, overlay: @androidx.compose.runtime.Composable () -> Unit = {}) {
        AppTheme.set(mode); rule.mainClock.autoAdvance = false
        rule.setContent { PocketFriendTheme(AppTheme.isDark()) { Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { settings()() } }; overlay() } }
        rule.mainClock.advanceTimeBy(1000); captureScreenRoboImage("build/roborazzi/$f")
    }

    @Test fun mapping() { listOf("light" to "Light", "dark" to "Dark", "system" to "System", "x" to "System").forEach { assertEquals(it.second, AppTheme.label(it.first)) } }
    @Test fun renameClean() { assertEquals("Pip the Fox", DeviceRename.clean("  Pip   the Fox ")); assertEquals(16, DeviceRename.clean("a".repeat(30)).length) }
    @Test fun settingsLightTheme() = show("light", "09_settings_theme_light.png")
    @Test fun settingsDarkTheme() = show("dark", "10_settings_theme_dark.png")
    @Test fun themePicker() = show("dark", "11_theme_picker.png") { ThemeDialog("dark", {}) {} }
    @Test fun renameDialog() = show("light", "12_rename_dialog.png") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
            Box(Modifier.padding(horizontal = 40.dp)) { RenameDevicePanel("Pip", {}) {} } } }
}
