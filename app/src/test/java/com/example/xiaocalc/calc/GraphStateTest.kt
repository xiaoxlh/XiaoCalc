package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图像页状态机的验证。
 *
 * 重点在**视图范围**：缩放的锚点、上下限、以及缩放后选中点的收敛。
 * 输入规则与方程页同源，这里只做抽查。
 */
class GraphStateTest {

    private fun state() = GraphState()

    @Test
    fun `默认范围是正负十`() {
        val s = state()
        assertEquals("-10", s.xMin.toPlainString())
        assertEquals("10", s.xMax.toPlainString())
        assertEquals("20", s.span.toPlainString())
        assertFalse(s.graphing)
    }

    @Test
    fun `空解析式不能进入图像态`() {
        val s = state()
        assertFalse(s.enterGraph())
        assertFalse(s.graphing)

        s.insert("x^2")
        assertTrue(s.enterGraph())
        assertTrue(s.graphing)
        s.exitGraph()
        assertFalse(s.graphing)
    }

    @Test
    fun `放大以视图中心为锚点`() {
        val s = state()
        s.zoom(2)
        // 中心仍是 0，跨度减半
        assertEquals("-5", s.xMin.toPlainString())
        assertEquals("5", s.xMax.toPlainString())
    }

    @Test
    fun `缩小以视图中心为锚点`() {
        val s = state()
        s.zoom(-2)
        assertEquals("-20", s.xMin.toPlainString())
        assertEquals("20", s.xMax.toPlainString())
    }

    @Test
    fun `缩放后中心保持不变`() {
        val s = state()
        s.zoom(2)
        s.zoom(2)
        s.zoom(-2)
        val center = s.xMin.add(s.xMax).divide(java.math.BigDecimal("2"), Dec.MC)
        assertEquals("0", center.stripTrailingZeros().toPlainString())
    }

    @Test
    fun `缩放有上下限`() {
        val s = state()
        // 连续放大会被最小半跨度挡住
        repeat(40) { s.zoom(2) }
        assertTrue("半跨度不应小于下限", s.span.signum() > 0)
        assertTrue(
            "半跨度应被下限截住",
            s.span >= java.math.BigDecimal("0.002"),
        )

        // 连续缩小会被最大半跨度挡住
        repeat(60) { s.zoom(-2) }
        assertTrue(
            "半跨度应被上限截住",
            s.span <= java.math.BigDecimal("2000000"),
        )
    }

    @Test
    fun `重置回到默认范围`() {
        val s = state()
        s.zoom(2)
        s.resetView()
        assertEquals("-10", s.xMin.toPlainString())
        assertEquals("10", s.xMax.toPlainString())
    }

    @Test
    fun `关键点在缩放后回到第一个`() {
        val s = state()
        s.selectNext(3)
        s.selectNext(3)
        assertEquals(2, s.selected)
        s.zoom(2)
        assertEquals(0, s.selected)
    }

    @Test
    fun `关键点循环不会越界`() {
        val s = state()
        s.selectNext(3)
        s.selectNext(3)
        s.selectNext(3)
        assertEquals("三次 next 后应回到起点", 0, s.selected)

        s.selectPrevious(3)
        assertEquals("向前越界应回到末尾", 2, s.selected)
    }

    @Test
    fun `没有关键点时收敛为零`() {
        val s = state()
        s.selectNext(2)
        assertEquals(1, s.selected)
        assertEquals(0, s.clampSelection(0))
    }

    @Test
    fun `选中下标超出数量时收敛到末尾`() {
        val s = state()
        s.selectNext(5)
        s.selectNext(5)
        s.selectNext(5)
        s.selectNext(5)
        assertEquals(4, s.selected)
        assertEquals(1, s.clampSelection(2))
    }

    @Test
    fun `数量足够时收敛不改变选中项`() {
        val s = state()
        s.selectNext(3)
        assertEquals(1, s.selected)
        assertEquals(1, s.clampSelection(3))
    }

    @Test
    fun `输入规则与方程页一致`() {
        val s = state()
        s.onKey(CalcKey.Digit('0'))
        s.onKey(CalcKey.Digit('5'))
        assertEquals("前导零被替换", "5", s.input)
        s.onKey(CalcKey.VariableX)
        assertEquals("5x", s.input)
        s.onKey(CalcKey.Power)
        s.onKey(CalcKey.Times)
        assertEquals("连按两个运算符是改主意", "5x×", s.input)
    }

    @Test
    fun `清空同时重置选中点`() {
        val s = state()
        s.insert("x^2")
        s.selectNext(4)
        s.clear()
        assertEquals("", s.input)
        assertEquals(0, s.selected)
    }
}
