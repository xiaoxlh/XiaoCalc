package com.example.xiaocalc.calc

/** 按键的视觉分组，决定配色层级（见 ui/components/Keypad.kt）。 */
enum class KeyKind { DIGIT, OPERATOR, FUNCTION, EQUALS, ACTION }

/** 键盘分页 */
enum class KeypadPage { MAIN, FUNCTIONS }

/**
 * 键盘按键模型。
 *
 * 旧实现的键位是"字符串数组 + 在 input() 里 when(value) 判断"，
 * 颜色靠 `label in arrayOf(...)` 反查——加一个键要改三处。
 * 这里按键自带类型与插入文本，键盘布局成为**数据**，页面只负责渲染。
 */
sealed class CalcKey(
    val label: String,
    val kind: KeyKind,
    /** 插入表达式的文本；null 表示由 [CalculatorState] 特殊处理 */
    val insert: String? = null,
) {

    data class Digit(val value: Char) : CalcKey(value.toString(), KeyKind.DIGIT, value.toString())

    object Dot : CalcKey(".", KeyKind.DIGIT, ".")
    object Plus : CalcKey("+", KeyKind.OPERATOR, "+")
    object Minus : CalcKey("-", KeyKind.OPERATOR, "-")
    object Times : CalcKey("×", KeyKind.OPERATOR, "×")
    object Divide : CalcKey("÷", KeyKind.OPERATOR, "÷")
    object Power : CalcKey("^", KeyKind.OPERATOR, "^")
    object Percent : CalcKey("%", KeyKind.OPERATOR, "%")
    object Factorial : CalcKey("!", KeyKind.OPERATOR, "!")
    object Mod : CalcKey("mod", KeyKind.OPERATOR, "mod")

    object LParen : CalcKey("(", KeyKind.FUNCTION, "(")
    object RParen : CalcKey(")", KeyKind.FUNCTION, ")")
    object SquareRoot : CalcKey("√", KeyKind.FUNCTION, "√")
    object Square : CalcKey("x²", KeyKind.FUNCTION, "^2")
    object Pi : CalcKey("π", KeyKind.FUNCTION, "π")
    object Euler : CalcKey("e", KeyKind.FUNCTION, "e")

    /** 三角函数：插入时自动带上左括号（`=` 时自动补右括号） */
    class Fn(val name: String) : CalcKey(name, KeyKind.FUNCTION, "$name(")

    object Equals : CalcKey("=", KeyKind.EQUALS)

    /**
     * 顶栏动作键的 label 只用于无障碍朗读（键盘本身不渲染它们）。
     * 刻意不用 `⌫` 字形：MiSans 不含 U+232B，见 ui/components/Icons.kt。
     */
    object Clear : CalcKey("清空", KeyKind.ACTION)
    object Backspace : CalcKey("退格", KeyKind.ACTION)
    object AngleUnit : CalcKey("deg", KeyKind.ACTION)
    object PageMain : CalcKey("123", KeyKind.ACTION)
    object PageFunctions : CalcKey("fx", KeyKind.ACTION)
}

/**
 * 主键盘：数字 + 四则运算 + `=`，即经典 4×4 计算器布局。
 * 这一页刻意不放 `(` `)` / 复杂函数，保证常用键最大化（圆形表盘上每格约 31dp）。
 */
val MainKeypad: List<CalcKey> = listOf(
    CalcKey.Digit('7'), CalcKey.Digit('8'), CalcKey.Digit('9'), CalcKey.Divide,
    CalcKey.Digit('4'), CalcKey.Digit('5'), CalcKey.Digit('6'), CalcKey.Times,
    CalcKey.Digit('1'), CalcKey.Digit('2'), CalcKey.Digit('3'), CalcKey.Minus,
    CalcKey.Digit('0'), CalcKey.Dot, CalcKey.Equals, CalcKey.Plus,
)

/**
 * 函数页：幂/根/三角/常数/百分号/阶乘，以及角度单位与返回主键盘。
 * `C` `⌫` 常驻顶部栏，因此这里腾出的槽位给了 `x²`。
 */
val FunctionKeypad: List<CalcKey> = listOf(
    CalcKey.LParen, CalcKey.RParen, CalcKey.Power, CalcKey.SquareRoot,
    CalcKey.Fn("sin"), CalcKey.Fn("cos"), CalcKey.Fn("tan"), CalcKey.Mod,
    CalcKey.Percent, CalcKey.Factorial, CalcKey.Pi, CalcKey.Euler,
    CalcKey.AngleUnit, CalcKey.Square, CalcKey.PageMain, CalcKey.Equals,
)

fun keypadFor(page: KeypadPage): List<CalcKey> = when (page) {
    KeypadPage.MAIN -> MainKeypad
    KeypadPage.FUNCTIONS -> FunctionKeypad
}
