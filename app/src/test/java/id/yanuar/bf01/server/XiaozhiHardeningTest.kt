package id.yanuar.bf01.server

import id.yanuar.bf01.ai.QwenUrl
import id.yanuar.bf01.ai.Xai
import id.yanuar.bf01.fw.FirmwareImage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XiaozhiHardeningTest {
    @Test fun grokChatUrlIsExact() {
        assertEquals("https://api.x.ai/v1/chat/completions", Xai.chatUrl())
        assertEquals("https://api.x.ai/v1/chat/completions", Xai.chatUrl("https://api.x.ai/v1/"))
    }
    @Test fun httpBaseRejected() {
        assertNull(QwenUrl.normalize("http://dashscope-intl.aliyuncs.com/api/v1"))
        assertNotNull(QwenUrl.normalize("https://dashscope-intl.aliyuncs.com/api/v1"))
        assertNotNull(QwenUrl.normalize("dashscope-intl.aliyuncs.com"))
    }
    @Test fun otaJsonHasNoToken() {
        val s = XiaozhiServer({ "secret-token" }, { "ws://1.2.3.4:8000/xiaozhi/v1/" }, object : XiaozhiServer.Pipeline {
            override suspend fun handle(pcm: ShortArray, onStt: suspend (String) -> Unit, onReply: suspend (String) -> Unit): ShortArray? = null
        })
        val j = s.otaJson()
        assertFalse(j.contains("secret-token"))
        assertEquals("ws://1.2.3.4:8000/xiaozhi/v1/", JSONObject(j).getJSONObject("websocket").getString("url"))
    }
    @Test fun pcmBufferCapped() {
        val b = XiaozhiServer.PcmBuf(cap = 100)
        b.add(ShortArray(60) { 1 }); b.add(ShortArray(60) { 2 })
        assertTrue(b.full); assertEquals(100, b.take().size); assertEquals(0, b.size)
    }
    @Test fun rateLimit() {
        val r = XiaozhiServer.RateLimit(maxPerMin = 3, maxFails = 2)
        repeat(3) { assertTrue(r.allow("a", 0)) }
        assertFalse(r.allow("a", 1000)); assertTrue(r.allow("a", 61_000))
        r.fail("b", 0); r.fail("b", 0); assertFalse(r.allow("b", 1000)); assertTrue(r.allow("b", 301_000))
    }
    @Test fun latestFirmwareKnown() = assertEquals("0.3.9.8.7-test", FirmwareImage.LATEST)

    @Test fun deviceGatePairsThenLocks() {
        var paired = ""; var open = true
        val gate = object : XiaozhiServer.DeviceGate {
            override fun pairedId() = paired
            override fun pairingOpen() = open
            override fun accept(deviceId: String): Boolean { paired = deviceId; open = false; return true }
            override fun forget() { paired = ""; open = true }
        }
        assertTrue(Auth.allowDevice("aa:bb:cc:dd:ee:ff", gate))
        assertEquals("aa:bb:cc:dd:ee:ff", paired)
        assertTrue(Auth.allowDevice("aa:bb:cc:dd:ee:ff", gate))
        assertFalse(Auth.allowDevice("11:22:33:44:55:66", gate))
        assertFalse(Auth.allowDevice("", gate))
        assertTrue(Auth.check("Bearer secret", "secret"))
        assertFalse(Auth.check("Bearer wrong", "secret"))
    }

    @Test fun bindHostFallsBackWhenUnknown() {
        assertEquals("0.0.0.0", bindHost(""))
        assertEquals("0.0.0.0", bindHost("8.8.8.8"))
    }
}
