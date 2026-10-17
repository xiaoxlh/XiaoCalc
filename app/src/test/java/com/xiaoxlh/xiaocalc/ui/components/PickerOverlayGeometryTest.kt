package com.xiaoxlh.xiaocalc.ui.components

import com.xiaoxlh.xiaocalc.adaptive.Disc
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PickerOverlayGeometryTest {
    private fun cornerDistance(width: Float, height: Float): Double =
        sqrt((width / 2f).toDouble() * (width / 2f) + (height / 2f).toDouble() * (height / 2f))

    private val minClearance = 12.0

    @Test
    fun `面板四角落在表盘圆内且有余量`() {
        val distance = cornerDistance(PICKER_WIDTH_DU, PICKER_TOTAL_HEIGHT_DU)
        val clearance = Disc.RADIUS - distance
        assertTrue(
            "面板 ${PICKER_WIDTH_DU}×$PICKER_TOTAL_HEIGHT_DU 的四角距圆心 $distance，" +
                "已超出半径 ${Disc.RADIUS}",
            clearance > 0,
        )
        assertTrue(
            "面板 ${PICKER_WIDTH_DU}×$PICKER_TOTAL_HEIGHT_DU 距圆周只剩 $clearance，" +
                "低于最小余量 $minClearance",
            clearance >= minClearance,
        )
    }

    @Test
    fun `面板宽度不超过圆的内接正方形以外太多`() {
        assertTrue("面板过宽", PICKER_WIDTH_DU <= Disc.RADIUS * 1.4f)
    }

    @Test
    fun `列表上限小于面板高度上限`() {
        assertTrue(PICKER_LIST_MAX_DU < PICKER_TOTAL_HEIGHT_DU)
    }
}
