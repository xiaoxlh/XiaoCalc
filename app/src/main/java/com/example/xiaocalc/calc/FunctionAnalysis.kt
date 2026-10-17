package com.example.xiaocalc.calc

import java.math.BigDecimal

private val FORMAT_SETTINGS = CalcSettings()

enum class PointKind(val label: String) {
    ZERO("零点"),
    Y_INTERCEPT("y 截距"),
    MINIMUM("极小值"),
    MAXIMUM("极大值"),
}

data class NotablePoint(
    val kind: PointKind,
    val x: BigDecimal,
    val y: BigDecimal,
    val exactX: String? = null,
) {
    val xDisplay: String get() = NumberFormatter.format(x, FORMAT_SETTINGS)
    val yDisplay: String get() = NumberFormatter.format(y, FORMAT_SETTINGS)
}

data class Sample(val x: BigDecimal, val y: BigDecimal?)

data class Analysis(
    val samples: List<Sample>,
    val points: List<NotablePoint>,
)

object FunctionAnalysis {
    const val DEFAULT_SAMPLES = 401

    private const val MAX_BISECTION = 220

    private val BISECTION_TOLERANCE = BigDecimal("1E-45")

    private val DERIVATIVE_STEP = BigDecimal("1E-20")

    /** Exact route for polynomials, numeric fallback otherwise. */
    fun analyze(
        expression: String,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit = AngleUnit.DEG,
        sampleCount: Int = DEFAULT_SAMPLES,
    ): Analysis {
        val samples = sample(expression, xMin, xMax, angleUnit, sampleCount)
        val poly = PolynomialFit.fit(expression, VARIABLE, angleUnit)
        val points = if (poly != null && poly.degree >= 1) {
            exactPoints(poly, expression, xMin, xMax, angleUnit)
        } else {
            numericPoints(expression, samples, xMin, xMax, angleUnit)
        }
        return Analysis(samples, dropRedundantIntercept(points).sortedBy { it.x })
    }

    private fun dropRedundantIntercept(points: List<NotablePoint>): List<NotablePoint> {
        val covered = points.any { it.kind != PointKind.Y_INTERCEPT && it.x.signum() == 0 }
        if (!covered) return points
        return points.filterNot { it.kind == PointKind.Y_INTERCEPT }
    }

    const val VARIABLE = "x"

    /** 98th percentile of |y|, so a pole cannot blow up the scale */
    fun viewRange(samples: List<Sample>): Pair<BigDecimal, BigDecimal> {
        val magnitudes = samples.mapNotNull { it.y?.abs() }.sorted()
        if (magnitudes.isEmpty()) return BigDecimal("-1") to BigDecimal("1")
        val index = ((magnitudes.size - 1) * 0.98).toInt().coerceIn(0, magnitudes.size - 1)
        var half = Dec.mul(magnitudes[index], PADDING)
        if (half.signum() == 0) half = BigDecimal.ONE
        return half.negate() to half
    }

    private val PADDING: BigDecimal = BigDecimal("1.1")

    fun sample(
        expression: String,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit = AngleUnit.DEG,
        count: Int = DEFAULT_SAMPLES,
    ): List<Sample> {
        val steps = (count - 1).coerceAtLeast(1)
        val span = Dec.sub(xMax, xMin)
        return (0..steps).map { i ->
            val x = Dec.add(xMin, Dec.div(Dec.mul(span, Dec.of(i)), Dec.of(steps)))
            Sample(x, evaluate(expression, x, angleUnit))
        }
    }

