package com.streamhub.app

import java.net.URLEncoder

private const val SEARCH_URL = "https://duckduckgo.com/?q="

fun normalizeUrl(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return SEARCH_URL
    if (text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true)
    ) {
        return text
    }
    val looksLikeHost = !text.contains(" ") && text.contains(".") && !text.contains("..")
    if (looksLikeHost) return "https://$text"
    return SEARCH_URL + URLEncoder.encode(text, "UTF-8")
}
