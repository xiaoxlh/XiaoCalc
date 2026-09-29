package com.example.xiaocalc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 表冠行为决策的回归。
 *
 * "能滚就滚、没得滚才换页"这条规则必须可测，否则很容易退化成
 * "短表达式下拧表冠毫无反应"——那看起来就像功能坏了。
 */
class RotaryTest {

    @Test
    fun `表达式可滚时优先滚表达式`() {
        assertEquals(
            RotaryTarget.EXPRESSION,
            rotaryTarget(expressionScrollable = true, resultScrollable = true),
        )
        assertEquals(
            RotaryTarget.EXPRESSION,
            rotaryTarget(expressionScrollable = true, resultScrollable = false),
        )
    }

    @Test
    fun `表达式不可滚时滚结果`() {
        assertEquals(
            RotaryTarget.RESULT,
            rotaryTarget(expressionScrollable = false, resultScrollable = true),
        )
    }

    @Test
    fun `都不可滚时换页——保证拧表冠永远有反应`() {
        assertEquals(
            RotaryTarget.KEYPAD_PAGE,
            rotaryTarget(expressionScrollable = false, resultScrollable = false),
        )
    }

    // ------------------------------------------------------------ 量纲换算

    @Test
    fun `一档对应一个可观的滚动量`() {
        // 实测一档 ≈ 0.0131；若直接当像素用，转一档只会动 0.01px，等于没反应
        assertEquals(PIXELS_PER_DETENT, abs(rotaryPixels(ROTARY_DETENT)), 1e-3f)
        assertEquals(0f, rotaryPixels(0f), 1e-6f)
    }

    @Test
    fun `正转使列表向上滚动`() {
        // 真机实测：正转（内核 REL_WHEEL 为正）应向上滚，即 ScrollState 的值减小。
        // 方向单独成常量，避免与"速度"混在一个数里互相干扰。
        assertTrue(rotaryPixels(ROTARY_DETENT) < 0f)
        assertTrue(rotaryPixels(-ROTARY_DETENT) > 0f)
    }

    @Test
    fun `快速旋转有上限保护`() {
        // 实测单次事件最大可携带约 27 档，不封顶会瞬移
        assertEquals(ROTARY_DIRECTION * MAX_PIXELS_PER_EVENT, rotaryPixels(10f), 1e-3f)
        assertEquals(-ROTARY_DIRECTION * MAX_PIXELS_PER_EVENT, rotaryPixels(-10f), 1e-3f)
        assertTrue(MAX_PIXELS_PER_EVENT < 27f * PIXELS_PER_DETENT)
    }
}
