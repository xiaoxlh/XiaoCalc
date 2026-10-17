package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal

private val FORMAT_SETTINGS = CalcSettings()

sealed interface SystemSolution {
    data class Unique(
        val x: BigDecimal,
        val y: BigDecimal,
        val xExact: String?,
        val yExact: String?,
    ) : SystemSolution {
        val xDisplay: String get() = NumberFormatter.format(x, FORMAT_SETTINGS)
        val yDisplay: String get() = NumberFormatter.format(y, FORMAT_SETTINGS)
    }

    data object Infinite : SystemSolution

    data object NoSolution : SystemSolution

    data class Failed(val error: EquationError) : SystemSolution
}

object LinearSystemSolver {
    const val VARIABLE_X = "x"
    const val VARIABLE_Y = "y"

    private val SEPARATORS = charArrayOf(';', '\n', '；')

    fun solve(input: String, angleUnit: AngleUnit = AngleUnit.DEG): SystemSolution {
        val parts = input.split(*SEPARATORS)
            .map { ExpressionEvaluator.normalize(it).trim() }
            .filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> SystemSolution.Failed(EquationError.EMPTY)
            parts.size < 2 -> SystemSolution.Failed(EquationError.EQUATION_COUNT)
            parts.size > 2 -> SystemSolution.Failed(EquationError.EQUATION_COUNT)
            else -> solve(parts[0], parts[1], angleUnit)
        }
    }

    fun solve(first: String, second: String, angleUnit: AngleUnit = AngleUnit.DEG): SystemSolution {
        val one = coefficientsOf(ExpressionEvaluator.normalize(first), angleUnit)
            ?: return SystemSolution.Failed(EquationError.NOT_POLYNOMIAL)
        val two = coefficientsOf(ExpressionEvaluator.normalize(second), angleUnit)
            ?: return SystemSolution.Failed(EquationError.NOT_POLYNOMIAL)
        val determinant = Dec.sub(Dec.mul(one.a, two.b), Dec.mul(two.a, one.b))
        val dx = Dec.sub(Dec.mul(two.c, one.b), Dec.mul(one.c, two.b))
        val dy = Dec.sub(Dec.mul(one.c, two.a), Dec.mul(two.c, one.a))
        if (determinant.signum() == 0) {
            return if (dx.signum() == 0 && dy.signum() == 0) {
                SystemSolution.Infinite
            } else {
                SystemSolution.NoSolution
            }
        }
        val x = Dec.div(dx, determinant)
        val y = Dec.div(dy, determinant)
        return SystemSolution.Unique(
            x = x,
            y = y,
            xExact = EquationSolver.rationalText(dx, determinant),
            yExact = EquationSolver.rationalText(dy, determinant),
        )
    }

    private fun coefficientsOf(equation: String, angleUnit: AngleUnit): PolynomialFit.Linear2? {
        val sides = equation.split('=')
        if (sides.size != 2) return null
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        return PolynomialFit.fitLinear2("($lhs)-($rhs)", VARIABLE_X, VARIABLE_Y, angleUnit)
    }
}
