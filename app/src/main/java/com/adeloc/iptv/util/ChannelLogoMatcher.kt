package com.adeloc.iptv.util

import java.util.Locale

object ChannelLogoMatcher {

    private const val LOGO_BASE_URL = "https://raw.githubusercontent.com/Interstellar-Developers/TV-Logos/main/logos"

    /**
     * Normalizes a channel name and returns a fallback logo URL.
     * Example: "UK| BBC 1 FHD [1080p]" -> "https://.../bbc_1.png"
     */
    fun getFallbackLogoUrl(rawName: String): String {
        val normalized = normalizeChannelName(rawName)
        return if (normalized.isNotEmpty()) {
            "$LOGO_BASE_URL/$normalized.png"
        } else {
            ""
        }
    }

    private fun normalizeChannelName(name: String): String {
        // Simple, robust normalization to avoid PatternSyntaxException
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove special characters
            .trim()
            .replace(Regex("\\s+"), "_") // Collapse spaces to underscores
    }
}
