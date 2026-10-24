package id.yanuar.bf01.wifi

/** Standard Wi-Fi QR payload: WIFI:T:WPA;S:<ssid>;P:<pass>;H:true;;  (fields in any order, \ escapes \ ; , : "). */
data class WifiQr(val ssid: String, val pass: String, val type: String, val hidden: Boolean) {
    val open get() = type.equals("nopass", true) || (type.isBlank() && pass.isEmpty())

    companion object {
        fun parse(raw: String?): WifiQr? {
            val s = raw?.trim() ?: return null
            if (!s.startsWith("WIFI:", ignoreCase = true)) return null
            val fields = HashMap<String, String>(); val cur = StringBuilder(); var esc = false
            // Key/value split uses the first unescaped ':' only, so we track it while unescaping.
            val body = s.substring(5)
            val parts = ArrayList<Pair<String, String>>()
            var key: String? = null
            for (c in body) {
                when {
                    esc -> { cur.append(c); esc = false }
                    c == '\\' -> esc = true
                    c == ':' && key == null -> { key = cur.toString(); cur.clear() }
                    c == ';' -> { if (key != null) parts += key!!.uppercase() to cur.toString(); key = null; cur.clear() }
                    else -> cur.append(c)
                }
            }
            if (key != null) parts += key!!.uppercase() to cur.toString()
            for ((k, v) in parts) fields.putIfAbsent(k, v)
            val ssid = unquoteHex(fields["S"] ?: return null)
            if (ssid.isEmpty()) return null
            val type = fields["T"] ?: ""
            val pass = if (type.equals("nopass", true)) "" else unquoteHex(fields["P"] ?: "")
            return WifiQr(ssid, pass, type, fields["H"].equals("true", true))
        }

        /** Some generators wrap values in "quotes". */
        private fun unquoteHex(v: String) = if (v.length >= 2 && v.startsWith('"') && v.endsWith('"')) v.substring(1, v.length - 1) else v
    }
}
