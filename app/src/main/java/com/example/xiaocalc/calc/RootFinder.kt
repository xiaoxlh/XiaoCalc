package com.example.xiaocalc.calc

import java.math.BigDecimal

/**
 * 复数，实部与虚部都是 [BigDecimal]，精度与 [Dec] 一致。
 *
 * 刻意不复用 Double 版的复数实现：高次方程的根要显示 16 位有效数字，
 * 而 Durand–Kerner 是迭代法，用 Double 迭代等于把误差预算全花在迭代上。
 */
data class Cx(val re: BigDecimal, val im: BigDecimal) {

    val isReal: Boolean get() = im.signum() == 0

    /** |z|² */
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

/** 复系数多项式的求值（霍纳法则） */
fun Poly.evaluateComplex(x: Cx): Cx {
    var acc = Cx.ZERO
    for (i in coefficients.indices.reversed()) {
        acc = acc * x + Cx(coefficients[i], BigDecimal.ZERO)
    }
    return acc
}

/**
 * Durand–Kerner（Weierstrass）迭代：**同时**逼近多项式的全部复根。
 *
 * 每一步：
 * ```
 * z_k ← z_k − p(z_k) / Π_{j≠k} (z_k − z_j)
 * ```
 *
 * 相比"逐个降次求根"，它的好处是**不需要先猜到一个根**，而且收敛到全部根；
 * 代价是要复数运算，且初值影响收敛。
 *
 * 初值取经典序列 `z_k = (0.4 + 0.9i)^k`——这个点在复平面上既不在实轴上、
 * 模又略小于 1，能避开多数对称退化（例如全部初值相同会导致除数为 0）。
 */
object RootFinder {

    /** 迭代上限。正常 4 次多项式几十步内收敛到工作精度 */
    private const val MAX_ITERATIONS = 500

    /**
     * 收敛阈值，按工作精度取。
     *
     * 判据是"这一步的位移相对于根的量级已经小到 1e-40 以下"——
     * 比对外显示的 16 位宽得多，因此显示出来的每一位都是收敛后的真值。
     */
    private val CONVERGED = BigDecimal("1E-40")

    /** 初值基准点 */
    private val SEED = Cx(BigDecimal("0.4"), BigDecimal("0.9"))

    /**
     * 求全部复根。
     *
     * @return 按实部再虚部排序的根；迭代不收敛时返回 null（调用方应当报"无法求解"，
     *         而不是给出未收敛的数值）
     */
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
        // 迭代到上限仍未收敛：不返回可疑结果
        return null
    }

    private fun Cx.pow(exponent: Int): Cx {
        var result = Cx.ONE
        repeat(exponent) { result *= this }
        return result
    }

    /** 化成首一多项式；首项系数为 0 时返回 null */
    private fun toMonic(poly: Poly): Poly? {
        val normalized = poly.normalized()
        val leading = normalized.coefficients.lastOrNull() ?: return null
        if (leading.signum() == 0) return null
        if (leading.compareTo(BigDecimal.ONE) == 0) return normalized
        return Poly(normalized.coefficients.map { Dec.div(it, leading) }).normalized()
    }

    /**
     * 牛顿法抛光。
     *
     * Durand–Kerner 收敛到接近工作精度后会因除数的病态而停在某个误差平台上；
     * 再用牛顿法单独把每个根推几步，能把这几位补回来。
     *
     * 同时借这一步检验结果：抛光后若某个根代入不接近 0，说明整体没收敛，返回 null。
     */
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

    /** 形式导数 */
    private fun derivative(poly: Poly): Poly {
        if (poly.degree < 1) return Poly(listOf(BigDecimal.ZERO))
        return Poly(
            (1..poly.degree).map { i -> Dec.mul(BigDecimal.valueOf(i.toLong()), poly[i]) },
        )
    }

    /**
     * 合并共轭对并把虚部极小的根判定为实根。
     *
     * 数值求根不会给出严格相等的共轭对（虚部是 ±1e-48 这种量级），
     * 不清理的话显示出来就是 `1+0.0000000000000000…i`。
     */
    private fun mergeConjugatesAndSort(roots: List<Cx>): List<Cx> {
        val cleaned = roots.map { z ->
            val scale = BigDecimal.ONE.max(z.abs)
            if (z.im.abs() < Dec.mul(scale, BigDecimal("1E-30"))) {
                Cx(z.re, BigDecimal.ZERO)
            } else {
                z
            }
        }
        // 去重：共轭对被判定为实根后可能产生两个几乎相同的实数根
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
