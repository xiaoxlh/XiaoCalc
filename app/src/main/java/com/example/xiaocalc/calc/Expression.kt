package com.example.xiaocalc.calc

import java.math.BigDecimal

enum class AngleUnit { DEG, RAD }

class ExpressionEvaluator(
    private val source: String,
    private val angleUnit: AngleUnit = AngleUnit.DEG,

    private val variables: Map<String, BigDecimal> = emptyMap(),
) {
    private enum class Kind {
        NUM, PLUS, MINUS, MUL, DIV, POW, PCT, FACT,
        LPAREN, RPAREN, SQRT, FUNC, CONST, MOD, VAR, END,
    }

    private class Token(
        val kind: Kind,
        val pos: Int,
        val num: BigDecimal = BigDecimal.ZERO,
        val name: String = "",
    )

    private val tokens: List<Token> = tokenize(source)
    private var index = 0

    private var lastOperandWasPercent = false

    private var depth = 0

    fun evaluate(): BigDecimal {
        if (tokens.size <= 1) throw CalcException(CalcError.EMPTY, 0)
        val value = additive()
        val rest = peek()
        if (rest.kind != Kind.END) {
            throw CalcException(
                if (rest.kind == Kind.RPAREN) CalcError.UNMATCHED_PAREN else CalcError.UNEXPECTED_TOKEN,
                rest.pos,
            )
        }
        return guardMagnitude(value)
    }

    private fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>(text.length + 1)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == 'π' -> {
                    out += Token(Kind.CONST, i, name = "pi")
                    i++
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    var seenDot = false
                    while (i < text.length && (text[i].isDigit() || (text[i] == '.' && !seenDot))) {
                        if (text[i] == '.') seenDot = true
                        i++
                    }
                    val literal = text.substring(start, i)
                    val value = literal.toBigDecimalOrNull()
                        ?: throw CalcException(CalcError.BAD_CHARACTER, start)
                    out += Token(Kind.NUM, start, num = value)
                }
                c.isLetter() -> {
                    val start = i
                    while (i < text.length && text[i].isLetter() && text[i] != 'π') i++
                    val raw = text.substring(start, i)
                    val name = raw.lowercase()
                    when {
                        name == "sin" || name == "cos" || name == "tan" || name == "ln" || name == "log" ->
                            out += Token(Kind.FUNC, start, name = name)
                        name == "mod" -> out += Token(Kind.MOD, start)
                        name == "pi" -> out += Token(Kind.CONST, start, name = "pi")
                        name == "e" -> out += Token(Kind.CONST, start, name = "e")
                        raw.length == 1 && name in variables -> out += Token(Kind.VAR, start, name = name)
                        else -> throw CalcException(CalcError.UNKNOWN_FUNCTION, start)
                    }
                }
                else -> {
                    val kind = when (c) {
                        '+' -> Kind.PLUS
                        '-', '−' -> Kind.MINUS
                        '×', '*', '·' -> Kind.MUL
                        '÷', '/' -> Kind.DIV
                        '^' -> Kind.POW
                        '%' -> Kind.PCT
                        '!' -> Kind.FACT
                        '(' -> Kind.LPAREN
                        ')' -> Kind.RPAREN
                        '√' -> Kind.SQRT
                        else -> throw CalcException(CalcError.BAD_CHARACTER, i)
                    }
                    out += Token(kind, i)
                    i++
                }
            }
        }
        out += Token(Kind.END, text.length)
        return out
    }

    private fun peek(): Token = tokens[index]

    private fun advance(): Token = tokens[index++]

    private fun guard() {
        if (++depth > MAX_DEPTH) throw CalcException(CalcError.TOO_LONG, peek().pos)
    }

    private fun additive(): BigDecimal {
        guard()
        var value = multiplicative()
        while (true) {
            when (peek().kind) {
                Kind.PLUS -> {
                    advance()
                    val base = value
                    val operand = multiplicative()
                    value = if (lastOperandWasPercent) {
                        Dec.add(base, Dec.mul(base, operand))
                    } else {
                        Dec.add(base, operand)
                    }
                }
                Kind.MINUS -> {
                    advance()
                    val base = value
                    val operand = multiplicative()
                    value = if (lastOperandWasPercent) {
                        Dec.sub(base, Dec.mul(base, operand))
                    } else {
                        Dec.sub(base, operand)
                    }
                }
                else -> {
                    depth--
                    return value
                }
            }
        }
    }

    private fun multiplicative(): BigDecimal {
        guard()
        var value = unary()
        val onlyPercent = lastOperandWasPercent
        var combined = false
        while (true) {
            when (peek().kind) {
                Kind.MUL -> {
                    advance()
                    value = Dec.mul(value, unary())
                    combined = true
                }
                Kind.DIV -> {
                    advance()
                    val divisor = unary()
                    if (divisor.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO, peek().pos)
                    value = Dec.div(value, divisor)
                    combined = true
                }
                Kind.MOD -> {
                    advance()
                    val divisor = unary()
                    if (divisor.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO, peek().pos)
                    value = value.remainder(divisor)
                    combined = true
                }
                else -> if (startsPrimary(peek().kind)) {
                    value = Dec.mul(value, unary())
                    combined = true
                } else {
                    lastOperandWasPercent = onlyPercent && !combined
                    depth--
                    return value
                }
            }
        }
    }

    private fun unary(): BigDecimal {
        guard()
        return when (peek().kind) {
            Kind.PLUS -> {
                advance()
                unary()
            }
            Kind.MINUS -> {
                advance()
                unary().negate()
            }
            else -> power()
        }.also { depth-- }
    }

    private fun power(): BigDecimal {
        guard()
        val base = postfix()
        if (peek().kind == Kind.POW) {
            advance()
            val exponent = unary()
            lastOperandWasPercent = false
            val result = math(peek().pos) { Dec.pow(base, exponent) }
            depth--
            return result
        }
        depth--
        return base
    }

    private fun postfix(): BigDecimal {
        var value = primary()
        var sawPercent = false
        while (true) {
            when (peek().kind) {
                Kind.FACT -> {
                    advance()
                    value = factorial(value)
                }
                Kind.PCT -> {
                    advance()
                    value = Dec.div(value, HUNDRED)
                    sawPercent = true
                }
                else -> {
                    lastOperandWasPercent = sawPercent
                    return value
                }
            }
        }
    }

    private fun primary(): BigDecimal {
        val token = peek()
        return when (token.kind) {
            Kind.NUM -> {
                advance()
                token.num
            }
            Kind.CONST -> {
                advance()
                if (token.name == "pi") Dec.PI else Dec.E
            }
            Kind.VAR -> {
                advance()
                variables.getValue(token.name)
            }
            Kind.LPAREN -> {
                advance()
                val value = additive()
                expect(Kind.RPAREN)
                value
            }
            Kind.SQRT -> {
                advance()
                val radicand = unary()
                if (radicand.signum() < 0) throw CalcException(CalcError.NEGATIVE_SQRT, token.pos)
                Dec.sqrt(radicand)
            }
            Kind.FUNC -> {
                advance()
                applyFunction(token)
            }
            Kind.END -> throw CalcException(CalcError.MISSING_OPERAND, token.pos)
            else -> throw CalcException(CalcError.UNEXPECTED_TOKEN, token.pos)
        }
    }

    private fun applyFunction(token: Token): BigDecimal {
        val argument = if (peek().kind == Kind.LPAREN) {
            advance()
            val value = additive()
            expect(Kind.RPAREN)
            value
        } else {
            unary()
        }
        return when (token.name) {
            "sin" -> math(token.pos) { Dec.sin(toRadians(argument)) }
            "cos" -> math(token.pos) { Dec.cos(toRadians(argument)) }
            "tan" -> math(token.pos) { Dec.tan(toRadians(argument)) }
            "ln" -> {
                if (argument.signum() <= 0) throw CalcException(CalcError.NOT_FINITE, token.pos)
                math(token.pos) { Dec.ln(argument) }
            }
            "log" -> {
                if (argument.signum() <= 0) throw CalcException(CalcError.NOT_FINITE, token.pos)
                math(token.pos) { Dec.log10(argument) }
            }
            else -> throw CalcException(CalcError.UNKNOWN_FUNCTION, token.pos)
        }
    }

    private fun expect(kind: Kind) {
        if (peek().kind != kind) throw CalcException(CalcError.UNMATCHED_PAREN, peek().pos)
        advance()
    }

    private fun startsPrimary(kind: Kind): Boolean = when (kind) {
        Kind.NUM, Kind.CONST, Kind.VAR, Kind.LPAREN, Kind.SQRT, Kind.FUNC -> true
        else -> false
    }

    private fun factorial(value: BigDecimal): BigDecimal {
        val stripped = value.stripTrailingZeros()
        if (stripped.scale() > 0 || stripped.signum() < 0 ||
            stripped.compareTo(MAX_FACTORIAL) > 0
        ) {
            throw CalcException(CalcError.FACTORIAL_DOMAIN, peek().pos)
        }
        return Dec.factorial(stripped)
    }

    private fun toRadians(angle: BigDecimal): BigDecimal =
        if (angleUnit == AngleUnit.DEG) {
            Dec.div(Dec.mul(angle, Dec.PI), HUNDRED_EIGHTY)
        } else {
            angle
        }

    private inline fun math(pos: Int, block: () -> BigDecimal): BigDecimal = try {
        block()
    } catch (_: ArithmeticException) {
        throw CalcException(CalcError.NOT_FINITE, pos)
    }

    private fun guardMagnitude(v: BigDecimal): BigDecimal {
        if (v.precision() - v.scale() > MAX_RESULT_DIGITS) {
            throw CalcException(CalcError.NOT_FINITE, source.length)
        }
        return v
    }

    companion object {
        private const val MAX_DEPTH = 96
        private const val MAX_RESULT_DIGITS = 100_000
        private val MAX_FACTORIAL: BigDecimal = BigDecimal.valueOf(170)
        private val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
        private val HUNDRED_EIGHTY: BigDecimal = BigDecimal.valueOf(180)
        fun normalize(text: String): String = buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        '−' -> '-'
                        '*' -> '×'
                        '/' -> '÷'
                        else -> c
                    },
                )
            }
        }
    }
}
