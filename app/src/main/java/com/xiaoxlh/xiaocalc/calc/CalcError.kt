package com.xiaoxlh.xiaocalc.calc

enum class CalcError {
    EMPTY,

    TOO_LONG,

    BAD_CHARACTER,

    UNEXPECTED_TOKEN,

    MISSING_OPERAND,

    UNMATCHED_PAREN,

    UNKNOWN_FUNCTION,

    DIVIDE_BY_ZERO,

    NEGATIVE_SQRT,

    FACTORIAL_DOMAIN,

    NOT_FINITE,
}

class CalcException(
    val error: CalcError,
    val position: Int = -1,
) : Exception(error.name)
