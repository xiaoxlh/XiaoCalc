package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方程页输入状态机的验证。
 *
 * 与 [UnitConverterStateTest] 同一个重点：值是交给求解器的，
 * 所以这里的职责只有"别让用户轻易敲出语法错误"与"别把明显的手滑变成报错"。
 */
class EquationStateTest {

    private fun state() = EquationState()

    private fun EquationState.type(text: String) {
        for (c in text) {
            when (c) {
                in '0'..'9' -> onKey(CalcKey.Digit(c))
                '.' -> onKey(CalcKey.Dot)
                'x' -> onKey(CalcKey.VariableX)
                '+' -> onKey(CalcKey.Plus)
                '-' -> onKey(CalcKey.Minus)
                '×' -> onKey(CalcKey.Times)
                '÷' -> onKey(CalcKey.Divide)
                '^' -> onKey(CalcKey.Power)
                else -> Unit
            }
        }
    }

    @Test
    fun `默认为一元模式且输入为空`() {
        val s = state()
        assertEquals(EquationMode.SINGLE, s.mode)
        assertEquals("", s.input)
    }

    @Test
    fun `可以输入含未知数的等式`() {
        val s = state()
        s.type("x^2-3x+2")
        assertEquals("x^2-3x+2", s.input)
        s.insert("=")
        s.onKey(CalcKey.Digit('0'))
        assertEquals("x^2-3x+2=0", s.input)
    }

    @Test
    fun `未知数键插入的是字母 x`() {
        val s = state()
        s.onKey(CalcKey.VariableX)
        assertEquals("x", s.input)
    }

    @Test
    fun `前导零被替换`() {
        val s = state()
        s.type("0")
        s.type("5")
        assertEquals("5", s.input)
    }

    @Test
    fun `小数点不会重复`() {
        val s = state()
        s.type("1.5")
        assertEquals("1.5", s.input)
        s.type(".")
        assertEquals("1.5", s.input)
    }

    @Test
    fun `连按两个运算符是改主意`() {
        val s = state()
        s.type("2+")
        s.type("×")
        assertEquals("2×", s.input)
    }

    @Test
    fun `退格一次删掉整个函数名`() {
        val s = state()
        s.insert("sin(")
        assertEquals("sin(", s.input)
        s.backspace()
        assertEquals("", s.input)
    }

    @Test
    fun `退格在空输入上是安全的`() {
        val s = state()
        s.backspace()
        assertEquals("", s.input)
        s.clear()
        assertEquals("", s.input)
    }

    @Test
    fun `等号最多两个`() {
        val s = state()
        s.insert("=")
        s.insert("=")
        assertEquals("==", s.input)
        // 第三个被拒——方程组也只有两个等式
        s.insert("=")
        assertEquals("==", s.input)
    }

    @Test
    fun `切换到二元模式会清空输入`() {
        val s = state()
        s.insert("x^2-1=0")
        s.toggleMode()
        assertEquals(EquationMode.SYSTEM, s.mode)
        // 两种模式语法不同，保留另一半的输入只会让人对着必然报错的式子发愣
        assertEquals("", s.input)
        s.toggleMode()
        assertEquals(EquationMode.SINGLE, s.mode)
    }

    @Test
    fun `二元模式下可以输入分号分隔的两个方程`() {
        val s = state()
        s.toggleMode()
        s.insert("x+y=3;2x-y=0")
        assertEquals("x+y=3;2x-y=0", s.input)
        val solution = LinearSystemSolver.solve(s.input)
        assertTrue(solution is SystemSolution.Unique)
        assertEquals("1", (solution as SystemSolution.Unique).xExact)
    }

    @Test
    fun `输入的方程能立刻求解`() {
        val s = state()
        s.type("x^2-3x+2")
        s.insert("=0")
        val solution = EquationSolver.solve(s.input)
        assertTrue(solution is EquationSolution.Solved)
        assertEquals(
            setOf("1", "2"),
            (solution as EquationSolution.Solved).roots.mapNotNull { it.exact }.toSet(),
        )
    }
}
