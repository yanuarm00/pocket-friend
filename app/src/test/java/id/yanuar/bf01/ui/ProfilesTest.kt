package id.yanuar.bf01.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import id.yanuar.bf01.data.Prefs.Profile
import id.yanuar.bf01.data.ProfileTypes as PT
import org.junit.Assert.*
import org.junit.Test

class ProfilesTest {
    private fun url(t: String, v: String, msg: String = "") = PT.url(Profile(t, v, "", msg))

    @Test fun handlesBecomeUrls() {
        assertEquals("https://instagram.com/alex", url("ig", "@alex"))
        assertEquals("https://instagram.com/alex.phone", url("ig", "alex.phone"))
        assertEquals("https://www.linkedin.com/in/yanuar", url("li", "alex"))
        assertEquals("https://github.com/alex", url("gh", "alex"))
        assertEquals("https://www.youtube.com/@yanuar", url("yt", "@yanuar").replace("https://youtube.com", "https://www.youtube.com"))
        assertEquals("https://t.me/yanuar", url("tg", "@yanuar"))
        assertEquals("mailto:a@b.co", url("em", "a@b.co"))
        assertEquals("https://example.com/x", url("web", "example.com/x"))
        assertEquals("https://example.com/x", url("other", "https://example.com/x"))
    }
    @Test fun pastedUrlsAreKept() {
        assertEquals("https://github.com/alex/pocket-friend", url("gh", "https://github.com/alex/pocket-friend"))
        assertEquals("https://www.instagram.com/alex/", url("ig", "https://www.instagram.com/alex/").let { if (it.endsWith("/")) it else "$it/" })
    }
    @Test fun whatsappNumberAndMessage() {
        assertEquals("https://wa.me/628123?text=Hi%20there", url("wa", "+62 812-3", "Hi there"))
        assertEquals("https://wa.me/628123", url("wa", "08123"))
        assertEquals("", url("wa", ""))
    }
    @Test fun emptyValueGivesNoQr() { for (t in PT.all) assertEquals(t.key, "", url(t.key, "  ")) }

    @Test fun detectsLogoFromDomain() {
        assertEquals("yt", PT.detectLogo("https://youtu.be/abc")); assertEquals("gh", PT.detectLogo("https://gist.github.com/x"))
        assertEquals("x", PT.detectLogo("https://twitter.com/x")); assertEquals("tt", PT.detectLogo("https://www.tiktok.com/@a"))
        assertEquals("fb", PT.detectLogo("https://m.facebook.com/a")); assertEquals("em", PT.detectLogo("mailto:a@b.co"))
        assertEquals("", PT.detectLogo("https://example.com"))
    }
    @Test fun otherLogoAutoChosenOrLetter() {
        assertEquals("yt", PT.logoKey(Profile("other", "https://www.youtube.com/@a")))
        assertEquals("gh", PT.logoKey(Profile("other", "github.com/a")))
        assertEquals("#B", PT.logoKey(Profile("other", "https://blog.example.com", "blog")))
        assertEquals("#E", PT.logoKey(Profile("other", "https://example.com")))
        assertEquals("tg", PT.logoKey(Profile("other", "https://example.com", "x", icon = "tg")))
        assertEquals("#M", PT.logoKey(Profile("other", "https://github.com/a", "my", icon = "#")))
        for (p in listOf(Profile("other", "https://example.com", "日本"), Profile("other", "", ""))) assertTrue(PT.logoKey(p).let { it == "link" || it.startsWith("#") })
        for (t in PT.all) assertNotEquals("bf", PT.logoKey(Profile(t.key, "x")))
    }
    @Test fun migrationKeepsEverything() {
        val old = """[{"t":"ig","v":"alex","n":"Insta"},{"t":"li","v":"alex"}]"""
        val l = PT.migrate(old, "Alex", "+62 812-34", "Hello")
        assertEquals(3, l.size)
        assertEquals(Profile("wa", "6281234", "Alex", "Hello"), l[0])
        assertEquals(Profile("ig", "alex", "Insta"), l[1]); assertEquals("li", l[2].type)
        assertEquals(2, PT.migrate(old, "", "", "").size)
        assertEquals(PT.LIMIT, PT.migrate("[" + List(9) { """{"t":"ig","v":"a$it"}""" }.joinToString(",") + "]", "", "1", "").size)
        assertTrue(PT.migrate("garbage", "", "", "").isEmpty())
    }
    @Test fun tooLongIsFlagged() {
        assertFalse(PT.tooLong(Profile("ig", "alex")))
        assertTrue(PT.tooLong(Profile("other", "https://example.com/" + "a".repeat(120))))
    }

    private fun decode(img: Array<BooleanArray>): String? {
        val sc = 3; val n = img.size * sc
        val px = IntArray(n * n) { if (img[it / n / sc][it % n / sc]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(n, n, px)))).text }.getOrNull()
    }
    /** Every logo, with the QR text as BF draws it, must still scan. */
    @Test fun everyLogoScans() {
        val texts = listOf("https://instagram.com/alex", "https://wa.me/6281234567890?text=Hi%20there", "mailto:alex@example.com",
            "https://www.linkedin.com/in/alex-design-1234567", "https://example.com/" + "p".repeat(80))
        for (k in LogoBits.keys) for (t in texts) assertEquals("$k $t", t, decode(BfQr.bitmap(t, k)!!))
        for (k in listOf("#A", "#Z", "#7")) for (t in texts) assertEquals("$k $t", t, decode(BfQr.bitmap(t, k)!!))
    }
    @Test fun longestTextScansAtVersion10() {
        val t = "https://example.com/" + "q".repeat(PT.MAX_QR_BYTES - 20)
        assertEquals(119, t.length)
        assertEquals(t, decode(BfQr.bitmap(t, "link")!!))
        assertNull(BfQr.bitmap(t + "qq", "link"))
    }
}
