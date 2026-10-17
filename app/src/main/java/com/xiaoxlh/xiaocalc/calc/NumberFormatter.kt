package com.xiaoxlh.xiaocalc.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object NumberFormatter {
    const val SIGNIFICANT_DIGITS = Dec.DISPLAY_DIGITS

    private const val MANTISSA_DECIMALS = 15

    private val SCI_UPPER: BigDecimal = BigDecimal("1E10")

    private val SCI_LOWER: BigDecimal = BigDecimal("1E-9")

    private val SCI_SETTING_THRESHOLD: BigDecimal = BigDecimal("1E4")

    private val TEN: BigDecimal = BigDecimal.TEN

    fun format(value: BigDecimal, settings: CalcSettings, source: String = ""): String {
        if (value.signum() == 0) return "0"
        if (settings.keepRoot || settings.keepSymbols) {
            SymbolicFormatter.format(safeDouble(value), settings, source)?.let {
                return applyGroupingIfPlain(it, settings)
            }
        }
        val magnitude = value.abs()
        if (settings.scientific && magnitude >= SCI_SETTING_THRESHOLD) return scientific(value)
        if (magnitude >= SCI_UPPER || magnitude < SCI_LOWER) return scientific(value)
        val plain = decimal(value, settings.decimals)
        return if (settings.groupDigits) groupThousands(plain) else plain
    }

    fun format(value: Double, settings: CalcSettings, source: String = ""): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "∞"
        value == Double.NEGATIVE_INFINITY -> "-∞"
        else -> format(BigDecimal(value), settings, source)
    }

    fun plainForReuse(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val magnitude = value.abs()
        if (magnitude >= SCI_UPPER || magnitude < SCI_LOWER) {
            return scientificPlain(value)
        }
        return decimal(value, Dec.REUSE_DIGITS)
    }

    fun plainForReuse(value: Double): String =
        if (value.isFinite()) plainForReuse(BigDecimal(value)) else ""

    private fun decimal(value: BigDecimal, decimals: Int): String {
        val limit = decimals.coerceIn(0, CalcSettings.FULL)
        val rounded = if (limit >= CalcSettings.FULL) {
            value.round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
        } else {
            val scaled = value.setScale(limit, RoundingMode.HALF_UP)
            // a non-zero value must never be shown as 0
            if (scaled.signum() == 0 && value.signum() != 0) return scientific(value)
            scaled
        }.stripTrailingZeros()
        var text = rounded.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text.ifEmpty { "0" }
    }

    private fun scientific(value: BigDecimal): String {
        val (mantissa, exponent) = mantissaAndExponent(value)
        return "$mantissa×10${superscript(exponent)}"
    }

    private fun scientificPlain(value: BigDecimal): String {
        val (mantissa, exponent) = mantissaAndExponent(value)
        return "$mantissa×10^$exponent"
    }

    private fun mantissaAndExponent(value: BigDecimal): Pair<String, Int> {
        var exponent = decimalExponent(value)
        var mantissa = value.movePointLeft(exponent)
            .round(MathContext(MANTISSA_DECIMALS, RoundingMode.HALF_UP))
            .stripTrailingZeros()
        if (mantissa.abs() >= TEN) {
            mantissa = mantissa.movePointLeft(1).stripTrailingZeros()
            exponent += 1
        }
        var text = mantissa.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text to exponent
    }

    private fun decimalExponent(value: BigDecimal): Int =
        value.precision() - value.scale() - 1

    private fun safeDouble(value: BigDecimal): Double {
        val d = value.toDouble()
        return if (d.isFinite()) d else Double.NaN
    }

    private val SUPERSCRIPTS = mapOf(
        '-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    )

    private val SUPERSCRIPT_TO_ASCII = SUPERSCRIPTS.entries.associate { (k, v) -> v to k }

    fun superscript(exponent: Int): String = buildString {
        for (c in exponent.toString()) SUPERSCRIPTS[c]?.let { append(it) }
    }

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

    internal fun simplifyRadical(n: Long): Pair<Long, Long> = SymbolicFormatter.simplifyRadical(n)
}
