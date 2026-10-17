package com.xiaoxlh.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

data class GraphFunction(val expression: String) {
    val isDrawable: Boolean get() = expression.isNotBlank()
}

class GraphState {
    var functions: List<GraphFunction> by mutableStateOf(listOf(GraphFunction("")))
        private set

    var editing: Int by mutableStateOf(0)
        private set

    var xMin: BigDecimal by mutableStateOf(BigDecimal("-10"))
        private set
    var xMax: BigDecimal by mutableStateOf(BigDecimal("10"))
        private set

    var yMin: BigDecimal by mutableStateOf(BigDecimal("-10"))
        private set
    var yMax: BigDecimal by mutableStateOf(BigDecimal("10"))
        private set

    var selected: Int by mutableStateOf(0)
        private set

    /** false once the user pans or zooms; resetView() turns it back on */
    var autoFit: Boolean by mutableStateOf(true)
        private set

    val span: BigDecimal get() = Dec.sub(xMax, xMin)
    val ySpan: BigDecimal get() = Dec.sub(yMax, yMin)

    val input: String get() = functions.getOrNull(editing)?.expression.orEmpty()

    val drawable: List<GraphFunction> get() = functions.filter { it.isDrawable }

    val activeCurve: Int
        get() = if (functions.getOrNull(editing)?.isDrawable == true) {
            functions.take(editing).count { it.isDrawable }
        } else {
            -1
        }

    fun clear() {
        setExpression("")
        selected = 0
    }

    fun setExpression(text: String) {
        update(editing, text)
    }

    fun update(index: Int, text: String) {
        if (index !in functions.indices) return
        if (functions[index].expression != text) {
            functions = functions.toMutableList().also { it[index] = GraphFunction(text) }
        }
        editing = index
        selected = 0
    }

    fun selectFunction(index: Int) {
        editing = index.coerceIn(0, functions.lastIndex)
        selected = 0
    }

    fun addFunction(): Boolean {
        // reuse a trailing blank row instead of stacking empty ones
        if (functions.size >= MAX_FUNCTIONS) return false
        if (functions.last().isDrawable) {
            functions = functions + GraphFunction("")
        }
        editing = functions.lastIndex
        selected = 0
        return true
    }

    fun removeFunction(index: Int) {
        if (functions.size <= 1) {
            if (index == editing.coerceIn(0, functions.lastIndex)) clear()
            return
        }
        if (index !in functions.indices) return
        functions = functions.toMutableList().also { it.removeAt(index) }
        editing = editing.coerceIn(0, functions.lastIndex)
        selected = 0
    }

    fun panBy(xFraction: Float, yFraction: Float) {
        if (xFraction == 0f && yFraction == 0f) return
        if (xFraction.isNaN() || yFraction.isNaN()) return
        val dx = Dec.mul(span, fraction(xFraction))
        val dy = Dec.mul(ySpan, fraction(yFraction))
        xMin = Dec.sub(xMin, dx)
        xMax = Dec.sub(xMax, dx)
        yMin = Dec.add(yMin, dy)
        yMax = Dec.add(yMax, dy)
        autoFit = false
        selected = 0
    }

    fun zoomAt(factor: Float, xFraction: Float = 0.5f, yFraction: Float = 0.5f) {
        if (!factor.isFinite() || factor <= 0f || factor == 1f) return
        // capture the ratio before xMin/xMax change: span is a computed property
        val ratio = Dec.div(ySpan, span)
        val next = Dec.mul(span, fraction(1f / factor)).max(MIN_SPAN).min(MAX_SPAN)
        val ax = Dec.add(xMin, Dec.mul(span, fraction(xFraction)))
        val ay = Dec.add(yMin, Dec.mul(ySpan, fraction(yFraction)))
        xMin = Dec.sub(ax, Dec.mul(next, fraction(xFraction)))
        xMax = Dec.add(xMin, next)
        val nextY = Dec.mul(next, ratio)
        yMin = Dec.sub(ay, Dec.mul(nextY, fraction(yFraction)))
        yMax = Dec.add(yMin, nextY)
        autoFit = false
        selected = 0
    }

    fun zoom(factor: Int) {
        if (factor == 0 || factor == 1 || factor == -1) return
        zoomAt(if (factor > 0) factor.toFloat() else 1f / (-factor).toFloat())
    }

    fun fitY(low: BigDecimal, high: BigDecimal) {
        if (low >= high) return
        yMin = low
        yMax = high
        selected = 0
    }

    fun resetView() {
        xMin = DEFAULT_X_MIN
        xMax = DEFAULT_X_MAX
        yMin = BigDecimal("-10")
        yMax = BigDecimal("10")
        autoFit = true
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

    fun clampSelection(count: Int): Int = selected.coerceIn(0, (count - 1).coerceAtLeast(0))

    private fun fraction(value: Float): BigDecimal =
        if (!value.isFinite()) Dec.ZERO else BigDecimal.valueOf(value.toDouble())

    companion object {
        const val MAX_FUNCTIONS = 9
        val DEFAULT_X_MIN: BigDecimal = BigDecimal("-10")
        val DEFAULT_X_MAX: BigDecimal = BigDecimal("10")
        private val MIN_SPAN: BigDecimal = BigDecimal("0.002")
        private val MAX_SPAN: BigDecimal = BigDecimal("2000000")
    }
}
