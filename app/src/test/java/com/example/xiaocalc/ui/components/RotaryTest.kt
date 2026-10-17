package com.example.xiaocalc.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    @Test
    fun `一档对应一个可观的滚动量`() {
        assertEquals(PIXELS_PER_DETENT, abs(rotaryPixels(ROTARY_DETENT)), 1e-3f)
        assertEquals(0f, rotaryPixels(0f), 1e-6f)
    }

    @Test
    fun `正转使列表向上滚动`() {
        assertTrue(rotaryPixels(ROTARY_DETENT) < 0f)
        assertTrue(rotaryPixels(-ROTARY_DETENT) > 0f)
    }

    @Test
    fun `快速旋转有上限保护`() {
        assertEquals(ROTARY_DIRECTION * MAX_PIXELS_PER_EVENT, rotaryPixels(10f), 1e-3f)
        assertEquals(-ROTARY_DIRECTION * MAX_PIXELS_PER_EVENT, rotaryPixels(-10f), 1e-3f)
        assertTrue(MAX_PIXELS_PER_EVENT < 27f * PIXELS_PER_DETENT)
    }
}
