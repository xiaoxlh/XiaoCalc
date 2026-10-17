package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

/**
 * 换算输入值的求值结果。
 *
 * 用密封接口而不是 `BigDecimal?`：**"还没输入"和"输入有误"是两回事**，
 * 前者应当是安静的空态，后者才该显示错误文案。
 * 旧计算器把两者都写成空串，用户分不清"我没打字"和"我打错了"。
 */
sealed interface InputValue {
    data class Ok(val value: BigDecimal) : InputValue
    data object Empty : InputValue
    data class Bad(val error: CalcError) : InputValue
}

/**
 * 单位换算页的状态。
 *
 * 输入框沿用了计算器的键盘与求值器：值可以是**表达式**（`100+20`、`2^10`），
 * 不限于单个数字。这样"先把数算出来再换算"不必来回切页。
 *
 * 页面状态刻意独立于 [CalculatorState]：换算是另一件事，
 * 复用它会让"换算页按了 C"意外清掉计算器里正在算的式子。
 */
class UnitConverterState(initialCategory: UnitCategory = UnitCategory.LENGTH) {

    var category: UnitCategory by mutableStateOf(initialCategory)
        private set

    var fromIndex: Int by mutableIntStateOf(0)
        private set

    var toIndex: Int by mutableIntStateOf(1)
        private set

    /** 输入框内容，可以是表达式 */
    var input: String by mutableStateOf("")
        private set

    val units: List<UnitDef> get() = Units.catalog.getValue(category)

    /** 起算单位。类别切换后索引可能越界，这里统一收敛，调用方不必自查。 */
    val from: UnitDef get() = units[fromIndex.coerceIn(0, units.lastIndex)]

    /** 目标单位 */
    val to: UnitDef get() = units[toIndex.coerceIn(0, units.lastIndex)]

    // ---------------------------------------------------------------- 类别

    /** 切换类别。默认把"起算/目标"设为该类别的前两个单位。 */
    fun selectCategory(value: UnitCategory) {
        if (value == category) return
        category = value
        fromIndex = 0
        toIndex = if (units.size > 1) 1 else 0
        // 输入值保留：换个类别继续换算同一个数是常见操作
    }

    fun selectFrom(index: Int) {
        if (index in units.indices) fromIndex = index
    }

    fun selectTo(index: Int) {
        if (index in units.indices) toIndex = index
    }

    /** 交换起算与目标单位。 */
    fun swap() {
        val a = fromIndex
        fromIndex = toIndex
        toIndex = a
    }

    // ---------------------------------------------------------------- 输入

    fun clear() {
        input = ""
    }

    fun backspace() {
        if (input.isEmpty()) return
        // 退格一次删掉整个函数名，而不是留下一个残缺的 "sin" 让解析器报错
        val tail = FUNCTION_TAILS.firstOrNull { input.endsWith(it) }
        input = if (tail != null) input.dropLast(tail.length) else input.dropLast(1)
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
        // 前导零：0 后面直接跟数字时替换掉那个 0（"0" → "5" 得到 "5" 而非 "05"）
        if (trailing == "0" && digit != '0') {
            input = input.dropLast(1) + digit
            return
        }
        if (trailing == "0" && digit == '0') return
        appendRaw(digit.toString())
    }

    private fun appendDot() {
        if (trailingNumber().contains('.')) return
        input = if (trailingNumber().isEmpty()) "$input." else "$input."
    }

    /**
     * 追加一段由键盘给出的文本。
     *
     * 只做两条最小防错：**不连续出现两个二元运算符**、**不以运算符开头**（负号除外）。
     * 其余交给 [CalcEngine]，让它的错误分类来告诉用户哪里不对——
     * 在输入层做完整校验等于把语法分析写两遍。
     */
    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val last = input.lastOrNull()
        if (input.isEmpty()) {
            // 负号允许出现在开头（温度经常从 -40 这种值开始）；其余二元运算符不行
            if (text.length == 1 && text[0] in BINARY_OPERATORS && text[0] != '-') return
            input = text
            return
        }
        if (text.length == 1 && text[0] in BINARY_OPERATORS && last != null && last in BINARY_OPERATORS) {
            // 连续按两个运算符＝改主意，替换掉前一个
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

    // ---------------------------------------------------------------- 求值

    /**
     * 求值当前输入。
     *
     * 空输入返回 [InputValue.Empty]，求值失败返回 [InputValue.Bad]。
     */
    fun evaluate(settings: CalcSettings): InputValue {
        if (input.isBlank()) return InputValue.Empty
        return when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> InputValue.Ok(outcome.value)
            is EvalOutcome.Failure -> InputValue.Bad(outcome.error)
        }
    }

    /**
     * 换算并格式化结果。输入为空或非法时返回 null。
     */
    fun convertedText(settings: CalcSettings): String? {
        val value = (evaluate(settings) as? InputValue.Ok)?.value ?: return null
        val converted = Units.convert(value, from, to)
        return NumberFormatter.format(converted, settings)
    }

    private companion object {
        val BINARY_OPERATORS = setOf('+', '-', '×', '÷', '^', '%')

        /** 退格时应当整体删除的函数尾串（长的排前面，避免 "n(" 抢先匹配 "ln("） */
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "mod", "√(")
    }
}
