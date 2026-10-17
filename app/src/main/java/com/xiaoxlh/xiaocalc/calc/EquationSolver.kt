package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal
import java.math.BigInteger

sealed interface EquationRoot {
    val display: String

    val exact: String?

    data class Real(
        val value: BigDecimal,
        override val display: String,
        override val exact: String? = null,
    ) : EquationRoot

    data class Complex(
        val real: BigDecimal,
        val imaginary: BigDecimal,
        override val display: String,
        override val exact: String? = null,
    ) : EquationRoot
}

sealed interface EquationSolution {
    data class Solved(
        val roots: List<EquationRoot>,
        val degree: Int,
        val discriminant: BigDecimal? = null,
    ) : EquationSolution

    data class Failed(val error: EquationError) : EquationSolution
}

enum class EquationError {
    EMPTY,

    NO_EQUALS,

    MULTIPLE_EQUALS,

    NOT_POLYNOMIAL,

    DEGREE_TOO_HIGH,

    NOT_CONVERGED,

    EQUATION_COUNT,

    IDENTITY,

    NO_SOLUTION,
}

object EquationSolver {
    const val VARIABLE = "x"

    private val RADICAL_LIMIT: BigInteger = BigInteger.valueOf(1_000_000_000_000L)

    private val DEFAULT_SETTINGS = CalcSettings()

    fun solve(input: String, angleUnit: AngleUnit = AngleUnit.DEG): EquationSolution {
        val text = ExpressionEvaluator.normalize(input).trim()
        if (text.isEmpty()) return EquationSolution.Failed(EquationError.EMPTY)
        val sides = text.split('=')
        when {
            sides.size < 2 -> return EquationSolution.Failed(EquationError.NO_EQUALS)
            sides.size > 2 -> return EquationSolution.Failed(EquationError.MULTIPLE_EQUALS)
        }
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return EquationSolution.Failed(EquationError.NOT_POLYNOMIAL)
        val expression = "($lhs)-($rhs)"
        val poly = PolynomialFit.fit(expression, VARIABLE, angleUnit)
            ?: return EquationSolution.Failed(classifyUnfittable(expression, angleUnit))
        return solvePoly(poly)
    }

    private fun classifyUnfittable(expression: String, angleUnit: AngleUnit): EquationError {
        val higher = PolynomialFit.fit(
            expression,
            VARIABLE,
            angleUnit,
            maxDegree = PolynomialFit.MAX_DEGREE + DIAGNOSTIC_EXTRA_DEGREES,
        )
        return if (higher != null) EquationError.DEGREE_TOO_HIGH else EquationError.NOT_POLYNOMIAL
    }

    private const val DIAGNOSTIC_EXTRA_DEGREES = 2

    internal fun solvePolynomial(poly: Poly): EquationSolution = solvePoly(poly)

    private fun solvePoly(poly: Poly): EquationSolution {
        val coefficients = poly.coefficients
        when (poly.degree) {
            0 -> return if (coefficients[0].signum() == 0) {
                EquationSolution.Failed(EquationError.IDENTITY)
            } else {
                EquationSolution.Failed(EquationError.NO_SOLUTION)
            }
            1 -> return EquationSolution.Solved(
                roots = listOf(
                    realRoot(
                        value = Dec.div(coefficients[0].negate(), coefficients[1]),
                        exact = fraction(
                            coefficients[0].negate().toBigIntegerExactOrNull() ?: BigInteger.ZERO,
                            coefficients[1].toBigIntegerExactOrNull(),
                        ),
                    ),
                ),
                degree = 1,
            )
            2 -> return solveQuadratic(coefficients[2], coefficients[1], coefficients[0])
        }
        if (poly.degree > PolynomialFit.MAX_DEGREE) {
            return EquationSolution.Failed(EquationError.DEGREE_TOO_HIGH)
        }
        return solveHigher(poly)
    }

