package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 函数分析的验证。
 *
 * 主力是三条**由定义直接给出**的判据，而不是我记住的具体数值：
 *
 *  1. 标为零点的点，代入必须为 0；
 *  2. 标为极值的点，数值导数必须为 0，且曲率符号必须与"极大/极小"一致；
 *  3. 标为截距的点，必须真的在 y 轴上且等于 f(0)。
 *
 * 这三条对任何函数都成立，因此它们能覆盖我没想到的用例；
 * 而精确形式（`√2`）另用字符串断言守住。
 */
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

    // ------------------------------------------------------------ 自洽性判据

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

            // 用**二阶差分**判曲率，而不是 f(x+h)−f(x−h)。
            // 后者在驻点处二阶项恰好抵消、由 h³·f''' 主导，符号会反过来——
            // 三次函数 x³-3x 在极大值处算出来就是正的，纯属测量方法的错。
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

    // ------------------------------------------------------------------ 多项式

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
        // x²-2 的极小值恰好在 (0,-2)，也就是 y 截距。
        // 两个都列会在同一个点上贴两个标签，让人以为有两个不同的点。
        val analysis = analyze("x^2-2")
        assertEquals("不应重复列出 y 截距", 0, of(analysis, PointKind.Y_INTERCEPT).size)
    }

    @Test
    fun `截距未被覆盖时会给出`() {
        // x²-3x+2 的极值在 x=1.5，y 截距在 (0,2)，两者不重合
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
        // x³-3x 在 x=±1 处取极值
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
        // ±√2 ≈ ±1.414 落在区间外
        assertEquals(0, of(analysis, PointKind.ZERO).size)
        assertEquals(1, of(analysis, PointKind.MINIMUM).size)
    }

    // ---------------------------------------------------------------- 非多项式

    @Test
    fun `三角函数的零点走数值路线`() {
        val analysis = analyze("sin(x)", from = "-4", to = "4", angleUnit = AngleUnit.RAD)
        val zeros = of(analysis, PointKind.ZERO).map { it.x }
        assertEquals(3, zeros.size)
        // −π, 0, π
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
        // π/2 ≈ 1.5708 为极大；极小只有 −π/2（3π/2 ≈ 4.712 已超出区间 [−4,4]）
        assertEquals(1, maxima.size)
        assertEquals("1.570796326794897", sig(maxima[0]))
        assertEquals(1, minima.size)
        assertEquals("-1.570796326794897", sig(minima[0]))
        assertExtremaAreStationary("sin(x)", analysis, AngleUnit.RAD)
    }

    @Test
    fun `无定义处不会崩且不会被当成零点`() {
        // 1÷x 在 x=0 无定义
        val analysis = analyze("1÷x", from = "-5", to = "5")
        assertTrue("应当存在无定义的采样点", analysis.samples.any { it.y == null })
        // 1/x 没有零点（左右分别趋于 ±∞）
        assertEquals(0, of(analysis, PointKind.ZERO).size)
        // x=0 处无定义，因此也没有 y 截距
        assertEquals(0, of(analysis, PointKind.Y_INTERCEPT).size)
    }

    // ------------------------------------------------------------------ 采样

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
        // x=10 处 1÷x²=0.01 正常；用 1÷x^8 在 x=0.01 处会到 1e16 量级，
        // 仍在限制内。这里用一个爆炸更快的例子确认限制确实生效。
        val analysis = analyze("1÷x^20", from = "-5", to = "5")
        assertTrue(
            "极近零点处的巨大取值应当被丢弃",
            analysis.samples.filter { it.y != null }.all { it.y!!.abs() <= BigDecimal("1E30") },
        )
    }
}
