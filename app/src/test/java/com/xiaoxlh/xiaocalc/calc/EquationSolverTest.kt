package com.xiaoxlh.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class EquationSolverTest {
    private fun sig(v: BigDecimal, n: Int = 25): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun residual(equation: String, x: BigDecimal): BigDecimal {
        val parts = equation.split("=")
        val f = "(${parts[0]})-(${parts[1]})"
        return ExpressionEvaluator(f, AngleUnit.DEG, mapOf("x" to x)).evaluate()
    }

    private fun solved(input: String): EquationSolution.Solved {
        val result = EquationSolver.solve(input)
        assertTrue("$input 求解失败：$result", result is EquationSolution.Solved)
        return result as EquationSolution.Solved
    }

    private fun failed(input: String): EquationError {
        val result = EquationSolver.solve(input)
        assertTrue("$input 本应失败，实际 $result", result is EquationSolution.Failed)
        return (result as EquationSolution.Failed).error
    }

    private fun exactForms(solution: EquationSolution.Solved): Set<String> =
        solution.roots.mapNotNull { it.exact }.map { it.replace('−', '-') }.toSet()

    private fun assertRootsSatisfy(equation: String, solution: EquationSolution.Solved) {
        for (root in solution.roots) {
            assertTrue("$equation 的根不是实数：$root", root is EquationRoot.Real)
            val value = (root as EquationRoot.Real).value
            val r = residual(equation, value).abs()
            assertTrue(
                "$equation：把 $value 代回去残差为 $r，超出容差",
                r < BigDecimal("1E-40").max(value.abs().multiply(BigDecimal("1E-40"))),
            )
        }
    }

    private fun assertVieta(a: BigDecimal, b: BigDecimal, c: BigDecimal, solution: EquationSolution.Solved) {
        val sum = if (solution.roots.size == 1) {
            Dec.mul(Dec.of(2), realOf(solution.roots[0]))
        } else {
            solution.roots.fold(BigDecimal.ZERO) { acc, root -> Dec.add(acc, realOf(root)) }
        }
        val product = when (solution.roots.size) {
            2 -> {
                val z1 = solution.roots[0]
                val z2 = solution.roots[1]
                val (r1, i1) = partsOf(z1)
                val (r2, i2) = partsOf(z2)
                Dec.sub(Dec.mul(r1, r2), Dec.mul(i1, i2))
            }
            else -> Dec.mul(realOf(solution.roots[0]), realOf(solution.roots[0]))
        }
        assertEquals("两根之和应等于 -b/a", sig(Dec.div(b.negate(), a)), sig(sum))
        assertEquals("两根之积应等于 c/a", sig(Dec.div(c, a)), sig(product))
    }

    private fun realOf(root: EquationRoot): BigDecimal = when (root) {
        is EquationRoot.Real -> root.value
        is EquationRoot.Complex -> root.real
    }

    private fun partsOf(root: EquationRoot): Pair<BigDecimal, BigDecimal> = when (root) {
        is EquationRoot.Real -> root.value to BigDecimal.ZERO
        is EquationRoot.Complex -> root.real to root.imaginary
    }

    @Test
    fun `一元一次`() {
        val cases = listOf(
            "2x+1=0" to "-0.5",
            "x-3=0" to "3",
            "3x=9" to "3",
            "x÷2=4" to "8",
            "5-2x=1" to "2",
        )
        for ((equation, expected) in cases) {
            val solution = solved(equation)
            assertEquals("$equation 的次数", 1, solution.degree)
            assertEquals("$equation 的解", expected, sig(realOf(solution.roots[0]), 16))
            assertRootsSatisfy(equation, solution)
        }
    }

    @Test
    fun `移项把右边的项搬到左边`() {
        val solution = solved("2x+3=x+5")
        assertEquals("2", sig(realOf(solution.roots[0]), 16))
        assertRootsSatisfy("2x+3=x+5", solution)
    }

    @Test
    fun `判别式为完全平方时两根都是整数`() {
        val solution = solved("x^2-3x+2=0")
        assertEquals(2, solution.degree)
        assertEquals("1", solution.discriminant?.let { sig(it) })
        val exact = exactForms(solution)
        assertEquals(setOf("1", "2"), exact)
        assertRootsSatisfy("x^2-3x+2=0", solution)
    }
    @Test
    fun `重根给出一个精确值`() {
        val solution = solved("x^2-2x+1=0")
        assertEquals("1", sig(realOf(solution.roots[0]), 16))
        assertEquals("1", solution.roots[0].exact)
        assertRootsSatisfy("x^2-2x+1=0", solution)
    }

    @Test
    fun `无理根给出精确根式而不是小数`() {
        val solution = solved("x^2-2=0")
        assertEquals(setOf("-√2", "√2"), exactForms(solution))
        assertRootsSatisfy("x^2-2=0", solution)
    }

    @Test
    fun `根式会约去公因子`() {
        assertEquals(setOf("1-√2", "1+√2"), exactForms(solved("x^2-2x-1=0")))
        assertEquals(setOf("(2-√6)/2", "(2+√6)/2"), exactForms(solved("2x^2-4x-1=0")))
    }

    @Test
    fun `精确形式里不会出现根号一`() {
        for (equation in listOf("x^2-3x+2=0", "x^2-5x+6=0", "4x^2-4x+1=0")) {
            for (root in solved(equation).roots) {
                val exact = root.exact ?: continue
                assertTrue("$equation 的精确形式含有 √1：$exact", !exact.contains("√1"))
            }
        }
    }

    @Test
    fun `复根`() {
        val solution = solved("x^2+1=0")
        assertEquals(2, solution.roots.size)
        assertTrue(solution.roots.all { it is EquationRoot.Complex })
        assertEquals(setOf("-i", "i"), exactForms(solution))
        assertTrue("判别式应为负", solution.discriminant!!.signum() < 0)
    }

    @Test
    fun `复根带实数部分`() {
        val solution = solved("x^2+2x+5=0")
        assertEquals(setOf("-1-2i", "-1+2i"), exactForms(solution))
        assertVieta(BigDecimal.ONE, BigDecimal("2"), BigDecimal("5"), solution)
    }

    @Test
    fun `韦达定理对一批二次方程都成立`() {
        val cases = listOf(
            Triple("1", "-3", "2"),
            Triple("1", "0", "-2"),
            Triple("1", "-2", "-1"),
            Triple("2", "-4", "-1"),
            Triple("1", "2", "5"),
            Triple("3", "6", "2"),
            Triple("1", "0", "1"),
            Triple("4", "-4", "1"),
        )
        for ((a, b, c) in cases) {
            val equation = "${a}x^2+${b}x+${c}=0"
            val solution = solved(equation)
            assertVieta(BigDecimal(a), BigDecimal(b), BigDecimal(c), solution)
        }
    }

    @Test
    fun `缺少等号时明确报错`() {
        assertEquals(EquationError.NO_EQUALS, failed("x^2-3x+2"))
    }

    @Test
    fun `多个等号被拒`() {
        assertEquals(EquationError.MULTIPLE_EQUALS, failed("x=1=2"))
    }

    @Test
    fun `空输入`() {
        assertEquals(EquationError.EMPTY, failed("   "))
    }

    @Test
    fun `恒等式与矛盾式分别报错`() {
        assertEquals(EquationError.IDENTITY, failed("x=x"))
        assertEquals(EquationError.NO_SOLUTION, failed("x=x+1"))
    }

    @Test
    fun `非多项式被拒`() {
        assertEquals(EquationError.NOT_POLYNOMIAL, failed("sin(x)=0"))
        assertEquals(EquationError.NOT_POLYNOMIAL, failed("1÷x=0"))
    }
}
