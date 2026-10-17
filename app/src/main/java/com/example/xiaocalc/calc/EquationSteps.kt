package com.example.xiaocalc.calc

import java.math.BigDecimal

object EquationSteps {
    private val FORMAT_SETTINGS = CalcSettings(groupDigits = false)

    /** Detailed steps only for degree 1 and 2; anything higher returns an empty list. */
    fun build(input: String, angleUnit: AngleUnit = AngleUnit.DEG): List<String> {
        val text = ExpressionEvaluator.normalize(input).trim()
        if (text.isEmpty()) return emptyList()
        val sides = text.split('=')
        if (sides.size != 2) return emptyList()
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return emptyList()
        val poly = PolynomialFit.fit("($lhs)-($rhs)", "x", angleUnit)
            ?.normalized()
            ?: return emptyList()
        return when (poly.degree) {
            1 -> linear(poly)
            2 -> quadratic(poly)
            else -> emptyList()
        }
    }

    private fun linear(poly: Poly): List<String> {
        val a = poly[1]
        val b = poly[0]
        if (a.signum() == 0) return emptyList()
        val x = Dec.sub(BigDecimal.ZERO, Dec.div(b, a))
        return listOf(
            "移项：${fmt(a)}x = ${fmt(Dec.sub(BigDecimal.ZERO, b))}",
            "两边同除 ${fmt(a)}",
            "x = ${fmt(x)}",
        )
    }

    private fun quadratic(poly: Poly): List<String> {
        val a = poly[2]
        val b = poly[1]
        val c = poly[0]
        if (a.signum() == 0) return emptyList()
        val bSquared = Dec.mul(b, b)
        val fourAc = Dec.mul(Dec.mul(BigDecimal.valueOf(4L), a), c)
        val delta = Dec.sub(bSquared, fourAc)
        val conclusion = when (delta.signum()) {
            1 -> "Δ > 0，有两个不相等的实根"
            0 -> "Δ = 0，有两个相等的实根"
            else -> "Δ < 0，没有实根，得到一对共轭复根"
        }
        return listOf(
            "a = ${fmt(a)}，b = ${fmt(b)}，c = ${fmt(c)}",
            "Δ = b² − 4ac = ${fmt(delta)}",
            conclusion,
            "x = (−b ± √Δ) ÷ 2a",
        )
    }

    private fun fmt(value: BigDecimal): String = NumberFormatter.format(value, FORMAT_SETTINGS)
}
