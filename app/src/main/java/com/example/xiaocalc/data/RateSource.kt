package com.example.xiaocalc.data

import com.example.xiaocalc.calc.RateCodec
import com.example.xiaocalc.calc.RateTable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface RateSource {
    fun fetch(base: String, quotes: List<String>): RateTable?
}

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
            null
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.frankfurter.dev/v2/rates"
    }
}
