package com.xiaoxlh.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationStateTest {
    private fun state() = EquationState()

    @Test
    fun `默认为一元模式且输入为空`() {
        val s = state()
        assertEquals(EquationMode.SINGLE, s.mode)
        assertEquals("", s.singleText)
        assertEquals("", s.solverInput)
    }

    @Test
    fun `一元模式写入整段表达式`() {
        val s = state()
        s.setText(0, "x^2-3x+2=0")
        assertEquals("x^2-3x+2=0", s.singleText)
        assertEquals("x^2-3x+2=0", s.solverInput)
        val solution = EquationSolver.solve(s.solverInput)
        assertTrue(solution is EquationSolution.Solved)
        assertEquals(
            setOf("1", "2"),
            (solution as EquationSolution.Solved).roots.mapNotNull { it.exact }.toSet(),
        )
    }

    @Test
    fun `符号条插入到当前聚焦的框`() {
        val s = state()
        s.setText(0, "x")
        s.insert("^2")
        assertEquals("x^2", s.singleText)
        s.selectMode(EquationMode.SYSTEM)
        s.focus(1)
        s.insert("y=1")
        assertEquals(listOf("", "y=1"), s.systemTexts)
        assertEquals("x^2", s.singleText)
    }

    @Test
    fun `退格一次删掉整个函数名`() {
        val s = state()
        s.insert("sin(")
        assertEquals("sin(", s.singleText)
        s.backspace()
        assertEquals("", s.singleText)
        s.insert("abc")
        s.backspace()
        assertEquals("ab", s.singleText)
    }

    @Test
    fun `退格与清空在空输入上是安全的`() {
        val s = state()
        s.backspace()
        assertEquals("", s.singleText)
        s.clear()
        assertEquals("", s.singleText)
    }

    @Test
    fun `清空只清当前聚焦的框`() {
        val s = state()
        s.selectMode(EquationMode.SYSTEM)
        s.setText(0, "x+y=3")
        s.setText(1, "2x-y=0")
        s.focus(0)
        s.clear()
        assertEquals(listOf("", "2x-y=0"), s.systemTexts)
    }

    @Test
    fun `切换模式不会清掉另一边的输入`() {
        val s = state()
        s.setText(0, "x^2-1=0")
        s.selectMode(EquationMode.SYSTEM)
        assertEquals(EquationMode.SYSTEM, s.mode)
        assertEquals("", s.systemTexts[0])
        s.selectMode(EquationMode.SINGLE)
        assertEquals("x^2-1=0", s.singleText)
    }

    @Test
    fun `二元模式把两个框拼成分号分隔的一串`() {
        val s = state()
        s.selectMode(EquationMode.SYSTEM)
        s.setText(0, "x+y=3")
        s.setText(1, "2x-y=0")
        assertEquals("x+y=3;2x-y=0", s.solverInput)
        val solution = LinearSystemSolver.solve(s.solverInput)
        assertTrue(solution is SystemSolution.Unique)
        assertEquals("1", (solution as SystemSolution.Unique).xExact)
    }

    @Test
    fun `两个框都空时不给求解器半截输入`() {
        val s = state()
        s.selectMode(EquationMode.SYSTEM)
        assertEquals("", s.solverInput)
        s.setText(0, "x+y=3")
        assertEquals("x+y=3;", s.solverInput)
    }

    @Test
    fun `聚焦序号被夹在合法范围内`() {
        val s = state()
        s.focus(1)
        assertEquals(0, s.focused)
        s.selectMode(EquationMode.SYSTEM)
        s.focus(5)
        assertEquals(1, s.focused)
        s.focus(-3)
        assertEquals(0, s.focused)
    }
}
