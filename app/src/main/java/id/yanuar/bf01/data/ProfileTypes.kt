package id.yanuar.bf01.data

/**
 * Hi! profile types. To add a new type: add one [ProfileType] line to [ProfileTypes.all]
 * (and its logo to the firmware logo set + ui/LogoBits.kt). Nothing else needs to change.
 */
enum class Field { HANDLE, PHONE, EMAIL, URL }

data class ProfileType(
    val key: String,              // stored value, also the logo key sent to BF
    val label: String,
    val field: Field,
    val valueLabel: String,       // label of the value text field
    val example: String,
    val domains: List<String> = emptyList(),   // used to recognise the logo of a custom link
    val build: (String) -> String,             // clean value -> URL
)

object ProfileTypes {
    const val LIMIT = 6              // BF stores up to 6 profiles
    const val MAX_QR_BYTES = 119     // longest text the BF QR (version 10, error correction H) holds
    const val OTHER = "other"

    private fun handle(v: String) = v.trim().removePrefix("@").trim('/')
    private fun hasScheme(v: String) = v.startsWith("http://", true) || v.startsWith("https://", true)
    /** "instagram.com/me", "www.x.com" (no scheme) -> true; "alex.phone" (a username with a dot) -> false. */
    private fun looksLikeLink(v: String) = !v.startsWith("@") && !v.contains(' ') && v.substringBefore('/').contains('.') && (v.contains('/') || v.startsWith("www.", true))
    private fun asUrl(v: String): String { val x = v.trim(); return if (hasScheme(x)) x else "https://$x" }

    val all: List<ProfileType> = listOf(
        ProfileType("wa", "WhatsApp", Field.PHONE, "WhatsApp number (62…)", "6281234567890", listOf("wa.me", "whatsapp.com")) { v -> "https://wa.me/" + phoneDigits(v) },
        ProfileType("ig", "Instagram", Field.HANDLE, "Username or link", "alex", listOf("instagram.com", "instagr.am")) { "https://instagram.com/" + handle(it) },
        ProfileType("li", "LinkedIn", Field.HANDLE, "Username or link", "alex-design", listOf("linkedin.com", "lnkd.in")) { "https://www.linkedin.com/in/" + handle(it) },
        ProfileType("yt", "YouTube", Field.HANDLE, "Channel (@name) or link", "@alex", listOf("youtube.com", "youtu.be")) { "https://www.youtube.com/@" + handle(it) },
        ProfileType("gh", "GitHub", Field.HANDLE, "Username or link", "alex", listOf("github.com")) { "https://github.com/" + handle(it) },
        ProfileType("tt", "TikTok", Field.HANDLE, "Username or link", "alex", listOf("tiktok.com")) { "https://www.tiktok.com/@" + handle(it) },
        ProfileType("fb", "Facebook", Field.HANDLE, "Username or link", "alex.phone", listOf("facebook.com", "fb.com", "fb.me")) { "https://www.facebook.com/" + handle(it) },
        ProfileType("x", "X", Field.HANDLE, "Username or link", "alex", listOf("x.com", "twitter.com")) { "https://x.com/" + handle(it) },
        ProfileType("tg", "Telegram", Field.HANDLE, "Username or link", "alex", listOf("t.me", "telegram.me", "telegram.org")) { "https://t.me/" + handle(it) },
        ProfileType("em", "Email", Field.EMAIL, "Email address", "hello@example.com") { "mailto:" + it.trim().removePrefix("mailto:").trim() },
        ProfileType("web", "Website", Field.URL, "Website address", "example.com") { asUrl(it) },
        ProfileType("waw", "WhatsApp Work", Field.PHONE, "WhatsApp Business number (62…)", "6281234567890", listOf("wa.me", "whatsapp.com")) { v -> "https://wa.me/" + phoneDigits(v) },
        ProfileType(OTHER, "Other / Link", Field.URL, "Link", "example.com/page") { asUrl(it) },
    )
    fun type(key: String): ProfileType = all.firstOrNull { it.key == key } ?: all.first { it.key == OTHER }
    fun label(key: String) = all.firstOrNull { it.key == key }?.label ?: key

