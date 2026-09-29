package com.example.xiaocalc.calc

/** 求值结果。失败不再是"魔法字符串"，而是可判定的枚举。 */
sealed interface EvalOutcome {
    data class Success(val value: Double, val display: String) : EvalOutcome
    data class Failure(val error: CalcError) : EvalOutcome
}

/**
 * 计算入口：把"用户看到的表达式"变成"可显示的结果"。
 *
 * 纯 Kotlin（无 Android 依赖），因此核心计算逻辑可在 JVM 上完整回归。
 */
object CalcEngine {

    /** 表达式长度上限：手表上显示区有限，也顺带挡住病态输入 */
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
        } catch (_: StackOverflowError) {
            // 理论上不会发生（有深度上限），但共享给 UI 的入口不能崩
            EvalOutcome.Failure(CalcError.TOO_LONG)
        }
    }

    /**
     * 自动补全右侧缺失的括号。
     *
     * 手表上 `(` / `)` 在函数页，最简形态的输入是 `sin30`、`(2+3×4`，
     * 因为缺一个右括号就整条报错，用户体验很差——这里按计算器惯例自动闭合并呈现。
     */
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
