package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * 函数图像页的状态。
 *
 * 与方程页、换算页同一个思路：**输入态与展示态分离**。
 * 区别在于这里的展示态需要一整块画布，而 480×480 的圆屏放不下"键盘 + 大图"，
 * 所以用 [graphing] 显式切换两种布局。
 */
class GraphState {

    /** 解析式输入。`x` 为自变量 */
    var input: String by mutableStateOf("")
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

    // ------------------------------------------------------------------ 输入

    fun clear() {
        input = ""
        selected = 0
    }

    fun backspace() {
        if (input.isEmpty()) return
        val tail = FUNCTION_TAILS.firstOrNull { input.endsWith(it) }
        input = if (tail != null) input.dropLast(tail.length) else input.dropLast(1)
    }

    fun insert(text: String) {
        if (text.isEmpty()) return
        input += text
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
            input = input.dropLast(1) + digit
            return
        }
        if (trailing == "0" && digit == '0') return
        appendRaw(digit.toString())
    }

    private fun appendDot() {
        if (trailingNumber().contains('.')) return
        input += "."
    }

    /** 与方程页同一套最小防错：连续两个二元运算符视为改主意 */
    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val last = input.lastOrNull()
        if (text.length == 1 && text[0] in BINARY_OPERATORS && last != null && last in BINARY_OPERATORS) {
            input = input.dropLast(1) + text
            return
        }
        input += text
    }

    private fun trailingNumber(): String {
        var start = input.length
        while (start > 0 && (input[start - 1].isDigit() || input[start - 1] == '.')) start--
        return input.substring(start)
    }

    // ------------------------------------------------------------------ 视图

    /** 进入图像态。解析式为空时拒绝，返回是否成功。 */
    fun enterGraph(): Boolean {
        if (input.isBlank()) return false
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

    private companion object {
        val BINARY_OPERATORS = setOf('+', '-', '−', '×', '÷', '^')
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "√(")

        /** 最小/最大半跨度：再窄或再宽都已经没有绘图意义 */
        val MIN_HALF_SPAN: BigDecimal = BigDecimal("0.001")
        val MAX_HALF_SPAN: BigDecimal = BigDecimal("1000000")
    }
}
