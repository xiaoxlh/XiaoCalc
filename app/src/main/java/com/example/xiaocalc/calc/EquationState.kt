package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class EquationMode(val label: String) {
    SINGLE("一元"),

    SYSTEM("二元"),
}

class EquationState {
    var mode: EquationMode by mutableStateOf(EquationMode.SINGLE)
        private set

    var focused: Int by mutableStateOf(0)
        private set

    private var single by mutableStateOf("")
    private var first by mutableStateOf("")
    private var second by mutableStateOf("")

    val singleText: String get() = single

    val systemTexts: List<String> get() = listOf(first, second)

    val activeText: String
        get() = when {
            mode == EquationMode.SINGLE -> single
            focused == 0 -> first
            else -> second
        }

    val solverInput: String
        get() = when (mode) {
            EquationMode.SINGLE -> single
            EquationMode.SYSTEM -> if (first.isBlank() && second.isBlank()) "" else "$first;$second"
        }

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
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }
}
