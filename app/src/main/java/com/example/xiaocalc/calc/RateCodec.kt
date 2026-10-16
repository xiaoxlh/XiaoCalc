package com.example.xiaocalc.calc

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject

/**
 * 汇率快照的解析与本地存储编码。
 *
 * 之所以要自己写：接口返回的是 JSON，而本项目**不引第三方 JSON 库**——
 * Android 框架自带 `org.json`，省下一个依赖和几十 KB 包体。
 * （单元测试里则用 Maven 上的 `org.json:json`，因为 JVM 上没有这个类。）
 */
object RateCodec {

    /** 本地存储格式的版本号，便于日后改动时识别老数据 */
    private const val VERSION = "v1"

    /**
     * 解析 Frankfurter 的 `/v2/rates` 响应。
     *
     * 期望形如：
     * ```json
     * [{"date":"2026-10-16","base":"CNY","quote":"USD","rate":0.14961}, …]
     * ```
     *
     * 任何一项格式不对就整份丢弃——半份汇率比没有汇率更危险，
     * 用户会把基于错数据的换算结果当真。
     */
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
                // 一份表里混了两个基准，无法互相换算
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

    /**
     * 取汇率数值。
     *
     * 刻意走 `toString()` 再交给 [BigDecimal]，而不是 `getDouble()`：
     * 后者会先把十进制文本变成二进制浮点，白白引入一次舍入。
     */
    private fun JSONObject.optRate(): BigDecimal? {
        val raw = opt("rate") ?: return null
        return try {
            BigDecimal(raw.toString())
        } catch (_: NumberFormatException) {
            null
        }
    }

    // ------------------------------------------------------------ 本地存储

    /**
     * 编码成一行文本。
     *
     * 格式：`v1|基准|日期|CODE=汇率;CODE=汇率;…`
     * 用 `;` 与 `=` 分隔是为了避免再引一个序列化器；
     * 货币代码与日期都不含这些字符，无需转义。
     */
    fun encode(table: RateTable): String = buildString {
        append(VERSION).append('|')
        append(table.base).append('|')
        append(table.date).append('|')
        append(
            table.rates.entries.joinToString(";") { "${it.key}=${it.value.toPlainString()}" },
        )
    }

    /** 解析本地存储；格式不符或为空时返回 null，由调用方当作"还没有汇率"处理 */
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
