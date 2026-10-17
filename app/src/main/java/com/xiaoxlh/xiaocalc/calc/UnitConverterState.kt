package com.xiaoxlh.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal

sealed interface InputValue {
    data class Ok(val value: BigDecimal) : InputValue
    data object Empty : InputValue
    data class Bad(val error: CalcError) : InputValue
}

class UnitConverterState(initialCategory: UnitCategory = UnitCategory.LENGTH) {
    var category: UnitCategory by mutableStateOf(initialCategory)
        private set

    var fromIndex: Int by mutableIntStateOf(0)
        private set

    var toIndex: Int by mutableIntStateOf(1)
        private set

    var input: String by mutableStateOf("")
        private set

    val units: List<UnitDef> get() = Units.catalog.getValue(category)

    val from: UnitDef get() = units[fromIndex.coerceIn(0, units.lastIndex)]

    val to: UnitDef get() = units[toIndex.coerceIn(0, units.lastIndex)]

    fun selectCategory(value: UnitCategory) {
        if (value == category) return
        category = value
        fromIndex = 0
        toIndex = if (units.size > 1) 1 else 0
    }

    fun selectFrom(index: Int) {
        if (index in units.indices) fromIndex = index
    }

    fun selectTo(index: Int) {
        if (index in units.indices) toIndex = index
    }

    fun swap() {
        val a = fromIndex
        fromIndex = toIndex
        toIndex = a
    }

    fun clear() {
        input = ""
    }

    fun backspace() {
        if (input.isEmpty()) return
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

    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val last = input.lastOrNull()
        if (input.isEmpty()) {
            if (text.length == 1 && text[0] in BINARY_OPERATORS && text[0] != '-') return
            input = text
            return
        }
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

    fun evaluate(settings: CalcSettings): InputValue {
        if (input.isBlank()) return InputValue.Empty
        return when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> InputValue.Ok(outcome.value)
            is EvalOutcome.Failure -> InputValue.Bad(outcome.error)
        }
    }

    fun convertedText(settings: CalcSettings): String? {
        val value = (evaluate(settings) as? InputValue.Ok)?.value ?: return null
        val converted = Units.convert(value, from, to)
        return NumberFormatter.format(converted, settings)
    }

    private companion object {
        val BINARY_OPERATORS = setOf('+', '-', '×', '÷', '^', '%')
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "mod", "√(")
    }
}
