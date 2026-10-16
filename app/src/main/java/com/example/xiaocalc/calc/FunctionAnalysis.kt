package com.example.xiaocalc.calc

import java.math.BigDecimal

/** 显示用设置。放在文件级是因为 [NotablePoint] 是顶层类型，看不到 object 内部成员。 */
private val FORMAT_SETTINGS = CalcSettings()

/** 值得标注的点 */
enum class PointKind(val label: String) {
    ZERO("零点"),
    Y_INTERCEPT("y 截距"),
    MINIMUM("极小值"),
    MAXIMUM("极大值"),
}

/**
 * 一个值得标注的点。
 *
 * @param exactX 精确形式（如 `√2`）；无法精确表达时为 null
 */
data class NotablePoint(
    val kind: PointKind,
    val x: BigDecimal,
    val y: BigDecimal,
    val exactX: String? = null,
) {
    val xDisplay: String get() = NumberFormatter.format(x, FORMAT_SETTINGS)
    val yDisplay: String get() = NumberFormatter.format(y, FORMAT_SETTINGS)
}

/** 采样点；[y] 为 null 表示该处无定义（如 `1÷x` 在 x=0） */
data class Sample(val x: BigDecimal, val y: BigDecimal?)

data class Analysis(
    val samples: List<Sample>,
    val points: List<NotablePoint>,
)

/**
 * 函数分析：采样、零点、极值、截距。
 *
 * ## 两条路线，精确优先
 *
 * 若解析式是**多项式**，零点与极值都走 [EquationSolver] 的精确求解：
 * 于是 `x^2-2` 的零点显示成 `√2`，而不是 `1.414213562373095`。
 * 极值则对它求导后再解 `p'(x)=0`——这正好是 [EquationSolver] 已经做得很好的事。
 *
 * 否则（`sin(x)` 这类）退到数值路线：扫描采样点找符号变化，再二分收敛。
 * 二分在 50 位工作精度上能收敛到远超对外显示的 16 位。
 *
 * ## 为什么不用数値微分求多项式极值
 *
 * 差分会把有效位砍掉一半（h=1e-20 时只剩约 30 位），而且得到的根还是近似的，
 * 白白丢掉了"多项式求导是精确的"这一优势。
 */
object FunctionAnalysis {

    /** 默认采样点数。400 段足以让 480px 的屏幕上看不出折线感 */
    const val DEFAULT_SAMPLES = 401

    /** 二分迭代上限。50 位精度下 ~170 次即可把区间压到 1e-50 */
    private const val MAX_BISECTION = 220

    /** 收敛判据：区间宽度相对于 x 的量级*/
    private val BISECTION_TOLERANCE = BigDecimal("1E-45")

    /** 数值微分的步长（相对量级） */
    private val DERIVATIVE_STEP = BigDecimal("1E-20")

    /**
     * 分析 [expression] 在 `[xMin, xMax]` 上的图像。
     *
     * @param expression 关于 `x` 的解析式，两侧不需要写 `y=`
     */
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

    /**
     * 丢掉已被覆盖的 y 截距。
     *
     * 截距恒在 x=0；若该处已经是零点或极值（`x^2-2` 就是极小值又恰好是截距），
     * 再列一遍只是同一个点上贴两个标签，反而让人以为有两个不同的点。
     */
    private fun dropRedundantIntercept(points: List<NotablePoint>): List<NotablePoint> {
        val covered = points.any { it.kind != PointKind.Y_INTERCEPT && it.x.signum() == 0 }
        if (!covered) return points
        return points.filterNot { it.kind == PointKind.Y_INTERCEPT }
    }

    const val VARIABLE = "x"

    // ------------------------------------------------------------------ 采样

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

    /** 超出这个量级的取值不再参与绘图，避免纵轴被一两个爆炸点拉爆 */
    private val MAGNITUDE_LIMIT: BigDecimal = BigDecimal("1E30")

    // -------------------------------------------------------------- 精确路线

    private fun exactPoints(
        poly: Poly,
        expression: String,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit,
    ): List<NotablePoint> {
        val out = mutableListOf<NotablePoint>()

        // 零点：直接复用方程求解器的精确根
        for (root in realRoots(poly)) {
            if (root.value < xMin || root.value > xMax) continue
            out += NotablePoint(
                kind = PointKind.ZERO,
                x = root.value,
                y = poly.evaluate(root.value),
                exactX = root.exact,
            )
        }

        // y 截距：仅当 0 在区间内
        if (xMin.signum() <= 0 && xMax.signum() >= 0) {
            out += NotablePoint(PointKind.Y_INTERCEPT, BigDecimal.ZERO, poly.evaluate(BigDecimal.ZERO))
        }

        // 极值：导数多项式的根，再用二阶导数判定
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
                    // 二阶导为零无法判定，跳过而不是猜一个
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

    /** 形式导数 */
    private fun derivative(poly: Poly): Poly {
        if (poly.degree < 1) return Poly(listOf(BigDecimal.ZERO))
        return Poly((1..poly.degree).map { i -> Dec.mul(Dec.of(i), poly[i]) })
    }

    // -------------------------------------------------------------- 数值路线

    private fun numericPoints(
        expression: String,
        samples: List<Sample>,
        xMin: BigDecimal,
        xMax: BigDecimal,
        angleUnit: AngleUnit,
    ): List<NotablePoint> {
        val out = mutableListOf<NotablePoint>()
        val usable = samples.filter { it.y != null }

        // 零点：相邻采样异号即夹住一个根
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

        // 极值：采样点上的"高/低于两侧"即夹住一个驻点
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

    /**
     * 二分求根：要求 `f(a)` 与 `f(b)` 异号。
     *
     * **极点不是根**，这一点必须显式处理：`1÷x` 在 0 两侧也异号，
     * 但那里没有零点。两种情形靠两条判据区分：
     *  - 区间内部**无定义**（除零、越出定义域）→ 直接判定不是根；
     *  - 收敛后**回验**：真正的根处 |f| 比区间两端都小，极点处恰好相反。
     */
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

    /** 回验候选根：必须是"函数值比区间两端更接近 0"，否则是极点 */
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

    /**
     * 细化驻点：在 `[a, b]` 上对**数值导函数**二分。
     *
     * 导函数用中心差分，步长按 x 的量级取相对值：
     * 固定步长在 x=1e6 处会因相消而完全失真。
     */
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

    /** 中心差分斜率 */
    private fun slope(expression: String, x: BigDecimal, angleUnit: AngleUnit): BigDecimal? {
        val h = Dec.mul(Dec.add(x.abs(), BigDecimal.ONE), DERIVATIVE_STEP)
        val forward = evaluate(expression, Dec.add(x, h), angleUnit) ?: return null
        val backward = evaluate(expression, Dec.sub(x, h), angleUnit) ?: return null
        return Dec.div(Dec.sub(forward, backward), Dec.mul(Dec.of(2), h))
    }

    /**
     * 合并过近的重复点。
     *
     * 采样可能对同一个驻点夹出两次（相邻两组三元组都满足"高于两侧"），
     * 不去重的话列表里会出现两个几乎相同的极值。
     */
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
