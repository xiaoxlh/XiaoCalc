package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

object RateCodec {
    private const val VERSION = "v1"

    fun parse(json: String): RateTable? {
        val array = try {
            JSONArray(json)
        } catch (_: Exception) {
            return null
        }
        if (array.length() == 0) return null
        val rates = mutableMapOf<String, BigDecimal>()
        var base: String? = null
        var date: String? = null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return null
            val quote = item.optString("quote").takeIf { it.isNotBlank() } ?: return null
            val itemBase = item.optString("base").takeIf { it.isNotBlank() } ?: return null
            val rate = item.optRate() ?: return null
            if (rate.signum() <= 0) return null
            if (base == null) {
                base = itemBase
                date = item.optString("date").takeIf { it.isNotBlank() }
            } else if (base != itemBase) {
                return null
            }
            rates[quote] = rate
        }
        return RateTable(
            base = base ?: return null,
            rates = rates,
            date = date ?: "未知日期",
        )
    }

    private fun JSONObject.optRate(): BigDecimal? {
        val raw = opt("rate") ?: return null
        return try {
            BigDecimal(raw.toString())
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun encode(table: RateTable): String = buildString {
        append(VERSION).append('|')
        append(table.base).append('|')
        append(table.date).append('|')
        append(
            table.rates.entries.joinToString(";") { "${it.key}=${it.value.toPlainString()}" },
        )
    }

    fun decode(text: String?): RateTable? {
        if (text.isNullOrBlank()) return null
        val parts = text.split('|')
        if (parts.size != 4 || parts[0] != VERSION) return null
        val base = parts[1].takeIf { it.isNotBlank() } ?: return null
        val date = parts[2].takeIf { it.isNotBlank() } ?: return null
        val rates = mutableMapOf<String, BigDecimal>()
        if (parts[3].isNotBlank()) {
            for (entry in parts[3].split(';')) {
                val pair = entry.split('=')
                if (pair.size != 2) return null
                val rate = try {
                    BigDecimal(pair[1])
                } catch (_: NumberFormatException) {
                    return null
                }
                if (rate.signum() <= 0) return null
                rates[pair[0]] = rate
            }
        }
        if (rates.isEmpty()) return null
        return RateTable(base, rates, date)
    }
}
