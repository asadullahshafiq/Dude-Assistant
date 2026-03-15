package com.dude.assistant

/**
 * Urdu/Roman-Urdu voice commands ko parse karta hai
 * aur ActionType + parameters return karta hai.
 */
object CommandParser {

    enum class ActionType {
        WHATSAPP_MESSAGE,
        OPEN_APP,
        CLOSE_APP,
        BACK,
        HOME,
        SCREENSHOT,
        CALL,
        VOLUME_SET,
        VOLUME_UP,
        VOLUME_DOWN,
        SEARCH_WEB,
        CLICK_TEXT,
        CLICK_COLOR,
        SCROLL_UP,
        SCROLL_DOWN,
        RECENT_APPS,
        NOTIFICATION_PANEL,
        WIFI_TOGGLE,
        BLUETOOTH_TOGGLE,
        FLASHLIGHT_TOGGLE,
        BRIGHTNESS_SET,
        REPLY_LAST,
        READ_NOTIFICATIONS,
        ANSWER_QUESTION,
        FAREWELL,
        GREETING,
        UNKNOWN
    }

    data class ParsedCommand(
        val action: ActionType,
        val params: Map<String, String> = emptyMap(),
        val rawText: String = ""
    )

    fun parse(text: String, encryptionManager: EncryptionManager): ParsedCommand {
        val t = text.lowercase().trim()

        // ─── Farewell ─────────────────────────────────────────
        if (t.contains("bye dude") || t.contains("bye") && t.contains("dude") ||
            t.contains("band kar") || t.contains("band karo") || t.contains("alvida")) {
            return ParsedCommand(ActionType.FAREWELL, rawText = text)
        }

        // ─── Greeting ─────────────────────────────────────────
        if (t.contains("hello") || t.contains("hi dude") || t.contains("kya hal") ||
            t.contains("assalam") || t.contains("salam")) {
            return ParsedCommand(ActionType.GREETING, rawText = text)
        }

        // ─── WhatsApp Message ──────────────────────────────────
        val waPattern = Regex(
            "(whatsapp|wassup|watsup).*(pe|par|ko|mein).*(message|msg|text|likho|bhejo|send|bata|bol)"
        )
        val waAlt = Regex("(message|msg|text).*(bhejo|karo|kro|likho|send).*(whatsapp|wassup)")
        val waSimple = Regex("(whatsapp).*(ko).*(kaho|bolo|bol|bata|likho|bhejo)")

        if (waPattern.containsMatchIn(t) || waAlt.containsMatchIn(t) || waSimple.containsMatchIn(t) ||
            (t.contains("whatsapp") && (t.contains("message") || t.contains("msg") || t.contains("bata") || t.contains("kaho")))) {

            val contactName = extractContactName(t, encryptionManager)
            val message = extractMessageContent(t)
            return ParsedCommand(
                ActionType.WHATSAPP_MESSAGE,
                mapOf("contact" to contactName, "message" to message),
                rawText = text
            )
        }

        // ─── Call ─────────────────────────────────────────────
        if ((t.contains("call") || t.contains("phone") || t.contains("ring") ||
                    t.contains("milaao") || t.contains("baat karo")) &&
            !t.contains("whatsapp")) {
            val contactName = extractContactName(t, encryptionManager)
            return ParsedCommand(
                ActionType.CALL,
                mapOf("contact" to contactName),
                rawText = text
            )
        }

        // ─── App Open ─────────────────────────────────────────
        val openKeywords = listOf("kholo", "open", "chalaao", "start", "launch", "karo on", "on karo")
        if (openKeywords.any { t.contains(it) } || t.contains("khol")) {
            val appName = extractAppName(t)
            if (appName.isNotEmpty()) {
                return ParsedCommand(ActionType.OPEN_APP, mapOf("app" to appName), rawText = text)
            }
        }

        // ─── App Close ────────────────────────────────────────
        val closeKeywords = listOf("band karo", "band kro", "close karo", "close kro", "close", "quit")
        if (closeKeywords.any { t.contains(it) }) {
            val appName = extractAppName(t)
            return ParsedCommand(ActionType.CLOSE_APP, mapOf("app" to appName), rawText = text)
        }

        // ─── Navigation ───────────────────────────────────────
        if (t.contains("wapas") || t.contains("peeche") || t.contains("back")) {
            return ParsedCommand(ActionType.BACK, rawText = text)
        }
        if ((t.contains("home") && !t.contains("screen")) || t.contains("ghar") && t.contains("screen")) {
            return ParsedCommand(ActionType.HOME, rawText = text)
        }
        if (t.contains("recent") || t.contains("apps list") || t.contains("chalti apps")) {
            return ParsedCommand(ActionType.RECENT_APPS, rawText = text)
        }

        // ─── Screenshot ───────────────────────────────────────
        if (t.contains("screenshot") || t.contains("screen capture") ||
            t.contains("tasveer lo") || t.contains("capture karo")) {
            return ParsedCommand(ActionType.SCREENSHOT, rawText = text)
        }

        // ─── Volume ───────────────────────────────────────────
        val volNum = Regex("volume\\s*(\\d+)").find(t)
            ?: Regex("awaz\\s*(\\d+)").find(t)
        if (volNum != null) {
            return ParsedCommand(ActionType.VOLUME_SET, mapOf("level" to volNum.groupValues[1]), rawText = text)
        }
        if (t.contains("volume badhao") || t.contains("awaz badhao") || t.contains("volume up")) {
            return ParsedCommand(ActionType.VOLUME_UP, rawText = text)
        }
        if (t.contains("volume kam karo") || t.contains("awaz kam karo") || t.contains("volume down")) {
            return ParsedCommand(ActionType.VOLUME_DOWN, rawText = text)
        }

        // ─── Web Search ───────────────────────────────────────
        if (t.contains("search karo") || t.contains("google pe") || t.contains("dhundo") ||
            t.contains("bata") || t.contains("kya hai") || t.contains("what is") ||
            t.contains("nikal") || t.contains("batao") || t.contains("search")) {
            val query = extractSearchQuery(t)
            return ParsedCommand(ActionType.SEARCH_WEB, mapOf("query" to query), rawText = text)
        }

        // ─── Click by text ────────────────────────────────────
        val clickText = Regex("\"([^\"]+)\"\\s*(pe|par|ko)\\s*(click|tap|dabao|press)")
            .find(t)?.groupValues?.get(1)
        val startBtn = Regex("(start|ok|cancel|send|submit|login|done|next|agle)\\s*(wala|ka)\\s*button")
            .find(t)?.groupValues?.get(1)

        if (clickText != null) {
            return ParsedCommand(ActionType.CLICK_TEXT, mapOf("text" to clickText), rawText = text)
        }
        if (startBtn != null) {
            return ParsedCommand(ActionType.CLICK_TEXT, mapOf("text" to startBtn), rawText = text)
        }
        if (t.contains("click") || t.contains("press") || t.contains("dabao") || t.contains("tap")) {
            val label = extractButtonLabel(t)
            if (label.isNotEmpty()) {
                return ParsedCommand(ActionType.CLICK_TEXT, mapOf("text" to label), rawText = text)
            }
        }

        // ─── Click by color ───────────────────────────────────
        val colorBtn = Regex("(blue|red|green|yellow|orange|purple|neel|laal|sabz|neela|lal)\\s*(button|btn|wala)")
            .find(t)
        if (colorBtn != null) {
            val color = colorBtn.groupValues[1]
            return ParsedCommand(ActionType.CLICK_COLOR, mapOf("color" to color), rawText = text)
        }

        // ─── Scroll ───────────────────────────────────────────
        if (t.contains("upar") && t.contains("scroll") || t.contains("scroll up")) {
            return ParsedCommand(ActionType.SCROLL_UP, rawText = text)
        }
        if (t.contains("neeche") && t.contains("scroll") || t.contains("scroll down")) {
            return ParsedCommand(ActionType.SCROLL_DOWN, rawText = text)
        }

        // ─── Notification ─────────────────────────────────────
        if (t.contains("notification") || t.contains("ittefaq")) {
            return ParsedCommand(ActionType.NOTIFICATION_PANEL, rawText = text)
        }

        // ─── Toggles ──────────────────────────────────────────
        if (t.contains("wifi") || t.contains("wi-fi")) {
            return ParsedCommand(ActionType.WIFI_TOGGLE, rawText = text)
        }
        if (t.contains("bluetooth") || t.contains("blue tooth")) {
            return ParsedCommand(ActionType.BLUETOOTH_TOGGLE, rawText = text)
        }
        if (t.contains("torch") || t.contains("flashlight") || t.contains("flash")) {
            return ParsedCommand(ActionType.FLASHLIGHT_TOGGLE, rawText = text)
        }

        // ─── General Question – answer karein ─────────────────
        if (t.length > 5) {
            return ParsedCommand(ActionType.ANSWER_QUESTION, mapOf("q" to text), rawText = text)
        }

        return ParsedCommand(ActionType.UNKNOWN, rawText = text)
    }

