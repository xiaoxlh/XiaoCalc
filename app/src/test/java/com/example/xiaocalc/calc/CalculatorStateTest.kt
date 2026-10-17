package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        s.onKey(CalcKey.Divide)
        s.type("2")
        s.onKey(CalcKey.Equals)
        assertEquals("3.141592653589793", s.resultText)
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
        assertEquals("1", s.resultText)
    }

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
    fun `删除单条历史只删那一条并落盘`() {
        val s = state()
        s.type("1+1")
        s.onKey(CalcKey.Equals)
        s.type("2×3")
        s.onKey(CalcKey.Equals)
        s.type("3+3")
        s.onKey(CalcKey.Equals)
        assertEquals(3, s.history.size)
        val middle = s.history[1]
        s.removeHistory(middle)
        assertEquals(2, s.history.size)
        assertEquals("3+3", s.history[0].expression)
        assertEquals("1+1", s.history[1].expression)
        assertEquals(2, historyStore.loadEntries().size)
        assertTrue(historyStore.loadEntries().none { it == middle })
    }

    @Test
    fun `删除不存在的历史不会改动任何东西`() {
        val s = state()
        s.type("1+1")
        s.onKey(CalcKey.Equals)
        s.removeHistory(HistoryEntry("不存在的", "0", 999L))
        assertEquals(1, s.history.size)
        assertEquals(1, historyStore.loadEntries().size)
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
        assertTrue("函数页不能超过 4×4 的格位", FunctionKeypad.size <= 16)
        assertTrue("`x²` 应当已被删除（与 ^2 等价）", FunctionKeypad.none { it.label == "x²" })
        assertTrue(MainKeypad.any { it.label == "=" })
        assertTrue(FunctionKeypad.any { it is CalcKey.Fn })
    }

    @Test
    fun `角度单位只在用到三角函数时显示`() {
        val s = state()
        s.type("2+3")
        assertEquals("2+3", s.displayExpression)
        val trig = state()
        trig.updateSettings { it.copy(angleUnit = AngleUnit.DEG) }
        trig.onKey(CalcKey.Fn("sin"))
        trig.type("30")
        assertEquals("deg sin(30", trig.displayExpression)
        trig.updateSettings { it.copy(angleUnit = AngleUnit.RAD) }
        assertEquals("rad sin(30", trig.displayExpression)
        val log = state()
        log.updateSettings { it.copy(angleUnit = AngleUnit.DEG) }
        log.onKey(CalcKey.Ln)
        log.type("2")
        assertEquals("ln(2", log.displayExpression)
    }
}

private fun CalculatorState.clearAll() {
    onKey(CalcKey.Clear)
}
