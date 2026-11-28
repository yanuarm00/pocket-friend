package id.yanuar.bf01.svc

import id.yanuar.bf01.ble.BfProtocol

/**
 * Error report (v0.4.12): a short plain-text report the user reads first and shares only by tapping Share.
 * It holds versions and BF's own restart/crash fields. It never holds recordings, API keys, Wi-Fi passwords, tokens or the app log.
 */
object ErrorReport {
    const val NOT_INCLUDED = "Not included: recordings, API keys, Wi-Fi passwords, tokens."

    private val secretPatterns = listOf(
        Regex("""(?i)bearer\s+[A-Za-z0-9._\-]+"""),
        Regex("""\b(?:sk|xai|key|tok)[-_][A-Za-z0-9_\-]{8,}"""),
        Regex("""(?i)\b(?:token|key|api[_-]?key|secret|pass(?:word|wd)?|pwd|ssid)\s*[=:]\s*\S+"""),
        Regex("""\b[A-Za-z0-9+/_\-]{32,}={0,2}"""),
        Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""),
    )

    /** Removes anything that looks like a key, token, password, network name or IP address. */
    fun redact(s: String): String = secretPatterns.fold(s) { acc, r -> r.replace(acc, "[removed]") }

    fun build(info: BfProtocol.Info?, appVersion: String, appCode: Int, androidRelease: String, sdk: Int, fwDisplay: String?): String {
        val sb = StringBuilder()
        sb.appendLine("Pocket Friend error report")
        sb.appendLine("App: $appVersion ($appCode)")
        sb.appendLine("Phone: Android $androidRelease (API $sdk)")
        if (info == null) {
            sb.appendLine("BF: not connected, so no device details.")
        } else {
            sb.appendLine("BF firmware: ${fwDisplay?.takeIf { it.isNotBlank() } ?: info.fw.ifBlank { "unknown" }}")
            sb.appendLine("BF state: ${info.state.ifBlank { "unknown" }} · battery ${if (info.batt >= 0) "${info.batt}%" else "unknown"}${if (info.chg) " (charging)" else ""}")
            BfProtocol.lastRestartText(info)?.let { sb.appendLine("Summary: " + redact(it)) }
            sb.appendLine("Restart reason (rst): ${field(info.rst)}")
            sb.appendLine("Last crash (last_crash): ${field(info.lastCrash)}")
            sb.appendLine("Crash info (crash_info): ${field(info.crashInfo)}")
            sb.appendLine("Restart caused by (restart_why): ${field(info.restartWhy)}")
        }
        sb.append(NOT_INCLUDED)
        return sb.toString()
    }

    private fun field(v: String) = v.trim().ifBlank { "none" }.let { redact(it) }
}
