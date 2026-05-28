package com.spamblocker.election.data

import android.content.Context
import android.content.SharedPreferences
import com.spamblocker.election.filter.DefaultRules
import com.spamblocker.election.filter.SpamFilter

class SettingsStore private constructor(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var keywords: List<String>
        get() {
            val raw = prefs.getString(KEY_KEYWORDS, null)
            return if (raw.isNullOrBlank()) DefaultRules.keywords
            else raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) {
            prefs.edit().putString(KEY_KEYWORDS, value.joinToString("\n")).apply()
        }

    var whitelist: Set<String>
        get() = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WHITELIST, value).apply()

    var blockedCount: Int
        get() = prefs.getInt(KEY_BLOCK_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BLOCK_COUNT, value).apply()

    fun resetKeywordsToDefault() {
        prefs.edit().remove(KEY_KEYWORDS).apply()
    }

    fun buildFilter(): SpamFilter = SpamFilter(
        keywords = keywords,
        whitelistNumbers = whitelist,
    )

    fun incrementBlockCount() {
        blockedCount = blockedCount + 1
    }

    companion object {
        private const val PREFS = "spam_blocker_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_BLOCK_COUNT = "blocked_count"

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