    private fun solveQuadratic(a: BigDecimal, b: BigDecimal, c: BigDecimal): EquationSolution {
        val d = Dec.sub(Dec.mul(b, b), Dec.mul(Dec.of(4), Dec.mul(a, c)))
        val twoA = Dec.mul(Dec.of(2), a)
        if (d.signum() == 0) {
            val root = Dec.div(b.negate(), twoA)
            return EquationSolution.Solved(
                roots = listOf(
                    realRoot(root, fraction(b.negate().toBigIntegerExactOrNull(), twoA.toBigIntegerExactOrNull())),
                ),
                degree = 2,
                discriminant = d,
            )
        }
        if (d.signum() > 0) {
            val sqrt = Dec.sqrt(d)
            val forms = radicalPair(a, b, d, imaginary = false)
            return EquationSolution.Solved(
                roots = listOf(
                    realRoot(Dec.div(Dec.add(b.negate(), sqrt), twoA), forms?.first),
                    realRoot(Dec.div(Dec.sub(b.negate(), sqrt), twoA), forms?.second),
                ),
                degree = 2,
                discriminant = d,
            )
        }
        val realPart = Dec.div(b.negate(), twoA)
        val imaginary = Dec.div(Dec.sqrt(d.negate()), twoA.abs())
        val forms = radicalPair(a, b, d.negate(), imaginary = true)
        return EquationSolution.Solved(
            roots = listOf(
                complexRoot(realPart, imaginary, forms?.first),
                complexRoot(realPart, imaginary.negate(), forms?.second),
            ),
            degree = 2,
            discriminant = d,
        )
    }

    private fun realRoot(value: BigDecimal, exact: String? = null): EquationRoot.Real =
        EquationRoot.Real(value, NumberFormatter.format(value, DEFAULT_SETTINGS), exact)

    private fun complexRoot(real: BigDecimal, imaginary: BigDecimal, exact: String?): EquationRoot.Complex {
        val sign = if (imaginary.signum() < 0) "−" else "+"
        val imaginaryText = imaginaryMagnitudeText(imaginary)
        val display = if (real.signum() == 0) {
            (if (imaginary.signum() < 0) "−" else "") + imaginaryText
        } else {
            "${NumberFormatter.format(real, DEFAULT_SETTINGS)} $sign $imaginaryText"
        }
        return EquationRoot.Complex(real, imaginary, display, exact)
    }

    private fun imaginaryMagnitudeText(imaginary: BigDecimal): String {
        val magnitude = imaginary.abs()
        val text = NumberFormatter.format(magnitude, DEFAULT_SETTINGS)
        return if (text == "1") "i" else "${text}i"
    }

    private fun radicalPair(
        a: BigDecimal,
        b: BigDecimal,
        d: BigDecimal,
        imaginary: Boolean,
    ): Pair<String, String>? {
        val dInt = d.toBigIntegerExactOrNull() ?: return null
        if (dInt.signum() < 0 || dInt > RADICAL_LIMIT) return null
        val (k, m) = SymbolicFormatter.simplifyRadical(dInt.toLong())
        val aInt = a.toBigIntegerExactOrNull() ?: return null
        val bInt = b.toBigIntegerExactOrNull() ?: return null
        var nb = bInt.negate()
        var kk = BigInteger.valueOf(k)
        var den = aInt.multiply(BigInteger.valueOf(2))
        if (den.signum() < 0) {
            nb = nb.negate()
            kk = kk.negate()
            den = den.negate()
        }
        var g = nb.gcd(kk).gcd(den)
        if (g.signum() == 0) g = BigInteger.ONE
        nb /= g
        kk /= g
        den /= g
        if (kk.signum() == 0) {
            val value = fraction(nb, den) ?: return null
            return value to value
        }
        val mBig = BigInteger.valueOf(m)
        val positive = renderTerm(nb, kk, mBig, den, plus = true, imaginary = imaginary) ?: return null
        val negative = renderTerm(nb, kk, mBig, den, plus = false, imaginary = imaginary) ?: return null
        return positive to negative
    }

    private fun renderTerm(
        nb: BigInteger,
        kk: BigInteger,
        m: BigInteger,
        den: BigInteger,
        plus: Boolean,
        imaginary: Boolean,
    ): String? {
        val signedK = if (plus) kk else kk.negate()
        if (m == BigInteger.ONE && !imaginary) {
            return fraction(nb + signedK, den)
        }
        val magnitude = signedK.abs()
        val suffix = if (imaginary) "i" else ""
        val term = when {
            magnitude == BigInteger.ZERO -> ""
            m == BigInteger.ONE -> if (magnitude == BigInteger.ONE) suffix else "$magnitude$suffix"
            magnitude == BigInteger.ONE -> "√$m$suffix"
            else -> "$magnitude√$m$suffix"
        }
        val body = when {
            term.isEmpty() -> nb.toString()
            nb == BigInteger.ZERO -> if (signedK.signum() < 0) "−$term" else term
            else -> "$nb${if (signedK.signum() < 0) "−" else "+"}$term"
        }
        return if (den == BigInteger.ONE) body else "($body)/$den"
    }

