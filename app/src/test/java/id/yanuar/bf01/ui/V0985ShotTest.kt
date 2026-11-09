package id.yanuar.bf01.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import id.yanuar.bf01.data.Prefs
import id.yanuar.bf01.data.Prefs.Profile
import id.yanuar.bf01.svc.Hub
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v0.3.9.8.5: Name card + Hi! profiles merged into one editor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2300dp-420dpi")
class V0985ShotTest {
    private fun hub(name: String): Hub {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bf01", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = Prefs(ctx, ctx.getSharedPreferences(name, Context.MODE_PRIVATE))
        prefs.deviceAddress = "AA:BB:CC:DD:4E:27"; prefs.deviceName = "BF-01-4E27"
        return ready(Hub(ctx, prefs))
    }
    private fun ready(hub: Hub): Hub {
        hub.ble.preview(id.yanuar.bf01.ble.BleManager.State.READY, id.yanuar.bf01.ble.BfProtocol.Info(fw = "0.3.9.8.2-test", batt = 78, sd = true, sdFreeMb = 28_412, rec = false,
            paired = true, wifi = true, wifiOn = false, warm = false, state = "idle"))
        return hub
    }
    private fun shot(h: Hub, file: String) = captureRoboImage("build/roborazzi/$file") { PocketFriendTheme(true) { App(h, Screen.Kartu) } }

    @Test fun migratedUserSeesTheirOldDataInOneList() {
        RuntimeEnvironment.setFontScale(1.0f)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sp = ctx.getSharedPreferences("bf01", Context.MODE_PRIVATE); sp.edit().clear().commit()
        val p = Prefs(ctx, sp)
        p.cardName = "Alex"; p.cardPhone = "628123456789"; p.cardMsg = "Hi, nice to meet you"
        sp.edit().putString("profiles", """[{"t":"ig","v":"alex"},{"t":"li","v":"alex-design"}]""").commit()
        val l = p.profiles()
        assertEquals(listOf("wa", "ig", "li"), l.map { it.type })
        assertEquals("Hi, nice to meet you", l[0].msg); assertEquals("Alex", l[0].name)
        assertEquals(l, Prefs(ctx, sp).profiles())          // stored, reads back the same
        assertEquals("Alex", p.cardName)                  // old settings untouched
        shot(ready(Hub(ctx, p)), "01_migrated_whatsapp.png")
    }
    @Test fun youtubeGithubAndOthers() {
        RuntimeEnvironment.setFontScale(1.0f)
        val h = hub("t985b")
        h.prefs.setProfiles(listOf(Profile("yt", "@alex", "My channel"), Profile("gh", "alex", "GitHub"), Profile("ig", "alex"),
            Profile("other", "https://www.tiktok.com/@alex", "TikTok page"), Profile("other", "https://blog.example.com/yanuar", "Blog"), Profile("em", "alex@example.com")))
        shot(h, "02_youtube_selected.png")
    }
    @Test fun otherWithLetterBadge() {
        RuntimeEnvironment.setFontScale(1.0f)
        val h = hub("t985c")
        h.prefs.setProfiles(listOf(Profile("other", "https://blog.example.com/yanuar", "Blog"), Profile("gh", "alex")))
        shot(h, "03_other_letter_badge.png")
    }
    @Test fun noProfilesYet() {
        RuntimeEnvironment.setFontScale(1.0f)
        val h = hub("t985d"); h.prefs.setProfiles(emptyList())
        shot(h, "04_empty.png")
    }
}
