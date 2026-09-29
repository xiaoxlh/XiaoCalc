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

    private const val MAX_SYMBOLIC_DENOMINATOR = 12L

    fun format(value: Double, settings: CalcSettings, source: String = ""): String {
        if (value.isNaN()) return "NaN"
        if (value == 0.0) return "0"

        if (settings.keepRoot) radical(source)?.let { return applyGroupingIfPlain(it, settings) }
        if (settings.keepSymbols) symbolic(source, value)?.let { return it }

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

    // ------------------------------------------------------------- 根号符号化

    /**
     * 当源表达式形如 `√n` / `m×√n` / `√n×m` 且 n 为整数时，输出最简根式。
     * 例：`√8 → 2√2`，`√9 → 3`，`2×√18 → 6√2`。
     */
    private fun radical(source: String): String? {
        val s = source.replace(" ", "")
        val marker = s.indexOf('√')
        if (marker < 0 || s.indexOf('√', marker + 1) >= 0) return null

        val before = s.substring(0, marker)
        var after = s.substring(marker + 1)

        var coefficient = when {
            before.isEmpty() -> 1L
            before == "-" -> -1L
            before.endsWith("×") -> before.dropLast(1).toLongOrNull() ?: return null
            else -> return null
        }

        // √n×m 形态：把后置系数并入总系数
        val timesIndex = after.indexOf('×')
        if (timesIndex > 0) {
            val trailing = after.substring(timesIndex + 1).toLongOrNull() ?: return null
            coefficient *= trailing
            after = after.substring(0, timesIndex)
        }

        val radicand = after.toLongOrNull() ?: return null
        if (radicand < 1) return null

        val (outside, inside) = simplifyRadical(radicand)
        val totalOutside = coefficient * outside
        return when {
            inside == 1L -> totalOutside.toString()
            totalOutside == 1L -> "√$inside"
            totalOutside == -1L -> "-√$inside"
            else -> "${totalOutside}√$inside"
        }
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

    // --------------------------------------------------------- π / e 符号化

    /**
     * 当源表达式含 π 或 e，且结果恰为它们的"简单有理倍数"（分母 ≤ 12）时保留符号。
     * 例：`2×π → 2π`，`π÷2 → π/2`，`π+1` 不满足 → 回落到小数。
     */
    private fun symbolic(source: String, value: Double): String? {
        if (value == 0.0) return null
        for ((symbol, constant) in SYMBOLIC_CONSTANTS) {
            if (!source.contains(symbol)) continue
            val (numerator, denominator) = rationalMultiple(value, constant) ?: continue
            return when {
                denominator == 1L && numerator == 1L -> symbol
                denominator == 1L && numerator == -1L -> "-$symbol"
                denominator == 1L -> "$numerator$symbol"
                numerator == 1L -> "$symbol/$denominator"
                numerator == -1L -> "-$symbol/$denominator"
                else -> "$numerator$symbol/$denominator"
            }
        }
        return null
    }

    private val SYMBOLIC_CONSTANTS = listOf("π" to PI, "e" to E)

    /** 把 value 表示为 constant 的既约有理倍数 p/q；找不到简洁表示时返回 null。 */
    private fun rationalMultiple(value: Double, constant: Double): Pair<Long, Long>? {
        for (denominator in 1L..MAX_SYMBOLIC_DENOMINATOR) {
            val scaled = value * denominator / constant
            val numerator = scaled.roundToLong()
            if (numerator == 0L) continue
            val tolerance = 1e-9 * maxOf(1.0, abs(numerator.toDouble()))
            if (abs(scaled - numerator) > tolerance) continue

            val divisor = gcd(abs(numerator), denominator)
            val p = numerator / divisor
            val q = denominator / divisor
            if (abs(p) > 9999L) return null
            return p to q
        }
        return null
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
