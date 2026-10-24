package id.yanuar.bf01.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiStackTest {
    private fun prefs(): Prefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return Prefs(ctx, ctx.getSharedPreferences("test_ai", Context.MODE_PRIVATE))
    }

    @Test fun grokIsHiddenSoAppIsQwenOnly() {
        val p = prefs(); p.llmProvider = "grok"; p.xaiKey = "k"
        assertEquals("qwen", p.llmProvider); assertEquals("qwen", aiStack(p).chat)
        assertEquals(listOf("qwen"), AiProviders.visible.map { it.id }); assertTrue(AiProviders.ALL.any { it.id == "grok" })
    }

    @Test fun qwenModelListHasDefault() = assertEquals(Prefs.QWEN_MODEL, AiProviders.QWEN_MODELS.first().id)

    @Test fun qwenNeverTouchesXai() {
        val p = prefs(); p.llmProvider = "qwen"; p.xaiKey = "k"; p.qwenKey = "q"; p.sttProvider = "xai"; p.ttsProvider = "xai"
        val s = aiStack(p)
        assertEquals("qwen", s.stt); assertEquals("qwen", s.chat); assertEquals("system", s.tts)
    }

    @Test fun qwenAsrRequestShape() {
        val b = QwenStt.body(byteArrayOf(1, 2, 3))
        assertEquals("qwen3-asr-flash", b.getString("model"))
        assertEquals("id", b.getJSONObject("asr_options").getString("language"))
        val c = b.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("input_audio", c.getString("type"))
        assertTrue(c.getJSONObject("input_audio").getString("data").startsWith("data:audio/wav;base64,AQID"))
    }

    @Test fun qwenAsrParse() {
        val res = JSONObject("""{"choices":[{"message":{"role":"assistant","content":" halo BF ","annotations":[{"emotion":"neutral"}]}}]}""")
        assertEquals("halo BF", QwenStt.parse(res.toString()))
    }

    @Test fun qwenRegionEndpoints() {
        val p = prefs()
        p.qwenBaseUrl = Prefs.QWEN_BASE_CN; assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", p.qwenBaseUrl)
        p.qwenBaseUrl = Prefs.QWEN_BASE_INTL; assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", p.qwenBaseUrl)
    }
}
