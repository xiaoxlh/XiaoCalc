package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 货币换算数据层的验证。
 *
 * 解析用例用的是**接口真实返回的报文片段**（实测于 2026-10-16），
 * 不是我照着自己的解析器编出来的格式——那样只能证明"我自洽"，证明不了"对得上接口"。
 */
class CurrencyTest {

    /** api.frankfurter.dev/v2/rates?base=CNY&quotes=USD,EUR,JPY 的真实响应 */
    private val realResponse = """
        [
          {"date":"2026-10-16","base":"CNY","quote":"EUR","rate":0.13335},
          {"date":"2026-10-16","base":"CNY","quote":"JPY","rate":23.655},
          {"date":"2026-10-16","base":"CNY","quote":"USD","rate":0.14961}
        ]
    """.trimIndent()

    private fun sig(v: BigDecimal, n: Int = 16): String =
        v.round(MathContext(n, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()

    private fun of(s: String) = BigDecimal(s)

    // ------------------------------------------------------------------ 解析

    @Test
    fun `解析真实响应`() {
        val table = RateCodec.parse(realResponse)
        assertNotNull(table)
        assertEquals("CNY", table!!.base)
        assertEquals("2026-10-16", table.date)
        assertEquals("0.14961", table.rates.getValue("USD").toPlainString())
        assertEquals("23.655", table.rates.getValue("JPY").toPlainString())
        assertEquals(3, table.rates.size)
    }

    @Test
    fun `基准自身按定义为一`() {
        val table = RateCodec.parse(realResponse)!!
        assertEquals("1", table.rateOf("CNY")!!.toPlainString())
        // 表里没有的货币返回 null，而不是悄悄当成 1
        assertNull(table.rateOf("GBP"))
    }

    @Test
    fun `格式不对时整份丢弃`() {
        // 半份汇率比没有更危险：用户会把基于错数据的换算当真
        assertNull(RateCodec.parse(""))
        assertNull(RateCodec.parse("[]"))
        assertNull(RateCodec.parse("{}"))
        assertNull(RateCodec.parse("not json"))
        assertNull(RateCodec.parse("""[{"quote":"USD","rate":1.0}]"""))
        assertNull(RateCodec.parse("""[{"base":"CNY","quote":"USD"}]"""))
        // 汇率为 0 或负数不可能
        assertNull(RateCodec.parse("""[{"date":"d","base":"CNY","quote":"USD","rate":0}]"""))
        assertNull(RateCodec.parse("""[{"date":"d","base":"CNY","quote":"USD","rate":-1}]"""))
    }

    @Test
    fun `一份表里混了两个基准会被拒`() {
        // 两个基准之间无法互相换算，接受它等于凭空造出一组错误汇率
        val mixed = """
            [{"date":"d","base":"CNY","quote":"USD","rate":0.15},
             {"date":"d","base":"USD","quote":"EUR","rate":0.9}]
        """.trimIndent()
        assertNull(RateCodec.parse(mixed))
    }

    // ------------------------------------------------------------ 本地存储

    @Test
    fun `编码解码往返一致`() {
        val table = RateCodec.parse(realResponse)!!
        val restored = RateCodec.decode(RateCodec.encode(table))
        assertNotNull(restored)
        assertEquals(table, restored)
    }

    @Test
    fun `解码拒绝坏数据`() {
        assertNull(RateCodec.decode(null))
        assertNull(RateCodec.decode(""))
        assertNull(RateCodec.decode("v1|CNY"))            // 段数不对
        assertNull(RateCodec.decode("v2|CNY|d|USD=1"))    // 版本不对
        assertNull(RateCodec.decode("v1|CNY|d|USD=x"))    // 汇率不是数
        assertNull(RateCodec.decode("v1|CNY|d|USD=0"))    // 汇率非正
        assertNull(RateCodec.decode("v1|CNY|d|"))         // 没有任何汇率
    }

    // ------------------------------------------------------------------ 换算

    @Test
    fun `换算经过统一基准`() {
        val table = RateCodec.parse(realResponse)!!
        // 100 CNY → USD
        assertEquals("14.961", sig(table.convert(of("100"), "CNY", "USD")!!))
        // 100 CNY → JPY
        assertEquals("2365.5", sig(table.convert(of("100"), "CNY", "JPY")!!))
        // 同币种换算是恒等
        assertEquals("100", sig(table.convert(of("100"), "CNY", "CNY")!!))
    }

    @Test
    fun `任意两币种互转再转回来能还原`() {
        val table = RateCodec.parse(realResponse)!!
        val codes = table.rates.keys.toList() + table.base
        for (a in codes) {
            for (b in codes) {
                val there = table.convert(of("123.456"), a, b)!!
                val back = table.convert(there, b, a)!!
                assertEquals("$a → $b → 回来不等", sig(of("123.456")), sig(back))
            }
        }
    }

    @Test
    fun `缺少汇率时返回空而不是回退成某个数字`() {
        val table = RateCodec.parse(realResponse)!!
        assertNull(table.convert(of("100"), "CNY", "GBP"))
        assertNull(table.convert(of("100"), "GBP", "CNY"))
    }

    // -------------------------------------------------------------- 货币表

    @Test
    fun `货币表结构正确`() {
        assertTrue("至少要有十种货币", Currencies.catalog.size >= 10)
        val codes = Currencies.catalog.map { it.code }
        assertEquals("代码不能重复", codes.size, codes.distinct().size)
        assertTrue("必须包含基准货币", codes.contains(Currencies.BASE))
        assertFalse("请求参数里不该重复带上基准", Currencies.quoteCodes.contains(Currencies.BASE))
        assertTrue("代码应当是大写三字母", codes.all { it.length == 3 && it.all { c -> c.isUpperCase() } })
        assertTrue("标签不能为空", Currencies.catalog.all { it.label.isNotBlank() })
    }

    // -------------------------------------------------------------- 授权把关

    @Test
    fun `未授权时不发起请求`() {
        val store = InMemoryRateStore()
        val state = CurrencyState(store)

        // beginRefresh 是唯一能进入"准备请求"状态的入口
        assertFalse("未授权时必须拒绝", state.beginRefresh(allowNetwork = false))
        assertEquals(RateStatus.NotAuthorized, state.status)
    }

    @Test
    fun `授权后可以发起请求`() {
        val state = CurrencyState(InMemoryRateStore())
        assertTrue(state.beginRefresh(allowNetwork = true))
        assertEquals(RateStatus.Loading, state.status)
    }

    @Test
    fun `成功结果会落盘`() {
        val store = InMemoryRateStore()
        val state = CurrencyState(store)
        state.beginRefresh(allowNetwork = true)
        state.applyResult(RateCodec.parse(realResponse))

        assertEquals(RateStatus.Updated("2026-10-16"), state.status)
        assertEquals("缓存应当已写入", "2026-10-16", store.loadRates()?.date)
    }

    @Test
    fun `失败时保留上一次的缓存`() {
        val store = InMemoryRateStore(RateCodec.parse(realResponse))
        val state = CurrencyState(store)
        state.beginRefresh(allowNetwork = true)
        state.applyResult(null, "网络不可用")

        assertEquals(RateStatus.Failed("网络不可用"), state.status)
        // 断网时仍应能用上次的汇率换算
        assertEquals("2026-10-16", state.table?.date)

        for (c in "100") state.onKey(CalcKey.Digit(c))
        assertEquals("14.961", sig(state.convertedValue(CalcSettings())!!))
    }

    @Test
    fun `撤销授权会让状态立刻反映`() {
        val state = CurrencyState(InMemoryRateStore())
        state.beginRefresh(allowNetwork = true)
        state.applyResult(RateCodec.parse(realResponse))
        assertTrue(state.status is RateStatus.Updated)

        state.onAuthorizationRevoked()
        assertEquals(RateStatus.NotAuthorized, state.status)
        // 已缓存的汇率仍在：撤销的是"联网"，不是"清空数据"
        assertNotNull(state.table)
    }

    // ------------------------------------------------------------------ 界面

    @Test
    fun `金额可以是表达式`() {
        val state = CurrencyState(InMemoryRateStore(RateCodec.parse(realResponse)))
        for (c in "50×2") {
            when (c) {
                in '0'..'9' -> state.onKey(CalcKey.Digit(c))
                '×' -> state.onKey(CalcKey.Times)
            }
        }
        // 100 CNY → USD
        assertEquals("14.961", sig(state.convertedValue(CalcSettings())!!))
    }

    @Test
    fun `输入为空或非法时不换算`() {
        val state = CurrencyState(InMemoryRateStore(RateCodec.parse(realResponse)))
        assertNull(state.convertedValue(CalcSettings()))
        assertNull(state.inputError(CalcSettings()))

        state.onKey(CalcKey.Digit('1'))
        state.onKey(CalcKey.Plus)
        assertNull("算式不完整时不应给出结果", state.convertedValue(CalcSettings()))
        assertNotNull("但应当提示算式有误", state.inputError(CalcSettings()))
    }

    @Test
    fun `换向与选择币种不会撞成同一种`() {
        val state = CurrencyState(InMemoryRateStore())
        val cny = Currencies.require("CNY")
        val usd = Currencies.require("USD")

        // 选中的目标恰好等于当前来源时，两者对调而不是变成同一种货币
        state.selectTo(cny)
        assertTrue("来源与目标不应相同", state.from != state.to)
        state.selectFrom(usd)
        assertTrue("来源与目标不应相同", state.from != state.to)

        val beforeFrom = state.from
        val beforeTo = state.to
        state.swap()
        // 交换是对称的：新的来源是旧的来源，反之亦然
        assertEquals(beforeTo, state.from)
        assertEquals(beforeFrom, state.to)
    }
}
