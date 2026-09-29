package com.example.xiaocalc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(PIXELS_PER_DETENT, rotaryPixels(ROTARY_DETENT), 1e-3f)
        assertEquals(-PIXELS_PER_DETENT, rotaryPixels(-ROTARY_DETENT), 1e-3f)
        assertEquals(0f, rotaryPixels(0f), 1e-6f)
    }

    @Test
    fun `快速旋转有上限保护`() {
        // 内核会把连续档位批量累加（实测单次最大约 0.38），不设限会跳很远
        assertEquals(MAX_PIXELS_PER_EVENT, rotaryPixels(10f), 1e-3f)
        assertEquals(-MAX_PIXELS_PER_EVENT, rotaryPixels(-10f), 1e-3f)
    }

    @Test
    fun `换算保持方向`() {
        assertTrue(rotaryPixels(ROTARY_DETENT * 3) > 0f)
        assertTrue(rotaryPixels(-ROTARY_DETENT * 3) < 0f)
    }
}
