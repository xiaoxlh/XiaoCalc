package com.example.xiaocalc.calc

import java.math.BigDecimal

/**
 * 把求解过程写成几行给人看的文字。
 *
 * ## 只对"真的有过程"的方程给过程
 *
 * 一元一次、一元二次、二元一次都有明确的闭式解，每一步都能从系数直接写出来；
 * 三次以上走的是数值迭代或降次试根，中间的牛顿步、试除记录对使用者没有意义，
 * 写出来只会显得高深。**宁可不给，也不编造看起来像过程的步骤**——
 * 一个事后拼凑的"过程"比没有过程更糟，因为它会让人以为自己看懂了推导。
 *
 * 数字统一走 [NumberFormatter]，与结果区用同一套格式化，避免
 * 过程里写 0.3333333333333333、结果里写 1/3 这种前后不一致。
 */
object EquationSteps {

    private val FORMAT_SETTINGS = CalcSettings(groupDigits = false)

    fun build(input: String, angleUnit: AngleUnit = AngleUnit.DEG): List<String> {
        val text = ExpressionEvaluator.normalize(input).trim()
        if (text.isEmpty()) return emptyList()
        val sides = text.split('=')
        if (sides.size != 2) return emptyList()
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return emptyList()

        // 移项成 f(x) = 左 − 右，与 EquationSolver 的做法保持一致
        val poly = PolynomialFit.fit("($lhs)-($rhs)", "x", angleUnit)
            ?.normalized()
            ?: return emptyList()

        return when (poly.degree) {
            1 -> linear(poly)
            2 -> quadratic(poly)
            else -> emptyList()
        }
    }

    /** `ax + b = 0` → `x = −b/a` */
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

    /** `ax² + bx + c = 0`：先算判别式，再按 Δ 的符号给出根 */
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
