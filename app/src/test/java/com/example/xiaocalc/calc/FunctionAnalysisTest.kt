package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class FunctionAnalysisTest {
    private fun sig(v: BigDecimal, n: Int = 16): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun analyze(
        expression: String,
        from: String = "-10",
        to: String = "10",
        angleUnit: AngleUnit = AngleUnit.DEG,
    ): Analysis = FunctionAnalysis.analyze(
        expression,
        BigDecimal(from),
        BigDecimal(to),
        angleUnit,
    )

    private fun evaluate(expression: String, x: BigDecimal, angleUnit: AngleUnit = AngleUnit.DEG): BigDecimal =
        ExpressionEvaluator(expression, angleUnit, mapOf("x" to x)).evaluate()

    private fun of(analysis: Analysis, kind: PointKind): List<NotablePoint> =
        analysis.points.filter { it.kind == kind }

    private fun assertZerosAreReal(expression: String, analysis: Analysis, angleUnit: AngleUnit = AngleUnit.DEG) {
        for (point in of(analysis, PointKind.ZERO)) {
            val value = evaluate(expression, point.x, angleUnit).abs()
            val scale = BigDecimal.ONE.max(point.x.abs())
            assertTrue(
                "$expression：标为零点的 x=${point.x} 代入得 $value，不为 0",
                value < scale.multiply(BigDecimal("1E-20")),
            )
        }
    }

    private fun assertExtremaAreStationary(
        expression: String,
        analysis: Analysis,
        angleUnit: AngleUnit = AngleUnit.DEG,
    ) {
        val h = BigDecimal("1E-10")
        for (point in analysis.points) {
            if (point.kind != PointKind.MINIMUM && point.kind != PointKind.MAXIMUM) continue
            val left = evaluate(expression, point.x.subtract(h, Dec.MC), angleUnit)
            val right = evaluate(expression, point.x.add(h, Dec.MC), angleUnit)
            val curvature = left.add(right, Dec.MC).subtract(point.y.multiply(BigDecimal("2"), Dec.MC), Dec.MC)
            if (point.kind == PointKind.MINIMUM) {
                assertTrue("$expression：x=${point.x} 标为极小值，但二阶差分为正（上凸）", curvature.signum() >= 0)
                assertTrue("极小值应当不高于邻近点", left >= point.y && right >= point.y)
            } else {
                assertTrue("$expression：x=${point.x} 标为极大值，但二阶差分为负（下凸）", curvature.signum() <= 0)
                assertTrue("极大值应当不低于邻近点", left <= point.y && right <= point.y)
            }
        }
    }

    @Test
    fun `二次函数的零点与极值都是精确的`() {
        val analysis = analyze("x^2-2")
        assertEquals(listOf("−√2", "√2"), of(analysis, PointKind.ZERO).mapNotNull { it.exactX })
        assertZerosAreReal("x^2-2", analysis)
        val minima = of(analysis, PointKind.MINIMUM)
        assertEquals(1, minima.size)
        assertEquals("0", sig(minima[0].x))
        assertEquals("-2", sig(minima[0].y))
        assertExtremaAreStationary("x^2-2", analysis)
    }

    @Test
    fun `截距与极值重合时只保留一个`() {
        val analysis = analyze("x^2-2")
        assertEquals("不应重复列出 y 截距", 0, of(analysis, PointKind.Y_INTERCEPT).size)
    }

    @Test
    fun `截距未被覆盖时会给出`() {
        val analysis = analyze("x^2-3x+2")
        val intercept = of(analysis, PointKind.Y_INTERCEPT)
        assertEquals(1, intercept.size)
        assertEquals("0", sig(intercept[0].x))
        assertEquals("2", sig(intercept[0].y))
    }

    @Test
    fun `二次函数的整数根`() {
        val analysis = analyze("x^2-3x+2")
        assertEquals(setOf("1", "2"), of(analysis, PointKind.ZERO).mapNotNull { it.exactX }.toSet())
        assertZerosAreReal("x^2-3x+2", analysis)
        val minima = of(analysis, PointKind.MINIMUM)
        assertEquals(1, minima.size)
        assertEquals("1.5", sig(minima[0].x))
        assertEquals("-0.25", sig(minima[0].y))
        assertExtremaAreStationary("x^2-3x+2", analysis)
    }

    @Test
    fun `三次函数有极大值与极小值各一个`() {
        val analysis = analyze("x^3-3x")
        assertEquals(1, of(analysis, PointKind.MAXIMUM).size)
        assertEquals(1, of(analysis, PointKind.MINIMUM).size)
        assertEquals("-1", sig(of(analysis, PointKind.MAXIMUM)[0].x))
        assertEquals("1", sig(of(analysis, PointKind.MINIMUM)[0].x))
        assertExtremaAreStationary("x^3-3x", analysis)
    }

    @Test
    fun `区间外的点不会被报告`() {
        val analysis = analyze("x^2-2", from = "-1", to = "1")
        assertEquals(0, of(analysis, PointKind.ZERO).size)
        assertEquals(1, of(analysis, PointKind.MINIMUM).size)
    }

    @Test
    fun `三角函数的零点走数值路线`() {
        val analysis = analyze("sin(x)", from = "-4", to = "4", angleUnit = AngleUnit.RAD)
        val zeros = of(analysis, PointKind.ZERO).map { it.x }
        assertEquals(3, zeros.size)
        assertEquals("-3.141592653589793", sig(zeros[0]))
        assertEquals("0", sig(zeros[1]))
        assertEquals("3.141592653589793", sig(zeros[2]))
        assertZerosAreReal("sin(x)", analysis, AngleUnit.RAD)
    }

    @Test
    fun `三角函数的极值`() {
        val analysis = analyze("sin(x)", from = "-4", to = "4", angleUnit = AngleUnit.RAD)
        val maxima = of(analysis, PointKind.MAXIMUM).map { it.x }
        val minima = of(analysis, PointKind.MINIMUM).map { it.x }
        assertEquals(1, maxima.size)
        assertEquals("1.570796326794897", sig(maxima[0]))
        assertEquals(1, minima.size)
        assertEquals("-1.570796326794897", sig(minima[0]))
        assertExtremaAreStationary("sin(x)", analysis, AngleUnit.RAD)
    }

    @Test
    fun `无定义处不会崩且不会被当成零点`() {
        val analysis = analyze("1÷x", from = "-5", to = "5")
        assertTrue("应当存在无定义的采样点", analysis.samples.any { it.y == null })
        assertEquals(0, of(analysis, PointKind.ZERO).size)
        assertEquals(0, of(analysis, PointKind.Y_INTERCEPT).size)
    }

    @Test
    fun `采样覆盖闭区间且首尾精确`() {
        val analysis = analyze("x^2", from = "-2", to = "3")
        assertEquals(401, analysis.samples.size)
        assertEquals("-2", sig(analysis.samples.first().x))
        assertEquals("3", sig(analysis.samples.last().x))
        assertEquals("4", sig(analysis.samples.first().y!!))
        assertEquals("9", sig(analysis.samples.last().y!!))
    }

    @Test
    fun `点按 x 升序排列`() {
        val analysis = analyze("x^3-3x")
        val xs = analysis.points.map { it.x }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun `超出一亿倍量级的取值被丢弃`() {
        val analysis = analyze("1÷x^20", from = "-5", to = "5")
        assertTrue(
            "极近零点处的巨大取值应当被丢弃",
            analysis.samples.filter { it.y != null }.all { it.y!!.abs() <= BigDecimal("1E30") },
        )
    }
}
