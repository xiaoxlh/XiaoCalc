package com.example.xiaocalc.ui.components

import com.example.xiaocalc.adaptive.Disc
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 选择器面板的圆屏几何约束。
 *
 * 存在理由：面板是矩形，而屏幕是圆的。矩形**四角**到圆心的距离若超过半径，
 * 角就会被裁掉——这条不等式差一点点都会在截图里看出来，
 * 但单元测试看不出来（除非专门为它写一条）。初版就是只差 5du。
 *
 * 这里同时守住"留有余量"而不仅是"没超出"：刚好贴边在圆屏上是
 * "要切不切"的观感，[MIN_CLEARANCE_DU] 就是为此设的下限。
 */
class PickerOverlayGeometryTest {

    /** 四角到圆心的距离 */
    private fun cornerDistance(width: Float, height: Float): Double =
        sqrt((width / 2f).toDouble() * (width / 2f) + (height / 2f).toDouble() * (height / 2f))

    /** 允许的最小余量（设计单位）：面板角与圆周之间至少留这么多 */
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
        // 面板是"宽扁"形状还是"高瘦"形状会影响可用高度；
        // 这条断言保证宽度没有为了解决高度问题而无限膨胀
        assertTrue("面板过宽", PICKER_WIDTH_DU <= Disc.RADIUS * 1.4f)
    }

    @Test
    fun `列表上限小于面板高度上限`() {
        // 面板高度 = 表头 + 内边距 + 列表；列表不能吃掉全部高度
        assertTrue(PICKER_LIST_MAX_DU < PICKER_TOTAL_HEIGHT_DU)
    }
}
