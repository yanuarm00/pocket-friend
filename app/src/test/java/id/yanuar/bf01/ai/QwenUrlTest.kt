package id.yanuar.bf01.ai

import id.yanuar.bf01.data.Prefs
import org.junit.Assert.*
import org.junit.Test

class QwenUrlTest {
    private val ws = "https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1"
    @Test fun workspaceApiV1() = assertEquals(ws, QwenUrl.normalize("https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1"))
    @Test fun variants() {
        for (v in listOf("https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1/", " https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1// ",
            "ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com", "https://WS-HQI4R1H4GR5QRJ0F.ap-southeast-1.maas.aliyuncs.com/",
            "https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/chat/completions"))
            assertEquals(v, ws, QwenUrl.normalize(v))
    }
    @Test fun chatUrl() = assertEquals("$ws/chat/completions", QwenUrl.chatUrl("https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1"))
    @Test fun region() {
        assertEquals("Singapore · workspace", QwenUrl.regionName("https://ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com/api/v1"))
        assertEquals("China (Beijing) · workspace", QwenUrl.regionName("llm-x.cn-beijing.maas.aliyuncs.com"))
        assertEquals("International (Singapore)", QwenUrl.regionName(Prefs.QWEN_BASE_INTL))
    }
    @Test fun presets() {
        assertEquals(Prefs.QWEN_BASE_INTL, QwenUrl.normalize("https://dashscope-intl.aliyuncs.com/api/v1"))
        assertTrue(QwenUrl.isPreset("dashscope.aliyuncs.com")); assertFalse(QwenUrl.isPreset(ws))
    }
    @Test fun host() = assertEquals("ws-hqi4r1h4gr5qrj0f.ap-southeast-1.maas.aliyuncs.com", QwenUrl.host(ws))
    @Test fun rejects() { assertNull(QwenUrl.normalize("")); assertNull(QwenUrl.normalize("hello")); assertNull(QwenUrl.normalize("ftp://x.com")) }
}
