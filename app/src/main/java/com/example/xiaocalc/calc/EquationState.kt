package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 方程页的两种输入模式 */
enum class EquationMode(val label: String) {
    /** 一元方程，如 `x^2-3x+2=0` */
    SINGLE("一元"),

    /** 二元一次方程组，两个方程分两个输入框 */
    SYSTEM("二元"),
}

/**
 * 方程页的输入状态。
 *
 * ## 为什么二元模式用**两个输入框**而不是一个带 `;` 的字符串
 *
 * 旧实现要求用户在一个框里写 `2x+y=5;x-y=1`。那个分号是个**只存在于本应用里的
 * 约定**：系统输入法不会提示它，用户也不可能猜到。拆成两个框之后，
 * "二元一次方程组有两个方程"这件事直接由界面结构表达，不需要任何说明文字。
 *
 * 对外仍以 `;` 拼接后交给 [LinearSystemSolver]——求解器的语法不必改，
 * 界面结构与内部表示各归各的。
 *
 * 与 [UnitConverterState] 同一个思路：状态只负责编辑输入，不保存求解结果，
 * 因此不会出现"改了输入但忘了更新结果"这类不一致。
 */
class EquationState {

    var mode: EquationMode by mutableStateOf(EquationMode.SINGLE)
        private set

    /** 当前聚焦的输入框序号：0 是第一个（一元模式下恒为 0） */
    var focused: Int by mutableStateOf(0)
        private set

    private var single by mutableStateOf("")
    private var first by mutableStateOf("")
    private var second by mutableStateOf("")

    /** 一元模式的输入 */
    val singleText: String get() = single

    /** 二元模式的两个输入，顺序固定 */
    val systemTexts: List<String> get() = listOf(first, second)

    /** 当前聚焦框里的文本 */
    val activeText: String
        get() = when {
            mode == EquationMode.SINGLE -> single
            focused == 0 -> first
            else -> second
        }

    /**
     * 交给求解器的整串。
     *
     * 二元模式下两个框都为空时返回空串，避免拼出一个 ";"
     * 让求解器去报"需要两个方程"。
     */
    val solverInput: String
        get() = when (mode) {
            EquationMode.SINGLE -> single
            EquationMode.SYSTEM -> if (first.isBlank() && second.isBlank()) "" else "$first;$second"
        }

    /** 直接改写某个框的全文（[androidx.compose.foundation.text.BasicTextField] 用） */
    fun setText(index: Int, text: String) {
        when {
            mode == EquationMode.SINGLE -> single = text
            index == 0 -> first = text
            else -> second = text
        }
    }

    fun focus(index: Int) {
        focused = index.coerceIn(0, if (mode == EquationMode.SYSTEM) 1 else 0)
    }

    /**
     * 切换模式。
     *
     * **不清空已输入的内容**：一元与二元各自有独立的输入框，切回来时原来的式子还在，
     * 这比"切一下就全没了"符合直觉。旧实现会清空，是因为两种模式共用一个字符串。
     */
    fun selectMode(next: EquationMode) {
        if (next == mode) return
        mode = next
        focused = 0
    }

    fun clear() {
        setText(focused, "")
    }

    fun backspace() {
        val text = activeText
        if (text.isEmpty()) return
        val tail = FUNCTION_TAILS.firstOrNull { text.endsWith(it) }
        setText(focused, if (tail != null) text.dropLast(tail.length) else text.dropLast(1))
    }

    /** 在光标位置插入一段文本（符号条使用；光标由外部 TextField 维护，这里只做追加） */
    fun insert(text: String) {
        if (text.isEmpty()) return
        setText(focused, activeText + text)
    }

    fun onKey(key: CalcKey) {
        when (key) {
            is CalcKey.Digit -> insert(key.value.toString())
            CalcKey.Dot -> if (!activeText.endsWith(".")) insert(".")
            CalcKey.Clear -> clear()
            CalcKey.Backspace -> backspace()
            else -> key.insert?.let(::insert)
        }
    }

    private companion object {
        /** 退格时应当整体删除的函数尾串（长的排前面） */
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }
}
