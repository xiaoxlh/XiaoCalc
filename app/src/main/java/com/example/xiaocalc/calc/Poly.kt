package com.example.xiaocalc.calc

import java.math.BigDecimal

/**
 * 一元多项式（按次数升序存放系数）。
 *
 * `coefficients[i]` 是 `x^i` 的系数，末尾多余的零会被去掉。
 */
data class Poly(val coefficients: List<BigDecimal>) {

    val degree: Int get() = coefficients.size - 1

    operator fun get(power: Int): BigDecimal =
        if (power in coefficients.indices) coefficients[power] else BigDecimal.ZERO

    fun isZero(): Boolean = coefficients.all { it.signum() == 0 }

    fun evaluate(x: BigDecimal): BigDecimal {
        // 霍纳法则：从最高次开始，少一半乘法
        var acc = BigDecimal.ZERO
        for (i in coefficients.indices.reversed()) {
            acc = Dec.add(Dec.mul(acc, x), coefficients[i])
        }
        return acc
    }

    /** 末尾系数为 0 的高次项没有意义，去掉它们使 [degree] 反映真实次数 */
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

/**
 * 从表达式里提取多项式系数。
 *
 * ## 为什么不用"写一个多项式解析器"
 *
 * 那意味着把 `ExpressionEvaluator` 的语法（加→乘→一元→幂→后缀→基本项，
 * 含右结合的 `^`、隐式乘法、`%` 语义）再实现一遍。两份语法迟早会各自演化，
 * 而它们一旦不一致，用户看到的就是"计算器算得对、方程解错了"这种最难查的 bug。
 *
 * ## 改用的办法：牛顿前向差分
 *
 * 对次数 ≤ n 的多项式，在 `x = 0, 1, …, n` 处取 n+1 个值即可唯一确定它：
 *
 * ```
 * f(x) = Σ_k C(x,k) · Δ^k f(0)
 * ```
 *
 * 其中 `Δ` 是前向差分算子。这一路**只有减法与整数乘法**，
 * 而且转换回 x 的幂次时只除以 `k!`——对整数系数的多项式，`Δ^k f(0)` 必能被 `k!` 整除，
 * 于是系数**精确无损**。
 *
 * ## 自校验
 *
 * 提取完不等结果，而是拿**原始表达式**在若干额外点上回验。
 * 多项式次数不够（比如把 `x²-2` 硬拟合成一次）会在这一步露馅，于是升次重试。
 * 这也是为什么 `fit` 返回可空类型而不是抛异常：失败应当安静地退化为"解不了"，
 * 而不是给一个错的解。
 */
object PolynomialFit {

    /** 支持的最高次数。再高就该走数值求根，而不是指望差分提取 */
    const val MAX_DEGREE = 10

    /** 回验点。刻意避开拟合用的 0..n 整数点，且包含负数与小数 */
    private val VERIFY_POINTS = listOf("-1", "0.5", "3.7", "11.25", "-6.4")

    /**
     * 尝试把 [expression] 看成关于 [variable] 的一元多项式。
     *
     * @param maxDegree 尝试到的最高次数。默认 [MAX_DEGREE]；
     *                  调用方可用更大的值来**区分**"不是多项式"与"次数太高"——
     *                  见 `EquationSolver` 里对错误分类的处理。
     * @return 系数；不是多项式、或次数超过 [maxDegree] 时返回 null
     */
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

            // 回验：任何一点对不上就升次重试
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

    /**
     * 由 `f(0), f(1), …, f(n)` 求系数。
     *
     * 用递推构造下降阶乘 `x(x-1)…(x-k+1)`，避免引入斯特林数表：
     * ```
     * f(x) = Σ_k (Δ^k f(0) / k!) · x(x-1)…(x-k+1)
     * ```
     */
    private fun fromForwardDifferences(samples: List<BigDecimal>): Poly {
        val n = samples.size - 1
        val forward = forwardDifferences(samples)

        val result = MutableList(n + 1) { BigDecimal.ZERO }
        // 第 0 阶的下降阶乘是空乘积，即 1
        var falling = Poly(listOf(BigDecimal.ONE))
        var factorial = BigDecimal.ONE

        for (k in 0..n) {
            if (k > 0) factorial = factorial.multiply(BigDecimal.valueOf(k.toLong()))
            val scale = Dec.div(forward[k], factorial)
            for (i in falling.coefficients.indices) {
                result[i] = Dec.add(result[i], Dec.mul(scale, falling.coefficients[i]))
            }
            // 乘上 (x - k)，供下一阶使用
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

    /** 逐阶前向差分：返回 `[Δ^0 f(0), Δ^1 f(0), …]` */
    private fun forwardDifferences(samples: List<BigDecimal>): List<BigDecimal> {
        var current = samples
        val out = ArrayList<BigDecimal>(samples.size)
        while (current.isNotEmpty()) {
            out += current[0]
            current = current.zipWithNext { a, b -> Dec.sub(b, a) }
        }
        return out
    }

    /**
     * 两个值是否"足够接近"。
     *
     * 用相对容差而不是绝对容差：方程里可能既有 1e-9 也有 1e9 量级的项，
     * 绝对容差不是太松就是太紧。1e-30 远低于对外显示的 16 位，
     * 又远高于 50 位工作精度的舍入噪声。
     */
    private fun closeEnough(a: BigDecimal, b: BigDecimal): Boolean {
        val diff = Dec.sub(a, b).abs()
        if (diff.signum() == 0) return true
        val scale = a.abs().max(b.abs()).max(BigDecimal.ONE)
        return diff.compareTo(Dec.mul(scale, TOLERANCE)) < 0
    }

    private val TOLERANCE: BigDecimal = BigDecimal("1E-30")
}
