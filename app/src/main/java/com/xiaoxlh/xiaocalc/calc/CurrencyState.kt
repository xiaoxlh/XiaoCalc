package com.xiaoxlh.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.time.LocalDate

sealed interface RateStatus {
    data object Idle : RateStatus

    data object Loading : RateStatus

    data class Updated(val date: String) : RateStatus

    data class Failed(val reason: String) : RateStatus

    data object NotAuthorized : RateStatus
}

class CurrencyState(
    private val store: RateStore,
    initialFrom: Currency = Currencies.require("CNY"),
    initialTo: Currency = Currencies.require("USD"),
) {
    var from: Currency by mutableStateOf(initialFrom)
        private set

    var to: Currency by mutableStateOf(initialTo)
        private set

    var input: String by mutableStateOf("")
        private set

    var table: RateTable? by mutableStateOf(store.loadRates())
        private set

    var status: RateStatus by mutableStateOf(RateStatus.Idle)
        private set

    val cachedDate: String? get() = table?.date

    fun clear() {
        input = ""
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

    fun selectFrom(currency: Currency) {
        if (currency == to) to = from
        from = currency
    }

    fun selectTo(currency: Currency) {
        if (currency == from) from = to
        to = currency
    }

    fun swap() {
        val previous = from
        from = to
        to = previous
    }

    fun beginRefresh(allowNetwork: Boolean): Boolean {
        if (!allowNetwork) {
            status = RateStatus.NotAuthorized
            return false
        }
        status = RateStatus.Loading
        return true
    }

    fun applyResult(fetched: RateTable?, failureReason: String = "没取到汇率") {
        if (fetched == null) {
            status = RateStatus.Failed(failureReason)
            return
        }
        table = fetched
        store.saveRates(fetched)
        status = RateStatus.Updated(fetched.date)
    }

    fun onAuthorizationRevoked() {
        if (status is RateStatus.Updated || status is RateStatus.Loading) {
            status = RateStatus.NotAuthorized
        }
    }

    fun needsAutoRenew(today: LocalDate): Boolean {
        val cached = cachedDate ?: return true
        val date = runCatching { LocalDate.parse(cached) }.getOrNull() ?: return true
        return date.isBefore(today)
    }

    fun convertedValue(settings: CalcSettings): BigDecimal? {
        if (input.isBlank()) return null
        val amount = when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> outcome.value
            is EvalOutcome.Failure -> return null
        }
        return table?.convert(amount, from.code, to.code)
    }

    fun convertedText(settings: CalcSettings): String? =
        convertedValue(settings)?.let { NumberFormatter.format(it, settings) }

    fun inputError(settings: CalcSettings): String? {
        if (input.isBlank()) return null
        return when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> null
            is EvalOutcome.Failure -> "算式有误"
        }
    }

    private companion object {
        val BINARY_OPERATORS = setOf('+', '-', '−', '×', '÷', '^')
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }
}
