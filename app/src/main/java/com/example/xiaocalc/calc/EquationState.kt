package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 方程页的两种输入模式 */
enum class EquationMode(val label: String) {
    /** 一元方程，如 `x^2-3x+2=0` */
    SINGLE("一元"),

    /** 二元一次方程组，两个方程用 `;` 分隔 */
    SYSTEM("二元"),
}

/**
 * 方程页的输入状态。
 *
 * 只负责**编辑输入文本**；求解由页面调用 [EquationSolver] / [LinearSystemSolver] 完成。
 * 与 [UnitConverterState] 同一个思路：结果是实时的，因此状态里不保存结果，
 * 也就不会出现"改了输入但忘了更新结果"这类不一致。
 */
class EquationState {

    var input: String by mutableStateOf("")
        private set

    var mode: EquationMode by mutableStateOf(EquationMode.SINGLE)
        private set

    /**
     * 切换模式**并清空输入**。
     *
     * 两种模式的语法不同（二元用 `;` 分隔两个等式），保留另一半的输入
     * 只会让人对着一个必然报错的式子发愣。
     */
    fun toggleMode() {
        mode = if (mode == EquationMode.SINGLE) EquationMode.SYSTEM else EquationMode.SINGLE
        input = ""
    }

    fun clear() {
        input = ""
    }

    fun backspace() {
        if (input.isEmpty()) return
        val tail = FUNCTION_TAILS.firstOrNull { input.endsWith(it) }
        input = if (tail != null) input.dropLast(tail.length) else input.dropLast(1)
    }

    /** 直接插入一段文本（符号行与 `√`/`π` 键使用） */
    fun insert(text: String) {
        if (text.isEmpty()) return
        if (text == "=" && input.count { it == '=' } >= 2) return
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
        // 前导零：0 后面直接跟数字时替换掉那个 0
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

    /**
     * 追加一段由键盘给出的文本。
     *
     * 只做一条最小防错：**不连续出现两个二元运算符**（连按两次视为改主意）。
     * 其余交给求解器——在输入层做完整校验等于把语法分析写第二遍。
     */
    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val last = input.lastOrNull()
        if (text.length == 1 && text[0] in BINARY_OPERATORS && last != null && last in BINARY_OPERATORS) {
            input = input.dropLast(1) + text
            return
        }
        input += text
    }

    /** 末尾数字串（用于小数点去重与前导零判断） */
    private fun trailingNumber(): String {
        var start = input.length
        while (start > 0 && (input[start - 1].isDigit() || input[start - 1] == '.')) start--
        return input.substring(start)
    }

    private companion object {
        // 同时含 ASCII 的 `-` 与 U+2212 的 `−`：键盘插入的是前者，
        // 而用户从别处粘进来或显示层用的是后者，两者都要能识别
        val BINARY_OPERATORS = setOf('+', '-', '−', '×', '÷', '^')

        /** 退格时应当整体删除的函数尾串（长的排前面） */
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "√(")
    }
}
