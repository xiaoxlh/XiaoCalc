package com.example.xiaocalc.calc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.time.LocalDate

/** 汇率的状态 */
sealed interface RateStatus {
    /** 还没取过，用的是本地缓存（可能为空） */
    data object Idle : RateStatus

    /** 正在请求 */
    data object Loading : RateStatus

    /** 已更新到指定日期 */
    data class Updated(val date: String) : RateStatus

    /** 请求失败 */
    data class Failed(val reason: String) : RateStatus

    /** 用户尚未授权联网 */
    data object NotAuthorized : RateStatus
}

/**
 * 货币换算页的状态。
 *
 * ## 联网授权在这里把关
 *
 * [beginRefresh] 是**唯一**能进入"准备请求"状态的入口，而它在未授权时直接拒绝。
 * 也就是说"默认不联网"不是靠界面记得别调用，而是状态机本身不允许——
 * 将来谁在别处加了调用点，也一样取不到数据。
 *
 * ## 为什么把请求拆成 begin / apply
 *
 * 真正的网络调用由界面在 IO 线程发起，状态本身保持纯 JVM、可直接单测：
 * `beginRefresh(false)` 必须返回 false 且不触碰数据源，
 * 这一条不需要协程测试框架就能验证。
 */
class CurrencyState(
    private val store: RateStore,
    initialFrom: Currency = Currencies.require("CNY"),
    initialTo: Currency = Currencies.require("USD"),
) {

    var from: Currency by mutableStateOf(initialFrom)
        private set

    var to: Currency by mutableStateOf(initialTo)
        private set

    /** 待换算的金额表达式 */
    var input: String by mutableStateOf("")
        private set

    /** 当前可用的汇率表（来自本地缓存，或最近一次成功请求） */
    var table: RateTable? by mutableStateOf(store.loadRates())
        private set

    var status: RateStatus by mutableStateOf(RateStatus.Idle)
        private set

    /** 缓存里那份汇率的日期；没有缓存时为 null */
    val cachedDate: String? get() = table?.date

    // ------------------------------------------------------------------ 编辑

    fun clear() {
        input = ""
    }

    fun backspace() {
        if (input.isEmpty()) return
        val tail = FUNCTION_TAILS.firstOrNull { input.endsWith(it) }
        input = if (tail != null) input.dropLast(tail.length) else input.dropLast(1)
    }

    fun insert(text: String) {
        if (text.isEmpty()) return
        input += text
    }

    fun onKey(key: CalcKey) {
        when (key) {
            is CalcKey.Digit -> appendDigit(key.value)
            CalcKey.Dot -> appendDot()
            CalcKey.Clear -> clear()
            CalcKey.Backspace -> backspace()
            else -> key.insert?.let(::appendRaw)
        }
    }

    private fun appendDigit(digit: Char) {
        val trailing = trailingNumber()
        if (trailing == "0" && digit != '0') {
            input = input.dropLast(1) + digit
            return
        }
        if (trailing == "0" && digit == '0') return
        appendRaw(digit.toString())
    }

    private fun appendDot() {
        if (trailingNumber().contains('.')) return
        input += "."
    }

    private fun appendRaw(text: String) {
        if (text.isEmpty()) return
        val last = input.lastOrNull()
        if (text.length == 1 && text[0] in BINARY_OPERATORS && last != null && last in BINARY_OPERATORS) {
            input = input.dropLast(1) + text
            return
        }
        input += text
    }

    private fun trailingNumber(): String {
        var start = input.length
        while (start > 0 && (input[start - 1].isDigit() || input[start - 1] == '.')) start--
        return input.substring(start)
    }

    // -------------------------------------------------------------- 币种选择

    fun selectFrom(currency: Currency) {
        if (currency == to) to = from
        from = currency
    }

    fun selectTo(currency: Currency) {
        if (currency == from) from = to
        to = currency
    }

    fun swap() {
        val previous = from
        from = to
        to = previous
    }

    // ------------------------------------------------------------------ 汇率

    /**
     * 申请开始一次刷新。
     *
     * @return true 表示调用方**可以**去请求；false 表示未授权，已被拒绝
     */
    fun beginRefresh(allowNetwork: Boolean): Boolean {
        if (!allowNetwork) {
            status = RateStatus.NotAuthorized
            return false
        }
        status = RateStatus.Loading
        return true
    }

    /** 应用请求结果；成功时落盘缓存，供离线时继续使用 */
    fun applyResult(fetched: RateTable?, failureReason: String = "没取到汇率") {
        if (fetched == null) {
            status = RateStatus.Failed(failureReason)
            return
        }
        table = fetched
        store.saveRates(fetched)
        status = RateStatus.Updated(fetched.date)
    }

    /** 用户撤销授权时调用，让界面立刻反映"已停用" */
    fun onAuthorizationRevoked() {
        if (status is RateStatus.Updated || status is RateStatus.Loading) {
            status = RateStatus.NotAuthorized
        }
    }

    /**
     * 缓存的汇率是否已经"过期到需要自动续"。
     *
     * 判定用**日期**而不是"距上次更新多少小时"：汇率本来就是按日发布的，
     * 同一天里刷新多少次拿到都是同一份数据。因此"不是今天的"才值得再请求一次，
     * 也天然保证了同一天不会重复请求。
     *
     * 没有缓存时同样返回 true——第一次总得取一次。
     */
    fun needsAutoRenew(today: LocalDate): Boolean {
        val cached = cachedDate ?: return true
        val date = runCatching { LocalDate.parse(cached) }.getOrNull() ?: return true
        return date.isBefore(today)
    }

    // ------------------------------------------------------------------ 求值

    /** 换算结果；无法换算时返回 null */
    fun convertedValue(settings: CalcSettings): BigDecimal? {
        if (input.isBlank()) return null
        val amount = when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> outcome.value
            is EvalOutcome.Failure -> return null
        }
        return table?.convert(amount, from.code, to.code)
    }

    fun convertedText(settings: CalcSettings): String? =
        convertedValue(settings)?.let { NumberFormatter.format(it, settings) }

    /** 输入非法时的错误文案；输入为空时返回 null */
    fun inputError(settings: CalcSettings): String? {
        if (input.isBlank()) return null
        return when (val outcome = CalcEngine.evaluate(input, settings)) {
            is EvalOutcome.Success -> null
            is EvalOutcome.Failure -> "算式有误"
        }
    }

    private companion object {
        val BINARY_OPERATORS = setOf('+', '-', '−', '×', '÷', '^')
        val FUNCTION_TAILS = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    }
}
