package com.example.xiaocalc.calc

import androidx.compose.runtime.Immutable

@Immutable
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long,
) {
    val display: String get() = "$expression = $result"
}

object HistoryCodec {
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'

    fun encode(entries: List<HistoryEntry>): String = entries.joinToString(RECORD.toString()) { entry ->
        listOf(entry.timestamp.toString(), escape(entry.expression), escape(entry.result))
            .joinToString(FIELD.toString())
    }

    fun decode(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size != 3) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            HistoryEntry(unescape(parts[1]), unescape(parts[2]), timestamp)
        }
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                RECORD -> append("\\r")
                FIELD -> append("\\u")
                else -> append(c)
            }
        }
    }

    private fun unescape(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    '\\' -> append('\\')
                    'r' -> append(RECORD)
                    'u' -> append(FIELD)
                    else -> append(text[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
