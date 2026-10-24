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
    /** Chat models. First = default. TokenPlan Lite list seen in Model Studio on 24 Oct 2026, then the pay-as-you-go aliases. */
    val QWEN_MODELS = listOf(
        Model("qwen-flash-character", "Qwen Flash Character · free quota (recommended)"),
        Model("qwen-plus-character", "Qwen Plus Character · free quota"),
        Model("qwen3.8-flash", "Qwen3.8 Flash · fast"),
        Model("qwen3.7-plus", "Qwen3.7 Plus · balanced"),
        Model("qwen3.8-max", "Qwen3.8 Max · smartest"),
        Model("qwen3.7-max", "Qwen3.7 Max"),
        Model("qwen3.6-flash", "Qwen3.6 Flash"),
        Model("auto", "Auto · Model Studio picks"),
        Model("qwen-flash", "Qwen Flash (pay-as-you-go)"),
        Model("qwen-plus", "Qwen Plus (pay-as-you-go)"),
        Model("qwen-max", "Qwen Max (pay-as-you-go)"),
    )
    /** Speech-recognition models. qwen-audio-3.0 uses the native API; qwen3-asr-flash the OpenAI-compatible one. */
    val QWEN_ASR_MODELS = listOf(
        Model("qwen-audio-3.1-asr-flash", "Qwen Audio 3.1 ASR Flash · free quota (recommended)"),
        Model("qwen-audio-3.0-asr-flash", "Qwen Audio 3.0 ASR Flash"),
        Model("qwen3-asr-flash", "Qwen3 ASR Flash"),
    )
    val ALL = listOf(
        Spec("qwen", "Qwen", Prefs.QWEN_BASE_INTL, QWEN_MODELS, visible = true),
        // Grok (xAI) code is kept; hidden from the UI for now.
        Spec("grok", "Grok", "https://api.x.ai/v1", listOf(Model("grok-4.3", "Grok 4.3")), visible = false),
    )
    val visible get() = ALL.filter { it.visible }
    fun isVisible(id: String) = visible.any { it.id == id }
}
