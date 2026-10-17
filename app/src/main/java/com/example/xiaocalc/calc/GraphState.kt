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

    /**
     * 纵轴范围。
     *
     * 初版没有这两个字段：纵轴范围是每次绘制时按采样点的第 98 百分位现算的，
     * 于是"坐标系"只有横轴能变——拖动只改宽度，纵轴永远自动贴合数据，
     * 一放大就会被重新夹回去。能拖能缩的坐标系必须让两轴的视野都是**状态**。
     */
    var yMin: BigDecimal by mutableStateOf(BigDecimal("-10"))
        private set
    var yMax: BigDecimal by mutableStateOf(BigDecimal("10"))
        private set

    /** 当前选中的关键点下标；由页面在关键点数量变化时收敛 */
    var selected: Int by mutableStateOf(0)
        private set

    val span: BigDecimal get() = Dec.sub(xMax, xMin)
    val ySpan: BigDecimal get() = Dec.sub(yMax, yMin)

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
     * 平移视野。
     *
     * 参数是**占视野的比例**而不是像素：状态层不该知道屏幕密度，
     * 而"拖过半个屏"在任何设备上都该走半个视野。手指在哪个方向划过半个屏，
     * 视野的反方向就露出半个视野——这也是所有地图/坐标系的手感。
     *
     * @param xFraction 水平拖拽占视野宽度的比例（正 = 手指向右）
     * @param yFraction 垂直拖拽占视野高度的比例（正 = 手指向下）
     */
    fun panBy(xFraction: Float, yFraction: Float) {
        if (xFraction == 0f && yFraction == 0f) return
        if (xFraction.isNaN() || yFraction.isNaN()) return
        // 手指右移 ⇒ 同一个数据点要出现在更右边 ⇒ 视野左移
        val dx = Dec.mul(span, fraction(xFraction))
        // 手指下移 ⇒ 同一个数据点要出现在更下方 ⇒ 视野上移
        val dy = Dec.mul(ySpan, fraction(yFraction))
        xMin = Dec.sub(xMin, dx)
        xMax = Dec.sub(xMax, dx)
        yMin = Dec.add(yMin, dy)
        yMax = Dec.add(yMax, dy)
        selected = 0
    }

    /**
     * 以视野里的某一点为锚点缩放。
     *
     * @param factor      大于 1 放大（看到的范围变小）
     * @param xFraction   锚点占视野宽度的比例，0.5 即正中
     * @param yFraction   锚点占视野高度的比例
     */
    fun zoomAt(factor: Float, xFraction: Float = 0.5f, yFraction: Float = 0.5f) {
        if (!factor.isFinite() || factor <= 0f || factor == 1f) return
        // 两轴按原比例一起缩，否则图形会被拉扁——坐标系里"圆看起来是圆的"是底线。
        // 比例必须在改写 xMin/xMax **之前**取出来：span 是计算属性，
        // 改完就被换成新的跨度了（这里踩过一次，缩放会把纵轴放大成两倍）
        val ratio = Dec.div(ySpan, span)
        val next = Dec.mul(span, fraction(1f / factor)).max(MIN_SPAN).min(MAX_SPAN)
        val ax = Dec.add(xMin, Dec.mul(span, fraction(xFraction)))
        val ay = Dec.add(yMin, Dec.mul(ySpan, fraction(yFraction)))
        xMin = Dec.sub(ax, Dec.mul(next, fraction(xFraction)))
        xMax = Dec.add(xMin, next)
        val nextY = Dec.mul(next, ratio)
        yMin = Dec.sub(ay, Dec.mul(nextY, fraction(yFraction)))
        yMax = Dec.add(yMin, nextY)
        selected = 0
    }

    /**
     * 以视图中心为锚点缩放（工具行里的 ± 按钮）。
     *
     * @param factor 正数放大，负数缩小；绝对值是倍数
     */
    fun zoom(factor: Int) {
        if (factor == 0 || factor == 1 || factor == -1) return
        zoomAt(if (factor > 0) factor.toFloat() else 1f / (-factor).toFloat())
    }

    /**
     * 按数据收紧纵轴。
     *
     * 横轴留在 `[-10, 10]`（一个可预期的默认窗口），纵轴跟着数据走：
     * `x^2` 与 `sin(x)` 的纵向量级差三个数量级，共用一个默认值必然有一个看不清。
     */
    fun fitY(low: BigDecimal, high: BigDecimal) {
        if (low >= high) return
        yMin = low
        yMax = high
        selected = 0
    }

    fun resetView() {
        xMin = BigDecimal("-10")
        xMax = BigDecimal("10")
        yMin = BigDecimal("-10")
        yMax = BigDecimal("10")
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

    /** 比例 → 高精度小数。50 位工作精度下，float 的十进制展开足以表达一次拖拽 */
    private fun fraction(value: Float): BigDecimal =
        if (!value.isFinite()) Dec.ZERO else BigDecimal.valueOf(value.toDouble())

    companion object {
        /** 同时绘制的函数条数上限：再多颜色就分不开了 */
        const val MAX_FUNCTIONS = 9

        /** 最小/最大跨度：再窄或再宽都已经没有绘图意义，且能挡住浮点误差累积 */
        private val MIN_SPAN: BigDecimal = BigDecimal("0.002")
        private val MAX_SPAN: BigDecimal = BigDecimal("2000000")
    }
}
