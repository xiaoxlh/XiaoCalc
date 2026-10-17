package com.example.xiaocalc.calc

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

object SymbolicFormatter {
    private const val MAX_NUMERATOR = 99_999L

    private const val MAX_FACTOR = 999_999L

    private const val MAX_TERMS = 8

    fun format(value: Double, settings: CalcSettings, source: String): String? {
        if (value == 0.0) return null
        val terms = parseTerms(source) ?: return null
        if (terms.isEmpty()) return null
        if (terms.all { it.isPureRational() }) return null
        if (terms.any { !it.allowedBy(settings) }) return null
        val sum = terms.fold(0.0) { acc, t -> acc + t.numericValue() }
        val tolerance = 1e-9 * maxOf(1.0, abs(value))
        if (abs(sum - value) > tolerance) return null
        return render(terms)
    }

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

    private fun splitAdditive(s: String): List<Pair<Int, String>>? {
        val terms = ArrayList<Pair<Int, String>>()
        var depth = 0
        var sign = 1
        var start = 0
        var i = 0
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
                    if (i == start) return null
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
        if (radicand > 1L) {
            val (outside, inside) = simplifyRadical(radicand)
            numerator = multiplyCapped(numerator, outside) ?: return null
            radicand = inside
        }
        return Form.of(numerator, denominator, radicand, piPower, ePower)
    }

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
