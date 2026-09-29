package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 输入状态机回归。
 *
 * 这里覆盖的是旧版 `CalculatorController.input()` 中一批**实际存在的缺陷**：
 *  - 前导 0 会叠加成 "05"；
 *  - 连续按运算符被静默忽略（没有反馈）；
 *  - 求值后用显示串（含千分位逗号 / 符号）续算；
 *  - 出错后把 "错误" 当成有效结果继续运算；
 *  - 历史只存在内存里。
 */
class CalculatorStateTest {

    private val settingsStore = InMemorySettingsStore()
    private val historyStore = InMemoryHistoryStore()
    private var clock = 1_000L

    private fun state(): CalculatorState = CalculatorState(settingsStore, historyStore) { clock++ }

    private fun CalculatorState.type(text: String) {
        for (c in text) {
            when (c) {
                in '0'..'9' -> onKey(CalcKey.Digit(c))
                '.' -> onKey(CalcKey.Dot)
                '+' -> onKey(CalcKey.Plus)
                '-' -> onKey(CalcKey.Minus)
                '×' -> onKey(CalcKey.Times)
                '÷' -> onKey(CalcKey.Divide)
                '^' -> onKey(CalcKey.Power)
                '%' -> onKey(CalcKey.Percent)
                '!' -> onKey(CalcKey.Factorial)
                '(' -> onKey(CalcKey.LParen)
                ')' -> onKey(CalcKey.RParen)
                '√' -> onKey(CalcKey.SquareRoot)
                'π' -> onKey(CalcKey.Pi)
                'e' -> onKey(CalcKey.Euler)
                else -> error("测试不支持键入 $c")
            }
        }
    }

    // ------------------------------------------------------------------ 输入规则

    @Test
    fun `前导零被替换而不是叠加`() {
        val s = state()
        s.type("05")
        assertEquals("5", s.expression)
    }

    @Test
    fun `小数点不会重复且自动补零`() {
        val s = state()
        s.type("1..5")
        assertEquals("1.5", s.expression)

        val t = state()
        t.type(".5")
        assertEquals("0.5", t.expression)
    }

    @Test
    fun `连续按运算符会替换而不是静默吞掉`() {
        val s = state()
        s.type("5×÷")
        assertEquals("5÷", s.expression)
    }

    @Test
    fun `只允许负号作为开头`() {
        val s = state()
        s.onKey(CalcKey.Plus)
        assertEquals("", s.expression)
        s.onKey(CalcKey.Minus)
        assertEquals("-", s.expression)
    }

    @Test
    fun `后缀运算符前必须有操作数`() {
        val s = state()
        s.type("5+")
        s.onKey(CalcKey.Percent)
        assertEquals("5+", s.expression)
        s.type("20")
        s.onKey(CalcKey.Percent)
        assertEquals("5+20%", s.expression)
    }

    @Test
    fun `右括号需要匹配左括号且不能紧跟运算符`() {
        val s = state()
        s.onKey(CalcKey.RParen)
        assertEquals("", s.expression)

        s.type("(2+")
        s.onKey(CalcKey.RParen)
        assertEquals("(2+", s.expression)

        s.type("3)")
        assertEquals("(2+3)", s.expression)
    }

    @Test
    fun `退格整体删除函数名`() {
        val s = state()
        s.onKey(CalcKey.Fn("sin"))
        assertEquals("sin(", s.expression)
        s.onKey(CalcKey.Backspace)
        assertEquals("", s.expression)
    }

    @Test
    fun `长度上限生效`() {
        val s = state()
        repeat(200) { s.onKey(CalcKey.Digit('9')) }
        assertTrue(s.expression.length <= CalcEngine.MAX_LENGTH)
    }

    // ------------------------------------------------------------------ 求值

    @Test
    fun `基本求值`() {
        val s = state()
        s.type("12+3")
        s.onKey(CalcKey.Equals)
        assertEquals("15", s.resultText)
        assertNull(s.error)
        assertTrue(s.justEvaluated)
    }

