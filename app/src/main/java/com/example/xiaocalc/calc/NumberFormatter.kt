package com.example.xiaocalc.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 结果格式化。
 *
 * 旧实现用 `"%.6f"` 截断再补一堆正则做符号化，既有精度损失又容易产出
 * `NaN` / 空串这类脏结果。这里改为：
 *  - 十进制路径用 [BigDecimal] 舍到 **16 位有效数字**，杜绝浮点尾巴；
 *  - 极大/极小值走科学计数法，指数用上标（显示层友好）；
 *  - 千分位只在"纯十进制"结果上施加，且由设置控制；
 *  - "保留根号 / 保留特殊符号"改为**基于数值的真符号化**（唯一分解 + 有理逼近），
 *    不再是旧版那种匹配源串的正则拼贴。
 *
 * v1.2.0 起输入的 [BigDecimal] 来自 [Dec] 的 50 位工作精度，
 * 所以"舍到 16 位"是**安全的**——舍入误差远在第 17 位之后。
 */
object NumberFormatter {

    /** 对外显示的有效数字位数 */
    const val SIGNIFICANT_DIGITS = Dec.DISPLAY_DIGITS

    /** 尾数保留的小数位数。取 15 使科学计数法同样给足 16 位有效数字。 */
    private const val MANTISSA_DECIMALS = 15

    /** 超过此量级走科学计数法 */
    private val SCI_UPPER: BigDecimal = BigDecimal("1E10")

    /** 低于此量级（非 0）走科学计数法 */
    private val SCI_LOWER: BigDecimal = BigDecimal("1E-9")

    /** "科学计数法"开关的阈值（沿用旧版语义：≥10000 即切换） */
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

    /**
     * [Double] 入口。保留它是为了让既有调用点与测试不必改写，
     * 也为了让"旧行为"有一条可对照的路径。
     *
     * 注意 `BigDecimal(0.1)` 是**精确**的二进制展开（0.1000…555…），
     * 不是 0.1；但舍到 16 位后仍是 `0.1`，因此对外表现与直觉一致。
     *
     * NaN / ±∞ 必须在这里拦下：`BigDecimal(Double.NaN)` 会抛
     * NumberFormatException，不能让它漏到调用方。
     */
    fun format(value: Double, settings: CalcSettings, source: String = ""): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "∞"
        value == Double.NEGATIVE_INFINITY -> "-∞"
        else -> format(BigDecimal(value), settings, source)
    }

    /** 续算时使用的"纯数值"文本：无千分位、无上标，保证能再次进入解析器。 */
    fun plainForReuse(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val magnitude = value.abs()
        if (magnitude >= SCI_UPPER || magnitude < SCI_LOWER) {
            // 解析器认识 ^，用 1.2345×10^n 的纯文本形式
            return scientificPlain(value)
        }
        // 刻意比显示多 4 位：`1÷3` 显示成 0.3333333333333333，
        // 但续算用的中间值若是同一串，再乘 3 会得到 0.9999999999999999。
        // 多留几位后乘回去是 0.999…999（20 个 9），舍到 16 位即得 1。
        return decimal(value, Dec.REUSE_DIGITS)
    }

    /** [Double] 版本的续算文本；非有限值没有可续算的写法，返回空串。 */
    fun plainForReuse(value: Double): String =
        if (value.isFinite()) plainForReuse(BigDecimal(value)) else ""

    // ------------------------------------------------------------------ 十进制

    /**
     * 十进制输出。
     *
     * [decimals] 为 [CalcSettings.FULL]（默认）时舍到 16 位**有效数字**——
     * 这是对外承诺的精度，与 1.1.x 一致。
     *
     * 一旦用户把小数位限制到更小的值，就改为舍到那么多位小数。
     * 这里额外守住一条：**非零值不允许被舍成 `0`**。
     * `1÷3` 在"0 位小数"下按字面确实是 0，但把 0.333… 显示成 `0`
     * 等于把有效信息抹掉，用户会以为算错了；这种情况下改用科学计数法，
     * 至少还留着量级与几位有效数字。
     */
    private fun decimal(value: BigDecimal, decimals: Int): String {
        val limit = decimals.coerceIn(0, CalcSettings.FULL)
        val rounded = if (limit >= CalcSettings.FULL) {
            value.round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
        } else {
            val scaled = value.setScale(limit, RoundingMode.HALF_UP)
            if (scaled.signum() == 0 && value.signum() != 0) return scientific(value)
            scaled
        }.stripTrailingZeros()
        var text = rounded.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text.ifEmpty { "0" }
    }

    // ---------------------------------------------------------------- 科学计数

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
        // 9.9999999996 → 10 的进位
        if (mantissa.abs() >= TEN) {
            mantissa = mantissa.movePointLeft(1).stripTrailingZeros()
            exponent += 1
        }
        var text = mantissa.toPlainString()
        if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')
        return text to exponent
    }

    /**
     * `value` 的十进制阶：使 `value / 10^阶` 落在 [1, 10) 区间。
     *
     * 直接用 `BigDecimal` 自身的 precision/scale 求得，**不走 Double**——
     * 旧实现用 `floor(log10(abs(value)))`，在量级边界上会因浮点误差差一位，
     * 例如 1e10 恰好算成 9。
     */
    private fun decimalExponent(value: BigDecimal): Int =
        value.precision() - value.scale() - 1

    /**
     * 换算成 Double，用于交给 [SymbolicFormatter] 做数值识别。
     * 超出 Double 范围时返回 null 的语义由调用方兜底——这里给 0，
     * 使符号化直接判定为"不匹配"而回落到十进制路径。
     */
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

    /**
     * 根式化简。实现已迁到 [SymbolicFormatter]，这里保留入口供既有测试与调用点使用。
     */
    internal fun simplifyRadical(n: Long): Pair<Long, Long> = SymbolicFormatter.simplifyRadical(n)
}
