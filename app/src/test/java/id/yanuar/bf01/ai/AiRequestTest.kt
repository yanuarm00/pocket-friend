package id.yanuar.bf01.ai

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import id.yanuar.bf01.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiRequestTest {
    private val srv = MockWebServer().apply { start() }
    @After fun stop() = srv.shutdown()
    private fun prefs(): Prefs { val c = ApplicationProvider.getApplicationContext<Context>()
        return Prefs(c, c.getSharedPreferences("t_req", Context.MODE_PRIVATE)).apply { qwenKey = "sk-test"; llmProvider = "qwen" } }
    private fun ok(t: String) = MockResponse().setBody("""{"choices":[{"message":{"content":"$t"}}]}""")
    private fun base() = srv.url("/compatible-mode/v1").toString().trimEnd('/')
    private fun chat(p: Prefs) = OpenAiCompatChat("Qwen", { base() }, { p.qwenKey }, { p.qwenModel })

    /** Root cause of v0.3.3: the chat ran on the main thread (Compose scope) -> NetworkOnMainThreadException, swallowed. */
    @Test fun callWorksFromMainThread() = runBlocking {
        assertEquals(Looper.getMainLooper().thread, Thread.currentThread())
        srv.enqueue(ok("Halo"))
        assertEquals("Halo", chat(prefs()).chat("s", emptyList(), "hi"))
        val req = srv.takeRequest()
        assertEquals("/compatible-mode/v1/chat/completions", req.path)
        assertEquals("qwen-plus", org.json.JSONObject(req.body.readUtf8()).getString("model"))
    }

    @Test fun errorsAreClassified() = runBlocking {
        val p = prefs()
        mapOf(401 to AiFail.BAD_KEY, 429 to AiFail.QUOTA, 404 to AiFail.BAD_MODEL, 503 to AiFail.DOWN).forEach { (code, kind) ->
            srv.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
            val e = runCatching { chat(p).chat("s", emptyList(), "hi") }.exceptionOrNull()
            assertEquals(kind, (e as AiError).kind)
            assertFalse(friendlyError(e).contains("sk-test"))
        }
        srv.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":""}}]}"""))
        assertEquals(AiFail.BAD_REPLY, (runCatching { chat(p).chat("s", emptyList(), "hi") }.exceptionOrNull() as AiError).kind)
    }

    @Test fun noKeyAndNoInternet() = runBlocking {
        val p = prefs(); p.qwenKey = ""
        assertEquals(AiFail.NO_KEY, (runCatching { QwenChat(p).chat("s", emptyList(), "x") }.exceptionOrNull() as AiError).kind)
        val port = java.net.ServerSocket(0).use { it.localPort } // nothing listens here -> connection refused
        val dead = OpenAiCompatChat("Qwen", { "http://127.0.0.1:$port/v1" }, { "k" }, { "m" })
        assertEquals(AiFail.NO_INTERNET, (runCatching { dead.chat("s", emptyList(), "x") }.exceptionOrNull() as AiError).kind)
    }

    @Test fun qwenRegionFallbackRemembersWorkingRegion() = runBlocking {
        val p = prefs(); p.qwenBaseUrl = Prefs.QWEN_BASE_INTL
        val tried = mutableListOf<String>()
        val r = qwenWithRegion(p) { b -> tried += b; if (b == Prefs.QWEN_BASE_INTL) throw AiError(AiFail.BAD_KEY, "Qwen", 401) else "ok" }
        assertEquals("ok", r); assertEquals(listOf(Prefs.QWEN_BASE_INTL, Prefs.QWEN_BASE_CN), tried)
        assertEquals(Prefs.QWEN_BASE_CN, p.qwenBaseUrl)
        // both fail -> first region's error, saved region unchanged
        val e = runCatching { qwenWithRegion(p) { b -> throw AiError(if (b == Prefs.QWEN_BASE_CN) AiFail.BAD_KEY else AiFail.DOWN, "Qwen") } }.exceptionOrNull() as AiError
        assertEquals(AiFail.BAD_KEY, e.kind); assertEquals(Prefs.QWEN_BASE_CN, p.qwenBaseUrl)
    }

    @Test fun onlyQwenKeyMakesQwenActive() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("bf01", Context.MODE_PRIVATE).edit().remove("llm").commit()
        val p = Prefs(c, c.getSharedPreferences("t_only", Context.MODE_PRIVATE)); p.qwenKey = "q"
        assertEquals("qwen", p.llmProvider)
        assertEquals("grok-4.3", p.chatModel)
    }

    /** Real endpoints with a fake key: the error must surface as BAD_KEY (skipped when offline). */
    @Test fun realEndpointsRejectFakeKey() = runBlocking {
        // China endpoint answers the box with an empty 404 (network), curl confirms 401 invalid_api_key there; not asserted.
        for (base in listOf(Prefs.QWEN_BASE_INTL, Xai.BASE)) {
            val e = withContext(Dispatchers.IO) { runCatching { OpenAiCompatChat("X", { base }, { "sk-invalid-000" }, { "qwen-plus" }).chat("s", emptyList(), "hi") }.exceptionOrNull() } as AiError
            println("REAL $base -> ${e.kind} ${e.code} ${e.message}")
            if (e.kind != AiFail.NO_INTERNET) assertEquals(AiFail.BAD_KEY, e.kind)
        }
    }
}
