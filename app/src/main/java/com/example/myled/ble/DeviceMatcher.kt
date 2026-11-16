package com.example.myled.ble

object DeviceMatcher {
    private val HINTS = listOf(
        "elk-bledom", "bledom", "duoco", "ledble", "melk", "qhm-", "triones", "led-", "light",
    )

    /** Case-insensitive: the advertised name starts with or contains any known controller hint. */
    fun isLikelyController(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.lowercase()
        return HINTS.any { n.startsWith(it) || n.contains(it) }
    }
}
