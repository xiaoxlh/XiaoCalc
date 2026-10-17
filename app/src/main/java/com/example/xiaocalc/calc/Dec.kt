package com.example.xiaocalc.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

object Dec {
    const val WORKING_DIGITS = 50

    const val DISPLAY_DIGITS = 16

    const val REUSE_DIGITS = 20

    val MC: MathContext = MathContext(WORKING_DIGITS, RoundingMode.HALF_EVEN)

    val ZERO: BigDecimal = BigDecimal.ZERO
    val ONE: BigDecimal = BigDecimal.ONE
    val TWO: BigDecimal = BigDecimal.valueOf(2)
    val HALF: BigDecimal = BigDecimal("0.5")

    private const val MAX_SERIES = 400

    private const val MAX_NEWTON = 200

    private const val MAX_EXP_SCALE = 100_000

    fun of(v: Int): BigDecimal = BigDecimal.valueOf(v.toLong())

    fun of(v: Long): BigDecimal = BigDecimal.valueOf(v)

    val PI: BigDecimal by lazy {
        arctanReciprocal(5).multiply(of(4), MC)
            .subtract(arctanReciprocal(239), MC)
            .multiply(of(4), MC)
    }

    val HALF_PI: BigDecimal by lazy { PI.divide(TWO, MC) }

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

    fun add(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MC)

    fun sub(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MC)

    fun mul(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MC)

    fun div(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, MC)

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

    private fun sqrtSeed(x: BigDecimal): BigDecimal {
        val exponent = x.precision() - x.scale()
        return ONE.scaleByPowerOfTen(Math.floorDiv(exponent, 2))
    }

    fun log10(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw ArithmeticException("对数的定义域为 (0, +∞)")
        return ln(x).divide(LN10, MC)
    }

    private val LN10: BigDecimal by lazy { ln(BigDecimal.TEN) }

    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw ArithmeticException("对数的定义域为 (0, +∞)")
        if (x.compareTo(ONE) == 0) return ZERO
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

    private fun exponentAsInt(x: BigDecimal): Int? {
        val stripped = x.stripTrailingZeros()
        if (stripped.scale() > 0) return null
        return try {
            stripped.intValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun reduce(x: BigDecimal): BigDecimal {
        if (x.abs().compareTo(PI) <= 0) return x
        val twoPi = PI.multiply(TWO, MC)
        val turns = x.divide(twoPi, MC).setScale(0, RoundingMode.HALF_EVEN)
        return x.subtract(twoPi.multiply(turns, MC), MC)
    }

    fun sin(x: BigDecimal): BigDecimal {
        var t = reduce(x)
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

    fun tan(x: BigDecimal): BigDecimal {
        val s = sin(x)
        val c = cos(x)
        val limit = ONE.scaleByPowerOfTen(-(WORKING_DIGITS - DISPLAY_DIGITS))
        if (c.abs().compareTo(limit) < 0) throw ArithmeticException("正切在 π/2+kπ 处无定义")
        return s.divide(c, MC)
    }

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
