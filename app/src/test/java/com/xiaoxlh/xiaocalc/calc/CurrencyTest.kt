package com.xiaoxlh.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.math.MathContext
import java.math.RoundingMode

class CurrencyTest {
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
        assertNull(table.rateOf("GBP"))
    }

    @Test
    fun `格式不对时整份丢弃`() {
        assertNull(RateCodec.parse(""))
        assertNull(RateCodec.parse("[]"))
        assertNull(RateCodec.parse("{}"))
        assertNull(RateCodec.parse("not json"))
        assertNull(RateCodec.parse("""[{"quote":"USD","rate":1.0}]"""))
        assertNull(RateCodec.parse("""[{"base":"CNY","quote":"USD"}]"""))
        assertNull(RateCodec.parse("""[{"date":"d","base":"CNY","quote":"USD","rate":0}]"""))
        assertNull(RateCodec.parse("""[{"date":"d","base":"CNY","quote":"USD","rate":-1}]"""))
    }

    @Test
    fun `一份表里混了两个基准会被拒`() {
        val mixed = """
            [{"date":"d","base":"CNY","quote":"USD","rate":0.15},
             {"date":"d","base":"USD","quote":"EUR","rate":0.9}]
        """.trimIndent()
        assertNull(RateCodec.parse(mixed))
    }

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
        assertNull(RateCodec.decode("v1|CNY"))
        assertNull(RateCodec.decode("v2|CNY|d|USD=1"))
        assertNull(RateCodec.decode("v1|CNY|d|USD=x"))
        assertNull(RateCodec.decode("v1|CNY|d|USD=0"))
        assertNull(RateCodec.decode("v1|CNY|d|"))
    }

    @Test
    fun `换算经过统一基准`() {
        val table = RateCodec.parse(realResponse)!!
        assertEquals("14.961", sig(table.convert(of("100"), "CNY", "USD")!!))
        assertEquals("2365.5", sig(table.convert(of("100"), "CNY", "JPY")!!))
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

    @Test
    fun `未授权时不发起请求`() {
        val store = InMemoryRateStore()
        val state = CurrencyState(store)
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
        assertNotNull(state.table)
    }

    @Test
    fun `金额可以是表达式`() {
        val state = CurrencyState(InMemoryRateStore(RateCodec.parse(realResponse)))
        for (c in "50×2") {
            when (c) {
                in '0'..'9' -> state.onKey(CalcKey.Digit(c))
                '×' -> state.onKey(CalcKey.Times)
            }
        }
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
        state.selectTo(cny)
        assertTrue("来源与目标不应相同", state.from != state.to)
        state.selectFrom(usd)
        assertTrue("来源与目标不应相同", state.from != state.to)
        val beforeFrom = state.from
        val beforeTo = state.to
        state.swap()
        assertEquals(beforeTo, state.from)
        assertEquals(beforeFrom, state.to)
    }

    @Test
    fun `汇率过期判定按日期而不是按小时`() {
        val store = InMemoryRateStore()
        val state = CurrencyState(store)
        assertTrue("无缓存时应当需要续期", state.needsAutoRenew(LocalDate.of(2026, 10, 17)))
        state.applyResult(
            RateTable("USD", mapOf("CNY" to BigDecimal("7.1")), "2026-10-16"),
        )
        assertTrue(
            "旧一天的汇率应当触发自动续期",
            state.needsAutoRenew(LocalDate.of(2026, 10, 17)),
        )
        assertFalse(
            "同一天内不该再请求——汇率按日发布，刷新多少次都是同一份",
            state.needsAutoRenew(LocalDate.of(2026, 10, 16)),
        )
        assertFalse(
            "日期比现在还新（时区差）也不该反复请求",
            state.needsAutoRenew(LocalDate.of(2026, 10, 15)),
        )
    }

    @Test
    fun `缓存日期无法解析时按过期处理`() {
        val state = CurrencyState(InMemoryRateStore())
        state.applyResult(RateTable("USD", mapOf("CNY" to BigDecimal("7.1")), "不是日期"))
        assertTrue(
            "除非能证明是今天的，否则宁可多取一次",
            state.needsAutoRenew(LocalDate.of(2026, 10, 17)),
        )
    }}
