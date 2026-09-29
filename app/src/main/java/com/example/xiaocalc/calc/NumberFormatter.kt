package com.example.xiaocalc.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * 结果格式化。
 *
 * 旧实现用 `"%.6f"` 截断再补一堆正则做符号化，既有精度损失又容易产出
 * `NaN` / 空串这类脏结果。这里改为：
 *  - 十进制路径用 [BigDecimal] 做 10 位有效数字四舍五入，杜绝浮点尾巴；
 *  - 极大/极小值走科学计数法，指数用上标（显示层友好）；
 *  - 千分位只在"纯十进制"结果上施加，且由设置控制；
 *  - "保留根号 / 保留特殊符号"改为**基于数值的真符号化**（唯一分解 + 有理逼近），
 *    不再是旧版那种匹配源串的正则拼贴。
 */
object NumberFormatter {

    private const val SIGNIFICANT_DIGITS = 10
    private const val MANTISSA_DECIMALS = 5

    /** 超过此量级走科学计数法 */
    private const val SCI_UPPER = 1e10

    /** 低于此量级（非 0）走科学计数法 */
    private const val SCI_LOWER = 1e-9

    /** "科学计数法"开关的阈值（沿用旧版语义：≥10000 即切换） */
    private const val SCI_SETTING_THRESHOLD = 10_000.0

    /** 符号化式的分子绝对值上限，挡住病态输入 */
    private const val MAX_SYMBOLIC_NUMERATOR = 99_999L

    /** 根号内数字与各因子累乘的上限 */
    private const val MAX_SYMBOLIC_FACTOR = 999_999L

    fun format(value: Double, settings: CalcSettings, source: String = ""): String {
        if (value.isNaN()) return "NaN"
        if (value == 0.0) return "0"

        if (settings.keepRoot || settings.keepSymbols) {
            symbolicForm(source, value)?.let { form ->
                if (form.allowedBy(settings)) {
                    return applyGroupingIfPlain(form.render(), settings)
                }
            }
        }

        val magnitude = abs(value)
        if (settings.scientific && magnitude >= SCI_SETTING_THRESHOLD) return scientific(value)
        if (magnitude >= SCI_UPPER || magnitude < SCI_LOWER) return scientific(value)

        val plain = decimal(value)
        return if (settings.groupDigits) groupThousands(plain) else plain
    }

