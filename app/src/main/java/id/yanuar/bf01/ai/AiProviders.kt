package id.yanuar.bf01.ai

import id.yanuar.bf01.data.Prefs

/**
 * Provider registry. Every provider is OpenAI-compatible: base URL + key + model list.
 * To add one later, add a Spec here and wire its key in Prefs; set visible = true to show it in Settings.
 */
object AiProviders {
    data class Model(val id: String, val label: String)
    data class Spec(val id: String, val label: String, val defaultBase: String, val models: List<Model>, val visible: Boolean)

    /** Qwen chat models from Model Studio's model list (checked 24 Oct 2026). The stable aliases always point to the newest snapshot. */
    val QWEN_MODELS = listOf(
        Model("qwen-plus", "Qwen Plus · balanced (recommended)"),
        Model("qwen-flash", "Qwen Flash · fastest, cheapest"),
        Model("qwen-turbo", "Qwen Turbo · fast (older)"),
        Model("qwen-max", "Qwen Max · smartest, slower"),
        Model("qwen3.7-plus", "Qwen3.7 Plus"),
        Model("qwen3.8-flash", "Qwen3.8 Flash"),
        Model("qwen3.8-max", "Qwen3.8 Max"),
    )

    val ALL = listOf(
        Spec("qwen", "Qwen", Prefs.QWEN_BASE_INTL, QWEN_MODELS, visible = true),
        // Grok (xAI) code is kept; hidden from the UI for now.
        Spec("grok", "Grok", "https://api.x.ai/v1", listOf(Model("grok-4.3", "Grok 4.3")), visible = false),
    )
    val visible get() = ALL.filter { it.visible }
    fun isVisible(id: String) = visible.any { it.id == id }
}
