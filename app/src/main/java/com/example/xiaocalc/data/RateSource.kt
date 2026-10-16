package com.example.xiaocalc.data

import com.example.xiaocalc.calc.RateCodec
import com.example.xiaocalc.calc.RateTable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 汇率数据源。
 *
 * 抽成接口是为了让 [com.example.xiaocalc.calc.CurrencyState] 能被单元测试驱动，
 * 而不必真的联网——测试里注入一个返回固定值的实现即可。
 */
interface RateSource {
    /** 失败（网络、超时、格式不符）时返回 null，不抛异常 */
    fun fetch(base: String, quotes: List<String>): RateTable?
}

/**
 * Frankfurter（欧洲央行等官方源）实现。
 *
 * ## 为什么选它
 *
 * 无 API key、无调用配额、只走 HTTPS GET、223 种货币、104 家央行与官方机构，
 * 其中 ECB 是它的原始数据源。对"可信渠道"这个要求来说，
 * 它比需要注册的聚合服务更干净——不需要在本应用里存放任何凭据。
 *
 * ## 隐私
 *
 * 请求只带货币代码，不带任何设备或用户标识。本实现**不发送** User-Agent 之外的
 * 任何头，也不记录日志。
 */
class FrankfurterRateSource(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : RateSource {

    override fun fetch(base: String, quotes: List<String>): RateTable? {
        val url = buildString {
            append(endpoint)
            append("?base=").append(encode(base))
            append("&quotes=").append(encode(quotes.joinToString(",")))
        }
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                RateCodec.parse(body)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // 网络异常的种类很多（DNS、超时、TLS），对用户而言都只是"没取到"。
            // 这里全部吞掉并返回 null，由界面统一提示。
            null
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.frankfurter.dev/v2/rates"
    }
}