    /** Digits only; a leading 0 is read as Indonesia (62). */
    fun phoneDigits(v: String): String { val d = v.filter(Char::isDigit); return if (d.startsWith("0")) "62" + d.drop(1) else d }

    /** The text inside the QR for [p]; "" while nothing is filled in. */
    fun url(p: Prefs.Profile): String {
        val v = p.value.trim()
        if (v.isEmpty()) return ""
        val t = type(p.type)
        if (t.field == Field.HANDLE || t.field == Field.URL) {
            if (hasScheme(v)) return v
            if (t.field == Field.HANDLE && looksLikeLink(v)) return asUrl(v)
        }
        val base = t.build(v)
        if (t.field == Field.PHONE && p.msg.isNotBlank() && phoneDigits(v).isNotEmpty()) return base + "?text=" + percentEncode(p.msg.trim())
        return base
    }

    /** Same escaping as android.net.Uri.encode (keeps A-Z a-z 0-9 and _-!.~'()*). */
    fun percentEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if (c < 128 && (c.toChar().isLetterOrDigit() || c.toChar() in "_-!.~'()*")) sb.append(c.toChar())
            else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
        return sb.toString()
    }

    fun qrBytes(p: Prefs.Profile) = url(p).toByteArray(Charsets.UTF_8).size
    fun tooLong(p: Prefs.Profile) = qrBytes(p) > MAX_QR_BYTES

    /** Host of a link without scheme / www. / m. ("" when not a link). */
    fun host(url: String): String {
        val u = url.trim(); if (u.startsWith("mailto:", true)) return ""
        var h = u.substringAfter("://", u).substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
        for (pre in listOf("www.", "m.", "mobile.")) if (h.startsWith(pre)) h = h.removePrefix(pre)
        return h
    }

    /** Logo key of a known site for [url] ("" when the domain is not recognised). */
    fun detectLogo(url: String): String {
        if (url.trim().startsWith("mailto:", true)) return "em"
        val h = host(url); if (h.isEmpty()) return ""
        for (t in all) if (t.key != "waw") for (d in t.domains) if (h == d || h.endsWith(".$d")) return t.key
        return ""
    }

    /** Icons a custom link can choose from (key to label). "" = automatic. */
    val iconChoices: List<Pair<String, String>> = listOf("" to "Automatic") + all.filter { it.key != OTHER && it.key != "waw" }.map { it.key to it.label } +
        listOf("link" to "Link (chain)", "#" to "Letter badge")

    /** First letter or digit (A-Z, 0-9) of [s], upper case; "" if none. */
    fun letterOf(s: String): String = s.firstOrNull { it.code < 128 && it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: ""

    /**
     * Logo key that goes into the QR on BF and the app: the type's own logo; for Other / Link the chosen icon,
     * else the logo of the link's domain, else a letter badge ("#K"), never the BF logo.
     */
    fun logoKey(p: Prefs.Profile): String {
        if (p.type != OTHER) return if (p.type in all.map { it.key }) p.type else "link"
        val letter = { letterOf(p.name).ifEmpty { letterOf(host(url(p))) } }
        return when (p.icon) {
            "" -> detectLogo(url(p)).ifEmpty { letter().let { if (it.isEmpty()) "link" else "#$it" } }
            "#" -> letter().let { if (it.isEmpty()) "link" else "#$it" }
            else -> p.icon
        }
    }
    /** Name shown on BF under the QR. */
    fun displayName(p: Prefs.Profile) = p.name.trim().ifEmpty { label(p.type) }.take(24)

    /** Old data -> new list (see [Prefs.profiles]). [oldProfilesJson] is the old Hi! profile list ([{t,v,n}]). */
    fun migrate(oldProfilesJson: String, cardName: String, cardPhone: String, cardMsg: String): List<Prefs.Profile> {
        val old = runCatching { val a = org.json.JSONArray(oldProfilesJson)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Prefs.Profile(o.getString("t"), o.getString("v"), o.optString("n", "")) } } }.getOrDefault(emptyList())
        val first = if (cardPhone.any(Char::isDigit)) listOf(Prefs.Profile("wa", cardPhone.filter(Char::isDigit), cardName.trim().take(24), cardMsg.trim())) else emptyList()
        return (first + old).take(LIMIT)
    }
}
