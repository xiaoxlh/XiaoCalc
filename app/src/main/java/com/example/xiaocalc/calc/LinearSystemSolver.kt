package com.example.xiaocalc.calc

import java.math.BigDecimal

/** 格式化设置。放在文件级是因为 [SystemSolution.Unique] 是顶层嵌套类，看不到 object 内部成员。 */
private val FORMAT_SETTINGS = CalcSettings()

/** 二元一次方程组的解。 */
sealed interface SystemSolution {

    data class Unique(
        val x: BigDecimal,
        val y: BigDecimal,
        /** 精确写法（如 `2/3`）；系数不是整数时为 null */
        val xExact: String?,
        val yExact: String?,
    ) : SystemSolution {
        val xDisplay: String get() = NumberFormatter.format(x, FORMAT_SETTINGS)
        val yDisplay: String get() = NumberFormatter.format(y, FORMAT_SETTINGS)
    }

    /** 两个方程是同一条直线：无穷多组解 */
    data object Infinite : SystemSolution

    /** 两条平行线：无解 */
    data object NoSolution : SystemSolution

    data class Failed(val error: EquationError) : SystemSolution
}

/**
 * 二元一次方程组求解。
 *
 * 输入形如 `x+y=3; 2x-y=0`（分号或换行分隔两个方程）。
 *
 * 系数提取仍走"在格点上取值 + 回验"（见 [PolynomialFit.fitLinear2]），
 * **没有第二套语法分析**：方程组里的写法与计算器里完全一致。
 *
 * 求解用克拉默法则。判别式 `D = a₁b₂ − a₂b₁` 为 0 时再区分"平行无解"
 * 与"两式同解"——这两者的用户提示完全不同，不能都报"无解"。
 */
object LinearSystemSolver {

    const val VARIABLE_X = "x"
    const val VARIABLE_Y = "y"

    /** 两个方程之间的分隔符 */
    private val SEPARATORS = charArrayOf(';', '\n', '；')

    /**
     * @param input 用分号或换行分隔的两个方程
     */
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
            // D=0 且两个分子也为 0 才是"同一条直线"，否则是平行无解
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

    /**
     * 把一个方程化成 `a·x + b·y + c = 0`。
     *
     * 先移项成 `左 − 右`，再交给 [PolynomialFit.fitLinear2]。
     */
    private fun coefficientsOf(equation: String, angleUnit: AngleUnit): PolynomialFit.Linear2? {
        val sides = equation.split('=')
        if (sides.size != 2) return null
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        return PolynomialFit.fitLinear2("($lhs)-($rhs)", VARIABLE_X, VARIABLE_Y, angleUnit)
    }
}
