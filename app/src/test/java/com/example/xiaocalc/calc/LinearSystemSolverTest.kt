package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 二元一次方程组的验证。
 *
 * 主力判据是**把解代回两个方程**，两边都必须成立。
 * 这条判据直接检验"解"的定义，也自然覆盖了克拉默法则的符号错误——
 * 符号搞反时代回去立刻不成立。
 */
class LinearSystemSolverTest {

    private fun unique(input: String): SystemSolution.Unique {
        val result = LinearSystemSolver.solve(input)
        assertTrue("$input 求解失败：$result", result is SystemSolution.Unique)
        return result as SystemSolution.Unique
    }

    private fun failed(input: String): EquationError {
        val result = LinearSystemSolver.solve(input)
        assertTrue("$input 本应失败，实际 $result", result is SystemSolution.Failed)
        return (result as SystemSolution.Failed).error
    }

    /** 把 (x, y) 代回方程，检查两边之差是否接近 0 */
    private fun assertSatisfies(equation: String, x: BigDecimal, y: BigDecimal) {
        val sides = equation.split("=")
        val residual = try {
            val lhs = evaluate(sides[0], x, y)
            val rhs = evaluate(sides[1], x, y)
            lhs.subtract(rhs, Dec.MC).abs()
        } catch (e: CalcException) {
            throw AssertionError("$equation 无法求值：${e.error}")
        }
        assertTrue("$equation 在 x=$x y=$y 处残差为 $residual", residual < BigDecimal("1E-30"))
    }

    private fun evaluate(expression: String, x: BigDecimal, y: BigDecimal): BigDecimal =
        ExpressionEvaluator(
            expression,
            AngleUnit.DEG,
            mapOf("x" to x, "y" to y),
        ).evaluate()

    private fun assertSolutionSatisfies(input: String, solution: SystemSolution.Unique) {
        for (equation in input.split(';', '\n', '；')) {
            val trimmed = equation.trim()
            if (trimmed.isNotEmpty()) assertSatisfies(trimmed, solution.x, solution.y)
        }
    }

    // ------------------------------------------------------------------ 正常

    @Test
    fun `标准二元一次方程组`() {
        // x+y=3, 2x-y=0 → x=1, y=2
        val input = "x+y=3;2x-y=0"
        val solution = unique(input)
        assertEquals("1", solution.xExact)
        assertEquals("2", solution.yExact)
        assertSolutionSatisfies(input, solution)
    }

    @Test
    fun `系数含负数与分数解`() {
        // 2x+3y=6, x-y=1 → x=9/5? 用整数解的例子更直观：3x+2y=12, x-y=1 → x=14/5
        val input = "3x+2y=12;x-y=1"
        val solution = unique(input)
        assertSolutionSatisfies(input, solution)
        // x = (12·1 - 2·1)/(3·1+2·1)… 直接核对数值
        assertEquals("2.8", solution.xDisplay)
        assertEquals("1.8", solution.yDisplay)
        assertEquals("14/5", solution.xExact)
        assertEquals("9/5", solution.yExact)
    }

    @Test
    fun `未按标准形式书写也能识别`() {
        // 把未知数挪到右边的写法
        val input = "3=y+x;0=2x-y"
        val solution = unique(input)
        assertSolutionSatisfies(input, solution)
        assertEquals("1", solution.xExact)
        assertEquals("2", solution.yExact)
    }

    @Test
    fun `隐式乘法与括号`() {
        val input = "2(x+y)=6;x-y=1"
        val solution = unique(input)
        assertEquals("2", solution.xExact)
        assertEquals("1", solution.yExact)
        assertSolutionSatisfies(input, solution)
    }

    @Test
    fun `用换行分隔也可以`() {
        val solution = LinearSystemSolver.solve("x+y=3\n2x-y=0")
        assertTrue(solution is SystemSolution.Unique)
        assertEquals("1", (solution as SystemSolution.Unique).xExact)
    }

    // ------------------------------------------------------------------ 退化

    @Test
    fun `平行无解`() {
        // x+y=1 与 x+y=2 是同斜率不同截距
        assertEquals(SystemSolution.NoSolution, LinearSystemSolver.solve("x+y=1;x+y=2"))
        assertEquals(SystemSolution.NoSolution, LinearSystemSolver.solve("x+y=1;2x+2y=3"))
    }

    @Test
    fun `同一条直线有无穷多解`() {
        assertEquals(SystemSolution.Infinite, LinearSystemSolver.solve("x+y=1;2x+2y=2"))
        assertEquals(SystemSolution.Infinite, LinearSystemSolver.solve("x-y=0;3x-3y=0"))
    }

    // ------------------------------------------------------------------ 失败

    @Test
    fun `方程个数不对时报错`() {
        assertEquals(EquationError.EQUATION_COUNT, failed("x+y=3"))
        assertEquals(EquationError.EQUATION_COUNT, failed("x+y=3;x-y=0;x=1"))
        assertEquals(EquationError.EMPTY, failed("   "))
    }

    @Test
    fun `非线性方程被拒`() {
        // xy 是二次项，不能被误当作线性式接受
        assertEquals(EquationError.NOT_POLYNOMIAL, failed("xy=1;x+y=3"))
        assertEquals(EquationError.NOT_POLYNOMIAL, failed("x^2+y=1;x+y=3"))
    }

    @Test
    fun `缺少等号时报错`() {
        assertEquals(EquationError.NOT_POLYNOMIAL, failed("x+y-3;x-y"))
    }

    @Test
    fun `一批随机系数的方程组解都满足原式`() {
        val cases = listOf(
            "2x+3y=8;x-2y=-3",
            "5x-y=7;2x+3y=1",
            "x+4y=9;3x-2y=-1",
            "-2x+5y=3;4x+y=11",
            "0.5x+y=2;x-0.25y=0",
        )
        for (input in cases) {
            assertSolutionSatisfies(input, unique(input))
        }
    }
}
