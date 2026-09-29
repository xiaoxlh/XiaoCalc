package com.example.xiaocalc.calc

import androidx.compose.runtime.Immutable

/**
 * 一条历史记录。
 *
 * 旧实现只往 `MutableList<String>` 里塞 `"表达式 = 结果"` 并只存在内存里——
 * 应用一退就全丢。这里改成结构化条目 + 持久化，并保留原表达式以便一键回填。
 */
@Immutable
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long,
) {
    /** 展示用单行文本 */
    val display: String get() = "$expression = $result"
}

/**
 * 历史记录的持久化编码。
 *
 * 刻意不引 JSON 库：手表包体敏感，且核心逻辑要保持纯 Kotlin 可测。
 * 记录之间用 RS(0x1E) 分隔、字段之间用 US(0x1F) 分隔，
 * 文本内的分隔符与反斜杠做转义，因此表达式里的任何可见字符都不会破坏结构。
 */
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
