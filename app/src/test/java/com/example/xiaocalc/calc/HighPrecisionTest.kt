package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighPrecisionTest {
    private val plain = CalcSettings()

    private fun eval(expr: String, settings: CalcSettings = plain): String {
        val outcome = CalcEngine.evaluate(expr, settings)
        assertTrue("$expr 求值失败：$outcome", outcome is EvalOutcome.Success)
        return (outcome as EvalOutcome.Success).display
    }

    private fun rad() = plain.copy(angleUnit = AngleUnit.RAD)

    @Test
    fun `无限小数续乘能回到整数`() {
        assertEquals("1", eval("1÷3×3"))
        assertEquals("2", eval("2÷3×3"))
        assertEquals("1", eval("1÷7×7"))
        assertEquals("0.2", eval("0.1×3÷3×2"))
    }

    @Test
    fun `零点一加零点二等于零点三`() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.3", eval("0.3"))
        assertEquals("1.1", eval("1.1"))
    }

    @Test
    fun `相消不会吃掉有效数字`() {
        assertEquals("0.000000004999999995833333", eval("1-cos(0.0001)", rad()))
        assertEquals("0.000000005", eval("√(10000000000000001)-100000000"))
    }

    @Test
    fun `显示确为十六位有效数字`() {
        assertEquals("0.6666666666666667", eval("2÷3"))
        assertEquals("3.141592653589793", eval("π"))
        assertEquals("1.414213562373095", eval("√2"))
        assertEquals("2.718281828459045", eval("e"))
        val digits = eval("2÷3").removePrefix("0.").length
        assertEquals(Dec.DISPLAY_DIGITS, digits)
    }

    @Test
    fun `数值范围不再受 Double 限制`() {
        assertEquals("1×10⁶⁰⁰", eval("(1×10^300)^2"))
        assertEquals("1×10⁻⁶⁰⁰", eval("1÷(1×10^300)^2"))
    }

    @Test
    fun `病态量级被拦下而不是耗尽内存`() {
        val outcome = CalcEngine.evaluate("9^9^9", plain)
        assertTrue("应当被拦下，实际 $outcome", outcome is EvalOutcome.Failure)
        assertEquals(CalcError.NOT_FINITE, (outcome as EvalOutcome.Failure).error)
    }

    @Test
    fun `角度制与弧度制只影响三角函数`() {
        assertEquals("0.5", eval("sin(30)", plain))
        assertEquals("1", eval("ln(e)", plain))
        assertEquals("0.5", eval("sin(π÷6)", rad()))
    }
}