    private fun evaluate(expression: String, x: BigDecimal, angleUnit: AngleUnit): BigDecimal? = try {
        val value = ExpressionEvaluator(expression, angleUnit, mapOf(VARIABLE to x)).evaluate()
        if (value.abs() > MAGNITUDE_LIMIT) null else value
    } catch (_: CalcException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private val MAGNITUDE_LIMIT: BigDecimal = BigDecimal("1E30")

    private fun exactPoints(
        poly: Poly,
        expression: String,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit,
    ): List<NotablePoint> {
        val out = mutableListOf<NotablePoint>()
        for (root in realRoots(poly)) {
            if (root.value < xMin || root.value > xMax) continue
            out += NotablePoint(
                kind = PointKind.ZERO,
                x = root.value,
                y = BigDecimal.ZERO,
                exactX = root.exact,
            )
        }
        if (xMin.signum() <= 0 && xMax.signum() >= 0) {
            out += NotablePoint(PointKind.Y_INTERCEPT, BigDecimal.ZERO, poly.evaluate(BigDecimal.ZERO))
        }
        val derivative = derivative(poly)
        if (derivative.degree >= 1) {
            val second = derivative(derivative)
            for (stationary in realRoots(derivative)) {
                val x = stationary.value
                if (x < xMin || x > xMax) continue
                val curvature = second.evaluate(x)
                val kind = when {
                    curvature.signum() < 0 -> PointKind.MAXIMUM
                    curvature.signum() > 0 -> PointKind.MINIMUM
                    else -> continue
                }
                out += NotablePoint(kind, x, poly.evaluate(x), exactX = stationary.exact)
            }
        }
        return out
    }

    private fun realRoots(poly: Poly): List<EquationRoot.Real> =
        when (val solution = EquationSolver.solvePolynomial(poly)) {
            is EquationSolution.Solved -> solution.roots
                .filterIsInstance<EquationRoot.Real>()
                .sortedBy { it.value }
            is EquationSolution.Failed -> emptyList()
        }

    private fun derivative(poly: Poly): Poly {
        if (poly.degree < 1) return Poly(listOf(BigDecimal.ZERO))
        return Poly((1..poly.degree).map { i -> Dec.mul(Dec.of(i), poly[i]) })
    }

    private fun numericPoints(
        expression: String,
        samples: List<Sample>,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit,
    ): List<NotablePoint> {
        val out = mutableListOf<NotablePoint>()
        val usable = samples.filter { it.y != null }
        for (i in 0 until usable.size - 1) {
            val a = usable[i]
            val b = usable[i + 1]
            val ya = a.y!!
            val yb = b.y!!
            if (ya.signum() == 0) {
                out += NotablePoint(PointKind.ZERO, a.x, BigDecimal.ZERO)
                continue
            }
            if (ya.signum() * yb.signum() < 0) {
                val root = bisect(expression, a.x, b.x, angleUnit) ?: continue
                out += NotablePoint(PointKind.ZERO, root, BigDecimal.ZERO)
            }
        }
        for (i in 1 until usable.size - 1) {
            val prev = usable[i - 1].y!!
            val here = usable[i].y!!
            val next = usable[i + 1].y!!
            val isPeak = here > prev && here > next
            val isValley = here < prev && here < next
            if (!isPeak && !isValley) continue
            val x = refineStationary(expression, usable[i - 1].x, usable[i + 1].x, angleUnit) ?: continue
            val y = evaluate(expression, x, angleUnit) ?: continue
            out += NotablePoint(
                if (isPeak) PointKind.MAXIMUM else PointKind.MINIMUM,
                x,
                y,
            )
        }
        if (xMin.signum() <= 0 && xMax.signum() >= 0) {
            evaluate(expression, BigDecimal.ZERO, angleUnit)?.let {
                out += NotablePoint(PointKind.Y_INTERCEPT, BigDecimal.ZERO, it)
            }
        }
        return dedupe(out)
    }

    private fun bisect(
        expression: String,
        a: BigDecimal,
        b: BigDecimal,
        angleUnit: AngleUnit,
    ): BigDecimal? {
        var lo = a
        var hi = b
        val flo = evaluate(expression, lo, angleUnit) ?: return null
        val fhi = evaluate(expression, hi, angleUnit) ?: return null
        if (flo.signum() == 0) return lo
        if (fhi.signum() == 0) return hi
        if (flo.signum() * fhi.signum() >= 0) return null
        repeat(MAX_BISECTION) {
            val mid = Dec.div(Dec.add(lo, hi), Dec.of(2))
            val fm = evaluate(expression, mid, angleUnit) ?: return null
            if (fm.signum() == 0) return mid
            if (flo.signum() * fm.signum() < 0) hi = mid else lo = mid
            if (Dec.sub(hi, lo).abs() < Dec.mul(Dec.add(lo.abs(), BigDecimal.ONE), BISECTION_TOLERANCE)) {
                return verifyRoot(expression, Dec.div(Dec.add(lo, hi), Dec.of(2)), flo, fhi, angleUnit)
            }
        }
        return verifyRoot(expression, Dec.div(Dec.add(lo, hi), Dec.of(2)), flo, fhi, angleUnit)
    }

    private fun verifyRoot(
        expression: String,
        candidate: BigDecimal,
        flo: BigDecimal,
        fhi: BigDecimal,
        angleUnit: AngleUnit,
    ): BigDecimal? {
        val value = evaluate(expression, candidate, angleUnit) ?: return null
        return if (value.abs() < minOf(flo.abs(), fhi.abs())) candidate else null
    }

    private fun refineStationary(
        expression: String,
        a: BigDecimal,
        b: BigDecimal,
        angleUnit: AngleUnit,
    ): BigDecimal? {
        var lo = a
        var hi = b
        var flo = slope(expression, lo, angleUnit) ?: return null
        if (flo.signum() == 0) return lo
        repeat(MAX_BISECTION) {
            val mid = Dec.div(Dec.add(lo, hi), Dec.of(2))
            val fm = slope(expression, mid, angleUnit) ?: return mid
            if (fm.signum() == 0) return mid
            if (flo.signum() * fm.signum() < 0) hi = mid else lo = mid
            if (Dec.sub(hi, lo).abs() < Dec.mul(Dec.add(lo.abs(), BigDecimal.ONE), BISECTION_TOLERANCE)) {
                return Dec.div(Dec.add(lo, hi), Dec.of(2))
            }
        }
        return Dec.div(Dec.add(lo, hi), Dec.of(2))
    }

    private fun slope(expression: String, x: BigDecimal, angleUnit: AngleUnit): BigDecimal? {
        val h = Dec.mul(Dec.add(x.abs(), BigDecimal.ONE), DERIVATIVE_STEP)
        val forward = evaluate(expression, Dec.add(x, h), angleUnit) ?: return null
        val backward = evaluate(expression, Dec.sub(x, h), angleUnit) ?: return null
        return Dec.div(Dec.sub(forward, backward), Dec.mul(Dec.of(2), h))
    }

    private fun dedupe(points: List<NotablePoint>): List<NotablePoint> {
        val out = mutableListOf<NotablePoint>()
        for (point in points) {
            val duplicate = out.any {
                it.kind == point.kind &&
                    Dec.sub(it.x, point.x).abs() < Dec.mul(it.x.abs().add(BigDecimal.ONE), BigDecimal("1E-12"))
            }
            if (!duplicate) out += point
        }
        return out
    }
}
