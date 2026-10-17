package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal

data class Poly(val coefficients: List<BigDecimal>) {
    val degree: Int get() = coefficients.size - 1

    operator fun get(power: Int): BigDecimal =
        if (power in coefficients.indices) coefficients[power] else BigDecimal.ZERO

    fun isZero(): Boolean = coefficients.all { it.signum() == 0 }

    fun evaluate(x: BigDecimal): BigDecimal {
        var acc = BigDecimal.ZERO
        for (i in coefficients.indices.reversed()) {
            acc = Dec.add(Dec.mul(acc, x), coefficients[i])
        }
        return acc
    }

    fun normalized(): Poly {
        val last = coefficients.indexOfLast { it.signum() != 0 }
        return if (last < 0) Poly(listOf(BigDecimal.ZERO)) else Poly(coefficients.take(last + 1))
    }

    override fun toString(): String = buildString {
        for (i in coefficients.indices.reversed()) {
            if (coefficients[i].signum() == 0) continue
            if (isNotEmpty()) append(" + ")
            append(coefficients[i])
            if (i > 0) append("x^").append(i)
        }
    }.ifEmpty { "0" }
}

object PolynomialFit {
    const val MAX_DEGREE = 10

    private val VERIFY_POINTS = listOf("-1", "0.5", "3.7", "11.25", "-6.4")

    fun fit(
        expression: String,
        variable: String = "x",
        angleUnit: AngleUnit = AngleUnit.DEG,
        maxDegree: Int = MAX_DEGREE,
    ): Poly? {
        val eval = { x: String -> evaluateAt(expression, variable, BigDecimal(x), angleUnit) }
        for (degree in 0..maxDegree) {
            val samples = (0..degree).mapNotNull { eval(it.toString()) }
            if (samples.size != degree + 1) return null
            val candidate = fromForwardDifferences(samples) ?: return null
            val verified = VERIFY_POINTS.all { point ->
                val expected = eval(point) ?: return@all false
                val actual = candidate.evaluate(BigDecimal(point))
                closeEnough(expected, actual)
            }
            if (verified) return candidate.normalized()
        }
        return null
    }

    private fun evaluateAt(
        expression: String,
        variable: String,
        value: BigDecimal,
        angleUnit: AngleUnit,
    ): BigDecimal? = try {
        ExpressionEvaluator(expression, angleUnit, mapOf(variable to value)).evaluate()
    } catch (_: CalcException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private fun fromForwardDifferences(samples: List<BigDecimal>): Poly {
        val n = samples.size - 1
        val forward = forwardDifferences(samples)
        val result = MutableList(n + 1) { BigDecimal.ZERO }
        var falling = Poly(listOf(BigDecimal.ONE))
        var factorial = BigDecimal.ONE
        for (k in 0..n) {
            if (k > 0) factorial = factorial.multiply(BigDecimal.valueOf(k.toLong()))
            val scale = Dec.div(forward[k], factorial)
            for (i in falling.coefficients.indices) {
                result[i] = Dec.add(result[i], Dec.mul(scale, falling.coefficients[i]))
            }
            val next = MutableList(falling.coefficients.size + 1) { BigDecimal.ZERO }
            val kBig = BigDecimal.valueOf(k.toLong())
            for (i in falling.coefficients.indices) {
                next[i + 1] = Dec.add(next[i + 1], falling.coefficients[i])
                next[i] = Dec.sub(next[i], Dec.mul(falling.coefficients[i], kBig))
            }
            falling = Poly(next)
        }
        return Poly(result)
    }

    private fun forwardDifferences(samples: List<BigDecimal>): List<BigDecimal> {
        var current = samples
        val out = ArrayList<BigDecimal>(samples.size)
        while (current.isNotEmpty()) {
            out += current[0]
            current = current.zipWithNext { a, b -> Dec.sub(b, a) }
        }
        return out
    }

    private fun closeEnough(a: BigDecimal, b: BigDecimal): Boolean {
        val diff = Dec.sub(a, b).abs()
        if (diff.signum() == 0) return true
        val scale = a.abs().max(b.abs()).max(BigDecimal.ONE)
        return diff.compareTo(Dec.mul(scale, TOLERANCE)) < 0
    }

    private val TOLERANCE: BigDecimal = BigDecimal("1E-30")

    data class Linear2(val a: BigDecimal, val b: BigDecimal, val c: BigDecimal)

    fun fitLinear2(
        expression: String,
        varX: String = "x",
        varY: String = "y",
        angleUnit: AngleUnit = AngleUnit.DEG,
    ): Linear2? {
        val sample = { x: String, y: String ->
            evaluateAt2(expression, varX, varY, BigDecimal(x), BigDecimal(y), angleUnit)
        }
        val f00 = sample("0", "0") ?: return null
        val f10 = sample("1", "0") ?: return null
        val f01 = sample("0", "1") ?: return null
        val c = f00
        val a = Dec.sub(f10, c)
        val b = Dec.sub(f01, c)
        val checks = listOf(
            Triple("1", "1", Dec.add(Dec.add(a, b), c)),
            Triple("2", "3", Dec.add(Dec.add(Dec.mul(a, of(2)), Dec.mul(b, of(3))), c)),
            Triple("-1", "2", Dec.add(Dec.add(a.negate(), Dec.mul(b, of(2))), c)),
            Triple("0.5", "-1.5", Dec.add(Dec.add(Dec.mul(a, BigDecimal("0.5")), Dec.mul(b, BigDecimal("-1.5"))), c)),
        )
        for ((x, y, expected) in checks) {
            val actual = sample(x, y) ?: return null
            if (!closeEnough(expected, actual)) return null
        }
        return Linear2(a, b, c)
    }

    private fun of(v: Int): BigDecimal = BigDecimal.valueOf(v.toLong())

    private fun evaluateAt2(
        expression: String,
        varX: String,
        varY: String,
        x: BigDecimal,
        y: BigDecimal,
        angleUnit: AngleUnit,
    ): BigDecimal? = try {
        ExpressionEvaluator(expression, angleUnit, mapOf(varX to x, varY to y)).evaluate()
    } catch (_: CalcException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}
