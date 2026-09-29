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

    /**
     * `%` 与 `!` 是**后缀**运算，不是四则运算符。
     *
     * 这里用 [KeyKind.FUNCTION] 而非 OPERATOR 只是为了配色——让"红色 = 二元运算符"
     * 这条视觉规则保持成立，函数页上不再有 4 个孤零零的红键。
     * 输入状态机**不读 kind**（见 CalculatorState 中的说明），因此改变 kind 不影响行为。
     */
    object Percent : CalcKey("%", KeyKind.FUNCTION, "%")
    object Factorial : CalcKey("!", KeyKind.FUNCTION, "!")
    object Mod : CalcKey("mod", KeyKind.OPERATOR, "mod")

    object LParen : CalcKey("(", KeyKind.FUNCTION, "(")
    object RParen : CalcKey(")", KeyKind.FUNCTION, ")")
    object SquareRoot : CalcKey("√", KeyKind.FUNCTION, "√")
    object Square : CalcKey("x²", KeyKind.FUNCTION, "^2")
    object Pi : CalcKey("π", KeyKind.FUNCTION, "π")
    object Euler : CalcKey("e", KeyKind.FUNCTION, "e")

    /** 三角函数与对数：插入时自动带上左括号（`=` 时自动补右括号） */
    class Fn(val name: String) : CalcKey(name, KeyKind.FUNCTION, "$name(")

    object Ln : CalcKey("ln", KeyKind.FUNCTION, "ln(")

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
 * 函数页：幂/根/三角/对数/常数/百分号/阶乘，以及角度单位。
 *
 * 末行原本还放了一个 `123` 分页键，但它与底部操作行的 `123` 完全重复——
 * 函数页上那个槽位纯属浪费，改放 `ln`（对数本就是函数页最明显的缺口）。
 *
 * `√` 与 `^` 的位置是刻意互换的：主键盘的 `÷ × − +` 全在右列，
 * 函数页的两个二元运算符 `^` / `mod` 也应落在右列并上下相邻。
 * 若按"根号在前"的直觉排，`^` 会被挤到第 3 列，两个红色键在对角线上，
 * 既割裂又和主键盘的运算符列对不上。
 */
val FunctionKeypad: List<CalcKey> = listOf(
    CalcKey.LParen, CalcKey.RParen, CalcKey.SquareRoot, CalcKey.Power,
    CalcKey.Fn("sin"), CalcKey.Fn("cos"), CalcKey.Fn("tan"), CalcKey.Mod,
    CalcKey.Percent, CalcKey.Factorial, CalcKey.Pi, CalcKey.Euler,
    CalcKey.AngleUnit, CalcKey.Square, CalcKey.Ln, CalcKey.Equals,
)

fun keypadFor(page: KeypadPage): List<CalcKey> = when (page) {
    KeypadPage.MAIN -> MainKeypad
    KeypadPage.FUNCTIONS -> FunctionKeypad
}
