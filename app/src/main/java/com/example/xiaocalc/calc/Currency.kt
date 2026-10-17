package com.example.xiaocalc.calc

import java.math.BigDecimal

data class Currency(val code: String, val label: String) {
    val display: String get() = "$label $code"
}

object Currencies {
    const val BASE = "USD"

    val catalog: List<Currency> = listOf(
        Currency("CNY", "人民币"),
        Currency("USD", "美元"),
        Currency("EUR", "欧元"),
        Currency("JPY", "日元"),
        Currency("GBP", "英镑"),
        Currency("HKD", "港币"),
        Currency("KRW", "韩元"),
        Currency("TWD", "新台币"),
        Currency("AUD", "澳元"),
        Currency("CAD", "加元"),
        Currency("SGD", "新加坡元"),
        Currency("CHF", "瑞士法郎"),
        Currency("NZD", "新西兰元"),
        Currency("THB", "泰铢"),
        Currency("MYR", "林吉特"),
        Currency("PHP", "菲律宾比索"),
        Currency("VND", "越南盾"),
        Currency("IDR", "印尼盾"),
        Currency("INR", "印度卢比"),
        Currency("RUB", "卢布"),
        Currency("SEK", "瑞典克朗"),
        Currency("NOK", "挪威克朗"),
        Currency("DKK", "丹麦克朗"),
        Currency("PLN", "兹罗提"),
        Currency("TRY", "土耳其里拉"),
        Currency("BRL", "雷亚尔"),
        Currency("MXN", "墨西哥比索"),
        Currency("ZAR", "南非兰特"),
        Currency("AED", "迪拉姆"),
        Currency("SAR", "里亚尔"),
    )

    val quoteCodes: List<String> = catalog.map { it.code }.filter { it != BASE }

    private val byCode: Map<String, Currency> = catalog.associateBy { it.code }

    fun find(code: String): Currency? = byCode[code]

    fun require(code: String): Currency = byCode.getValue(code)
}

data class RateTable(
    val base: String,
    val rates: Map<String, BigDecimal>,

    val date: String,
) {
    fun rateOf(code: String): BigDecimal? =
        if (code == base) BigDecimal.ONE else rates[code]

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        val fromRate = rateOf(from) ?: return null
        val toRate = rateOf(to) ?: return null
        if (fromRate.signum() == 0) return null
        return Dec.div(Dec.mul(amount, toRate), fromRate)
    }

    val isEmpty: Boolean get() = rates.isEmpty()
}
