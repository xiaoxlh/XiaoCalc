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
        assertEquals("0.3333333333333333", NumberFormatter.format(1.0 / 3.0, defaults))
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
        assertEquals("1.2345678×10¹²", NumberFormatter.format(1.2345678e12, defaults))
        assertEquals("1×10⁻¹²", NumberFormatter.format(1e-12, defaults))
        // 科学计数法开关：沿用旧版语义（绝对值 ≥ 10000 即切换）
        assertEquals("1.5×10⁴", NumberFormatter.format(15000.0, defaults.copy(scientific = true)))
        assertEquals("15,000", NumberFormatter.format(15000.0, defaults))
    }

    @Test
    fun `续算用的纯文本形式可被解析器再次接受`() {
        val text = NumberFormatter.plainForReuse(1.2345678e12)
        assertEquals("1.2345678×10^12", text)
        val value = ExpressionEvaluator(text).evaluate()
        assertEquals(1.2345678e12, value.toDouble(), 1e6)
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
        // 源表达式不是根式形态时不启用符号化。
        // 末尾的 0 会被 stripTrailingZeros 去掉，因此是 15 位而非 16 位——
        // 显示的位数少一位好过显示一个无意义的尾零。
        assertEquals("2.82842712474619", NumberFormatter.format(kotlin.math.sqrt(8.0), settings, "2.828427125"))
    }

    @Test
    fun `保留特殊符号输出常数的简洁倍数`() {
        val settings = defaults.copy(keepSymbols = true)
        assertEquals("π", NumberFormatter.format(Math.PI, settings, "π"))
        assertEquals("2π", NumberFormatter.format(2 * Math.PI, settings, "2×π"))
        assertEquals("π/2", NumberFormatter.format(Math.PI / 2, settings, "π÷2"))
        // 含常数项的和式同样保留符号
        assertEquals("π+1", NumberFormatter.format(Math.PI + 1, settings, "π+1"))
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

    // -------------------------------------------------- 根号与常数同时出现

    @Test
    fun `根号与π同时出现时都保留`() {
        val both = defaults.copy(keepRoot = true, keepSymbols = true)
        val v = Math.PI * kotlin.math.sqrt(2.0)
        assertEquals("π√2", NumberFormatter.format(v, both, "π√2"))
        assertEquals("2π√2", NumberFormatter.format(2 * v, both, "2×π×√2"))
        assertEquals("2π√2", NumberFormatter.format(2 * v, both, "2π√2"))
    }

    @Test
    fun `只开一个开关而符号不足时回落小数`() {
        val v = Math.PI * kotlin.math.sqrt(2.0)
        // 需要 π，但"保留特殊符号"没开
        assertEquals(
            "4.442882938158366",
            NumberFormatter.format(v, defaults.copy(keepRoot = true), "π√2"),
        )
        // 需要根号，但"保留根号"没开
        assertEquals(
            "4.442882938158366",
            NumberFormatter.format(v, defaults.copy(keepSymbols = true), "π√2"),
        )
    }

    @Test
    fun `多个根号相乘会合并成一个`() {
        val root = defaults.copy(keepRoot = true)
        assertEquals("√6", NumberFormatter.format(kotlin.math.sqrt(6.0), root, "√2×√3"))
        // √2×√2 = 2，化简后没有根号可留，走常规格式化
        assertEquals("2", NumberFormatter.format(2.0, root, "√2×√2"))
    }

    @Test
    fun `根号内除以常数同样化到最简`() {
        val root = defaults.copy(keepRoot = true)
        // √8÷2 = √2
        assertEquals("√2", NumberFormatter.format(kotlin.math.sqrt(2.0), root, "√8÷2"))
    }

    @Test
    fun `带括号的根号内数字可识别`() {
        val root = defaults.copy(keepRoot = true)
        assertEquals("2√2", NumberFormatter.format(kotlin.math.sqrt(8.0), root, "√(8)"))
    }

    // -------------------------------------------------- 作业场景：和式与同类项合并

    @Test
    fun `和为无理数时保留和式`() {
        val both = defaults.copy(keepRoot = true, keepSymbols = true)
        val v = 1 + kotlin.math.sqrt(2.0)
        assertEquals("1+√2", NumberFormatter.format(v, both, "1+√2"))
        assertEquals(
            "√2+√3",
            NumberFormatter.format(
                kotlin.math.sqrt(2.0) + kotlin.math.sqrt(3.0),
                both,
                "√2+√3",
            ),
        )
    }

    @Test
    fun `同类根式会合并系数`() {
        val root = defaults.copy(keepRoot = true)
        val v2 = kotlin.math.sqrt(2.0)
        assertEquals("5√2", NumberFormatter.format(5 * v2, root, "2√2+3√2"))
        assertEquals("2√2", NumberFormatter.format(2 * v2, root, "√2+√2"))
        // 相减
        assertEquals("√2", NumberFormatter.format(v2, root, "3√2-2√2"))
    }

    @Test
    fun `先化简再合并`() {
        val root = defaults.copy(keepRoot = true)
        // √18 - √8 = 3√2 - 2√2 = √2
        assertEquals("√2", NumberFormatter.format(kotlin.math.sqrt(2.0), root, "√18-√8"))
    }

    @Test
    fun `同类项相消后回落常规格式化`() {
        val root = defaults.copy(keepRoot = true)
        // √2-√2 = 0，没有符号可留
        assertEquals("0", NumberFormatter.format(0.0, root, "√2-√2"))
    }

    @Test
    fun `括号内加减不参与符号化`() {
        val both = defaults.copy(keepRoot = true, keepSymbols = true)
        // (1+√2) 含括号，超出表达边界 → 回落小数
        assertEquals(
            "0.6035533905932737",
            NumberFormatter.format(
                (1 + kotlin.math.sqrt(2.0)) / 4,
                both,
                "(1+√2)÷4",
            ),
        )
    }

    @Test
    fun `非有限值不会崩`() {
        assertEquals("NaN", NumberFormatter.format(Double.NaN, defaults))
        assertTrue(NumberFormatter.plainForReuse(Double.POSITIVE_INFINITY).isEmpty())
    }

    // ------------------------------------------------------------ 小数位数设置

    @Test
    fun `默认全精度与 16 位有效数字逐值一致`() {
        // 默认值必须等于"没有这个设置"，否则老用户升级后看到的结果会变
        for (v in listOf(1.0 / 3.0, 2.0 / 7.0, 12345.6789, -0.5, 1e-3)) {
            assertEquals(
                "v=$v",
                NumberFormatter.format(v, defaults.copy(decimals = CalcSettings.FULL)),
                NumberFormatter.format(v, defaults),
            )
        }
    }

    @Test
    fun `限制小数位数后按位舍入`() {
        val two = defaults.copy(decimals = 2, groupDigits = false)
        assertEquals("0.33", NumberFormatter.format(java.math.BigDecimal("0.3333333333333333"), two))
        assertEquals("3", NumberFormatter.format(java.math.BigDecimal("2.7"), defaults.copy(decimals = 0)))
        assertEquals("0.1235", NumberFormatter.format(java.math.BigDecimal("0.12345"), defaults.copy(decimals = 4)))
        assertEquals("1,234.6", NumberFormatter.format(java.math.BigDecimal("1234.567"), defaults.copy(decimals = 1)))
        // 末尾的 0 要去掉：保留 3 位小数时 0.5 仍然写 0.5，不是 0.500
        assertEquals("0.5", NumberFormatter.format(java.math.BigDecimal("0.5"), defaults.copy(decimals = 3)))
    }

    @Test
    fun `小数位为 0 时非零值不会被抹成 0`() {
        // 把 1/3 显示成 "0" 等于抹掉全部有效信息，宁可退回科学计数法
        val zero = defaults.copy(decimals = 0)
        val third = NumberFormatter.format(java.math.BigDecimal("0.333333333333"), zero)
        assertTrue("0 位小数把非零值抹成了 $third", third != "0")
        assertEquals("3", NumberFormatter.format(java.math.BigDecimal("3.4"), zero))
    }

    @Test
    fun `小数位数越界时被夹到合法区间`() {
        val big = java.math.BigDecimal("0.12345678901234567")
        assertEquals(
            NumberFormatter.format(big, defaults.copy(decimals = CalcSettings.FULL)),
            NumberFormatter.format(big, defaults.copy(decimals = 99)),
        )
        assertEquals(
            NumberFormatter.format(big, defaults.copy(decimals = 0)),
            NumberFormatter.format(big, defaults.copy(decimals = -5)),
        )
    }}
