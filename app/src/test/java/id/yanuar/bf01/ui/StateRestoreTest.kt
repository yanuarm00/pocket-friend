package id.yanuar.bf01.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.svc.Hub
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** State layar tetap ada setelah kembali (gesture back) atau pindah tab. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class StateRestoreTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun hub(): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx, ctx.getSharedPreferences("test_state", Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"
        return Hub(ctx, prefs)
    }
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun setelanSubPageKeepsTypedTextAfterBack() {
        rule.setContent { PocketFriendTheme(true) { App(hub(), Screen.Pengaturan) } }
        rule.onNodeWithText("Connection & network").performClick()
        rule.onNodeWithText("Phone IP as seen by BF").performScrollTo().performTextReplacement("10.0.0.9")
        back()
        rule.onNodeWithText("Device").performClick()
        rule.onNodeWithText("Recordings").performClick()
        rule.onNodeWithText("Sync recordings").assertExists()
        back(); back()
        rule.onNodeWithText("Connection & network").performClick()
        // Isian yang belum disimpan dan posisi scroll tetap ada (judul bagian atas sudah tergulir).
        rule.onNodeWithText("10.0.0.9").assertExists()
        rule.onNodeWithText("10.0.0.9").assertIsDisplayed()
        back(); back()
        rule.onNodeWithText("Sync").assertExists()
    }

    @Test fun tabSwitchKeepsState() {
        rule.setContent { PocketFriendTheme(true) { App(hub(), Screen.Ngobrol) } }
        rule.onNodeWithText("Ask BF…").performTextInput("halo BF")
        rule.onNodeWithText("BF persona & memory").performClick()
        rule.onNodeWithText("Persona", ignoreCase = true).assertExists()
        rule.onNodeWithText("To-do").performClick()
        rule.onNodeWithText("Ask AI").performClick()
        rule.onNodeWithText("halo BF").assertExists()
        rule.onNodeWithText("Persona", ignoreCase = true).assertExists()
        rule.onAllNodesWithText("Hide", substring = true).assertCountEquals(0)
    }

    @Test fun backFromTabGoesToBerandaThenExitAllowed() {
        rule.setContent { PocketFriendTheme(true) { App(hub(), Screen.Beranda) } }
        rule.onNodeWithText("Notes").performClick()
        rule.onNodeWithText("Search notes").assertExists()
        back()
        rule.onNodeWithText("Sync").assertExists()
    }

    @Test fun swipeDeleteTaskWithUndo() {
        val h = hub(); h.db.tasks().forEach { h.db.deleteTask(it.id) }; h.db.addTask("Tugas geser")
        rule.setContent { PocketFriendTheme(true) { App(h, Screen.Tugas) } }
        rule.onNodeWithText("Tugas geser").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithText("Tugas geser").assertExists()
        assertEquals(1, h.db.tasks().size)
        rule.onNodeWithText("Tugas geser").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(10_000); rule.waitForIdle()
        assertEquals(0, h.db.tasks().size)
    }

    @Test fun statusPillOpensSheet() {
        val h = hub(); h.prefs.linkEnabled = false; h.linked.value = false
        rule.setContent { PocketFriendTheme(true) { App(h, Screen.Tugas) } }
        rule.onNodeWithContentDescription("Status BF OFF", substring = true).performClick()
        rule.onNodeWithText("Reconnect").assertExists()
        rule.onNodeWithText("Disconnect").assertDoesNotExist()  // v0.3.4: hidden while unlinked
    }
}
