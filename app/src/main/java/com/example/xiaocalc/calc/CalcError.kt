package com.example.xiaocalc.calc

/**
 * 求值/输入失败的原因。
 *
 * 旧实现把失败编码成魔法字符串（"错误" / "NaN"）并塞进 result，
 * 导致"用错误结果继续运算"这类状态污染。这里改为强类型枚举，
 * 由 UI 层决定文案与动效。
 */
enum class CalcError {
    /** 表达式为空 */
    EMPTY,

    /** 超出长度上限 */
    TOO_LONG,

    /** 出现无法识别的字符 */
    BAD_CHARACTER,

    /** 记号顺序不合法（如 `2+×3`） */
    UNEXPECTED_TOKEN,

    /** 运算缺少操作数（如 `2+`） */
    MISSING_OPERAND,

    /** 括号不匹配 */
    UNMATCHED_PAREN,

    /** 未知函数名 */
    UNKNOWN_FUNCTION,

    /** 除以 0 */
    DIVIDE_BY_ZERO,

    /** 负数开平方 */
    NEGATIVE_SQRT,

    /** 阶乘定义域非法（负数 / 非整数 / > 170） */
    FACTORIAL_DOMAIN,

    /** 溢出或结果非有限值 */
    NOT_FINITE,
}

/** 携带位置信息的求值异常，便于定位出错记号。 */
class CalcException(
    val error: CalcError,
    val position: Int = -1,
) : Exception(error.name)
