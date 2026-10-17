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
 * 而九正好是 3×3，图例排得整齐。
 *
 * 与方程页、换算页同一个思路：**输入态与展示态分离**。
 * 区别在于这里的展示态需要一整块画布，而 480×480 的圆屏放不下"键盘 + 大图"，
 * 所以用 [graphing] 显式切换两种布局。
 */
class GraphState {

    /** 函数列表，长度恒在 `1..[MAX_FUNCTIONS]` 之间 */
    var functions: List<GraphFunction> by mutableStateOf(listOf(GraphFunction("")))
        private set

    /** 正在编辑的那一条的下标 */
    var editing: Int by mutableStateOf(0)
        private set

    /** 是否处于图像态 */
    var graphing: Boolean by mutableStateOf(false)
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

    /** 当前正在编辑的解析式。给"单条"的旧调用点用，语义等价于 [functions]`[editing]` */
    val input: String get() = functions.getOrNull(editing)?.expression.orEmpty()

    /** 可以画出来的函数（非空表达式），顺序即颜色顺序 */
    val drawable: List<GraphFunction> get() = functions.filter { it.isDrawable }

    // ------------------------------------------------------------------ 输入

    fun clear() {
        setExpression("")
        selected = 0
    }

    fun backspace() {
        val text = input
        if (text.isEmpty()) return
        val tail = FUNCTION_TAILS.firstOrNull { text.endsWith(it) }
        setExpression(if (tail != null) text.dropLast(tail.length) else text.dropLast(1))
    }

    fun insert(text: String) {
        if (text.isEmpty()) return
        setExpression(input + text)
    }

    fun setExpression(text: String) {
        val index = editing.coerceIn(0, functions.lastIndex)
        functions = functions.toMutableList().also { it[index] = GraphFunction(text) }
    }

    fun onKey(key: CalcKey) {
        when (key) {
            is CalcKey.Digit -> appendDigit(key.value)
            CalcKey.Dot -> appendDot()
            CalcKey.Clear -> clear()
            CalcKey.Backspace -> backspace()
            else -> key.insert?.let(::appendRaw)
        }
    }

    private fun appendDigit(digit: Char) {
        val trailing = trailingNumber()
        if (trailing == "0" && digit != '0') {
            setExpression(input.dropLast(1) + digit)
            return
        }
        if (trailing == "0" && digit == '0') return
        appendRaw(digit.toString())
    }

    private fun appendDot() {
        if (trailingNumber().contains('.')) return
        insert(".")
    }

    /** 与方程页同一套最小防错：连续两个二元运算符视为改主意 */
    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val current = input
        val last = current.lastOrNull()
        if (text.length == 1 && text[0] in BINARY_OPERATORS && last != null && last in BINARY_OPERATORS) {
            setExpression(current.dropLast(1) + text)
            return
        }
        setExpression(current + text)
    }

    private fun trailingNumber(): String {
        val text = input
        var start = text.length
        while (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '.')) start--
        return text.substring(start)
    }

    // -------------------------------------------------------------- 函数列表

    /** 切到某一条去编辑（页面点图例时用） */
    fun selectFunction(index: Int) {
        editing = index.coerceIn(0, functions.lastIndex)
        selected = 0
    }

    /**
     * 追加一条空函数并转去编辑它。
     *
     * @return 已到上限时返回 false——界面据此把"+"按钮置灰，
     *         而不是加了第 10 条再默默丢掉
     */
    fun addFunction(): Boolean {
        if (functions.size >= MAX_FUNCTIONS) return false
        functions = functions + GraphFunction("")
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

    /** 进入图像态。没有任何可画的函数时拒绝，返回是否成功。 */
    fun enterGraph(): Boolean {
        if (drawable.isEmpty()) return false
        graphing = true
        selected = 0
        return true
    }

    fun exitGraph() {
        graphing = false
    }

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
        /** 同时绘制的函数条数上限：再多颜色就分不开了，而九正好排成 3×3 图例 */
        const val MAX_FUNCTIONS = 9

        private val BINARY_OPERATORS = setOf('+', '-', '−', '×', '÷', '^')
        private val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")

        /** 最小/最大半跨度：再窄或再宽都已经没有绘图意义 */
        private val MIN_HALF_SPAN: BigDecimal = BigDecimal("0.001")
        private val MAX_HALF_SPAN: BigDecimal = BigDecimal("1000000")
    }
}
