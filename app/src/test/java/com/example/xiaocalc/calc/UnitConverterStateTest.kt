package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterStateTest {
    private val settings = CalcSettings(groupDigits = false)

    private fun state() = UnitConverterState()

    private fun UnitConverterState.type(text: String) {
        for (c in text) {
            when (c) {
                in '0'..'9' -> onKey(CalcKey.Digit(c))
                '.' -> onKey(CalcKey.Dot)
                '+' -> onKey(CalcKey.Plus)
                '-' -> onKey(CalcKey.Minus)
                '×' -> onKey(CalcKey.Times)
                '÷' -> onKey(CalcKey.Divide)
                '^' -> onKey(CalcKey.Power)
                '(' -> onKey(CalcKey.LParen)
                else -> Unit
            }
        }
    }

    @Test
    fun `默认是最常用的长度换算`() {
        val s = state()
        assertEquals(UnitCategory.LENGTH, s.category)
        assertEquals("m", s.from.symbol)
        assertEquals("ft", s.to.symbol)
    }

    @Test
    fun `每个类别的默认两项都不相同`() {
        for (category in UnitCategory.entries) {
            val s = state()
            s.selectCategory(category)
            assertTrue(
                "$category 的默认起算与目标单位相同：${s.from.symbol}",
                s.from != s.to,
            )
        }
    }

    @Test
    fun `前导零被替换而不是累积`() {
        val s = state()
        s.type("0")
        assertEquals("0", s.input)
        s.type("5")
        assertEquals("5", s.input)
        s.type("0")
        assertEquals("50", s.input)
    }

    @Test
    fun `连续的零在小数点前不累积`() {
        val s = state()
        s.type("000")
        assertEquals("0", s.input)
    }

    @Test
    fun `小数点不会重复`() {
        val s = state()
        s.type("1.5")
        assertEquals("1.5", s.input)
        s.type(".")
        assertEquals("1.5", s.input)
        s.type("+2.")
        assertEquals("1.5+2.", s.input)
    }

    @Test
    fun `以小数点开头可以解析`() {
        val s = state()
        s.type(".5")
        assertEquals(".5", s.input)
        assertEquals("1.640419947506562", s.convertedText(settings))
    }

    @Test
    fun `负号可以作为第一个字符`() {
        val s = state()
        s.type("-40")
        assertEquals("-40", s.input)
        s.selectCategory(UnitCategory.TEMPERATURE)
        assertEquals("-40", s.convertedText(settings))
    }

    @Test
    fun `不能以二元运算符开头`() {
        val s = state()
        s.type("×")
        assertEquals("", s.input)
        s.type("-")
        assertEquals("-", s.input)
    }

    @Test
    fun `连按两个运算符是改主意而不是叠加`() {
        val s = state()
        s.type("12+")
        assertEquals("12+", s.input)
        s.type("×")
        assertEquals("12×", s.input)
        s.type("3")
        assertEquals("12×3", s.input)
    }

    @Test
    fun `退格一次删掉整个函数名`() {
        val s = state()
        s.onKey(CalcKey.Fn("sin"))
        assertEquals("sin(", s.input)
        s.backspace()
        assertEquals("", s.input)
    }

    @Test
    fun `退格在空输入上是安全的`() {
        val s = state()
        s.backspace()
        assertEquals("", s.input)
    }

    @Test
    fun `清空会回到空输入`() {
        val s = state()
        s.type("123")
        s.onKey(CalcKey.Clear)
        assertEquals("", s.input)
    }

    @Test
    fun `交换起算与目标单位`() {
        val s = state()
        val from = s.from
        val to = s.to
        s.swap()
        assertEquals(to, s.from)
        assertEquals(from, s.to)
    }

    @Test
    fun `切换类别会把单位重置为该类前两项`() {
        val s = state()
        s.selectTo(5)
        s.selectCategory(UnitCategory.MASS)
        assertEquals(UnitCategory.MASS, s.category)
        assertEquals("kg", s.from.symbol)
        assertEquals("lb", s.to.symbol)
    }

    @Test
    fun `切换类别保留已输入的数值`() {
        val s = state()
        s.type("100")
        s.selectCategory(UnitCategory.TEMPERATURE)
        assertEquals("100", s.input)
        assertEquals("212", s.convertedText(settings))
    }

    @Test
    fun `越界的单位索引被忽略`() {
        val s = state()
        val before = s.from
        s.selectFrom(999)
        assertEquals(before, s.from)
        s.selectFrom(-1)
        assertEquals(before, s.from)
    }

    @Test
    fun `重复选择同一类别不做改动`() {
        val s = state()
        s.selectTo(4)
        s.selectCategory(UnitCategory.LENGTH)
        assertEquals(4, s.toIndex)
    }

    @Test
    fun `换算结果按当前单位对给出`() {
        val s = state()
        s.type("1")
        assertEquals("3.280839895013123", s.convertedText(settings))
        s.selectTo(2)
        assertEquals("100", s.convertedText(settings))
    }

    @Test
    fun `值是表达式时也能换算`() {
        val s = state()
        s.type("100+20")
        s.selectTo(2)
        assertEquals("12000", s.convertedText(settings))
    }

    @Test
    fun `空输入没有换算结果`() {
        val s = state()
        assertEquals(InputValue.Empty, s.evaluate(settings))
        assertEquals(null, s.convertedText(settings))
    }

    @Test
    fun `非法输入返回错误而不是脏值`() {
        val s = state()
        s.type("1+")
        val value = s.evaluate(settings)
        assertTrue("应当是错误态，实际 $value", value is InputValue.Bad)
        assertEquals(null, s.convertedText(settings))
    }

    @Test
    fun `除以零被识别为错误`() {
        val s = state()
        s.type("1÷0")
        assertEquals(InputValue.Bad(CalcError.DIVIDE_BY_ZERO), s.evaluate(settings))
    }
}
