package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphStateTest {
    private fun state() = GraphState()

    private fun assertDec(expected: String, actual: java.math.BigDecimal) =
        assertEquals(expected, actual.stripTrailingZeros().toPlainString())

    @Test
    fun `默认范围是正负十`() {
        val s = state()
        assertDec("-10", s.xMin)
        assertDec("10", s.xMax)
        assertDec("20", s.span)
    }

    @Test
    fun `放大以视图中心为锚点`() {
        val s = state()
        s.zoom(2)
        assertDec("-5", s.xMin)
        assertDec("5", s.xMax)
    }

    @Test
    fun `缩小以视图中心为锚点`() {
        val s = state()
        s.zoom(-2)
        assertDec("-20", s.xMin)
        assertDec("20", s.xMax)
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
        repeat(40) { s.zoom(2) }
        assertTrue("半跨度不应小于下限", s.span.signum() > 0)
        assertTrue(
            "半跨度应被下限截住",
            s.span >= java.math.BigDecimal("0.002"),
        )
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
        assertDec("-10", s.xMin)
        assertDec("10", s.xMax)
        assertDec("-10", s.yMin)
        assertDec("10", s.yMax)
    }

    @Test
    fun `平移按视野比例移动两轴`() {
        val s = state()
        s.panBy(0.5f, 0f)
        assertDec("-20", s.xMin)
        assertDec("0", s.xMax)
        s.panBy(0f, 0.25f)
        assertDec("-5", s.yMin)
        assertDec("15", s.yMax)
    }

    @Test
    fun `平移不改变跨度`() {
        val s = state()
        s.panBy(-0.3f, 0.7f)
        assertDec("20", s.span)
        assertDec("20", s.ySpan)
    }

    @Test
    fun `缩放同时作用于两轴并保持比例`() {
        val s = state()
        s.fitY(java.math.BigDecimal("-2"), java.math.BigDecimal("2"))
        val ratio = s.ySpan.divide(s.span, Dec.MC)
        s.zoom(2)
        assertDec("10", s.span)
        assertEquals("两轴比例不该被缩放改掉", 0, ratio.compareTo(s.ySpan.divide(s.span, Dec.MC)))
    }

    @Test
    fun `以锚点缩放时锚点坐标不动`() {
        val s = state()
        s.zoomAt(2f, 1f, 0f)
        assertDec("0", s.xMin)
        assertDec("10", s.xMax)
        assertDec("-10", s.yMin)
        assertDec("0", s.yMax)
    }

    @Test
    fun `拖动后关闭自动贴合，重置后恢复`() {
        val s = state()
        assertTrue(s.autoFit)
        s.panBy(0.1f, 0f)
        assertTrue("拖动之后不该再自动贴合", !s.autoFit)
        s.resetView()
        assertTrue(s.autoFit)
        s.zoom(2)
        assertTrue("缩放之后同样关闭", !s.autoFit)
    }

    @Test
    fun `fitY 接受数据范围并拒绝倒置区间`() {
        val s = state()
        s.fitY(java.math.BigDecimal("-3"), java.math.BigDecimal("7"))
        assertDec("-3", s.yMin)
        assertDec("7", s.yMax)
        s.fitY(java.math.BigDecimal("7"), java.math.BigDecimal("-3"))
        assertDec("7", s.yMax)
        assertDec("-3", s.yMin)
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
    fun `清空同时重置选中点`() {
        val s = state()
        s.setExpression("x^2")
        s.selectNext(4)
        s.clear()
        assertEquals("", s.input)
        assertEquals(0, s.selected)
    }

    @Test
    fun `默认只有一条空函数`() {
        val s = GraphState()
        assertEquals(1, s.functions.size)
        assertEquals(0, s.editing)
        assertEquals("", s.input)
        assertEquals("空函数算不上可画", 0, s.drawable.size)
        assertEquals("没有可画的函数时不该有选中曲线", -1, s.activeCurve)
    }

    @Test
    fun `可以加到九条，第十条被拒绝`() {
        val s = GraphState()
        s.setExpression("x")
        repeat(GraphState.MAX_FUNCTIONS - 1) {
            assertTrue("第 ${s.functions.size + 1} 条应当加得进去", s.addFunction())
            s.setExpression("x")
        }
        assertEquals(GraphState.MAX_FUNCTIONS, s.functions.size)
        assertTrue("第十条必须被拒绝", !s.addFunction())
        assertEquals(GraphState.MAX_FUNCTIONS, s.functions.size)
    }

    @Test
    fun `末尾已有空白条目时复用而不是再加一行`() {
        val s = GraphState()
        assertTrue(s.addFunction())
        assertEquals("空白条目应被复用", 1, s.functions.size)
        s.setExpression("x")
        assertTrue(s.addFunction())
        assertEquals(2, s.functions.size)
    }

    @Test
    fun `新增后自动转去编辑新那一条`() {
        val s = GraphState()
        s.setExpression("x^2")
        s.addFunction()
        assertEquals(1, s.editing)
        assertEquals("", s.input)
        s.setExpression("sin(x)")
        assertEquals(listOf("x^2", "sin(x)"), s.functions.map { it.expression })
    }

    @Test
    fun `update 只改指定那一条并把编辑焦点移过去`() {
        val s = GraphState()
        s.setExpression("x")
        s.addFunction()
        s.setExpression("2x")
        s.update(0, "x^2")
        assertEquals(listOf("x^2", "2x"), s.functions.map { it.expression })
        assertEquals(0, s.editing)
    }

    @Test
    fun `activeCurve 跳过空白条目`() {
        val s = GraphState()
        s.setExpression("x")
        assertEquals(0, s.activeCurve)
        s.addFunction()
        assertEquals("正在编辑空白条目时没有选中曲线", -1, s.activeCurve)
        s.addFunction()
        s.setExpression("x^2")
        assertEquals("前面只有一条可画，因此是第二条", 1, s.activeCurve)
    }

    @Test
    fun `只有非空表达式才算可画`() {
        val s = GraphState()
        s.setExpression("x")
        s.addFunction()
        s.setExpression("x^2")
        s.addFunction()
        assertEquals(3, s.functions.size)
        assertEquals(2, s.drawable.size)
    }

    @Test
    fun `删掉中间一条后面的会补位`() {
        val s = GraphState()
        s.setExpression("a")
        s.addFunction(); s.setExpression("b")
        s.addFunction(); s.setExpression("c")
        s.removeFunction(1)
        assertEquals(listOf("a", "c"), s.functions.map { it.expression })
        assertTrue(s.editing in s.functions.indices)
    }

    @Test
    fun `只剩一条时删除等价于清空`() {
        val s = GraphState()
        s.setExpression("x^2")
        s.removeFunction(0)
        assertEquals(1, s.functions.size)
        assertEquals("", s.input)
    }

    @Test
    fun `越界的选择与删除都被忽略`() {
        val s = GraphState()
        s.setExpression("x")
        s.selectFunction(7)
        assertEquals(0, s.editing)
        s.removeFunction(5)
        assertEquals(1, s.functions.size)
    }

    @Test
    fun `清空只清当前那一条`() {
        val s = GraphState()
        s.setExpression("x")
        s.addFunction()
        s.setExpression("y")
        s.clear()
        assertEquals(listOf("x", ""), s.functions.map { it.expression })
    }
}
