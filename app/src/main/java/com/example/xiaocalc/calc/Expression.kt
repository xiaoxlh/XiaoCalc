package com.example.xiaocalc.calc

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 角度单位：显式设置项（旧版错误地用"保留特殊符号"开关兼职控制弧度，这里修正）。 */
enum class AngleUnit { DEG, RAD }

/**
 * 表达式求值器。
 *
 * 相比旧实现（逐字符裸递归、`readName` 贪吃字母、异常吞成 "错误"）：
 *  1. 先做词法分析得到记号流，语法错误能定位到具体位置；
 *  2. 语法层级明确：加 → 乘 → 一元 → 幂 → 后缀 → 基本项，
 *     其中 `^` 右结合、`-2^2 = -4`、隐式乘法（`2π`、`2(3+4)`、`(1+2)(3+4)`）显式建模；
 *  3. `%` 按计算器惯例实现：`50%` = 0.5，`200+10%` = 220，`200×10%` = 20；
 *  4. 纯 Kotlin，无 Android 依赖，可直接跑 JVM 单元测试。
 */
class ExpressionEvaluator(
    private val source: String,
    private val angleUnit: AngleUnit = AngleUnit.DEG,
) {

    // ---------------------------------------------------------------- 记号

    private enum class Kind {
        NUM, PLUS, MINUS, MUL, DIV, POW, PCT, FACT,
        LPAREN, RPAREN, SQRT, FUNC, CONST, MOD, END,
    }

    private class Token(
        val kind: Kind,
        val pos: Int,
        val num: Double = 0.0,
        val name: String = "",
    )

    private val tokens: List<Token> = tokenize(source)
    private var index = 0

    /** 当前记号之后的"一元操作数是否恰好是一个百分数"（`%` 语义需要） */
    private var lastOperandWasPercent = false

    /** 递归深度上限，防止构造出的畸形表达式打爆栈 */
    private var depth = 0

    // ---------------------------------------------------------------- 对外接口

    fun evaluate(): Double {
        if (tokens.size <= 1) throw CalcException(CalcError.EMPTY, 0)
        val value = additive()
        val rest = peek()
        if (rest.kind != Kind.END) {
            throw CalcException(
                if (rest.kind == Kind.RPAREN) CalcError.UNMATCHED_PAREN else CalcError.UNEXPECTED_TOKEN,
                rest.pos,
            )
        }
        if (!value.isFinite()) throw CalcException(CalcError.NOT_FINITE, source.length)
        return value
    }

    // ---------------------------------------------------------------- 词法

    private fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>(text.length + 1)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++

                // 注意：π 在 Java/Kotlin 里 isLetter() == true，必须先于字母分支判断，
                // 否则会被当成函数名读出 "π" 并报 UNKNOWN_FUNCTION
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
                    val value = literal.toDoubleOrNull()
                        ?: throw CalcException(CalcError.BAD_CHARACTER, start)
                    out += Token(Kind.NUM, start, num = value)
                }

                c.isLetter() -> {
                    val start = i
                    while (i < text.length && text[i].isLetter() && text[i] != 'π') i++
                    val name = text.substring(start, i).lowercase()
                    when (name) {
                        "sin", "cos", "tan", "ln" -> out += Token(Kind.FUNC, start, name = name)
                        "mod" -> out += Token(Kind.MOD, start)
                        "pi" -> out += Token(Kind.CONST, start, name = "pi")
                        "e" -> out += Token(Kind.CONST, start, name = "e")
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

    // ---------------------------------------------------------------- 语法

    private fun peek(): Token = tokens[index]

    private fun advance(): Token = tokens[index++]

    private fun guard() {
        if (++depth > MAX_DEPTH) throw CalcException(CalcError.TOO_LONG, peek().pos)
    }

    private fun additive(): Double {
        guard()
        var value = multiplicative()
        while (true) {
            when (peek().kind) {
                Kind.PLUS -> {
                    advance()
                    val base = value
                    val operand = multiplicative()
                    value = base + if (lastOperandWasPercent) base * operand else operand
                }
                Kind.MINUS -> {
                    advance()
                    val base = value
                    val operand = multiplicative()
                    value = base - if (lastOperandWasPercent) base * operand else operand
                }
                else -> {
                    depth--
                    return value
                }
            }
        }
    }

    private fun multiplicative(): Double {
        guard()
        var value = unary()
        var onlyPercent = lastOperandWasPercent
        var combined = false
        while (true) {
            when (peek().kind) {
                Kind.MUL -> {
                    advance()
                    value *= unary()
                    combined = true
                }
                Kind.DIV -> {
                    advance()
                    val divisor = unary()
                    if (divisor == 0.0) throw CalcException(CalcError.DIVIDE_BY_ZERO, peek().pos)
                    value /= divisor
                    combined = true
                }
                Kind.MOD -> {
                    advance()
                    val divisor = unary()
                    if (divisor == 0.0) throw CalcException(CalcError.DIVIDE_BY_ZERO, peek().pos)
                    value %= divisor
                    combined = true
                }
                // 隐式乘法：2π、2(3+4)、(1+2)(3+4)、2√9
                else -> if (startsPrimary(peek().kind)) {
                    value *= unary()
                    combined = true
                } else {
                    lastOperandWasPercent = onlyPercent && !combined
                    depth--
                    return value
                }
            }
        }
    }

    private fun unary(): Double {
        guard()
        return when (peek().kind) {
            Kind.PLUS -> {
                advance()
                unary()
            }
            // 一元负号不改变"是否为百分数"（-10% 仍是百分数）
            Kind.MINUS -> {
                advance()
                -unary()
            }
            else -> power()
        }.also { depth-- }
    }

    private fun power(): Double {
        guard()
        val base = postfix()
        if (peek().kind == Kind.POW) {
            advance()
            val exponent = unary() // 右结合，且允许 2^-3
            lastOperandWasPercent = false
            val result = base.pow(exponent)
            depth--
            return result
        }
        depth--
        return base
    }

    private fun postfix(): Double {
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
                    value /= 100.0
                    sawPercent = true
                }
                else -> {
                    lastOperandWasPercent = sawPercent
                    return value
                }
            }
        }
    }

    private fun primary(): Double {
        val token = peek()
        return when (token.kind) {
            Kind.NUM -> {
                advance()
                token.num
            }
            Kind.CONST -> {
                advance()
                if (token.name == "pi") PI else E
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
                if (radicand < 0.0) throw CalcException(CalcError.NEGATIVE_SQRT, token.pos)
                sqrt(radicand)
            }
            Kind.FUNC -> {
                advance()
                applyFunction(token)
            }
            Kind.END -> throw CalcException(CalcError.MISSING_OPERAND, token.pos)
            else -> throw CalcException(CalcError.UNEXPECTED_TOKEN, token.pos)
        }
    }

    private fun applyFunction(token: Token): Double {
        val argument = if (peek().kind == Kind.LPAREN) {
            advance()
            val value = additive()
            expect(Kind.RPAREN)
            value
        } else {
            // 允许省略括号：sin30、cosπ
            unary()
        }
        // 角度换算只对三角函数成立；`ln` 之类的函数与角度单位无关，
        // 因此换算值留在各分支里取，不能提前算好套给所有函数。
        val radians = if (angleUnit == AngleUnit.DEG) Math.toRadians(argument) else argument
        return when (token.name) {
            "sin" -> sin(radians)
            "cos" -> cos(radians)
            "tan" -> {
                if (abs(cos(radians)) < 1e-12) throw CalcException(CalcError.NOT_FINITE, token.pos)
                tan(radians)
            }
            // 自然对数定义域为 (0, +∞)，越界按"结果非有限"报错
            "ln" -> {
                if (argument <= 0.0) throw CalcException(CalcError.NOT_FINITE, token.pos)
                ln(argument)
            }
            else -> throw CalcException(CalcError.UNKNOWN_FUNCTION, token.pos)
        }
    }

    private fun expect(kind: Kind) {
        if (peek().kind != kind) throw CalcException(CalcError.UNMATCHED_PAREN, peek().pos)
        advance()
    }

    private fun startsPrimary(kind: Kind): Boolean = when (kind) {
        Kind.NUM, Kind.CONST, Kind.LPAREN, Kind.SQRT, Kind.FUNC -> true
        else -> false
    }

    private fun factorial(value: Double): Double {
        if (value < 0.0 || value > 170.0 || value % 1.0 != 0.0) {
            throw CalcException(CalcError.FACTORIAL_DOMAIN, peek().pos)
        }
        var result = 1.0
        for (i in 2..value.toInt()) result *= i
        return result
    }

    companion object {
        private const val MAX_DEPTH = 96

        /** 把用户可见的漂亮写法还原成解析器能处理的纯 ASCII 形式。 */
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
