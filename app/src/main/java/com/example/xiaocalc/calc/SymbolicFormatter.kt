package com.example.xiaocalc.calc

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 根号 / π / e 的**精确表示层**。
 *
 * 存在的意义：学生做作业时经常被要求保留根号与 π 的精确形式，
 * 而不是像普通计算器那样只能给浮点近似值。所以这里把结果表示为
 *
 * ```
 * 若干项之和，每项形如  (整数分子 / 整数分母) · √(平方自由数) · π^a · e^b
 * ```
 *
 * 例如 `√8 → 2√2`、`√2×√3 → √6`、`π√2 → π√2`、
 * `2√2+3√2 → 5√2`、`√18-√8 → √2`、`π+1 → π+1`。
 *
 * ## 安全性：数值反验
 *
 * 解析完一定会把符号式的**数值**与真实结果比对，不一致就放弃、回落小数。
 * 因此解析器的任何缺陷最多表现为"没化简"，**不会给出错误答案**。
 *
 * ## 表达边界
 *
 * 只处理加减与乘除构成的"多项式式"表达式。括号内做加减（如 `(1+√2)/2`）、
 * 除法含根号（如 `1/√2`）、函数（`sin`、`ln`）都不在此列，会回落小数。
 */
object SymbolicFormatter {

    /** 分子绝对值上限，挡住病态输入 */
    private const val MAX_NUMERATOR = 99_999L

    /** 分母与各因子累乘的上限 */
    private const val MAX_FACTOR = 999_999L

    /** 项数上限：超过说明输入异常，直接放弃符号化 */
    private const val MAX_TERMS = 8

    /**
     * 尝试给出符号化结果。
     *
     * @return 可显示文本；无法表达、或所需符号未被对应开关允许时返回 null。
     */
    fun format(value: Double, settings: CalcSettings, source: String): String? {
        if (value == 0.0) return null
        val terms = parseTerms(source) ?: return null
        if (terms.isEmpty()) return null

        // 全是纯有理项时没有符号可保留，交给常规格式化，
        // 否则会把 `10÷4` 这种纯数值表达式显示成 `5/2`
        if (terms.all { it.isPureRational() }) return null

        // 根号看"保留根号"，π/e 看"保留特殊符号"；
        // 任一项所需的符号没被允许，整体回落小数——避免出现半符号半小数的混合结果
        if (terms.any { !it.allowedBy(settings) }) return null

        val sum = terms.fold(0.0) { acc, t -> acc + t.numericValue() }
        val tolerance = 1e-9 * maxOf(1.0, abs(value))
        if (abs(sum - value) > tolerance) return null

        return render(terms)
    }

    // ------------------------------------------------------------ 解析

    private fun parseTerms(source: String): List<Form>? {
        val s = source.replace(" ", "")
        if (s.isEmpty()) return null
        val raw = splitAdditive(s) ?: return null
        if (raw.isEmpty() || raw.size > MAX_TERMS) return null

        val parsed = ArrayList<Form>(raw.size)
        for ((sign, text) in raw) {
            val form = parseProduct(text) ?: return null
            parsed += if (sign < 0) form.negated() else form
        }
        return mergeLikeTerms(parsed) ?: return null
    }

