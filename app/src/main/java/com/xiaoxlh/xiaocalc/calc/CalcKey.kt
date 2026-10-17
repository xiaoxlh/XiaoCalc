package com.xiaoxlh.xiaocalc.calc

enum class KeyKind { DIGIT, OPERATOR, FUNCTION, EQUALS, ACTION }

enum class KeypadPage { MAIN, FUNCTIONS }

sealed class CalcKey(
    val label: String,
    val kind: KeyKind,

    val insert: String? = null,
) {
    data class Digit(val value: Char) : CalcKey(value.toString(), KeyKind.DIGIT, value.toString())

    object Dot : CalcKey(".", KeyKind.DIGIT, ".")
    object Plus : CalcKey("+", KeyKind.OPERATOR, "+")
    object Minus : CalcKey("-", KeyKind.OPERATOR, "-")
    object Times : CalcKey("×", KeyKind.OPERATOR, "×")
    object Divide : CalcKey("÷", KeyKind.OPERATOR, "÷")
    object Power : CalcKey("^", KeyKind.OPERATOR, "^")

    object Percent : CalcKey("%", KeyKind.FUNCTION, "%")
    object Factorial : CalcKey("!", KeyKind.FUNCTION, "!")
    object Mod : CalcKey("mod", KeyKind.OPERATOR, "mod")

    object LParen : CalcKey("(", KeyKind.FUNCTION, "(")
    object RParen : CalcKey(")", KeyKind.FUNCTION, ")")
    object SquareRoot : CalcKey("√", KeyKind.FUNCTION, "√")
    object Pi : CalcKey("π", KeyKind.FUNCTION, "π")
    object Euler : CalcKey("e", KeyKind.FUNCTION, "e")

    class Fn(val name: String) : CalcKey(name, KeyKind.FUNCTION, "$name(")

    object Ln : CalcKey("ln", KeyKind.FUNCTION, "ln(")
    object Log : CalcKey("log", KeyKind.FUNCTION, "log(")

    object Equals : CalcKey("=", KeyKind.EQUALS)

    object VariableX : CalcKey("x", KeyKind.DIGIT, "x")

    object EqualsSign : CalcKey("=", KeyKind.DIGIT, "=")

    object Clear : CalcKey("清空", KeyKind.ACTION)
    object Backspace : CalcKey("退格", KeyKind.ACTION)
    object AngleUnit : CalcKey("deg", KeyKind.ACTION)
    object PageMain : CalcKey("123", KeyKind.ACTION)
    object PageFunctions : CalcKey("fx", KeyKind.ACTION)
}

val MainKeypad: List<CalcKey> = listOf(
    CalcKey.Digit('7'), CalcKey.Digit('8'), CalcKey.Digit('9'), CalcKey.Divide,
    CalcKey.Digit('4'), CalcKey.Digit('5'), CalcKey.Digit('6'), CalcKey.Times,
    CalcKey.Digit('1'), CalcKey.Digit('2'), CalcKey.Digit('3'), CalcKey.Minus,
    CalcKey.Digit('0'), CalcKey.Dot, CalcKey.Equals, CalcKey.Plus,
)

val FunctionKeypad: List<CalcKey> = listOf(
    CalcKey.LParen, CalcKey.RParen, CalcKey.SquareRoot, CalcKey.Power,
    CalcKey.Fn("sin"), CalcKey.Fn("cos"), CalcKey.Fn("tan"), CalcKey.Mod,
    CalcKey.Percent, CalcKey.Factorial, CalcKey.Pi, CalcKey.Euler,
    CalcKey.AngleUnit, CalcKey.Ln, CalcKey.Log, CalcKey.Equals,
)

fun keypadFor(page: KeypadPage): List<CalcKey> = when (page) {
    KeypadPage.MAIN -> MainKeypad
    KeypadPage.FUNCTIONS -> FunctionKeypad
}

val EquationKeypad: List<CalcKey> = listOf(
    CalcKey.Digit('7'), CalcKey.Digit('8'), CalcKey.Digit('9'), CalcKey.Divide,
    CalcKey.Digit('4'), CalcKey.Digit('5'), CalcKey.Digit('6'), CalcKey.Times,
    CalcKey.Digit('1'), CalcKey.Digit('2'), CalcKey.Digit('3'), CalcKey.Minus,
    CalcKey.Digit('0'), CalcKey.Dot, CalcKey.VariableX, CalcKey.Plus,
)