    private fun fraction(numerator: BigInteger?, denominator: BigInteger?): String? {
        if (numerator == null || denominator == null) return null
        if (denominator.signum() == 0) return null
        if (numerator.signum() == 0) return "0"
        var n = numerator
        var d = denominator
        if (d.signum() < 0) {
            n = n.negate()
            d = d.negate()
        }
        val g = n.gcd(d)
        if (g.signum() != 0) {
            n /= g
            d /= g
        }
        return if (d == BigInteger.ONE) n.toString() else "$n/$d"
    }

    private fun BigDecimal.toBigIntegerExactOrNull(): BigInteger? = try {
        toBigIntegerExact()
    } catch (_: ArithmeticException) {
        null
    }

    internal fun rationalText(numerator: BigDecimal, denominator: BigDecimal): String? =
        fraction(numerator.toBigIntegerExactOrNull(), denominator.toBigIntegerExactOrNull())

    private fun solveHigher(poly: Poly): EquationSolution {
        val (integerRoots, reduced) = extractIntegerRoots(poly)
        val collected: MutableList<EquationRoot> = integerRoots
            .map { realRoot(it, it.toBigIntegerExact().toString()) }
            .toMutableList()
        if (reduced.degree > 0) {
            when (val remainder = if (reduced.degree <= 2) solvePoly(reduced) else numericSolution(reduced)) {
                is EquationSolution.Solved -> collected += remainder.roots
                is EquationSolution.Failed -> return remainder
            }
        }
        return EquationSolution.Solved(collected, poly.degree)
    }

    private fun numericSolution(poly: Poly): EquationSolution {
        val found = RootFinder.findAll(poly)
            ?: return EquationSolution.Failed(EquationError.NOT_CONVERGED)
        val roots = found.map { z ->
            if (z.isReal) realRoot(z.re) else complexRoot(z.re, z.im, exact = null)
        }
        return EquationSolution.Solved(roots, poly.degree)
    }

    private fun extractIntegerRoots(poly: Poly): Pair<List<BigDecimal>, Poly> {
        val found = mutableListOf<BigDecimal>()
        var current = poly.normalized()
        while (current.degree >= 1) {
            if (current[0].signum() == 0) {
                found += BigDecimal.ZERO
                current = Poly(current.coefficients.drop(1)).normalized()
                continue
            }
            val candidates = integerDivisors(current[0]) ?: break
            val hit = candidates.firstOrNull { current.evaluate(it).signum() == 0 } ?: break
            found += hit
            current = divideByLinear(current, hit)
        }
        return found to current
    }

    private fun integerDivisors(constant: BigDecimal): List<BigDecimal>? {
        val value = constant.toBigIntegerExactOrNull() ?: return null
        val magnitude = value.abs()
        if (magnitude.signum() == 0) return listOf(BigDecimal.ZERO)
        if (magnitude > DIVISOR_LIMIT) return null
        val divisors = mutableListOf<BigDecimal>()
        var i = BigInteger.ONE
        while (i * i <= magnitude) {
            if (magnitude % i == BigInteger.ZERO) {
                val pair = magnitude / i
                divisors += BigDecimal(i)
                divisors += BigDecimal(i.negate())
                if (pair != i) {
                    divisors += BigDecimal(pair)
                    divisors += BigDecimal(pair.negate())
                }
            }
            i += BigInteger.ONE
        }
        return divisors
    }

    private fun divideByLinear(poly: Poly, root: BigDecimal): Poly {
        val degree = poly.degree
        val quotient = MutableList(degree) { BigDecimal.ZERO }
        var carry = poly[degree]
        for (i in degree - 1 downTo 0) {
            quotient[i] = carry
            carry = Dec.add(poly[i], Dec.mul(carry, root))
        }
        return Poly(quotient).normalized()
    }

    private val DIVISOR_LIMIT: BigInteger = BigInteger.valueOf(10_000_000L)
}
