package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 单位换算表的验证。
 *
 * 分三层：
 *  1. **结构不变量**——每类都有且只有一个基准单位、因子为正、符号不重复。
 *     这类断言能在有人往表里加单位时立刻拦住笔误。
 *  2. **已知换算值**——挑有精确定义或公认数值的换关系逐条比对。
 *  3. **往返一致性**——任意两个单位互转再转回来必须还原。
 *     这条覆盖面最广：它不依赖我是否记得某个具体数值，只依赖换算逻辑自洽。
 */
class UnitsTest {

    private fun of(s: String) = BigDecimal(s)

    private fun sig(v: BigDecimal, n: Int = 25): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun unit(category: UnitCategory, symbol: String): UnitDef =
        Units.catalog.getValue(category).first { it.symbol == symbol }

    private fun convert(value: String, category: UnitCategory, from: String, to: String): BigDecimal =
        Units.convert(of(value), unit(category, from), unit(category, to))

    // ------------------------------------------------------- 1. 结构不变量

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
    fun `目录覆盖全部类别`() {
        for (category in UnitCategory.entries) {
            assertTrue("$category 未在目录中", Units.catalog.containsKey(category))
        }
        assertEquals(UnitCategory.entries.size, Units.catalog.size)
    }

    // -------------------------------------------------------- 2. 已知换算值

    @Test
    fun `长度`() {
        val c = UnitCategory.LENGTH
        assertEquals("100", sig(convert("1", c, "m", "cm")))
        assertEquals("1000", sig(convert("1", c, "m", "mm")))
        assertEquals("1000", sig(convert("1", c, "km", "m")))
        // 1 in = 2.54 cm，1 ft = 12 in，1 mi = 1760 yd，全部是精确定义
        assertEquals("2.54", sig(convert("1", c, "in", "cm")))
        assertEquals("12", sig(convert("1", c, "ft", "in")))
        assertEquals("1760", sig(convert("1", c, "mi", "yd")))
        assertEquals("1.609344", sig(convert("1", c, "mi", "km")))
        assertEquals("1852", sig(convert("1", c, "nmi", "m")))
    }

    @Test
    fun `温度是仿射换算而非简单倍率`() {
        val t = UnitCategory.TEMPERATURE
        // 水的冰点与沸点
        assertEquals("32", sig(convert("0", t, "°C", "°F")))
        assertEquals("212", sig(convert("100", t, "°C", "°F")))
        assertEquals("273.15", sig(convert("0", t, "°C", "K")))
        // −40 是两个刻度的交点，也是检验偏移是否写反的最佳用例
        assertEquals("-40", sig(convert("-40", t, "°C", "°F")))
        assertEquals("-40", sig(convert("-40", t, "°F", "°C")))
        // 绝对零度
        assertEquals("-273.15", sig(convert("0", t, "K", "°C")))
        assertEquals("-459.67", sig(convert("0", t, "K", "°F")))
        // 100°F = (100−32)×5/9 = 37.777…°C
        assertEquals("37.77777777777777777777778", sig(convert("100", t, "°F", "°C")))
    }

    @Test
    fun `质量`() {
        val c = UnitCategory.MASS
        assertEquals("1000", sig(convert("1", c, "kg", "g")))
        assertEquals("16", sig(convert("1", c, "lb", "oz")))
        assertEquals("500", sig(convert("1", c, "斤", "g")))
        // 1 斤 = 500 g，故 1 kg = 2 斤
        assertEquals("2", sig(convert("1", c, "kg", "斤")))
        // 1 lb 的精确定义值 0.45359237 kg
        assertEquals("0.45359237", sig(convert("1", c, "lb", "kg")))
    }

    @Test
    fun `速度`() {
        val c = UnitCategory.SPEED
        assertEquals("3.6", sig(convert("1", c, "m/s", "km/h")))
        // 1 节 = 1 海里/小时 = 1.852 km/h，精确
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
        // 1 rad = 180/π°，与独立算出的商比对——顺带验证表里的弧度因子确实是 180/π
        assertEquals(sig(Dec.div(of("180"), Dec.PI), 25), sig(convert("1", c, "rad", "°")))
        // 180° = π rad，直接与 Dec.PI 比对（π 本身是现算的，这是条独立验证）
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

    // ------------------------------------------------------- 3. 往返一致性

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
        // 0°C 换算成 °F 必须得到 32，而不是 0——这是仿射换算最容易出错的地方
        val t = UnitCategory.TEMPERATURE
        assertTrue(Units.convert(BigDecimal.ZERO, unit(t, "°C"), unit(t, "°F")).compareTo(BigDecimal.ZERO) != 0)
        // 而非温度类别里 0 换算后仍是 0
        assertEquals(
            "0",
            sig(Units.convert(BigDecimal.ZERO, unit(UnitCategory.LENGTH, "m"), unit(UnitCategory.LENGTH, "ft"))),
        )
    }
}
