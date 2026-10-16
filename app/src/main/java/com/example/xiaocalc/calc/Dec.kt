package com.example.xiaocalc.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * 高精度十进制数学内核。
 *
 * ## 为什么不用 Double
 *
 * 目标是对外保证 **16 位有效数字全部正确**。Double 的十进制精度约 15.95 位，
 * 本来就卡在 16 位的边界上；更要命的是它经不起相消——`1-cos(0.0001)`
 * 会直接损失近 8 位有效数字，`(1e16+1)-1e16` 更是只剩 1 位。
 *
 * 所以这里用 [BigDecimal] 以 [WORKING_DIGITS] 位内部精度计算，最后才舍到
 * [DISPLAY_DIGITS] 位显示。多出来的那 34 位专作"安全垫"：吸收相消、
 * 吸收级数累积舍入，保证最终舍出的 16 位每一位都是真的。
 *
 * 一个直接可见的好处：`1÷3×3` 在 Double 下要靠运气才等于 1，
 * 这里 50 位算完再舍进 16 位，必然是 1。
 *
 * ## 常量为什么现算
 *
 * π / e / ln2 都由收敛级数在运行时算出，而不是写 50 位字面量。
 * 手抄 50 位数字出错时肉眼看不出来，而公式错了测试立刻会红。
 */
object Dec {

    /** 内部工作精度：50 位有效数字 */
    const val WORKING_DIGITS = 50

    /** 对外承诺的有效数字位数 */
    const val DISPLAY_DIGITS = 16

    /** 续算时写回表达式所用的位数：比显示多几位，避免 `1÷3` 续乘 3 掉到 0.999… */
    const val REUSE_DIGITS = 20

    /**
     * 工作上下文。用 HALF_EVEN（IEEE 默认）而不是 HALF_UP：
     * 内部舍入应当是"无偏"的，否则每一步都朝同一方向偏，误差会线性累积。
     * 对外显示时才换成 HALF_UP，因为那符合用户对"四舍五入"的直觉。
     */
    val MC: MathContext = MathContext(WORKING_DIGITS, RoundingMode.HALF_EVEN)

    val ZERO: BigDecimal = BigDecimal.ZERO
    val ONE: BigDecimal = BigDecimal.ONE
    val TWO: BigDecimal = BigDecimal.valueOf(2)
    val HALF: BigDecimal = BigDecimal("0.5")

    /** 级数项数上限。正常收敛远用不到，纯粹是防御性的。 */
    private const val MAX_SERIES = 400

    /** 牛顿迭代次数上限（sqrt 二次收敛，50 位通常 7 次内到位） */
    private const val MAX_NEWTON = 200

    /** exp 里 2^k 的指数上限，挡住 `e^(1e30)` 这类无意义输入 */
    private const val MAX_EXP_SCALE = 100_000

    fun of(v: Int): BigDecimal = BigDecimal.valueOf(v.toLong())

    fun of(v: Long): BigDecimal = BigDecimal.valueOf(v)

    // ------------------------------------------------------------------ 常量

    /** π = 4·(4·atan(1/5) − atan(1/239))，Machin 公式。 */
    val PI: BigDecimal by lazy {
        arctanReciprocal(5).multiply(of(4), MC)
            .subtract(arctanReciprocal(239), MC)
            .multiply(of(4), MC)
    }

    val HALF_PI: BigDecimal by lazy { PI.divide(TWO, MC) }

    /** e = Σ 1/k! */
    val E: BigDecimal by lazy {
        var sum = ONE
        var term = ONE
        var k = 1
        while (k <= MAX_SERIES) {
            term = term.divide(of(k), MC)
            val next = sum.add(term, MC)
            if (next.compareTo(sum) == 0) return@lazy next
            sum = next
            k++
        }
        sum
    }

    /** ln2 = 2·atanh(1/3) = 2·Σ (1/3)^(2k+1)/(2k+1) */
    private val LN2: BigDecimal by lazy {
        val x = ONE.divide(of(3), MC)
        val x2 = x.multiply(x, MC)
        var term = x
        var sum = x
        var k = 1
        while (k <= MAX_SERIES) {
            term = term.multiply(x2, MC)
            val next = sum.add(term.divide(of(2 * k + 1), MC), MC)
            if (next.compareTo(sum) == 0) return@lazy sum.multiply(TWO, MC)
            sum = next
            k++
        }
        sum.multiply(TWO, MC)
    }