    /**
     * 在**括号深度 0** 处按 `+` / `-` 拆项，返回 (符号, 文本)。
     * 括号内出现加减（如 `(1+√2)`）会作为一项传给乘积解析器，从而自然回落。
     */
    private fun splitAdditive(s: String): List<Pair<Int, String>>? {
        val terms = ArrayList<Pair<Int, String>>()
        var depth = 0
        var sign = 1
        var start = 0
        var i = 0

        // 开头的正负号属于符号而不是项内容
        if (s[i] == '+' || s[i] == '-') {
            if (s[i] == '-') sign = -1
            i++
            start = i
        }

        while (i < s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth < 0) return null
                }
                '+', '-' -> if (depth == 0) {
                    if (i == start) return null // 连续符号，如 `1++2`
                    terms += sign to s.substring(start, i)
                    sign = if (s[i] == '-') -1 else 1
                    start = i + 1
                }
            }
            i++
        }
        if (depth != 0) return null
        if (start >= s.length) return null
        terms += sign to s.substring(start)
        return terms
    }

    /**
     * 把一项解析为因子乘积：`整数 × √整数 × π × e`，允许隐式乘号与 `÷ 常数`。
     * 出现其他构造（小数、函数、括号内加减…）返回 null。
     */
    private fun parseProduct(text: String): Form? {
        var numerator = 1L
        var denominator = 1L
        var radicand = 1L
        var piPower = 0
        var ePower = 0
        var index = 0
        var sawFactor = false

        while (index < text.length) {
            var divide = false
            when (text[index]) {
                '×' -> index++
                '÷' -> { divide = true; index++ }
            }
            if (index >= text.length) return null
            val direction = if (divide) -1 else 1

            when {
                text[index] == 'π' -> { piPower += direction; index++ }
                text[index] == 'e' -> { ePower += direction; index++ }
                text[index] == '√' -> {
                    // ÷√n 需要分母有理化，超出本层的表达能力
                    if (divide) return null
                    index++
                    val read = readInteger(text, index) ?: return null
                    if (read.first < 1L) return null
                    radicand = multiplyCapped(radicand, read.first) ?: return null
                    index = read.second
                }
                text[index].isDigit() -> {
                    val read = readInteger(text, index) ?: return null
                    if (divide) {
                        denominator = multiplyCapped(denominator, read.first) ?: return null
                    } else {
                        numerator = multiplyCapped(numerator, read.first) ?: return null
                    }
                    index = read.second
                }
                else -> return null
            }
            sawFactor = true
        }

        if (!sawFactor) return null
        if (piPower !in 0..2 || ePower !in 0..2) return null

        // 根式化简：把完全平方因子提到根号外，根号内只留平方自由数
        if (radicand > 1L) {
            val (outside, inside) = simplifyRadical(radicand)
            numerator = multiplyCapped(numerator, outside) ?: return null
            radicand = inside
        }
        return Form.of(numerator, denominator, radicand, piPower, ePower)
    }

    /** 读一个非负整数，允许写成 `n` 或 `(n)`（函数页的括号补全会产生后者） */
    private fun readInteger(s: String, start: Int): Pair<Long, Int>? {
        var i = start
        var closeIndex = -1
        if (i < s.length && s[i] == '(') {
            closeIndex = s.indexOf(')', i)
            if (closeIndex < 0) return null
            i++
        }
        val from = i
        var acc = 0L
        while (i < s.length && s[i].isDigit()) {
            acc = acc * 10 + (s[i] - '0')
            if (acc > MAX_FACTOR) return null
            i++
        }
        if (i == from) return null
        if (closeIndex >= 0) {
            if (i != closeIndex) return null
            i = closeIndex + 1
        }
        return acc to i
    }

    // ------------------------------------------------------------ 合并同类项

    /**
     * 同类的判据是「根号内数字 + π/e 的幂」相同，系数相加。
     * `2√2 + 3√2 → 5√2`、`√2/2 + √2/2 → √2`。
     * 保持首次出现顺序，使结果读起来与输入顺序一致。
     */
    private fun mergeLikeTerms(terms: List<Form>): List<Form>? {
        val merged = LinkedHashMap<Triple<Long, Int, Int>, Form>()
        for (term in terms) {
            val key = Triple(term.radicand, term.piPower, term.ePower)
            val existing = merged[key]
            merged[key] = if (existing == null) term else {
                val sum = addFractions(
                    existing.numerator, existing.denominator,
                    term.numerator, term.denominator,
                ) ?: return null
                Form.of(sum.first, sum.second, term.radicand, term.piPower, term.ePower)
                    ?: return null
            }
        }
        return merged.values.filter { it.numerator != 0L }
    }

    /** 分数相加并约分；溢出或越界时返回 null */
    private fun addFractions(n1: Long, d1: Long, n2: Long, d2: Long): Pair<Long, Long>? {
        val g = gcd(d1, d2)
        val leftFactor = d2 / g
        val rightFactor = d1 / g
        if (abs(n1) > MAX_NUMERATOR / leftFactor) return null
        if (abs(n2) > MAX_NUMERATOR / rightFactor) return null
        val numerator = n1 * leftFactor + n2 * rightFactor
        if (d1 > MAX_FACTOR / d2) return null
        val denominator = d1 * d2 / g
        return reduce(numerator, denominator) ?: return null
    }

    // ------------------------------------------------------------ 渲染

    private fun render(terms: List<Form>): String {
        val sb = StringBuilder()
        terms.forEachIndexed { index, term ->
            val negative = term.numerator < 0
            when {
                index == 0 && negative -> sb.append('-')
                index > 0 -> sb.append(if (negative) '-' else '+')
            }
            sb.append(term.renderMagnitude())
        }
        return sb.toString()
    }

    /** `n = outside² · inside`，`inside` 无平方因子 */
    internal fun simplifyRadical(n: Long): Pair<Long, Long> {
        var outside = 1L
        var inside = n
        var factor = 2L
        while (factor * factor <= inside) {
            val square = factor * factor
            while (inside % square == 0L) {
                inside /= square
                outside *= factor
            }
            factor++
        }
        return outside to inside
    }

    /**
     * 一项：`分子/分母 · √radicand · π^piPower · e^ePower`。
     * 分子带符号；分母恒为正。
     */
    internal class Form private constructor(
        val numerator: Long,
        val denominator: Long,
        val radicand: Long,
        val piPower: Int,
        val ePower: Int,
    ) {
        fun isPureRational(): Boolean =
            radicand <= 1L && piPower == 0 && ePower == 0

        fun numericValue(): Double {
            var v = numerator.toDouble() / denominator.toDouble()
            if (radicand > 1L) v *= sqrt(radicand.toDouble())
            repeat(piPower) { v *= PI }
            repeat(ePower) { v *= E }
            return v
        }

        fun allowedBy(settings: CalcSettings): Boolean =
            (radicand <= 1L || settings.keepRoot) &&
                (piPower == 0 || settings.keepSymbols) &&
                (ePower == 0 || settings.keepSymbols)

        fun negated(): Form = Form(-numerator, denominator, radicand, piPower, ePower)

        /** 不含符号的正文，供求和渲染拼接正负号 */
        fun renderMagnitude(): String {
            val symbols = buildString {
                if (piPower > 0) append(PI_SUFFIX[piPower])
                if (ePower > 0) append(E_SUFFIX[ePower])
                if (radicand > 1L) append('√').append(radicand)
            }
            val magnitude = abs(numerator)
            val body = when {
                symbols.isEmpty() -> magnitude.toString()
                magnitude == 1L -> symbols
                else -> "$magnitude$symbols"
            }
            return if (denominator == 1L) body else "$body/$denominator"
        }

        companion object {
            private val PI_SUFFIX = arrayOf("", "π", "π²")
            private val E_SUFFIX = arrayOf("", "e", "e²")

            fun of(
                numerator: Long,
                denominator: Long,
                radicand: Long,
                piPower: Int,
                ePower: Int,
            ): Form? {
                if (denominator <= 0L || denominator > MAX_FACTOR) return null
                if (abs(numerator) > MAX_NUMERATOR) return null
                val reduced = reduce(numerator, denominator) ?: return null
                return Form(reduced.first, reduced.second, radicand, piPower, ePower)
            }
        }
    }

    private fun reduce(numerator: Long, denominator: Long): Pair<Long, Long>? {
        if (denominator <= 0L) return null
        val divisor = gcd(abs(numerator), denominator)
        return if (divisor > 1L) numerator / divisor to denominator / divisor
        else numerator to denominator
    }

    private fun multiplyCapped(a: Long, b: Long): Long? {
        if (b <= 0L) return null
        if (a > MAX_FACTOR / b) return null
        return a * b
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0L) 1L else x
    }
}
