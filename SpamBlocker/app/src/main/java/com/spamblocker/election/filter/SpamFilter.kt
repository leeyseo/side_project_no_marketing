package com.spamblocker.election.filter

data class FilterDecision(
    val isSpam: Boolean,
    val matchedKeyword: String? = null,
    val matchedSenderPattern: String? = null,
) {
    val reason: String?
        get() = matchedKeyword?.let { "키워드: $it" }
            ?: matchedSenderPattern?.let { "발신패턴: $it" }

    companion object {
        val NotSpam = FilterDecision(isSpam = false)
    }
}

class SpamFilter(
    private val keywords: List<String>,
    private val senderPatterns: List<Regex>,
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
        if (keywordHit != null) return FilterDecision(isSpam = true, matchedKeyword = keywordHit)

        if (sender != null) {
            val normalized = normalizeNumber(sender)
            val patternHit = senderPatterns.firstOrNull { it.containsMatchIn(normalized) }
            if (patternHit != null) {
                return FilterDecision(isSpam = true, matchedSenderPattern = patternHit.pattern)
            }
        }
        return FilterDecision.NotSpam
    }

    fun classifyCall(number: String?): FilterDecision {
        if (number.isNullOrBlank()) return FilterDecision.NotSpam
        if (isWhitelisted(number)) return FilterDecision.NotSpam
        val normalized = normalizeNumber(number)
        val patternHit = senderPatterns.firstOrNull { it.containsMatchIn(normalized) }
        return if (patternHit != null) {
            FilterDecision(isSpam = true, matchedSenderPattern = patternHit.pattern)
        } else FilterDecision.NotSpam
    }

    private fun isWhitelisted(number: String): Boolean {
        val normalized = normalizeNumber(number)
        return whitelistNumbers.any { normalizeNumber(it) == normalized }
    }

    private fun normalizeNumber(raw: String): String =
        raw.filter { it.isDigit() }
}
