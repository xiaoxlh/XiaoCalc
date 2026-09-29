package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** 表达式解析器回归：既覆盖正常语义，也覆盖旧实现里明确是 bug 的几处。 */
class ExpressionTest {

    private fun eval(expr: String, angleUnit: AngleUnit = AngleUnit.DEG): Double =
        ExpressionEvaluator(expr, angleUnit).evaluate()

    private fun error(expr: String, angleUnit: AngleUnit = AngleUnit.DEG): CalcError {
        try {
            ExpressionEvaluator(expr, angleUnit).evaluate()
        } catch (e: CalcException) {
            return e.error
        }
        throw AssertionError("期望 $expr 求值失败，但成功了")
    }

    // ------------------------------------------------------------------ 基本

    @Test
    fun `四则运算优先级`() {
        assertEquals(3.0, eval("1+2"), 1e-9)
        assertEquals(14.0, eval("2+3×4"), 1e-9)
        assertEquals(20.0, eval("(2+3)×4"), 1e-9)
        assertEquals(2.5, eval("10÷4"), 1e-9)
        assertEquals(-8.0, eval("-5-3"), 1e-9)
    }

    @Test
    fun `幂为右结合且一元负号优先级低于幂`() {
        assertEquals(512.0, eval("2^3^2"), 1e-9)
        // -2^2 = -(2^2) = -4，而不是 (-2)^2
        assertEquals(-4.0, eval("-2^2"), 1e-9)
        assertEquals(0.125, eval("2^-3"), 1e-9)
    }

    @Test
    fun `隐式乘法`() {
        assertEquals(2 * PI, eval("2π"), 1e-9)
        assertEquals(14.0, eval("2(3+4)"), 1e-9)
        assertEquals(21.0, eval("(1+2)(3+4)"), 1e-9)
        assertEquals(6.0, eval("2√9"), 1e-9)
    }

    @Test
    fun `根号与阶乘`() {
        assertEquals(3.0, eval("√9"), 1e-9)
        assertEquals(4.0, eval("√9+1"), 1e-9)
        assertEquals(120.0, eval("5!"), 1e-9)
        assertEquals(720.0, eval("3!×5!"), 1e-9)
    }

    @Test
    fun `三角函数按设置的角度单位计算`() {
        assertEquals(0.5, eval("sin(30)", AngleUnit.DEG), 1e-9)
        assertEquals(1.0, eval("sin(90)", AngleUnit.DEG), 1e-9)
        assertEquals(1.0, eval("sin(π÷2)", AngleUnit.RAD), 1e-9)
        // 省略括号同样可用：手表上少按一次
        assertEquals(0.5, eval("sin30", AngleUnit.DEG), 1e-9)
    }

    // ---------------------------------------------------------------- 百分号

    @Test
    fun `百分号按计算器惯例解释`() {
        // 独立百分数 → 除以 100
        assertEquals(0.5, eval("50%"), 1e-9)
        // 加减：相对左侧数值取百分比
        assertEquals(220.0, eval("200+10%"), 1e-9)
        assertEquals(180.0, eval("200-10%"), 1e-9)
        // 乘除：直接按比例
        assertEquals(20.0, eval("200×10%"), 1e-9)
        // 复合项不被当成"整体百分比"
        assertEquals(200.2, eval("200+2×10%"), 1e-9)
    }

    // ------------------------------------------------------------------ 错误

    @Test
    fun `除以零被拦截而不是返回无穷`() {
        assertEquals(CalcError.DIVIDE_BY_ZERO, error("1÷0"))
        assertEquals(CalcError.DIVIDE_BY_ZERO, error("0÷0"))
        assertEquals(CalcError.DIVIDE_BY_ZERO, error("5 mod 0"))
    }

    @Test
    fun `定义域错误`() {
        assertEquals(CalcError.NEGATIVE_SQRT, error("√-4"))
        assertEquals(CalcError.FACTORIAL_DOMAIN, error("(-1)!"))
        assertEquals(CalcError.FACTORIAL_DOMAIN, error("2.5!"))
        assertEquals(CalcError.FACTORIAL_DOMAIN, error("200!"))
    }

    @Test
    fun `语法错误有明确分类`() {
        assertEquals(CalcError.UNKNOWN_FUNCTION, error("foo(2)"))
        assertEquals(CalcError.MISSING_OPERAND, error("2+"))
        assertEquals(CalcError.UNMATCHED_PAREN, error("(2+3))"))
        assertEquals(CalcError.BAD_CHARACTER, error("2#3"))
        assertEquals(CalcError.EMPTY, error(""))
    }

    @Test
    fun `未知函数不再被静默当成隐式乘法`() {
        // 旧实现的 readName 会贪吃字母，"deg" 被读成函数名后直接抛异常；
        // 新版把它明确归类为未知函数，而不是产出 "错误" 字符串
        assertEquals(CalcError.UNKNOWN_FUNCTION, error("deg"))
    }

    // ------------------------------------------------------- 自动补括号

    @Test
    fun `自动补全右括号`() {
        assertEquals("sin(π÷2)", CalcEngine.balanceParens("sin(π÷2"))
        assertEquals("(2+3)×(4+5)", CalcEngine.balanceParens("(2+3)×(4+5"))
        assertEquals("1+2", CalcEngine.balanceParens("1+2"))
        assertTrue(CalcEngine.evaluate("(2+3×4", CalcSettings()).let { it is EvalOutcome.Success && it.value == 14.0 })
    }

    @Test
    fun `normalize 把用户可见写法还原成解析器语法`() {
        assertEquals("2×3÷4-5", ExpressionEvaluator.normalize("2*3/4−5"))
    }
}
