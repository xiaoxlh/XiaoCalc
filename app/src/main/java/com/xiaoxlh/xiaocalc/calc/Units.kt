package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal

enum class UnitCategory(val label: String) {
    LENGTH("长度"),
    AREA("面积"),
    VOLUME("体积"),
    MASS("质量"),
    TEMPERATURE("温度"),
    SPEED("速度"),
    TIME("时间"),
    DATA("数据"),
    PRESSURE("压强"),
    ENERGY("能量"),
    POWER("功率"),
    ANGLE("角度"),
}

data class UnitDef(
    val label: String,
    val symbol: String,
    val factor: BigDecimal,
    val preOffset: BigDecimal = BigDecimal.ZERO,
)

object Units {
    private fun frac(numerator: String, denominator: String): BigDecimal =
        Dec.div(BigDecimal(numerator), BigDecimal(denominator))

    private fun of(s: String): BigDecimal = BigDecimal(s)

    val catalog: Map<UnitCategory, List<UnitDef>> = mapOf(
        UnitCategory.LENGTH to listOf(
            UnitDef("米", "m", BigDecimal.ONE),
            UnitDef("英尺", "ft", of("0.3048")),
            UnitDef("厘米", "cm", of("0.01")),
            UnitDef("毫米", "mm", of("0.001")),
            UnitDef("千米", "km", of("1000")),
            UnitDef("英寸", "in", of("0.0254")),
            UnitDef("码", "yd", of("0.9144")),
            UnitDef("英里", "mi", of("1609.344")),
            UnitDef("海里", "nmi", of("1852")),
        ),
        UnitCategory.AREA to listOf(
            UnitDef("平方米", "m²", BigDecimal.ONE),
            UnitDef("平方英尺", "ft²", of("0.09290304")),
            UnitDef("平方厘米", "cm²", of("0.0001")),
            UnitDef("平方千米", "km²", of("1000000")),
            UnitDef("公顷", "ha", of("10000")),
            UnitDef("亩", "mu", frac("2000", "3")),
            UnitDef("英亩", "acre", of("4046.8564224")),
        ),
        UnitCategory.VOLUME to listOf(
            UnitDef("升", "L", BigDecimal.ONE),
            UnitDef("加仑(美)", "gal", of("3.785411784")),
            UnitDef("毫升", "mL", of("0.001")),
            UnitDef("立方厘米", "cm³", of("0.001")),
            UnitDef("立方米", "m³", of("1000")),
            UnitDef("品脱(美)", "pt", of("0.473176473")),
        ),
        UnitCategory.MASS to listOf(
            UnitDef("千克", "kg", BigDecimal.ONE),
            UnitDef("磅", "lb", of("0.45359237")),
            UnitDef("克", "g", of("0.001")),
            UnitDef("毫克", "mg", of("0.000001")),
            UnitDef("吨", "t", of("1000")),
            UnitDef("斤", "jin", of("0.5")),
            UnitDef("两", "liang", of("0.05")),
            UnitDef("盎司", "oz", of("0.028349523125")),
        ),
        UnitCategory.TEMPERATURE to listOf(
            UnitDef("摄氏度", "°C", BigDecimal.ONE),
            UnitDef("华氏度", "°F", frac("5", "9"), of("-32")),
            UnitDef("开尔文", "K", BigDecimal.ONE, of("-273.15")),
        ),
        UnitCategory.SPEED to listOf(
            UnitDef("千米/时", "km/h", frac("5", "18")),
            UnitDef("英里/时", "mph", of("0.44704")),
            UnitDef("米/秒", "m/s", BigDecimal.ONE),
            UnitDef("节", "kn", frac("1852", "3600")),
            UnitDef("英尺/秒", "ft/s", of("0.3048")),
        ),
        UnitCategory.TIME to listOf(
            UnitDef("分", "min", of("60")),
            UnitDef("时", "h", of("3600")),
            UnitDef("秒", "s", BigDecimal.ONE),
            UnitDef("毫秒", "ms", of("0.001")),
            UnitDef("天", "d", of("86400")),
            UnitDef("周", "wk", of("604800")),
        ),
        UnitCategory.DATA to listOf(
            UnitDef("兆字节", "MB", of("1048576")),
            UnitDef("吉字节", "GB", of("1073741824")),
            UnitDef("千字节", "KB", of("1024")),
            UnitDef("字节", "B", BigDecimal.ONE),
            UnitDef("比特", "bit", of("0.125")),
            UnitDef("太字节", "TB", of("1099511627776")),
        ),
        UnitCategory.PRESSURE to listOf(
            UnitDef("千帕", "kPa", of("1000")),
            UnitDef("兆帕", "MPa", of("1000000")),
            UnitDef("帕斯卡", "Pa", BigDecimal.ONE),
            UnitDef("巴", "bar", of("100000")),
            UnitDef("标准大气压", "atm", of("101325")),
            UnitDef("毫米汞柱", "mmHg", of("133.322387415")),
        ),
        UnitCategory.ENERGY to listOf(
            UnitDef("千卡", "kcal", of("4184")),
            UnitDef("千焦", "kJ", of("1000")),
            UnitDef("卡", "cal", of("4.184")),
            UnitDef("焦耳", "J", BigDecimal.ONE),
            UnitDef("瓦时", "Wh", of("3600")),
            UnitDef("千瓦时", "kWh", of("3600000")),
        ),
        UnitCategory.POWER to listOf(
            UnitDef("千瓦", "kW", of("1000")),
            UnitDef("公制马力", "PS", of("735.49875")),
            UnitDef("瓦", "W", BigDecimal.ONE),
            UnitDef("兆瓦", "MW", of("1000000")),
        ),
        UnitCategory.ANGLE to listOf(
            UnitDef("度", "°", BigDecimal.ONE),
            UnitDef("弧度", "rad", Dec.div(of("180"), Dec.PI)),
            UnitDef("角分", "′", frac("1", "60")),
            UnitDef("角秒", "″", frac("1", "3600")),
            UnitDef("百分度", "grad", of("0.9")),
        ),
    )

    fun baseOf(category: UnitCategory): UnitDef =
        catalog.getValue(category).first { it.preOffset.signum() == 0 && it.factor.compareTo(BigDecimal.ONE) == 0 }

    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): BigDecimal {
        val base = Dec.mul(Dec.add(value, from.preOffset), from.factor)
        return Dec.sub(Dec.div(base, to.factor), to.preOffset)
    }
}
