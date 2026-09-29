package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 计算器状态机。
 *
 * 与旧版 `CalculatorController` 的差别：
 *  - 输入规则被显式建模（前导 0、重复小数点、运算符替换、后缀运算符前置校验、
 *    括号配对、函数名整体退格），不再是 `input()` 里一堆 `if/return` 的隐式行为；
 *  - 失败以 [CalcError] 表达，不再把 "错误" / "NaN" 塞进结果串污染后续运算；
 *  - 求值后用**数值**续算，而不是把显示串（含千分位逗号、上标指数）反解析回去；
 *  - 历史结构化并持久化；
 *  - 依赖注入 [SettingsStore] / [HistoryStore]，核心逻辑可在 JVM 上直接单测。
 */
class CalculatorState(
    private val settingsStore: SettingsStore,
    private val historyStore: HistoryStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    var settings by mutableStateOf(settingsStore.load())
        private set

    /** 原始表达式（纯文本，可直接交给解析器） */
    var expression by mutableStateOf("")
        private set

    /** 展示用结果文本（已应用上标/千分位） */
    var resultText by mutableStateOf("")
        private set

    var error by mutableStateOf<CalcError?>(null)
        private set

    /** 上一次操作是 `=` */
    var justEvaluated by mutableStateOf(false)
        private set

    var page by mutableStateOf(KeypadPage.MAIN)
        private set

    /** 表达式/结果的修订号，供显示区做变化动画 */
    var revision by mutableStateOf(0)
        private set

    /** 历史记录，最新在前 */
    val history = mutableStateListOf<HistoryEntry>()

    /** 最近一次成功求值的数值，用于"求值后直接按运算符"续算 */
    private var lastValue: Double? = null

    init {
        history.addAll(historyStore.loadEntries().take(MAX_HISTORY))
    }

    /** 显示用表达式：`^` 转上标 */
    val displayExpression: String
        get() = NumberFormatter.superscriptExponents(expression)

    val canEvaluate: Boolean get() = expression.isNotBlank()

    // ------------------------------------------------------------------ 输入

    fun onKey(key: CalcKey) {
        when (key) {
            is CalcKey.Digit -> appendDigit(key.value)
            CalcKey.Dot -> appendDot()
            CalcKey.Clear -> clearAll()
            CalcKey.Backspace -> backspace()
            CalcKey.Equals -> evaluate()
            CalcKey.AngleUnit -> toggleAngleUnit()
            CalcKey.PageMain -> page = KeypadPage.MAIN
            CalcKey.PageFunctions -> page = KeypadPage.FUNCTIONS
            // 注：这里刻意**不**用 key.kind 判断"是不是运算符"。
            // KeyKind 只决定配色，而 `%` `!` 视觉上属于 OPERATOR 却是后缀运算符，
            // 早期版本因此在 `5+` 后误把 `%` 当成二元运算符替换掉 `+`。
            else -> key.insert?.let(::insertToken)
        }

        // 函数页插入完一个记号后自动回主键盘：
        // 手表上"按 sin → 紧接着输入参数"是绝对主流路径，多按一次「123」很打断节奏。
        // 角度单位切换、翻页、清空/退格、= 这几类保持留在当前页。
        if (page == KeypadPage.FUNCTIONS && returnsToMainAfterUse(key)) {
            page = KeypadPage.MAIN
        }
    }

    private fun returnsToMainAfterUse(key: CalcKey): Boolean = when (key) {
        CalcKey.AngleUnit, CalcKey.PageMain, CalcKey.PageFunctions,
        CalcKey.Equals, CalcKey.Clear, CalcKey.Backspace,
        -> false

        else -> true
    }

    private fun appendDigit(digit: Char) {
        error = null
        beginInput(continueWithResult = false)
        if (expression.length >= CalcEngine.MAX_LENGTH) return
        // 前导 0 被替换而非叠加：按 0 再按 5 应得到 "5"，而不是 "05"
        expression = if (trailingNumber() == "0") {
            expression.dropLast(1) + digit
        } else {
            expression + digit
        }
        bump()
    }

    private fun appendDot() {
        error = null
        beginInput(continueWithResult = false)
        if (expression.length >= CalcEngine.MAX_LENGTH) return
        val trailing = trailingNumber()
        if (trailing.contains('.')) return
        expression += when {
            trailing.isEmpty() -> "0."
            trailing.any { it.isLetter() } -> "0." // 刚输入 mod 等词时，补一个 0 保证合法
            else -> "."
        }
        bump()
    }

    private fun insertToken(token: String) {
        error = null
        val first = token.firstOrNull() ?: return
        val isBinary = token.length == 1 && first in BINARY_OPERATORS
        // `x²` 是"作用于当前数值"的后缀键，语义上等价于接着算
        val isSquare = token == SQUARE_TOKEN
        beginInput(continueWithResult = isBinary || isSquare)
        if (expression.length + token.length > CalcEngine.MAX_LENGTH) return

        val last = expression.lastOrNull()
        val hasOperand = last != null && last !in BINARY_OPERATORS && last != '('

        when {
            isBinary -> {
                if (last == null) {
                    // 只允许以负号开头
                    if (token != "-") return
                    expression = token
                    bump()
                    return
                }
                // 连续按运算符：替换掉上一个（旧版是静默忽略，手感很差）
                if (last in BINARY_OPERATORS) expression = expression.dropLast(1)
            }

            // 后缀运算符：前面必须有操作数
            token == "%" || token == "!" -> if (!hasOperand) return

            isSquare -> if (!hasOperand) return

            token == ")" -> if (!canCloseParen()) return
        }

        expression += token
        bump()
    }

    private fun backspace() {
        error = null
        if (justEvaluated) {
            // 结果态下退格：回到表达式继续编辑，而不是把结果删成空
            justEvaluated = false
        }
        if (expression.isEmpty()) {
            resultText = ""
            return
        }
        // 函数名整体退格，避免退成 "si" / "s" 这种非法中间态
        for (token in ATOMIC_TOKENS) {
            if (expression.endsWith(token)) {
                expression = expression.dropLast(token.length)
                bump()
                return
            }
        }
        expression = expression.dropLast(1)
        bump()
    }

    private fun clearAll() {
        expression = ""
        resultText = ""
        error = null
        justEvaluated = false
        lastValue = null
        bump()
    }

    private fun evaluate() {
        if (expression.isBlank()) return
        when (val outcome = CalcEngine.evaluate(expression, settings)) {
            is EvalOutcome.Success -> {
                val display = NumberFormatter.superscriptExponents(outcome.display)
                resultText = display
                lastValue = outcome.value
                error = null
                justEvaluated = true
                pushHistory(expression, display)
            }
            is EvalOutcome.Failure -> {
                error = outcome.error
                resultText = ""
                justEvaluated = false
            }
        }
        bump()
    }

    /**
     * 处理"刚按过 `=`"这一状态。
     *
     * @param continueWithResult true 表示接着按的是运算符 → 以数值结果续算；
     *                           false 表示开始一段全新输入。
     */
    private fun beginInput(continueWithResult: Boolean) {
        if (!justEvaluated) return
        justEvaluated = false
        resultText = ""
        if (continueWithResult) {
            expression = lastValue?.let(NumberFormatter::plainForReuse).orEmpty()
        } else {
            expression = ""
            lastValue = null
        }
    }

    /** 末尾数字串（用于小数点去重与前导 0 判断） */
    private fun trailingNumber(): String {
        var start = 0
        for (i in expression.indices) {
            val c = expression[i]
            if (c in BINARY_OPERATORS || c == '(' || c == ')') start = i + 1
        }
        return expression.substring(start)
    }

    private fun canCloseParen(): Boolean {
        var depth = 0
        for (c in expression) {
            when (c) {
                '(' -> depth++
                ')' -> depth--
            }
        }
        if (depth <= 0) return false
        val last = expression.lastOrNull() ?: return false
        return last != '(' && last !in BINARY_OPERATORS
    }

    private fun bump() {
        revision++
    }

    // ------------------------------------------------------------------ 设置

    fun updateSettings(transform: (CalcSettings) -> CalcSettings) {
        val updated = transform(settings)
        if (updated == settings) return
        settings = updated
        settingsStore.save(updated)
    }

    fun toggleAngleUnit() {
        updateSettings {
            it.copy(
                angleUnit = if (it.angleUnit == AngleUnit.DEG) AngleUnit.RAD else AngleUnit.DEG,
            )
        }
    }

    // ------------------------------------------------------------------ 历史

    private fun pushHistory(expression: String, result: String) {
        val previous = history.firstOrNull()
        if (previous != null && previous.expression == expression && previous.result == result) return
        history.add(0, HistoryEntry(expression, result, now()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        historyStore.saveEntries(history.toList())
    }

    fun clearHistory() {
        history.clear()
        historyStore.saveEntries(emptyList())
    }

    /** 从历史回填表达式，便于继续编辑 */
    fun reuse(entry: HistoryEntry) {
        expression = NumberFormatter.desuperscript(entry.expression)
        resultText = ""
        error = null
        justEvaluated = false
        lastValue = null
        bump()
    }

    private companion object {
        const val MAX_HISTORY = 50
        const val BINARY_OPERATORS = "+-×÷^"
        const val SQUARE_TOKEN = "^2"
        val ATOMIC_TOKENS = listOf("sin(", "cos(", "tan(", "mod")
    }
}
