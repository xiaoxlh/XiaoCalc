package com.xiaoxlh.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class CalculatorState(
    private val settingsStore: SettingsStore,
    private val historyStore: HistoryStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var settings by mutableStateOf(settingsStore.load())
        private set

    var expression by mutableStateOf("")
        private set

    var resultText by mutableStateOf("")
        private set

    var error by mutableStateOf<CalcError?>(null)
        private set

    var justEvaluated by mutableStateOf(false)
        private set

    var page by mutableStateOf(KeypadPage.MAIN)
        private set

    var revision by mutableStateOf(0)
        private set

    val history = mutableStateListOf<HistoryEntry>()

    private var lastValue: java.math.BigDecimal? = null

    init {
        history.addAll(historyStore.loadEntries().take(MAX_HISTORY))
    }

    val displayExpression: String
        get() {
            val body = NumberFormatter.superscriptExponents(expression)
            if (body.isEmpty() || !usesAngleUnit) return body
            val unit = if (settings.angleUnit == AngleUnit.DEG) "deg" else "rad"
            return "$unit $body"
        }

    private val usesAngleUnit: Boolean
        get() = TRIG_FUNCTIONS.any { expression.contains(it) }

    val canEvaluate: Boolean get() = expression.isNotBlank()

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
            else -> key.insert?.let(::insertToken)
        }
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
            trailing.any { it.isLetter() } -> "0."
            else -> "."
        }
        bump()
    }

    private fun insertToken(token: String) {
        error = null
        val first = token.firstOrNull() ?: return
        val isBinary = token.length == 1 && first in BINARY_OPERATORS
        beginInput(continueWithResult = isBinary)
        if (expression.length + token.length > CalcEngine.MAX_LENGTH) return
        val last = expression.lastOrNull()
        val hasOperand = last != null && last !in BINARY_OPERATORS && last != '('
        when {
            isBinary -> {
                if (last == null) {
                    if (token != "-") return
                    expression = token
                    bump()
                    return
                }
                if (last in BINARY_OPERATORS) expression = expression.dropLast(1)
            }
            token == "%" || token == "!" -> if (!hasOperand) return
            token == ")" -> if (!canCloseParen()) return
        }
        expression += token
        bump()
    }

    private fun backspace() {
        error = null
        if (justEvaluated) {
            justEvaluated = false
        }
        if (expression.isEmpty()) {
            resultText = ""
            return
        }
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

    fun removeHistory(entry: HistoryEntry) {
        if (history.remove(entry)) {
            historyStore.saveEntries(history.toList())
        }
    }

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
        val ATOMIC_TOKENS = listOf("sin(", "cos(", "tan(", "mod")
        val TRIG_FUNCTIONS = listOf("sin", "cos", "tan")
    }
}