    /** 续算时使用的"纯数值"文本：无千分位、无上标，保证能再次进入解析器。 */
    fun plainForReuse(value: Double): String {
        if (!value.isFinite()) return ""
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= SCI_UPPER || magnitude < SCI_LOWER) {
            // 解析器认识 ^，用 1.2345×10^n 的纯文本形式
            return scientificPlain(value)
        }
        return decimal(value)
    }

    // ------------------------------------------------------------------ 十进制

    private fun decimal(value: Double): String {
        val rounded = BigDecimal(value)
            .round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
            .stripTrailingZeros()
        var text = rounded.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text.ifEmpty { "0" }
    }

    // ---------------------------------------------------------------- 科学计数

    private fun scientific(value: Double): String {
        val (mantissa, exponent) = mantissaAndExponent(value)
        return "$mantissa×10${superscript(exponent)}"
    }

    private fun scientificPlain(value: Double): String {
        val (mantissa, exponent) = mantissaAndExponent(value)
        return "$mantissa×10^$exponent"
    }

    private fun mantissaAndExponent(value: Double): Pair<String, Int> {
        var exponent = floor(log10(abs(value))).toInt()
        var mantissa = BigDecimal(value / 10.0.pow(exponent))
            .setScale(MANTISSA_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        // 9.999996 → 10.00000 的进位
        if (mantissa.abs() >= BigDecimal.TEN) {
            mantissa = mantissa.movePointLeft(1).stripTrailingZeros()
            exponent += 1
        }
        var text = mantissa.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text to exponent
    }

    private val SUPERSCRIPTS = mapOf(
        '-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    )

    private val SUPERSCRIPT_TO_ASCII = SUPERSCRIPTS.entries.associate { (k, v) -> v to k }

    fun superscript(exponent: Int): String = buildString {
        for (c in exponent.toString()) SUPERSCRIPTS[c]?.let { append(it) }
    }

    /**
     * 把表达式/结果里的 `^数字` 转成上标，仅用于**显示**。
     * 反向转换见 [desuperscript]。
     */
    fun superscriptExponents(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '^') {
                var j = i + 1
                val digits = StringBuilder()
                if (j < text.length && text[j] == '-') {
                    digits.append('-')
                    j++
                }
                while (j < text.length && text[j].isDigit()) {
                    digits.append(text[j])
                    j++
                }
                if (digits.isNotEmpty() && digits.toString() != "-") {
                    for (d in digits) out.append(SUPERSCRIPTS.getValue(d))
                    i = j
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** 上标 → `^数字`，用于把显示文本还原成可解析表达式。 */
    fun desuperscript(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            if (SUPERSCRIPT_TO_ASCII.containsKey(text[i])) {
                append('^')
                while (i < text.length && SUPERSCRIPT_TO_ASCII.containsKey(text[i])) {
                    append(SUPERSCRIPT_TO_ASCII.getValue(text[i]))
                    i++
                }
            } else {
                append(text[i])
                i++
            }
        }
    }

    // ------------------------------------------------------------------ 千分位

    fun groupThousands(text: String): String {
        if (!PLAIN_NUMBER.matches(text)) return text
        val negative = text.startsWith('-')
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.')
        val integerPart = if (dot >= 0) body.substring(0, dot) else body
        val fractionPart = if (dot >= 0) body.substring(dot) else ""
        if (integerPart.length <= 3) return text
        val grouped = StringBuilder(integerPart.length + integerPart.length / 3)
        integerPart.forEachIndexed { i, c ->
            if (i > 0 && (integerPart.length - i) % 3 == 0) grouped.append(',')
            grouped.append(c)
        }
        return (if (negative) "-" else "") + grouped + fractionPart
    }

    private val PLAIN_NUMBER = Regex("""^-?\d+(\.\d+)?$""")

    private fun applyGroupingIfPlain(text: String, settings: CalcSettings): String =
        if (settings.groupDigits) groupThousands(text) else text

    // --------------------------------------------------- 根号与 π / e 的符号化

    /**
     * 符号化结果：`有理系数 × √(平方自由数) × π^a × e^b`。
     *
     * `radicand == 1` 表示没有根号，`piPower` / `ePower` 为 0 表示不含该常数。
     */
    internal class SymbolicForm(
        val numerator: Long,
        val denominator: Long,
        val radicand: Long,
        val piPower: Int,
        val ePower: Int,
    ) {
        fun numericValue(): Double {
            var v = numerator.toDouble() / denominator.toDouble()
            if (radicand > 1L) v *= sqrt(radicand.toDouble())
            repeat(piPower) { v *= PI }
            repeat(ePower) { v *= E }
            return v
        }

        /** 只有当所需符号都被对应开关允许时才输出，否则回落小数 */
        fun allowedBy(settings: CalcSettings): Boolean =
            (radicand <= 1L || settings.keepRoot) &&
                (piPower == 0 || settings.keepSymbols) &&
                (ePower == 0 || settings.keepSymbols)

        fun render(): String {
            val symbols = buildString {
                if (piPower > 0) append(PI_SUFFIX[piPower])
                if (ePower > 0) append(E_SUFFIX[ePower])
                if (radicand > 1L) append('√').append(radicand)
            }
            val magnitude = when {
                symbols.isEmpty() -> numerator.toString()
                numerator == 1L -> symbols
                numerator == -1L -> "-$symbols"
                else -> "$numerator$symbols"
            }
            return if (denominator == 1L) magnitude else "$magnitude/$denominator"
        }

        companion object {
            private val PI_SUFFIX = arrayOf("", "π", "π²")
            private val E_SUFFIX = arrayOf("", "e", "e²")
        }
    }

    /**
     * 把源表达式分解为 [SymbolicForm]。
     *
     * **为什么不再按字符串前缀硬匹配**：旧实现只认 `√n` / `m×√n` / `√n×m` 三种字面形态，
     * 一旦根号与 π/e 同时出现（如 `π√2`）就全盘失效、回落到小数。
     * 现在把整个表达式当作**因子乘积**来读，因此 `π√2`、`2π√2`、`√2×√3`、`√8÷2` 都能化简。
     *
     * 出现加减、函数等无法用该式表达的构造时返回 null（回落小数）。
     * 解析完成后还会**用数值反验**：符号式的求值必须与真实结果一致，防住解析误读。
     */
    private fun symbolicForm(source: String, value: Double): SymbolicForm? {
        val s = source.replace(" ", "")
        if (s.isEmpty()) return null

        var numerator = 1L
        var denominator = 1L
        var radicand = 1L
        var piPower = 0
        var ePower = 0
        var index = 0
        var sawFactor = false

        while (index < s.length) {
            var divide = false
            when (s[index]) {
                '×' -> index++
                '÷' -> { divide = true; index++ }
            }
            if (index >= s.length) return null
            val direction = if (divide) -1 else 1

            when {
                s[index] == 'π' -> { piPower += direction; index++ }
                s[index] == 'e' -> { ePower += direction; index++ }
                s[index] == '√' -> {
                    // ÷√n 需要分母有理化，超出本式的表达能力
                    if (divide) return null
                    index++
                    val read = readInteger(s, index) ?: return null
                    if (read.first < 1L) return null
                    radicand = multiplyCapped(radicand, read.first) ?: return null
                    index = read.second
                }
                s[index].isDigit() -> {
                    val read = readInteger(s, index) ?: return null
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

        // 没有任何符号可保留时交给常规格式化，
        // 否则会把 `10÷4` 这种纯有理式显示成 `5/2`
        if (radicand <= 1L && piPower == 0 && ePower == 0) return null

        val divisor = gcd(abs(numerator), denominator)
        if (divisor > 1L) {
            numerator /= divisor
            denominator /= divisor
        }
        if (abs(numerator) > MAX_SYMBOLIC_NUMERATOR) return null

        val form = SymbolicForm(numerator, denominator, radicand, piPower, ePower)
        val tolerance = 1e-9 * maxOf(1.0, abs(value))
        if (abs(form.numericValue() - value) > tolerance) return null
        return form
    }

    /**
     * 读一个非负整数，允许写成 `n` 或 `(n)`（函数页的括号补全会产生后者）。
     * 返回 (值, 下一位置)；读不到或超限时返回 null。
     */
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
            if (acc > MAX_SYMBOLIC_FACTOR) return null
            i++
        }
        if (i == from) return null
        if (closeIndex >= 0) {
            if (i != closeIndex) return null
            i = closeIndex + 1
        }
        return acc to i
    }

    private fun multiplyCapped(a: Long, b: Long): Long? {
        if (b <= 0L) return null
        if (a > MAX_SYMBOLIC_FACTOR / b) return null
        return a * b
    }

    /** n = outside² · inside，inside 无平方因子 */
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
