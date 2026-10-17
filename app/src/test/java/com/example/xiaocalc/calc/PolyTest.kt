package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PolyTest {
    private fun sig(v: BigDecimal, n: Int = 20): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun assertMatchesAt(expr: String, poly: Poly, vararg points: String) {
        for (p in points) {
            val x = BigDecimal(p)
            val fromExpr = ExpressionEvaluator(expr, AngleUnit.DEG, mapOf("x" to x)).evaluate()
            val fromPoly = poly.evaluate(x)
            assertEquals(
                "$expr 在 x=$p 处不一致：表达式=$fromExpr 多项式=$fromPoly",
                0,
                sig(fromExpr, 25).compareTo(sig(fromPoly, 25)),
            )
        }
    }

    @Test
    fun `一次多项式`() {
        val p = PolynomialFit.fit("2x+1")!!
        assertEquals(1, p.degree)
        assertEquals("2", sig(p[1]))
        assertEquals("1", sig(p[0]))
        assertMatchesAt("2x+1", p, "0", "5", "-3", "0.25", "100")
    }

    @Test
    fun `二次多项式`() {
        val p = PolynomialFit.fit("x^2-3x+2")!!
        assertEquals(2, p.degree)
        assertEquals("1", sig(p[2]))
        assertEquals("-3", sig(p[1]))
        assertEquals("2", sig(p[0]))
        assertMatchesAt("x^2-3x+2", p, "0", "1", "2", "-4", "1.5")
    }

    @Test
    fun `缺项不会被补成错误次数`() {
        val p = PolynomialFit.fit("x^2-2")!!
        assertEquals(2, p.degree)
        assertEquals("0", sig(p[1]))
        assertEquals("-2", sig(p[0]))
        assertEquals("1", sig(p[2]))
        assertMatchesAt("x^2-2", p, "3", "-3", "0.5")
    }

    @Test
    fun `常数`() {
        val p = PolynomialFit.fit("42")!!
        assertEquals(0, p.degree)
        assertMatchesAt("42", p, "0", "7", "-9")
    }

    @Test
    fun `分数系数`() {
        val p = PolynomialFit.fit("x÷2+1")!!
        assertEquals(1, p.degree)
        assertEquals("0.5", sig(p[1]))
        assertEquals("1", sig(p[0]))
        assertMatchesAt("x÷2+1", p, "3", "-2", "0.75")
    }

    @Test
    fun `常数可用 π 与 e`() {
        val p = PolynomialFit.fit("πx+e")!!
        assertEquals(1, p.degree)
        assertEquals(sig(Dec.PI, 25), sig(p[1], 25))
        assertEquals(sig(Dec.E, 25), sig(p[0], 25))
        assertMatchesAt("πx+e", p, "2", "-1.5")
    }

    @Test
    fun `三次及以上`() {
        val p = PolynomialFit.fit("x^3-6x^2+11x-6")!!
        assertEquals(3, p.degree)
        assertEquals("-6", sig(p[0]))
        assertEquals("11", sig(p[1]))
        assertEquals("-6", sig(p[2]))
        assertEquals("1", sig(p[3]))
        assertMatchesAt("x^3-6x^2+11x-6", p, "0", "1", "2", "3", "-2", "4.5")
    }

    @Test
    fun `隐式乘法与括号`() {
        val p = PolynomialFit.fit("(x+1)(x-1)")!!
        assertEquals(2, p.degree)
        assertEquals("1", sig(p[2]))
        assertEquals("0", sig(p[1]))
        assertEquals("-1", sig(p[0]))
        assertMatchesAt("(x+1)(x-1)", p, "5", "-5")
    }

    @Test
    fun `幂运算的右结合与负号优先级`() {
        val p = PolynomialFit.fit("-x^2")!!
        assertEquals(2, p.degree)
        assertEquals("-1", sig(p[2]))
        assertMatchesAt("-x^2", p, "3", "-3")
    }

    @Test
    fun `非多项式应当被拒而不是硬凑`() {
        assertNull(PolynomialFit.fit("sin(x)"))
        assertNull(PolynomialFit.fit("1÷x"))
    }

    @Test
    fun `未声明的字母仍按未知函数报错`() {
        var threw = false
        try {
            ExpressionEvaluator("x+1").evaluate()
        } catch (e: CalcException) {
            threw = true
            assertEquals(CalcError.UNKNOWN_FUNCTION, e.error)
        }
        assertTrue("未声明的 x 应当报未知函数", threw)
    }

    @Test
    fun `规范形式去掉高位零`() {
        assertEquals(1, Poly(listOf(BigDecimal.ONE, BigDecimal("2"), BigDecimal.ZERO)).normalized().degree)
        assertEquals(0, Poly(listOf(BigDecimal.ZERO, BigDecimal.ZERO)).normalized().degree)
        assertTrue(Poly(listOf(BigDecimal.ZERO)).isZero())
    }
}
