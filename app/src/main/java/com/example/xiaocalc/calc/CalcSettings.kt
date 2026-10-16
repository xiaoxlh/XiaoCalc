package com.example.xiaocalc.calc

import androidx.compose.runtime.Immutable

/**
 * 计算器设置。
 *
 * 前三个字段沿用旧版语义与存储 key（`root` / `scientific` / `symbols`），
 * 保证老用户升级后配置不丢；后三项是本次新增。
 */
@Immutable
data class CalcSettings(
    /** 保留根号：`√8` 显示为 `2√2` 而不是小数 */
    val keepRoot: Boolean = false,
    /** 科学计数法：|x| ≥ 10000 时使用 ×10ⁿ */
    val scientific: Boolean = false,
    /** 保留特殊符号：结果为 π / e 的简洁倍数时保留符号 */
    val keepSymbols: Boolean = false,
    /** 三角函数角度单位 */
    val angleUnit: AngleUnit = AngleUnit.DEG,
    /** 结果千分位分隔 */
    val groupDigits: Boolean = true,
    /** 按键触感反馈 */
    val haptics: Boolean = true,
    /** 强调色主题。默认为红，与旧版逐值相同 */
    val themeColor: ThemeColor = ThemeColor.RED,
    /**
     * 是否允许联网获取汇率。
     *
     * **默认为 false**，且只有在用户于货币页显式同意后才会变成 true。
     * 应用不会在未授权时发起任何网络请求——这条由 `CurrencyState` 把关，
     * 而不是靠"记得别调用"。
     */
    val allowNetwork: Boolean = false,
) {
    companion object {
        // 旧版存储 key（SharedPreferences: calculator_settings）
        const val KEY_ROOT = "root"
        const val KEY_SCIENTIFIC = "scientific"
        const val KEY_SYMBOLS = "symbols"

        // 本次新增
        const val KEY_ANGLE = "angle_unit"
        const val KEY_GROUP = "group_digits"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme_color"
        const val KEY_NETWORK = "allow_network"
    }
}
