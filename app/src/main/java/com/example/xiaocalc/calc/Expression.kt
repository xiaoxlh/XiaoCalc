package com.example.xiaocalc.calc

import java.math.BigDecimal

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
 *
 * v1.2.0 起数值类型由 `Double` 换成 [BigDecimal]，全部运算经 [Dec] 走 50 位工作精度。
 * 语法与语义**没有**任何改动——换的只是数字的表示。
 */
class ExpressionEvaluator(
    private val source: String,
    private val angleUnit: AngleUnit = AngleUnit.DEG,
    /**
     * 变量取值表。
     *
     * 只有出现在这里的单字母才会被当作变量——否则 `x` 依旧报"未知函数"。
     * 这样主计算器（不传此参数）的行为与之前完全一致，
     * 而方程求解可以传入 `x=2` 之类的绑定来复用同一套语法分析。
     */
    private val variables: Map<String, BigDecimal> = emptyMap(),
) {

    // ---------------------------------------------------------------- 记号

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

    /** 当前记号之后的"一元操作数是否恰好是一个百分数"（`%` 语义需要） */
    private var lastOperandWasPercent = false

    /** 递归深度上限，防止构造出的畸形表达式打爆栈 */
    private var depth = 0

    // ---------------------------------------------------------------- 对外接口

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
                    // 与旧版一样，"." / ".." 这类非法字面量在这里被拒
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
                        // 单字母且已被声明为变量才算变量；未声明的仍按旧行为报"未知函数"
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

    // ---------------------------------------------------------------- 语法

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
                    // remainder 不传 MathContext：取余是精确运算，不该被截断
                    value = value.remainder(divisor)
                    combined = true
                }
                // 隐式乘法：2π、2(3+4)、(1+2)(3+4)、2√9
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
            // 一元负号不改变"是否为百分数"（-10% 仍是百分数）
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
            val exponent = unary() // 右结合，且允许 2^-3
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
                // 记号化阶段已确认该名字在 variables 里，这里只是取值
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
            // 允许省略括号：sin30、cosπ
            unary()
        }
        // 角度换算只对三角函数成立；`ln` 之类的函数与角度单位无关，
        // 因此换算值留在各分支里取，不能提前算好套给所有函数。
        return when (token.name) {
            "sin" -> math(token.pos) { Dec.sin(toRadians(argument)) }
            "cos" -> math(token.pos) { Dec.cos(toRadians(argument)) }
            "tan" -> math(token.pos) { Dec.tan(toRadians(argument)) }
            // 自然对数定义域为 (0, +∞)，越界按"结果非有限"报错
            "ln" -> {
                if (argument.signum() <= 0) throw CalcException(CalcError.NOT_FINITE, token.pos)
                math(token.pos) { Dec.ln(argument) }
            }
            // 常用对数（以 10 为底）：ln 的天然搭档，函数页此前缺的就是它
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

    // ---------------------------------------------------------------- 数值

    private fun toRadians(angle: BigDecimal): BigDecimal =
        if (angleUnit == AngleUnit.DEG) {
            Dec.div(Dec.mul(angle, Dec.PI), HUNDRED_EIGHTY)
        } else {
            angle
        }

    /**
     * 把内核抛出的算术异常翻译成计算器的语义错误。
     *
     * [Dec] 保持"纯数学内核"的身份，只说 `ArithmeticException`；
     * 该映射成"结果非有限"还是别的，由这一层决定。
     */
    private inline fun math(pos: Int, block: () -> BigDecimal): BigDecimal = try {
        block()
    } catch (_: ArithmeticException) {
        throw CalcException(CalcError.NOT_FINITE, pos)
    }

    /** 结果量级保护：`9^9^9` 这类输入不允许把内存吃光。 */
    private fun guardMagnitude(v: BigDecimal): BigDecimal {
        if (v.precision() - v.scale() > MAX_RESULT_DIGITS) {
            throw CalcException(CalcError.NOT_FINITE, source.length)
        }
        return v
    }

    companion object {
        private const val MAX_DEPTH = 96

        /** 结果整数部分的位数上限（1e100000 量级），挡病态输入 */
        private const val MAX_RESULT_DIGITS = 100_000

        private val MAX_FACTORIAL: BigDecimal = BigDecimal.valueOf(170)
        private val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
        private val HUNDRED_EIGHTY: BigDecimal = BigDecimal.valueOf(180)

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