    @Test
    fun `求值后按运算符用数值续算`() {
        val s = state()
        s.type("2+3")
        s.onKey(CalcKey.Equals)
        s.onKey(CalcKey.Times)
        // 结果数值被回填为一个新的操作数，并接着插入按下的运算符
        assertEquals("5×", s.expression)
        s.type("4")
        s.onKey(CalcKey.Equals)
        assertEquals("20", s.resultText)
    }

    @Test
    fun `千分位结果续算不会污染解析器`() {
        val s = state()
        s.type("1234567+0")
        s.onKey(CalcKey.Equals)
        assertEquals("1,234,567", s.resultText)

        s.onKey(CalcKey.Plus)
        s.type("1")
        s.onKey(CalcKey.Equals)
        assertEquals("1,234,568", s.resultText)
    }

    @Test
    fun `保留根号时按键流程能化简根式`() {
        val s = state()
        s.updateSettings { it.copy(keepRoot = true) }
        s.type("√8")
        s.onKey(CalcKey.Equals)
        assertEquals("2√2", s.resultText)
    }

    @Test
    fun `保留根号时完全平方数直接开出整数`() {
        val s = state()
        s.updateSettings { it.copy(keepRoot = true) }
        s.type("√9")
        s.onKey(CalcKey.Equals)
        assertEquals("3", s.resultText)
    }

    @Test
    fun `保留根号时带系数的根式`() {
        val s = state()
        s.updateSettings { it.copy(keepRoot = true) }
        s.type("2×√18")
        s.onKey(CalcKey.Equals)
        assertEquals("6√2", s.resultText)
    }

    @Test
    fun `符号化结果续算使用数值而不是显示串`() {
        val s = state()
        s.updateSettings { it.copy(keepSymbols = true) }
        s.type("2×π")
        s.onKey(CalcKey.Equals)
        assertEquals("2π", s.resultText)

        // 旧实现会把 "2π" 原样塞回表达式（不可解析）；这里回落到数值
        s.onKey(CalcKey.Divide)
        s.type("2")
        s.onKey(CalcKey.Equals)
        assertEquals("3.141592654", s.resultText)
    }

    @Test
    fun `求值后按数字开始新算式`() {
        val s = state()
        s.type("2+3")
        s.onKey(CalcKey.Equals)
        s.onKey(CalcKey.Digit('7'))
        assertEquals("7", s.expression)
        assertEquals("", s.resultText)
    }

    @Test
    fun `角度单位设置影响三角函数`() {
        val s = state()
        s.updateSettings { it.copy(angleUnit = AngleUnit.RAD) }
        s.onKey(CalcKey.Fn("sin"))
        s.type("π÷2")
        s.onKey(CalcKey.Equals)
        // 缺省自动补右括号，因此不需要手输 ")"
        assertEquals("1", s.resultText)
    }

    // ------------------------------------------------------------------ 错误

