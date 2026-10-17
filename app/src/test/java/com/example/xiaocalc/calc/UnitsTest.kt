package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class UnitsTest {
    private fun of(s: String) = BigDecimal(s)

    private fun sig(v: BigDecimal, n: Int = 25): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun unit(category: UnitCategory, symbol: String): UnitDef =
        Units.catalog.getValue(category).first { it.symbol == symbol }

    private fun convert(value: String, category: UnitCategory, from: String, to: String): BigDecimal =
        Units.convert(of(value), unit(category, from), unit(category, to))

    @Test
    fun `每个类别都有且只有一个基准单位`() {
        for (category in UnitCategory.entries) {
            val list = Units.catalog[category]
            assertTrue("$category 没有单位表", list != null && list.isNotEmpty())
            val bases = list!!.filter {
                it.preOffset.signum() == 0 && it.factor.compareTo(BigDecimal.ONE) == 0
            }
            assertEquals("$category 的基准单位应当恰好一个", 1, bases.size)
        }
    }

    @Test
    fun `因子恒为正且符号标签非空且类内不重名`() {
        for ((category, list) in Units.catalog) {
            assertTrue("$category 至少要有两个单位才有意义", list.size >= 2)
            for (u in list) {
                assertTrue("$category/${u.label} 的因子必须为正", u.factor.signum() > 0)
                assertTrue("$category 存在空标签", u.label.isNotBlank())
                assertTrue("$category 存在空符号", u.symbol.isNotBlank())
            }
            val symbols = list.map { it.symbol }
            assertEquals("$category 内符号重复：$symbols", symbols.size, symbols.distinct().size)
            val labels = list.map { it.label }
            assertEquals("$category 内标签重复：$labels", labels.size, labels.distinct().size)
        }
    }

    @Test
    fun `单位符号不含汉字且足够短`() {
        for ((category, list) in Units.catalog) {
            for (u in list) {
                val hasHan = u.symbol.any {
                    Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
                }
                assertTrue("$category/${u.label} 的符号「${u.symbol}」含汉字", !hasHan)
                assertTrue(
                    "$category/${u.label} 的符号「${u.symbol}」超过 5 个字符",
                    u.symbol.length <= 5,
                )
            }
        }
    }

    @Test
    fun `目录覆盖全部类别`() {        for (category in UnitCategory.entries) {
            assertTrue("$category 未在目录中", Units.catalog.containsKey(category))
        }
        assertEquals(UnitCategory.entries.size, Units.catalog.size)
    }

    @Test
    fun `长度`() {
        val c = UnitCategory.LENGTH
        assertEquals("100", sig(convert("1", c, "m", "cm")))
        assertEquals("1000", sig(convert("1", c, "m", "mm")))
        assertEquals("1000", sig(convert("1", c, "km", "m")))
        assertEquals("2.54", sig(convert("1", c, "in", "cm")))
        assertEquals("12", sig(convert("1", c, "ft", "in")))
        assertEquals("1760", sig(convert("1", c, "mi", "yd")))
        assertEquals("1.609344", sig(convert("1", c, "mi", "km")))
        assertEquals("1852", sig(convert("1", c, "nmi", "m")))
    }

    @Test
    fun `温度是仿射换算而非简单倍率`() {
        val t = UnitCategory.TEMPERATURE
        assertEquals("32", sig(convert("0", t, "°C", "°F")))
        assertEquals("212", sig(convert("100", t, "°C", "°F")))
        assertEquals("273.15", sig(convert("0", t, "°C", "K")))
        assertEquals("-40", sig(convert("-40", t, "°C", "°F")))
        assertEquals("-40", sig(convert("-40", t, "°F", "°C")))
        assertEquals("-273.15", sig(convert("0", t, "K", "°C")))
        assertEquals("-459.67", sig(convert("0", t, "K", "°F")))
        assertEquals("37.77777777777777777777778", sig(convert("100", t, "°F", "°C")))
    }

    @Test
    fun `质量`() {
        val c = UnitCategory.MASS
        assertEquals("1000", sig(convert("1", c, "kg", "g")))
        assertEquals("16", sig(convert("1", c, "lb", "oz")))
        assertEquals("500", sig(convert("1", c, "jin", "g")))
        assertEquals("2", sig(convert("1", c, "kg", "jin")))
        assertEquals("0.45359237", sig(convert("1", c, "lb", "kg")))
    }

    @Test
    fun `速度`() {
        val c = UnitCategory.SPEED
        assertEquals("3.6", sig(convert("1", c, "m/s", "km/h")))
        assertEquals("1.852", sig(convert("1", c, "kn", "km/h")))
        assertEquals("0.3048", sig(convert("1", c, "ft/s", "m/s")))
    }

    @Test
    fun `数据用二进制前缀`() {
        val c = UnitCategory.DATA
        assertEquals("1024", sig(convert("1", c, "KB", "B")))
        assertEquals("1024", sig(convert("1", c, "GB", "MB")))
        assertEquals("8", sig(convert("1", c, "B", "bit")))
    }

    @Test
    fun `角度与弧度互换`() {
        val c = UnitCategory.ANGLE
        assertEquals(sig(Dec.div(of("180"), Dec.PI), 25), sig(convert("1", c, "rad", "°")))
        assertEquals(sig(Dec.PI, 25), sig(convert("180", c, "°", "rad")))
        assertEquals("0.9", sig(convert("1", c, "grad", "°")))
        assertEquals("60", sig(convert("1", c, "°", "′")))
    }

    @Test
    fun `能量与压强`() {
        assertEquals("4.184", sig(convert("1", UnitCategory.ENERGY, "kcal", "kJ")))
        assertEquals("101325", sig(convert("1", UnitCategory.PRESSURE, "atm", "Pa")))
        assertEquals("100000", sig(convert("1", UnitCategory.PRESSURE, "bar", "Pa")))
    }

    @Test
    fun `任意两单位互转后能还原`() {
        val sample = of("123.456789")
        for ((category, list) in Units.catalog) {
            for (from in list) {
                for (to in list) {
                    val there = Units.convert(sample, from, to)
                    val back = Units.convert(there, to, from)
                    assertEquals(
                        "$category: ${from.label} → ${to.label} → 回来不等",
                        sig(sample, 30),
                        sig(back, 30),
                    )
                }
            }
        }
    }

    @Test
    fun `换算到基准单位再换算回来也成立`() {
        for ((category, list) in Units.catalog) {
            val base = Units.baseOf(category)
            for (u in list) {
                val v = of("-273.15")
                val back = Units.convert(Units.convert(v, u, base), base, u)
                assertEquals("$category/${u.label}", sig(v, 30), sig(back, 30))
            }
        }
    }

    @Test
    fun `同单位换算是恒等`() {
        for ((category, list) in Units.catalog) {
            for (u in list) {
                assertEquals(
                    "$category/${u.label}",
                    "7.25",
                    sig(Units.convert(of("7.25"), u, u), 30),
                )
            }
        }
    }

    @Test
    fun `零值在温度里不等于零度`() {
        val t = UnitCategory.TEMPERATURE
        assertTrue(Units.convert(BigDecimal.ZERO, unit(t, "°C"), unit(t, "°F")).compareTo(BigDecimal.ZERO) != 0)
        assertEquals(
            "0",
            sig(Units.convert(BigDecimal.ZERO, unit(UnitCategory.LENGTH, "m"), unit(UnitCategory.LENGTH, "ft"))),
        )
    }
}
