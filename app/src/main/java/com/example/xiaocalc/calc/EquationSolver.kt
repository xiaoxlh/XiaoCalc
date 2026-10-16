package com.example.xiaocalc.calc

import java.math.BigDecimal
import java.math.BigInteger

/** 一个根。实数根只有一个值；复根成对出现。 */
sealed interface EquationRoot {

    /** 可供显示的数值（16 位有效数字） */
    val display: String

    /** 精确形式（如 `1+√2`、`(-3+√5)/2`）；无法精确表达时为 null */
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

/** 求解结果。失败是可判定的枚举，不是"错误"字符串。 */
sealed interface EquationSolution {

    /** @param discriminant 判别式；仅二次方程会给出 */
    data class Solved(
        val roots: List<EquationRoot>,
        val degree: Int,
        val discriminant: BigDecimal? = null,
    ) : EquationSolution

    data class Failed(val error: EquationError) : EquationSolution
}

enum class EquationError {
    /** 输入为空 */
    EMPTY,

    /** 没有等号——这更像"想求值"，应当引导用户去计算器 */
    NO_EQUALS,

    /** 多个等号 */
    MULTIPLE_EQUALS,

    /** 不像多项式（含 sin/ln，或除以了未知数） */
    NOT_POLYNOMIAL,

    /** 次数超过支持上限 */
    DEGREE_TOO_HIGH,

    /** 恒等式，如 `x=x` */
    IDENTITY,

    /** 矛盾式，如 `x=x+1` */
    NO_SOLUTION,
}

/**
 * 一元方程求解。
 *
 * 输入形如 `x^2-3x+2=0`。系数提取走 [PolynomialFit]（牛顿差分 + 回验），
 * 因此**没有第二套语法分析**——方程里的写法与计算器里完全一致。
 *
 * 精确根式的来源是**判别式**本身，而不是从浮点结果反猜：
 * 把 `D = k²·m`（m 无平方因子）分解出来，两根就是 `(-b ± k√m) / 2a`，
 * 再约去公因子。这样 `x²-2=0` 得到的是 `√2`，不是 `1.414213562373095`。
 */
object EquationSolver {

    /** 变量名。用 `x` 是因为它是数学作业里唯一通行的写法。 */
    const val VARIABLE = "x"

    /** 判别式超出这个量级就不再尝试精确根式分解（试除法会变慢） */
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

        // 移项成 f(x) = 左 − 右，于是"解方程"化为"求零点"
        val poly = PolynomialFit.fit("($lhs)-($rhs)", VARIABLE, angleUnit)
            ?: return EquationSolution.Failed(EquationError.NOT_POLYNOMIAL)

        return solvePoly(poly)
    }

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

    // ------------------------------------------------------------------ 二次

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

        // 复根：实部 −b/2a，虚部 ±√(−D)/2a
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
        // 实部为 0 时不写 "0 +"，纯虚数就直接写虚部
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

    // -------------------------------------------------------------- 精确根式

    /**
     * 把判别式分解成 `k²·m`，拼出 `(−b ± k√m) / 2a` 的两种写法。
     *
     * @param imaginary 复根时把 `i` 缀在根号后面
     */
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

        // 约去 −b、k、2a 的公因子，让 (3±√5)/2 这类结果不再带冗余系数
        var nb = bInt.negate()
        var kk = BigInteger.valueOf(k)
        // 不用 BigInteger.TWO：那个常量是 API 33 才加的，而本工程 minSdk 26，
        // 在老设备上会抛 NoSuchFieldError（lint 的 NewApi 抓到的就是这条）
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
            // 判别式是完全平方：没有根号，两根都是有理数
            val value = fraction(nb, den) ?: return null
            return value to value
        }

        val mBig = BigInteger.valueOf(m)
        val positive = renderTerm(nb, kk, mBig, den, plus = true, imaginary = imaginary) ?: return null
        val negative = renderTerm(nb, kk, mBig, den, plus = false, imaginary = imaginary) ?: return null
        return positive to negative
    }

    /**
     * 拼出 `(nb ± kk√m) / den` 的可读形式。
     *
     * `m == 1` 表示被开方数是完全平方，此时**没有根号**——实根退化为有理数，
     * 复根则是整数倍的 `i`。留着 `√1` 会是明显的破绽。
     */
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

    /** 约分后的分数；分母为 1 时就是整数。任一分量不是整数则返回 null。 */
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

    // -------------------------------------------------------- 高次（数值求根）

    /**
     * 三次及以上。
     *
     * 目前**尚不实现**，明确报"次数过高"而不是给出一个可疑的数值解——
     * 下一步会接入 Durand–Kerner 迭代法。
     */
    private fun solveHigher(poly: Poly): EquationSolution {
        // poly 本身暂时没有用途；参数保留是为了接入数值求根时不必改调用点
        require(poly.degree >= 3) { "solveHigher 只处理三次及以上" }
        return EquationSolution.Failed(EquationError.DEGREE_TOO_HIGH)
    }
}
