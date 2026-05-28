package com.spamblocker.election.filter

data class FilterDecision(
    val isSpam: Boolean,
    val matchedKeyword: String? = null,
) {
    val reason: String?
        get() = matchedKeyword?.let { "키워드: $it" }

    companion object {
        val NotSpam = FilterDecision(isSpam = false)
    }
}

class SpamFilter(
    private val keywords: List<String>,
    private val whitelistNumbers: Set<String> = emptySet(),
) {
    fun classifyMessage(sender: String?, body: String?): FilterDecision {
        if (sender != null && isWhitelisted(sender)) return FilterDecision.NotSpam

        val haystack = buildString {
            if (sender != null) append(sender).append(' ')
            if (body != null) append(body)
        }.lowercase()
        if (haystack.isBlank()) return FilterDecision.NotSpam

        val keywordHit = keywords.firstOrNull { keyword ->
            keyword.isNotBlank() && haystack.contains(keyword.lowercase())
        }
        return if (keywordHit != null) {
            FilterDecision(isSpam = true, matchedKeyword = keywordHit)
        } else FilterDecision.NotSpam
    }

    private fun isWhitelisted(number: String): Boolean {
        val normalized = normalizeNumber(number)
        if (normalized.isEmpty()) return false
        return whitelistNumbers.any { normalizeNumber(it) == normalized }
    }

    private fun normalizeNumber(raw: String): String =
        raw.filter { it.isDigit() }
}
