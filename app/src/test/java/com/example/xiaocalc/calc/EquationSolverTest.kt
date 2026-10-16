package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 方程求解的验证。
 *
 * 主力是三条**不依赖我记忆数值**的性质：
 *
 *  1. **把根代回原方程**，残差必须为 0 —— 这直接检验"解"的定义；
 *  2. **韦达定理**：两根之和 = −b/a、两根之积 = c/a。它对实根与复根同样成立，
 *     因此能同时守住复根分支（复根没法靠"代回去"检验，除非再写一套复数求值）；
 *  3. 精确形式的**结构**（如"含 √2 且不含 √1"），而不是逐字符比对整串。
 */
class EquationSolverTest {

    private fun sig(v: BigDecimal, n: Int = 25): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    /** 把 x 代回 `左 − 右`，返回残差 */
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

    /**
     * 精确形式归一到集合。
     *
     * **不用列表排序比对**：减号在精确形式里是 U+2212（码位大于所有 ASCII），
     * 归一成 ASCII 后 `+`(0x2B) 又排在 `-`(0x2D) 前面——无论怎么排，
     * 顺序都不反映任何语义，而我却要为它写一堆反直觉的期望值。
     * 解本来就是一组根，用集合比对既正确又不会因符号写法而误报。
     */
    private fun exactForms(solution: EquationSolution.Solved): Set<String> =
        solution.roots.mapNotNull { it.exact }.map { it.replace('−', '-') }.toSet()

    /** 实数根代回原方程，残差必须落在工作精度量级内 */
    private fun assertRootsSatisfy(equation: String, solution: EquationSolution.Solved) {
        for (root in solution.roots) {
            assertTrue("$equation 的根不是实数：$root", root is EquationRoot.Real)
            val value = (root as EquationRoot.Real).value
            val r = residual(equation, value).abs()
            // 存的根是精确值的 50 位小数近似，代回去得到 ~1e-49 而非严格 0，
            // 这正是工作精度的量级；用 1e-40 作相对容差既不过松也不会误报
            assertTrue(
                "$equation：把 $value 代回去残差为 $r，超出容差",
                r < BigDecimal("1E-40").max(value.abs().multiply(BigDecimal("1E-40"))),
            )
        }
    }

    /** 韦达定理：对实根与复根都成立 */
    private fun assertVieta(a: BigDecimal, b: BigDecimal, c: BigDecimal, solution: EquationSolution.Solved) {
        // 重根时实现只返回一个根，但韦达定理说的是"两个根"（此处两倍）之和
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
                // (r1+i·i1)(r2+i·i2) 的实部
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

    // ------------------------------------------------------------------ 一次

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
        // 2x+3 = x+5 → x = 2
        val solution = solved("2x+3=x+5")
        assertEquals("2", sig(realOf(solution.roots[0]), 16))
        assertRootsSatisfy("2x+3=x+5", solution)
    }

    // ------------------------------------------------------------------ 二次

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
        // x²-2x-1 = 0 → 1±√2
        assertEquals(setOf("1-√2", "1+√2"), exactForms(solved("x^2-2x-1=0")))
        // 2x²-4x-1 = 0 → (2±√6)/2
        assertEquals(setOf("(2-√6)/2", "(2+√6)/2"), exactForms(solved("2x^2-4x-1=0")))
    }

    @Test
    fun `精确形式里不会出现根号一`() {
        // 判别式是完全平方时若忘了化简，会写出 (3±√1)/2 这种破绽
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
        // x²+2x+5 = 0 → -1±2i
        val solution = solved("x^2+2x+5=0")
        assertEquals(setOf("-1-2i", "-1+2i"), exactForms(solution))
        // 韦达定理对复根同样成立
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

    // ------------------------------------------------------------------ 失败

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
