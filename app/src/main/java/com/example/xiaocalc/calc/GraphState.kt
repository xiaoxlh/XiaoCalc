package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * 一条待绘制的函数。
 *
 * 只存解析式；颜色由位置决定（见 `GraphScreen`），因此删掉中间一条时，
 * 后面的函数会整体前移一位、换色——这是刻意的：颜色代表"第几条"，
 * 而不是"这条函数的身份"。让颜色跟着身份走，删来删去之后会剩下一堆
 * 无法解释的撞色。
 */
data class GraphFunction(val expression: String) {
    val isDrawable: Boolean get() = expression.isNotBlank()
}

/**
 * 函数图像页的状态。
 *
 * ## 为什么是"最多九条"而不是一条
 *
 * 一条曲线只能回答"这个函数长什么样"；比较两个函数（交点、谁高谁低、
 * 平移的效果）才是画图真正要解决的问题。上限取九条是因为：
 * 画布上同时辨认九条曲线已是极限，再多颜色也分不开；
 * 而九正好是 3×3，抽屉里的清单排得整齐。
 *
 * ## 为什么没有"输入态/图像态"之分
 *
 * 初版把页面拆成两态：输入态摆键盘，图像态摆画布。480×480 的圆屏
 * 两种都要，结果两边都局促——键盘占掉下半屏，画布只剩一条横带。
 * 现在画布常驻，表达式在底部抽屉里用系统输入法改（与方程页同一套做法），
 * 于是"编辑"不再是一次页面切换，而是抽屉的一次展开。
 */
class GraphState {

    /** 函数列表，长度恒在 `1..[MAX_FUNCTIONS]` 之间 */
    var functions: List<GraphFunction> by mutableStateOf(listOf(GraphFunction("")))
        private set

    /** 正在编辑（也是画布上被强调）的那一条的下标 */
    var editing: Int by mutableStateOf(0)
        private set

    /** 横轴范围 */
    var xMin: BigDecimal by mutableStateOf(BigDecimal("-10"))
        private set
    var xMax: BigDecimal by mutableStateOf(BigDecimal("10"))
        private set

    /** 当前选中的关键点下标；由页面在关键点数量变化时收敛 */
    var selected: Int by mutableStateOf(0)
        private set

    val span: BigDecimal get() = Dec.sub(xMax, xMin)

    /** 当前正在编辑的解析式 */
    val input: String get() = functions.getOrNull(editing)?.expression.orEmpty()

    /** 可以画出来的函数（非空表达式），顺序即颜色顺序 */
    val drawable: List<GraphFunction> get() = functions.filter { it.isDrawable }

    /**
     * [editing] 那一条在 [drawable] 里的下标；正在编辑空白函数时为 -1。
     *
     * 颜色与关键点都按这个下标取，而不是直接拿 [editing]：列表里允许存在
     * 空白（刚点"+"还没写的）那一条，直接当下标用会把颜色整体错位一格。
     */
    val activeCurve: Int
        get() = if (functions.getOrNull(editing)?.isDrawable == true) {
            functions.take(editing).count { it.isDrawable }
        } else {
            -1
        }

    // ------------------------------------------------------------------ 编辑

    fun clear() {
        setExpression("")
        selected = 0
    }

    /** 改写当前正在编辑的那一条 */
    fun setExpression(text: String) {
        update(editing, text)
    }

    /**
     * 改写第 [index] 条并把编辑焦点移过去。
     *
     * 抽屉里每一行都是独立的输入框，用户可能在任何一行上敲字，
     * 所以"改哪一条"必须由调用点显式指定，不能只认 [editing]。
     */
    fun update(index: Int, text: String) {
        if (index !in functions.indices) return
        if (functions[index].expression != text) {
            functions = functions.toMutableList().also { it[index] = GraphFunction(text) }
        }
        editing = index
        // 换了曲线就换了一组关键点，旧下标再指下去会指到别的点上
        selected = 0
    }

    // -------------------------------------------------------------- 函数列表

    /** 切到某一条去编辑（抽屉里点一行时用） */
    fun selectFunction(index: Int) {
        editing = index.coerceIn(0, functions.lastIndex)
        selected = 0
    }

    /**
     * 追加一条空函数并转去编辑它。
     *
     * 末尾已经是空白条目时**复用它**而不是再加一行：否则第一次使用就会
     * 留下一行永远填不上的 y1，于是唯一的曲线叫 y2、颜色也跳到第二色。
     *
     * @return 已到上限时返回 false——界面据此把"+"按钮置灰，
     *         而不是加了第 10 条再默默丢掉
     */
    fun addFunction(): Boolean {
        if (functions.size >= MAX_FUNCTIONS) return false
        if (functions.last().isDrawable) {
            functions = functions + GraphFunction("")
        }
        editing = functions.lastIndex
        selected = 0
        return true
    }

    /**
     * 删掉一条。
     *
     * **始终保留至少一条**：删空之后页面就没有可编辑的对象，
     * 用户得先点"+"才能继续，而那个"+"在空列表里没有任何提示。
     */
    fun removeFunction(index: Int) {
        if (functions.size <= 1) {
            // 只剩一条时删除等价于清空它，而不是让列表变空
            if (index == editing.coerceIn(0, functions.lastIndex)) clear()
            return
        }
        if (index !in functions.indices) return
        functions = functions.toMutableList().also { it.removeAt(index) }
        editing = editing.coerceIn(0, functions.lastIndex)
        selected = 0
    }

    // ------------------------------------------------------------------ 视图

    /**
     * 以视图中心为锚点缩放。
     *
     * @param factor 1 表示放大一倍（看到的范围减半），2 表示缩小一倍
     */
    fun zoom(factor: Int) {
        if (factor <= 1 && factor >= -1) return
        val center = Dec.div(Dec.add(xMin, xMax), Dec.of(2))
        val halfSpan = Dec.div(span, Dec.of(2))
        val nextHalf = if (factor > 0) {
            Dec.div(halfSpan, Dec.of(factor))
        } else {
            Dec.mul(halfSpan, Dec.of(-factor))
        }
        // 防止缩放走到极端后画面上什么都看不到
        val clamped = nextHalf.max(MIN_HALF_SPAN).min(MAX_HALF_SPAN)
        xMin = Dec.sub(center, clamped)
        xMax = Dec.add(center, clamped)
        selected = 0
    }

    fun resetView() {
        xMin = BigDecimal("-10")
        xMax = BigDecimal("10")
        selected = 0
    }

    fun selectNext(count: Int) {
        if (count <= 0) return
        selected = (selected + 1) % count
    }

    fun selectPrevious(count: Int) {
        if (count <= 0) return
        selected = (selected - 1 + count) % count
    }

    /**
     * 把选中下标收敛到 `[0, count)`。
     *
     * 这是**读取侧**的纯函数：页面每次渲染时用它换算，而不是在关键点数量变化时
     * 用副作用去改写 [selected]。后者需要 `remember`/`LaunchedEffect`，
     * 而状态变更一旦漏执行，画面上就会出现"选中了不存在的点"。
     */
    fun clampSelection(count: Int): Int = selected.coerceIn(0, (count - 1).coerceAtLeast(0))

    companion object {
        /** 同时绘制的函数条数上限：再多颜色就分不开了 */
        const val MAX_FUNCTIONS = 9

        /** 最小/最大半跨度：再窄或再宽都已经没有绘图意义 */
        private val MIN_HALF_SPAN: BigDecimal = BigDecimal("0.001")
        private val MAX_HALF_SPAN: BigDecimal = BigDecimal("1000000")
    }
}
