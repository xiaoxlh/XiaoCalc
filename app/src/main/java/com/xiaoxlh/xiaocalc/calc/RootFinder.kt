package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal

data class Cx(val re: BigDecimal, val im: BigDecimal) {
    val isReal: Boolean get() = im.signum() == 0

    val abs2: BigDecimal get() = Dec.add(Dec.mul(re, re), Dec.mul(im, im))

    val abs: BigDecimal get() = Dec.sqrt(abs2)

    operator fun plus(other: Cx): Cx = Cx(Dec.add(re, other.re), Dec.add(im, other.im))

    operator fun minus(other: Cx): Cx = Cx(Dec.sub(re, other.re), Dec.sub(im, other.im))

    operator fun times(other: Cx): Cx = Cx(
        Dec.sub(Dec.mul(re, other.re), Dec.mul(im, other.im)),
        Dec.add(Dec.mul(re, other.im), Dec.mul(im, other.re)),
    )

    operator fun div(other: Cx): Cx {
        val denominator = other.abs2
        if (denominator.signum() == 0) throw ArithmeticException("复数除以零")
        return Cx(
            Dec.div(Dec.add(Dec.mul(re, other.re), Dec.mul(im, other.im)), denominator),
            Dec.div(Dec.sub(Dec.mul(im, other.re), Dec.mul(re, other.im)), denominator),
        )
    }

    fun scale(factor: BigDecimal): Cx = Cx(Dec.mul(re, factor), Dec.mul(im, factor))

    companion object {
        val ZERO = Cx(BigDecimal.ZERO, BigDecimal.ZERO)
        val ONE = Cx(BigDecimal.ONE, BigDecimal.ZERO)
        val I = Cx(BigDecimal.ZERO, BigDecimal.ONE)
    }
}

fun Poly.evaluateComplex(x: Cx): Cx {
    var acc = Cx.ZERO
    for (i in coefficients.indices.reversed()) {
        acc = acc * x + Cx(coefficients[i], BigDecimal.ZERO)
    }
    return acc
}

object RootFinder {
    private const val MAX_ITERATIONS = 500

    private val CONVERGED = BigDecimal("1E-40")

    private val SEED = Cx(BigDecimal("0.4"), BigDecimal("0.9"))

    fun findAll(poly: Poly): List<Cx>? {
        val monic = toMonic(poly) ?: return null
        val degree = monic.degree
        if (degree < 1) return emptyList()
        if (degree == 1) {
            return listOf(Cx(Dec.div(monic[0].negate(), monic[1]), BigDecimal.ZERO))
        }
        val n = degree
        val roots = Array(n) { index -> SEED.pow(index) }
        repeat(MAX_ITERATIONS) {
            var maxShift = BigDecimal.ZERO
            for (k in 0 until n) {
                var denominator = Cx.ONE
                for (j in 0 until n) {
                    if (j != k) denominator *= (roots[k] - roots[j])
                }
                if (denominator.abs2.signum() == 0) return null
                val value = monic.evaluateComplex(roots[k])
                val shift = value / denominator
                roots[k] -= shift
                val magnitude = shift.abs
                if (magnitude > maxShift) maxShift = magnitude
            }
            if (maxShift < CONVERGED) {
                return polish(monic, roots.toList())
            }
        }
        return null
    }

    private fun Cx.pow(exponent: Int): Cx {
        var result = Cx.ONE
        repeat(exponent) { result *= this }
        return result
    }

    private fun toMonic(poly: Poly): Poly? {
        val normalized = poly.normalized()
        val leading = normalized.coefficients.lastOrNull() ?: return null
        if (leading.signum() == 0) return null
        if (leading.compareTo(BigDecimal.ONE) == 0) return normalized
        return Poly(normalized.coefficients.map { Dec.div(it, leading) }).normalized()
    }

    private fun polish(poly: Poly, roots: List<Cx>): List<Cx>? {
        val derivative = derivative(poly)
        val polished = roots.map { root ->
            var z = root
            repeat(3) {
                val f = poly.evaluateComplex(z)
                val df = derivative.evaluateComplex(z)
                if (df.abs2.signum() == 0) return@repeat
                z -= (f / df)
            }
            z
        }
        for (z in polished) {
            val residual = poly.evaluateComplex(z).abs
            val scale = BigDecimal.ONE.max(z.abs)
            if (residual > Dec.mul(scale, BigDecimal("1E-30"))) return null
        }
        return mergeConjugatesAndSort(polished)
    }

    private fun derivative(poly: Poly): Poly {
        if (poly.degree < 1) return Poly(listOf(BigDecimal.ZERO))
        return Poly(
            (1..poly.degree).map { i -> Dec.mul(BigDecimal.valueOf(i.toLong()), poly[i]) },
        )
    }

    private fun mergeConjugatesAndSort(roots: List<Cx>): List<Cx> {
        val cleaned = roots.map { z ->
            val scale = BigDecimal.ONE.max(z.abs)
            if (z.im.abs() < Dec.mul(scale, BigDecimal("1E-30"))) {
                Cx(z.re, BigDecimal.ZERO)
            } else {
                z
            }
        }
        val unique = mutableListOf<Cx>()
        for (z in cleaned) {
            val duplicate = unique.any { existing ->
                val scale = BigDecimal.ONE.max(z.abs)
                (existing - z).abs < Dec.mul(scale, BigDecimal("1E-25"))
            }
            if (!duplicate) unique += z
        }
        return unique.sortedWith(
            compareBy({ it.re }, { it.im }),
        )
    }
}
