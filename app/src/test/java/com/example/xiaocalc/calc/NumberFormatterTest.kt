package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 结果格式化回归：精度、千分位、科学计数法、符号化。 */
class NumberFormatterTest {

    private val defaults = CalcSettings(groupDigits = true)

    @Test
    fun `浮点尾巴被消除`() {
        // 旧实现用 "%.6f" 截断，会把 0.30000000000000004 变成 0.3 但同时也砍掉真实精度
        assertEquals("0.3", NumberFormatter.format(0.1 + 0.2, defaults))
        assertEquals("0.3333333333", NumberFormatter.format(1.0 / 3.0, defaults))
        assertEquals("2", NumberFormatter.format(2.0, defaults))
        assertEquals("0", NumberFormatter.format(0.0, defaults))
        assertEquals("-0.5", NumberFormatter.format(-0.5, defaults))
    }

    @Test
    fun `千分位只作用于纯十进制结果`() {
        assertEquals("1,234,565", NumberFormatter.format(1234565.0, defaults))
        assertEquals("999", NumberFormatter.format(999.0, defaults))
        assertEquals("12,345.6789", NumberFormatter.format(12345.6789, defaults))
        // 关闭开关后不加分隔符
        assertEquals("1234565", NumberFormatter.format(1234565.0, defaults.copy(groupDigits = false)))
    }

    @Test
    fun `极大极小值走科学计数法`() {
        assertEquals("1.23457×10¹²", NumberFormatter.format(1.2345678e12, defaults))
        assertEquals("1×10⁻¹²", NumberFormatter.format(1e-12, defaults))
        // 科学计数法开关：沿用旧版语义（绝对值 ≥ 10000 即切换）
        assertEquals("1.5×10⁴", NumberFormatter.format(15000.0, defaults.copy(scientific = true)))
        assertEquals("15,000", NumberFormatter.format(15000.0, defaults))
    }

    @Test
    fun `续算用的纯文本形式可被解析器再次接受`() {
        val text = NumberFormatter.plainForReuse(1.2345678e12)
        assertEquals("1.23457×10^12", text)
        val value = ExpressionEvaluator(text).evaluate()
        assertEquals(1.23457e12, value, 1e6)
    }

    @Test
    fun `上标与脱上标互为逆运算`() {
        assertEquals("2¹⁰", NumberFormatter.superscriptExponents("2^10"))
        assertEquals("2^10", NumberFormatter.desuperscript("2¹⁰"))
        assertEquals("1.5×10⁻⁹", NumberFormatter.superscriptExponents("1.5×10^-9"))
        assertEquals("1.5×10^-9", NumberFormatter.desuperscript("1.5×10⁻⁹"))
        // 非指数位置不受影响
        assertEquals("2×(3+4)", NumberFormatter.superscriptExponents("2×(3+4)"))
    }

    @Test
    fun `保留根号输出最简根式`() {
        val settings = defaults.copy(keepRoot = true)
        assertEquals("2√2", NumberFormatter.format(kotlin.math.sqrt(8.0), settings, "√8"))
        assertEquals("3", NumberFormatter.format(3.0, settings, "√9"))
        assertEquals("6√2", NumberFormatter.format(6 * kotlin.math.sqrt(2.0), settings, "2×√18"))
        // 源表达式不是根式形态时不启用符号化
        assertEquals("2.828427125", NumberFormatter.format(kotlin.math.sqrt(8.0), settings, "2.828427125"))
    }

    @Test
    fun `保留特殊符号输出常数的简洁倍数`() {
        val settings = defaults.copy(keepSymbols = true)
        assertEquals("π", NumberFormatter.format(Math.PI, settings, "π"))
        assertEquals("2π", NumberFormatter.format(2 * Math.PI, settings, "2×π"))
        assertEquals("π/2", NumberFormatter.format(Math.PI / 2, settings, "π÷2"))
        // 不是简洁倍数 → 回落到小数
        assertEquals("4.141592654", NumberFormatter.format(Math.PI + 1, settings, "π+1"))
    }

    @Test
    fun `根号化简`() {
        assertEquals(2L to 2L, NumberFormatter.simplifyRadical(8))
        // 完全平方数：全部提到根号外
        assertEquals(3L to 1L, NumberFormatter.simplifyRadical(9))
        assertEquals(6L to 2L, NumberFormatter.simplifyRadical(72))
        assertEquals(1L to 1L, NumberFormatter.simplifyRadical(1))
        assertEquals(1L to 7L, NumberFormatter.simplifyRadical(7))
    }

    @Test
    fun `非有限值不会崩`() {
        assertEquals("NaN", NumberFormatter.format(Double.NaN, defaults))
        assertTrue(NumberFormatter.plainForReuse(Double.POSITIVE_INFINITY).isEmpty())
    }
}
