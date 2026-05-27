package com.spamblocker.election.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class BlockKind { SMS, CALL }

data class BlockEntry(
    val timestamp: Long,
    val kind: BlockKind,
    val sender: String,
    val preview: String,
    val reason: String,
)

class BlockLog private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("spam_blocker_log", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<BlockEntry>> = _entries.asStateFlow()

    fun add(entry: BlockEntry) {
        val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        _entries.value = updated
        save(updated)
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<BlockEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        BlockEntry(
                            timestamp = o.getLong("ts"),
                            kind = BlockKind.valueOf(o.getString("kind")),
                            sender = o.getString("sender"),
                            preview = o.getString("preview"),
                            reason = o.getString("reason"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(entries: List<BlockEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("ts", e.timestamp)
                    put("kind", e.kind.name)
                    put("sender", e.sender)
                    put("preview", e.preview)
                    put("reason", e.reason)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "log"
        private const val MAX_ENTRIES = 200

        @Volatile private var instance: BlockLog? = null
        fun get(context: Context): BlockLog = instance ?: synchronized(this) {
            instance ?: BlockLog(context).also { instance = it }
        }
    }
}
