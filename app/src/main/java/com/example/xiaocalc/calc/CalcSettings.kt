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
    /**
     * 显示的小数位数，0..[FULL]。
     *
     * **默认为 [FULL] 表示"全精度"**：此时完全沿用 16 位有效数字的显示契约，
     * 与 1.1.x 及本版此前逐字节相同。取值小于 [FULL] 时才额外按该位数舍入——
     * 也就是说这个设置只会**减少**显示位数，不会凭空多出位来。
     *
     * 只作用于计算器 / 单位换算 / 货币换算三个页面。函数图像与方程页
     * 直接使用各自的 `FORMAT_SETTINGS`，读不到这一项：那里的坐标值需要
     * 统一的有效位数才好对齐刻度，混进用户的小数位设置反而会变糊涂。
     */
    val decimals: Int = FULL,
    /**
     * 面板弹出时把背后的页面做模糊处理。
     *
     * 默认开启；关掉之后退化成纯遮罩——旧设备（API < 31）没有 Modifier.blur
     * 的底层支持，本身就会退化成遮罩，这个开关让新旧设备的观感可以被用户自己拉平。
     */
    val blurEnabled: Boolean = true,
) {
    companion object {
        /**
         * "不额外限制小数位"的哨兵值，也是 [decimals] 的上界。
         *
         * 取 16 与 [NumberFormatter.SIGNIFICANT_DIGITS] 同值：显示契约本身就是
         * "16 位有效数字"，再往下限制才有意义，往上是重复的。
         */
        const val FULL = 16

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
        const val KEY_DECIMALS = "decimals"
        const val KEY_BLUR = "blur_enabled"
    }
}
