package id.yanuar.bf01.svc

import org.junit.Assert.*
import org.junit.Test

class UpdateNotifyTest {
    @Test fun notifiesOnlyForNewerAndOnlyOnce() {
        assertTrue(AppUpdate.shouldNotify(31, 30, 0))
        assertFalse("same version", AppUpdate.shouldNotify(30, 30, 0))
        assertFalse("older", AppUpdate.shouldNotify(29, 30, 0))
        assertFalse("already told the user", AppUpdate.shouldNotify(31, 30, 31))
        assertTrue("an even newer one after that", AppUpdate.shouldNotify(32, 30, 31))
    }
    @Test fun readsTheSameManifestAsTheUpdateCard() {
        val r = AppUpdate.parse("""{"versionCode":31,"versionName":"0.3.9.8.3","size":10,"sha256":"${"A".repeat(64)}","notes":"n"}""")!!
        assertEquals(31, r.versionCode); assertEquals("0.3.9.8.3", r.versionName); assertEquals(AppUpdate.APK_URL, r.url)
        assertNull(AppUpdate.parse("not json"))
    }
}