    /** atan(1/n) = Σ (-1)^k / ((2k+1)·n^(2k+1)) */
    private fun arctanReciprocal(n: Int): BigDecimal {
        val inv = ONE.divide(of(n), MC)
        val inv2 = inv.multiply(inv, MC)
        var term = inv
        var sum = inv
        var k = 1
        while (k <= MAX_SERIES) {
            term = term.multiply(inv2, MC)
            val add = term.divide(of(2 * k + 1), MC).let { if (k % 2 == 1) it.negate() else it }
            val next = sum.add(add, MC)
            if (next.compareTo(sum) == 0) return sum
            sum = next
            k++
        }
        return sum
    }

    // -------------------------------------------------------------- 基本运算

    fun add(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MC)

    fun sub(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MC)

    fun mul(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MC)

    fun div(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, MC)

    // -------------------------------------------------------------- 开方 / 幂

    /**
     * 平方根，牛顿迭代 `g ← (g + x/g)/2`。
     *
     * 刻意**不用** `BigDecimal.sqrt(MathContext)`：那是 Java 9 引入的 API，
     * 在 Android 上未必全版本可用，而本工程 minSdk 只有 26。
     * 自己迭代既没有版本风险，也少一个平台差异点。
     */
    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw ArithmeticException("负数开平方")
        if (x.signum() == 0) return ZERO
        var guess = sqrtSeed(x)
        repeat(MAX_NEWTON) {
            val next = guess.add(x.divide(guess, MC), MC).divide(TWO, MC)
            if (next.compareTo(guess) == 0) return next
            guess = next
        }
        return guess
    }

    /**
     * 牛顿法初值。取 10^(e/2)（e 为 x 的十进制阶），
     * 这样相对误差只有几个数量级，迭代 7 次左右即可收敛到 50 位。
     * 不用 `Math.sqrt(x.toDouble())`：x 超出 Double 范围时会得到 0 或 Infinity。
     */
    private fun sqrtSeed(x: BigDecimal): BigDecimal {
        val exponent = x.precision() - x.scale()
        return ONE.scaleByPowerOfTen(Math.floorDiv(exponent, 2))
    }

    /** 自然对数，x 必须 > 0。 */
    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw ArithmeticException("对数的定义域为 (0, +∞)")
        if (x.compareTo(ONE) == 0) return ZERO

        // 归约到 m ∈ [1,2)：x = m·2^k，于是 ln x = ln m + k·ln2
        var m = x
        var k = 0L
        while (m.compareTo(TWO) >= 0 && k < MAX_EXP_SCALE) {
            m = m.divide(TWO, MC)
            k++
        }
        while (m.compareTo(ONE) < 0 && k > -MAX_EXP_SCALE) {
            m = m.multiply(TWO, MC)
            k--
        }

        // ln m = 2·atanh(z)，z = (m-1)/(m+1) ∈ [0, 1/3)
        val z = m.subtract(ONE, MC).divide(m.add(ONE, MC), MC)
        val z2 = z.multiply(z, MC)
        var term = z
        var sum = z
        var n = 1
        while (n <= MAX_SERIES) {
            term = term.multiply(z2, MC)
            val next = sum.add(term.divide(of(2 * n + 1), MC), MC)
            if (next.compareTo(sum) == 0) break
            sum = next
            n++
        }
        val lnm = sum.multiply(TWO, MC)
        return if (k == 0L) lnm else lnm.add(LN2.multiply(BigDecimal.valueOf(k), MC), MC)
    }

    /** 自然指数。归约 x = k·ln2 + r 后用泰勒级数算 e^r，再乘回 2^k。 */
    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return ONE

        val kRaw = x.divide(LN2, MC).setScale(0, RoundingMode.HALF_EVEN)
        if (kRaw.abs() > BigDecimal.valueOf(MAX_EXP_SCALE.toLong())) {
            throw ArithmeticException("指数超出可表示范围")
        }
        val k = kRaw.toInt()
        val r = x.subtract(LN2.multiply(kRaw, MC), MC)

        var sum = ONE
        var term = ONE
        var n = 1
        while (n <= MAX_SERIES) {
            term = term.multiply(r, MC).divide(of(n), MC)
            val next = sum.add(term, MC)
            if (next.compareTo(sum) == 0) {
                sum = next
                break
            }
            sum = next
            n++
        }

        if (k == 0) return sum
        val scale = BigDecimal(BigInteger.ONE.shiftLeft(Math.abs(k)))
        return if (k > 0) sum.multiply(scale, MC) else sum.divide(scale, MC)
    }

    /**
     * 幂运算。
     *
     * 整数指数走 [BigDecimal.pow]（精确且快）；`1/2` 走 [sqrt]（比 exp/ln 路径更干净）；
     * 其余情况走 `e^(y·ln x)`，要求底数为正。
     */
    fun pow(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        val asInt = exponentAsInt(exponent)
        if (asInt != null) {
            if (asInt == 0) return ONE
            if (base.signum() == 0) {
                if (asInt < 0) throw ArithmeticException("0 的负数次幂")
                return ZERO
            }
            return base.pow(asInt, MC)
        }
        if (base.signum() == 0) return ZERO
        if (base.signum() < 0) throw ArithmeticException("负数的非整数次幂不是实数")
        if (exponent.compareTo(HALF) == 0) return sqrt(base)
        return exp(exponent.multiply(ln(base), MC))
    }

    /** 若 [x] 是落在 Int 范围内的整数则返回它，否则 null。 */
    private fun exponentAsInt(x: BigDecimal): Int? {
        val stripped = x.stripTrailingZeros()
        if (stripped.scale() > 0) return null
        return try {
            stripped.intValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    // ------------------------------------------------------------ 三角函数

    /** 把角度归约到 [-π, π]。 */
    private fun reduce(x: BigDecimal): BigDecimal {
        if (x.abs().compareTo(PI) <= 0) return x
        val twoPi = PI.multiply(TWO, MC)
        val turns = x.divide(twoPi, MC).setScale(0, RoundingMode.HALF_EVEN)
        return x.subtract(twoPi.multiply(turns, MC), MC)
    }

    fun sin(x: BigDecimal): BigDecimal {
        var t = reduce(x)
        // 归到 [-π/2, π/2]：sin(π - t) = sin t
        when {
            t.compareTo(HALF_PI) > 0 -> t = PI.subtract(t, MC)
            t.compareTo(HALF_PI.negate()) < 0 -> t = PI.negate().subtract(t, MC)
        }
        val t2 = t.multiply(t, MC)
        var term = t
        var sum = t
        var n = 1
        while (n <= MAX_SERIES) {
            term = term.multiply(t2, MC)
                .divide(of((2 * n) * (2 * n + 1)), MC)
                .negate()
            val next = sum.add(term, MC)
            if (next.compareTo(sum) == 0) break
            sum = next
            n++
        }
        return sum
    }

    fun cos(x: BigDecimal): BigDecimal {
        val t = reduce(x)
        val t2 = t.multiply(t, MC)
        var term = ONE
        var sum = ONE
        var n = 1
        while (n <= MAX_SERIES) {
            term = term.multiply(t2, MC)
                .divide(of((2 * n - 1) * (2 * n)), MC)
                .negate()
            val next = sum.add(term, MC)
            if (next.compareTo(sum) == 0) break
            sum = next
            n++
        }
        return sum
    }

    /** 正切。余弦为 0 时（90°+kπ）结果非有限。 */
    fun tan(x: BigDecimal): BigDecimal {
        val s = sin(x)
        val c = cos(x)
        val limit = ONE.scaleByPowerOfTen(-(WORKING_DIGITS - DISPLAY_DIGITS))
        if (c.abs().compareTo(limit) < 0) throw ArithmeticException("正切在 π/2+kπ 处无定义")
        return s.divide(c, MC)
    }

    // ------------------------------------------------------------ 其它函数

    /**
     * 阶乘。这里用**不带 MathContext 的乘法**——阶乘是整数，
     * 被 50 位截断就不再精确了（170! 有 307 位），必须精确计算。
     */
    fun factorial(n: BigDecimal): BigDecimal {
        val stripped = n.stripTrailingZeros()
        if (stripped.scale() > 0 || stripped.signum() < 0) {
            throw ArithmeticException("阶乘只对非负整数有定义")
        }
        val k = stripped.intValueExact()
        var result = ONE
        for (i in 2..k) result = result.multiply(of(i))
        return result
    }
}
