package com.example.xiaocalc.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 主题色的验证。
 *
 * 两条判据都不依赖我的审美：
 *
 *  1. **对比度**：强调填充上的文字必须够清楚。这不是主观问题——WCAG 对比度可以算出来，
 *     而 `=` 键是整个界面上最需要一眼读到的元素。算出来不合格的主题必须改色，不能靠"看着还行"。
 *
 *  2. **默认值逐值不变**：升级到 v1.2.0 后外观必须与旧版完全一致。
 *     这条用字面量写死，防止有人"顺手微调"默认红而无人察觉。
 */
class ThemeColorTest {

    /** 相对亮度（WCAG 2.x 定义） */
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val raw = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (raw <= 0.03928) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG 对比度：(L亮 + 0.05) / (L暗 + 0.05) */
    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `默认主题与旧版配色逐值相同`() {
        val legacy = ThemeColor.RED
        assertEquals("强调填充", 0xFFFF5454, legacy.accent)
        assertEquals("强调填充上的文字", 0xFFFFFFFF, legacy.onAccent)
        assertEquals("运算符键容器", 0xFF6B1D1D, legacy.accentContainer)
        assertEquals("运算符键文字", 0xFFFFB4B4, legacy.onAccentContainer)
    }

    @Test
    fun `强调填充上的文字都有足够对比度`() {
        // 3:1 是 WCAG 对大号粗体的 AA 门槛。默认红是 3.16:1，
        // 这正说明了门槛不能定得比它更高——否则会逼着改掉现有设计。
        for (theme in ThemeColor.entries) {
            val ratio = contrast(theme.accent, theme.onAccent)
            assertTrue(
                "${theme.label}：${theme.accent.toString(16)} 上的文字对比度只有" +
                    " ${"%.2f".format(ratio)}:1，低于 3:1",
                ratio >= 3.0,
            )
        }
    }

    @Test
    fun `运算符键的底色与文字对比度不低于四点五`() {
        // 运算符键上的是常规字号，按 AA 常规文本要求 4.5:1
        for (theme in ThemeColor.entries) {
            val ratio = contrast(theme.accentContainer, theme.onAccentContainer)
            assertTrue(
                "${theme.label}：容器 ${theme.accentContainer.toString(16)} 上的文字对比度只有" +
                    " ${"%.2f".format(ratio)}:1，低于 4.5:1",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `循环切换会遍历全部主题并回到起点`() {
        var theme = ThemeColor.RED
        val seen = mutableListOf(theme)
        repeat(ThemeColor.entries.size - 1) {
            theme = theme.next()
            seen += theme
        }
        assertEquals("循环应当不重复地覆盖全部主题", ThemeColor.entries.size, seen.distinct().size)
        assertEquals("再切一次应当回到起点", ThemeColor.RED, theme.next())
    }

    @Test
    fun `按名字解析未知值会回落到默认`() {
        assertEquals(ThemeColor.RED, ThemeColor.fromName(null))
        assertEquals(ThemeColor.RED, ThemeColor.fromName("NOT_A_THEME"))
        assertEquals(ThemeColor.GREEN, ThemeColor.fromName("GREEN"))
    }

    @Test
    fun `设置里改了主题会持久化`() {
        val store = InMemorySettingsStore()
        val state = CalculatorState(store, InMemoryHistoryStore())
        assertEquals(ThemeColor.RED, state.settings.themeColor)

        state.updateSettings { it.copy(themeColor = ThemeColor.PURPLE) }
        assertEquals(ThemeColor.PURPLE, store.load().themeColor)

        // 重新加载一份状态，模拟重启
        val reloaded = CalculatorState(store, InMemoryHistoryStore())
        assertEquals(ThemeColor.PURPLE, reloaded.settings.themeColor)
    }
}