    // ─── Helper Functions ─────────────────────────────────────────────────────

    private fun extractContactName(t: String, em: EncryptionManager): String {
        val contacts = em.getAllContacts()
        for (name in contacts.keys) {
            if (t.contains(name.lowercase())) return name
        }
        // Common words that might be names
        val patterns = Regex("(mama|baba|bhai|behn|dost|yaar|abbu|ammi|apa|uncle|aunty)")
        return patterns.find(t)?.value ?: "mama"
    }

    private fun extractMessageContent(t: String): String {
        val msgMarkers = listOf("ke?h?o? ke?", "k ", "kaho ke", "likho ke", "bolo ke",
            "bhejo ke", "message karo ke", "bol ke", "ke bol")
        for (marker in msgMarkers) {
            val regex = Regex("$marker\\s*(.+)$")
            val match = regex.find(t)
            if (match != null) {
                val msg = match.groupValues[1].trim()
                if (msg.length > 2) return msg
            }
        }
        // Try to get text after "message" word
        val afterMsg = Regex("message.{1,30}?\"(.+?)\"").find(t)?.groupValues?.get(1)
        if (afterMsg != null) return afterMsg
        return "Kya hal hai?"
    }

    private fun extractAppName(t: String): String {
        val apps = mapOf(
            "youtube" to "youtube",
            "whatsapp" to "whatsapp",
            "facebook" to "facebook",
            "instagram" to "instagram",
            "chrome" to "chrome",
            "camera" to "camera",
            "gallery" to "gallery",
            "settings" to "settings",
            "maps" to "maps",
            "spotify" to "spotify",
            "tiktok" to "tiktok",
            "telegram" to "telegram",
            "gmail" to "gmail",
            "calculator" to "calculator",
            "clock" to "clock",
            "calendar" to "calendar",
            "notes" to "notes",
            "files" to "files",
            "play store" to "play store",
            "dialer" to "dialer"
        )
        for ((key, value) in apps) {
            if (t.contains(key)) return value
        }
        return ""
    }

    private fun extractSearchQuery(t: String): String {
        val markers = listOf("google pe", "search karo", "dhundo", "bata", "kya hai", "nikal k do", "nikal")
        for (m in markers) {
            val idx = t.indexOf(m)
            if (idx >= 0) {
                val q = t.substring(idx + m.length).trim()
                if (q.length > 2) return q
            }
        }
        return t
    }

    private fun extractButtonLabel(t: String): String {
        val matches = Regex("(start|ok|submit|send|login|next|done|cancel|yes|no|haan|nahi|aage|peeche)").find(t)
        return matches?.value ?: ""
    }
}
