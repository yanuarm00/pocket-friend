package id.yanuar.bf01.ai

import id.yanuar.bf01.data.Prefs

/**
 * Alibaba Model Studio endpoints (checked 24 Oct 2026, docs "Base URL overview" + live probe):
 *  - shared domains: dashscope-intl.aliyuncs.com (Singapore), dashscope.aliyuncs.com (Beijing), dashscope-us, cn-hongkong.dashscope
 *  - workspace domains: {WorkspaceId}.{region}.maas.aliyuncs.com
 *  OpenAI-compatible base = https://host/compatible-mode/v1 (chat + qwen3-asr-flash via /chat/completions),
 *  native = https://host/api/v1. Users may paste either, a bare host, or a full /chat/completions URL.
 */
object QwenUrl {
    private val tails = listOf("/chat/completions", "/compatible-mode/v1", "/compatible-mode", "/api/v1", "/api")

    /** Returns the OpenAI-compatible base, e.g. https://ws-x.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1, or null if not a URL. */
    fun normalize(raw: String?): String? {
        var s = raw?.trim()?.replace(" ", "") ?: return null
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        if (!s.startsWith("https://", true) && !s.startsWith("http://", true)) return null
        val schemeEnd = s.indexOf("://") + 3
        val slash = s.indexOf('/', schemeEnd).let { if (it < 0) s.length else it }
        val host = s.substring(schemeEnd, slash).lowercase().trimEnd('.')
        if (host.isEmpty() || !host.contains('.')) return null
        var path = s.substring(slash).substringBefore('?').substringBefore('#').trimEnd('/')
        var changed = true
        while (changed) { changed = false
            for (t in tails) if (path.endsWith(t, true)) { path = path.dropLast(t.length).trimEnd('/'); changed = true } }
        val scheme = s.substring(0, schemeEnd).lowercase()
        return "$scheme$host$path/compatible-mode/v1"
    }

    fun host(base: String): String = base.substringAfter("://").substringBefore('/')
    fun chatUrl(base: String) = (normalize(base) ?: base.trimEnd('/')) + "/chat/completions"
    /** Native DashScope multimodal endpoint (qwen-audio-* ASR): https://host/api/v1/services/aigc/multimodal-generation/generation */
    fun nativeMultimodalUrl(base: String) = (normalize(base) ?: base.trimEnd('/')).removeSuffix("/compatible-mode/v1") + "/api/v1/services/aigc/multimodal-generation/generation"
    fun isPreset(base: String) = normalize(base).let { it == Prefs.QWEN_BASE_INTL || it == Prefs.QWEN_BASE_CN }

    private val regions = mapOf("cn-beijing" to "China (Beijing)", "ap-southeast-1" to "Singapore", "us-east-1" to "US (Virginia)",
        "cn-hongkong" to "China (Hong Kong)", "ap-northeast-1" to "Japan (Tokyo)", "eu-central-1" to "Germany (Frankfurt)")

    /** Human region name from the host. */
    fun regionName(base: String): String {
        val h = host(normalize(base) ?: base)
        return when {
            h == "dashscope-intl.aliyuncs.com" -> "International (Singapore)"
            h == "dashscope.aliyuncs.com" -> "China (Beijing)"
            h == "dashscope-us.aliyuncs.com" -> "US (Virginia)"
            h == "cn-hongkong.dashscope.aliyuncs.com" -> "China (Hong Kong)"
            h.endsWith(".maas.aliyuncs.com") -> {
                val parts = h.removeSuffix(".maas.aliyuncs.com").split('.')
                val r = regions[parts.last()] ?: parts.last()
                if (parts.size >= 2 && parts.first() == "trial") "$r · trial" else if (parts.size >= 2 && parts.first() == "token-plan") "$r · Token Plan (sk-sp- key)" else "$r · workspace"
            }
            else -> "Custom URL"
        }
    }
}