    @Test
    fun `除以零报错且不写入结果`() {
        val s = state()
        s.type("5÷0")
        s.onKey(CalcKey.Equals)
        assertEquals(CalcError.DIVIDE_BY_ZERO, s.error)
        assertEquals("", s.resultText)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `报错后仍可退格修正`() {
        val s = state()
        s.type("5÷0")
        s.onKey(CalcKey.Equals)
        assertNotNull(s.error)

        s.onKey(CalcKey.Backspace)
        assertEquals("5÷", s.expression)
        s.type("2")
        s.onKey(CalcKey.Equals)
        assertEquals("2.5", s.resultText)
        assertNull(s.error)
    }

    @Test
    fun `按任意键清除上一次错误`() {
        val s = state()
        s.type("5÷0")
        s.onKey(CalcKey.Equals)
        s.onKey(CalcKey.Digit('1'))
        assertNull(s.error)
    }

    // ------------------------------------------------------------------ 历史

    @Test
    fun `历史结构化并持久化`() {
        val s = state()
        s.type("1+1")
        s.onKey(CalcKey.Equals)
        s.type("2×3")
        s.onKey(CalcKey.Equals)

        assertEquals(2, s.history.size)
        assertEquals("2×3", s.history[0].expression)
        assertEquals("6", s.history[0].result)

        // 用同一份存储重建状态：历史应当恢复（旧实现是纯内存，必丢）
        val restored = CalculatorState(settingsStore, historyStore) { clock++ }
        assertEquals(2, restored.history.size)
        assertEquals("1+1", restored.history[1].expression)
    }

    @Test
    fun `历史回填表达式`() {
        val s = state()
        s.type("2^10")
        s.onKey(CalcKey.Equals)
        val entry = s.history.first()
        s.clearAll()
        s.reuse(entry)
        assertEquals("2^10", s.expression)
    }

    @Test
    fun `清空历史会落盘`() {
        val s = state()
        s.type("1+1")
        s.onKey(CalcKey.Equals)
        s.clearHistory()
        assertTrue(s.history.isEmpty())
        assertTrue(historyStore.loadEntries().isEmpty())
    }

    // ------------------------------------------------------------------ 设置

    @Test
    fun `设置变更会持久化`() {
        val s = state()
        s.updateSettings { it.copy(keepRoot = true, haptics = false) }
        val reloaded = settingsStore.load()
        assertTrue(reloaded.keepRoot)
        assertTrue(!reloaded.haptics)
    }

    @Test
    fun `角度单位可在键盘上切换`() {
        val s = state()
        assertEquals(AngleUnit.DEG, s.settings.angleUnit)
        s.onKey(CalcKey.AngleUnit)
        assertEquals(AngleUnit.RAD, s.settings.angleUnit)
        s.onKey(CalcKey.AngleUnit)
        assertEquals(AngleUnit.DEG, s.settings.angleUnit)
    }

    @Test
    fun `键盘分页切换`() {
        val s = state()
        assertEquals(KeypadPage.MAIN, s.page)
        s.onKey(CalcKey.PageFunctions)
        assertEquals(KeypadPage.FUNCTIONS, s.page)
        s.onKey(CalcKey.PageMain)
        assertEquals(KeypadPage.MAIN, s.page)
    }

    @Test
    fun `函数页插入记号后自动回到主键盘`() {
        val s = state()
        s.onKey(CalcKey.PageFunctions)
        s.onKey(CalcKey.Fn("sin"))
        // 插入了 "sin("，同时回到数字键盘等待输入参数
        assertEquals("sin(", s.expression)
        assertEquals(KeypadPage.MAIN, s.page)
    }

    @Test
    fun `切换角度单位的键不会把用户踢回主键盘`() {
        val s = state()
        s.onKey(CalcKey.PageFunctions)
        s.onKey(CalcKey.AngleUnit)
        assertEquals(AngleUnit.RAD, s.settings.angleUnit)
        assertEquals(KeypadPage.FUNCTIONS, s.page)

        // 而插入类按键（如 π）会回到主键盘
        s.onKey(CalcKey.Pi)
        assertEquals("π", s.expression)
        assertEquals(KeypadPage.MAIN, s.page)
    }

    @Test
    fun `显示表达式把幂转成上标`() {
        val s = state()
        s.type("2^10")
        assertEquals("2¹⁰", s.displayExpression)
    }

    @Test
    fun `键盘布局完整性`() {
        assertEquals(16, MainKeypad.size)
        assertEquals(16, FunctionKeypad.size)
        assertTrue(MainKeypad.any { it.label == "=" })
        assertTrue(FunctionKeypad.any { it is CalcKey.Fn })
    }
}

/** 复制一份状态用于断言中间态 */
private fun CalculatorState.clearAll() {
    onKey(CalcKey.Clear)
}
