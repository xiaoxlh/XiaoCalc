package com.example.xiaocalc.calc

import java.math.BigDecimal

sealed interface EvalOutcome {
    data class Success(val value: BigDecimal, val display: String) : EvalOutcome
    data class Failure(val error: CalcError) : EvalOutcome
}

object CalcEngine {
    const val MAX_LENGTH = 64

    fun evaluate(expression: String, settings: CalcSettings): EvalOutcome {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return EvalOutcome.Failure(CalcError.EMPTY)
        if (trimmed.length > MAX_LENGTH) return EvalOutcome.Failure(CalcError.TOO_LONG)
        val balanced = balanceParens(trimmed)
        return try {
            val value = ExpressionEvaluator(balanced, settings.angleUnit).evaluate()
            EvalOutcome.Success(value, NumberFormatter.format(value, settings, balanced))
        } catch (e: CalcException) {
            EvalOutcome.Failure(e.error)
        } catch (_: ArithmeticException) {
            EvalOutcome.Failure(CalcError.NOT_FINITE)
        } catch (_: StackOverflowError) {
            EvalOutcome.Failure(CalcError.TOO_LONG)
        }
    }

    fun balanceParens(text: String): String {
        var depth = 0
        for (c in text) {
            when (c) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
            }
        }
        if (depth <= 0) return text
        return text + ")".repeat(depth)
    }
}
