package com.xiaoxlh.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class HigherDegreeEquationTest {
    private fun sig(v: BigDecimal, n: Int = 16): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

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

    private fun polynomialOf(equation: String): Poly {
        val parts = equation.split("=")
        return PolynomialFit.fit("(${parts[0]})-(${parts[1]})")!!
    }

    private fun assertAllRootsSatisfy(equation: String, solution: EquationSolution.Solved) {
        val poly = polynomialOf(equation)
        for (root in solution.roots) {
            val z = when (root) {
                is EquationRoot.Real -> Cx(root.value, BigDecimal.ZERO)
                is EquationRoot.Complex -> Cx(root.real, root.imaginary)
            }
            val residual = poly.evaluateComplex(z).abs
            val scale = BigDecimal.ONE.max(z.abs)
            assertTrue(
                "$equation：把 $z 代回去残差为 $residual，超出容差",
                residual < scale.multiply(BigDecimal("1E-25")),
            )
        }
    }

    private fun exactSet(solution: EquationSolution.Solved): Set<String> =
        solution.roots.mapNotNull { it.exact }.map { it.replace('−', '-') }.toSet()

    @Test
    fun `三次方程降到二次后给出精确整数根`() {
        val solution = solved("x^3-6x^2+11x-6=0")
        assertEquals(3, solution.degree)
        assertEquals(3, solution.roots.size)
        assertEquals(setOf("1", "2", "3"), exactSet(solution))
        assertAllRootsSatisfy("x^3-6x^2+11x-6=0", solution)
    }

    @Test
    fun `非首一的三次方程也能精确求解`() {
        val solution = solved("2x^3-3x^2-3x+2=0")
        assertEquals(3, solution.roots.size)
        assertEquals(setOf("-1", "2", "1/2"), exactSet(solution))
        assertAllRootsSatisfy("2x^3-3x^2-3x+2=0", solution)
    }

    @Test
    fun `四次方程全部为整数根`() {
        val solution = solved("x^4-5x^2+4=0")
        assertEquals(4, solution.degree)
        assertEquals(setOf("-2", "-1", "1", "2"), exactSet(solution))
        assertAllRootsSatisfy("x^4-5x^2+4=0", solution)
    }

    @Test
    fun `零根会被单独约去`() {
        val solution = solved("x^3-4x=0")
        assertEquals(3, solution.roots.size)
        assertEquals(setOf("-2", "0", "2"), exactSet(solution))
        assertAllRootsSatisfy("x^3-4x=0", solution)
    }

    @Test
    fun `立方差分解出一个实根与一对复根`() {
        val solution = solved("x^3-1=0")
        assertEquals(3, solution.roots.size)
        assertEquals(setOf("1"), exactSet(solution).filter { !it.contains("i") }.toSet())
        assertEquals(
            setOf("(-1-√3i)/2", "(-1+√3i)/2"),
            exactSet(solution).filter { it.contains("i") }.toSet(),
        )
        assertAllRootsSatisfy("x^3-1=0", solution)
    }

    @Test
    fun `立方和分解`() {
        val solution = solved("x^3+1=0")
        assertEquals(3, solution.roots.size)
        assertEquals(setOf("-1"), exactSet(solution).filter { !it.contains("i") }.toSet())
        assertAllRootsSatisfy("x^3+1=0", solution)
    }

    @Test
    fun `无理根交给数值迭代`() {
        val solution = solved("x^3-2=0")
        assertEquals(3, solution.roots.size)
        val realRoots = solution.roots.filterIsInstance<EquationRoot.Real>()
        assertEquals("实数根应当恰好一个", 1, realRoots.size)
        assertEquals("1.259921049894873", sig(realRoots[0].value))
        assertEquals(2, solution.roots.count { it is EquationRoot.Complex })
        assertAllRootsSatisfy("x^3-2=0", solution)
    }

    @Test
    fun `五次方程也能求出全部根`() {
        val solution = solved("x^5-15x^4+85x^3-225x^2+274x-120=0")
        assertEquals(5, solution.roots.size)
        assertEquals(setOf("1", "2", "3", "4", "5"), exactSet(solution))
        assertAllRootsSatisfy("x^5-15x^4+85x^3-225x^2+274x-120=0", solution)
    }

    @Test
    fun `数值求根的结果代回确实为零`() {
        val solution = solved("x^3+x-1=0")
        assertEquals(3, solution.roots.size)
        assertAllRootsSatisfy("x^3+x-1=0", solution)
        val positiveReal = solution.roots
            .filterIsInstance<EquationRoot.Real>()
            .map { it.value }
            .first { it.signum() > 0 }
        assertEquals("0.6823278038280193", sig(positiveReal))
    }

    @Test
    fun `根的数量等于次数`() {
        val cases = listOf(
            "x^3-6x^2+11x-6=0" to 3,
            "x^4-5x^2+4=0" to 4,
            "x^3-1=0" to 3,
            "x^3-2=0" to 3,
            "x^3+x-1=0" to 3,
        )
        for ((equation, degree) in cases) {
            assertEquals("$equation 的根数", degree, solved(equation).roots.size)
        }
    }

    @Test
    fun `次数超过上限时明确报错`() {
        val equation = (1..12).joinToString("") { "x^$it+" } + "1=0"
        assertEquals(EquationError.DEGREE_TOO_HIGH, failed(equation))
    }
}
