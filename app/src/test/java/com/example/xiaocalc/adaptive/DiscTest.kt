package com.example.xiaocalc.adaptive

import com.example.xiaocalc.design.MISANS_LINE_HEIGHT_RATIO
import com.example.xiaocalc.ui.components.EXPRESSION_ROW_DU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆屏几何的纯数学回归。
 *
 * 这是本次重构的核心：**"内容是否落在表盘内"必须是一条可验证的不等式，
 * 而不是靠肉眼看截图调 padding**。
 */
class DiscTest {

    @Test
    fun `圆心处弦长等于直径`() {
        assertEquals(Disc.DESIGN, Disc.chord(Disc.CENTER), 1e-3f)
    }

    @Test
    fun `上下边缘弦长为零`() {
        assertEquals(0f, Disc.chord(0f), 1e-3f)
        assertEquals(0f, Disc.chord(Disc.DESIGN), 1e-3f)
    }

    @Test
    fun `弦长关于圆心对称`() {
        for (offset in 0..190 step 5) {
            val up = Disc.chord(Disc.CENTER - offset)
            val down = Disc.chord(Disc.CENTER + offset)
            assertEquals(up, down, 1e-3f)
        }
    }

    @Test
    fun `区间弦长取离圆心较远的一端`() {
        // 区间 [80,300] 跨过圆心，|80-192|=112 比 |300-192|=108 更远，
        // 因此最窄处出现在 y=80 一侧
        assertEquals(Disc.chord(80f), Disc.chordInBand(80f, 300f), 1e-3f)
        // 区间完全在下半部，远端为 y=360
        assertEquals(Disc.chord(360f), Disc.chordInBand(300f, 360f), 1e-3f)
    }

    @Test
    fun `区间弦长是区间内处处可用的最窄宽度`() {
        // chordInBand 的契约是"该区间内处处可用的宽度"，
        // 因此它必须是区间内弦长的**下确界**：任何 y 处的弦长都不小于它。
        for (top in 0..340 step 20) {
            val bottom = (top + 40).coerceAtMost(384)
            val band = Disc.chordInBand(top.toFloat(), bottom.toFloat())
            for (y in top..bottom step 4) {
                assertTrue(
                    "y=$y 处弦长 ${Disc.chord(y.toFloat())} 小于区间可用宽度 $band",
                    Disc.chord(y.toFloat()) >= band - 1e-3f,
                )
            }
        }
    }

    @Test
    fun `内接正方形边长等于半径乘根号二`() {
        assertEquals(Disc.RADIUS * 1.4142135f, Disc.inscribedSquare(), 1e-2f)
    }

    // ------------------------------------------------------------------ 布局

    @Test
    fun `圆形屏每个分区的四角都在表盘内`() {
        val layout = WatchLayout.of(round = true)
        for ((name, zone) in zonesOf(layout)) {
            assertCornersInsideDisc(name, zone)
        }
    }

    /** 键盘逐行铺满：每一行的四角都必须在表盘内，否则宽键会切到表盘外 */
    @Test
    fun `圆形屏键盘每一行的四角都在表盘内`() {
        val layout = WatchLayout.of(round = true)
        layout.keyRows.forEachIndexed { index, row ->
            val half = row.width / 2f
            for (y in listOf(row.top, row.bottom)) {
                assertTrue(
                    "第 ${index + 1} 行 x=±$half, y=$y 落在表盘外",
                    pointInsideDisc(half, y),
                )
            }
        }
    }

    /** "尽量铺满"：每一行实际用掉的宽度应当接近该行可用的最窄弦长 */
    @Test
    fun `圆形屏键盘每一行都铺满可用宽度`() {
        val layout = WatchLayout.of(round = true)
        layout.keyRows.forEachIndexed { index, row ->
            val available = Disc.chordInBand(row.top, row.bottom)
            val fillRatio = row.maxWidth / available
            assertTrue(
                "第 ${index + 1} 行只用了可用宽度的 ${(fillRatio * 100).toInt()}%",
                fillRatio >= 0.9f,
            )
            assertTrue(
                "第 ${index + 1} 行宽度 ${row.maxWidth} 超过可用弦长 $available",
                row.maxWidth <= available + 1e-3f,
            )
            // 键宽由行宽等分而来，二者必须自洽
            val expected = row.columns * row.keyWidth + (row.columns - 1) * WatchLayout.KEY_GAP
            assertEquals(row.maxWidth, expected, 1e-3f)
        }
    }

    @Test
    fun `键盘各行不重叠且落在键盘区间内`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.of(round)
            val rows = layout.keyRows
            assertEquals(WatchLayout.ROWS, rows.size)
            rows.forEachIndexed { index, row ->
                assertTrue("round=$round 第 ${index + 1} 行越过键盘上界", row.top >= layout.keypad.top - 1e-3f)
                assertTrue(
                    "round=$round 第 ${index + 1} 行越过键盘下界",
                    row.bottom <= layout.keypad.bottom + 1e-3f,
                )
                assertTrue("round=$round 第 ${index + 1} 行按键过小", row.keyWidth >= 30f)
                assertTrue("round=$round 第 ${index + 1} 行按键过矮", row.height >= 30f)
            }
            rows.zipWithNext().forEach { (a, b) ->
                assertTrue("round=$round 键盘行重叠：${a.bottom} > ${b.top}", a.bottom <= b.top + 1e-3f)
            }
        }
    }

    @Test
    fun `底部操作行足以放下四个等宽项`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.of(round)
            val item = layout.actionItemWidth()
            assertTrue("round=$round 操作项过小：$item", item >= 30f)
            val total = item * WatchLayout.ACTION_ITEMS +
                (WatchLayout.ACTION_ITEMS - 1) * layout.keyGap
            assertTrue("round=$round 操作行总宽 $total 超过 ${layout.actionRow.maxWidth}", total <= layout.actionRow.maxWidth + 1e-3f)
        }
    }

    @Test
    fun `方形屏分区不重叠且都在内容区内`() {
        val layout = WatchLayout.of(round = false)
        // content 是"子页面"的替代布局，与计算器的 display/keypad 互斥，
        // 因此只对计算器自身的分段做重叠校验
        val calculatorZones = listOf(
            "header" to layout.header,
            "display" to layout.display,
            "keypad" to layout.keypad,
            "actionRow" to layout.actionRow,
        ).sortedBy { it.second.top }

        calculatorZones.forEach { (name, zone) ->
            assertTrue("$name 超出内容区上界", zone.top >= 0f)
            assertTrue("$name 超出内容区下界", zone.bottom <= Disc.DESIGN)
            assertTrue("$name 宽度超过可用宽度", zone.maxWidth <= Disc.DESIGN + 1e-3f)
        }
        calculatorZones.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "${a.first} 与 ${b.first} 纵向重叠：${a.second.bottom} > ${b.second.top}",
                a.second.bottom <= b.second.top + 1e-3f,
            )
        }

        assertTrue("content 与 header 重叠", layout.content.top >= layout.header.bottom)
        assertTrue("content 超出内容区下界", layout.content.bottom <= Disc.DESIGN)
        assertTrue("content 宽度超过可用宽度", layout.content.maxWidth <= Disc.DESIGN + 1e-3f)
    }

    /**
     * 回归护栏：`Modifier.size()` 会被父级约束收紧，顶栏高度小于图标命中区时，
     * 圆形按钮会被压成椭圆、可点面积缩水（实测出现过 83×49 的椭圆主按钮）。
     */
    @Test
    fun `顶栏高度足以容纳图标命中区`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.of(round)
            assertTrue(
                "round=$round 顶栏高度 ${layout.header.height} 小于命中区 ${WatchLayout.HEADER_ICON_TOUCH}",
                layout.header.height >= WatchLayout.HEADER_ICON_TOUCH,
            )
        }
    }

    /** 显示区的两行都必须容纳 MiSans 的 1.326em 行高，否则会裁掉逗号等下伸字形 */
    @Test
    fun `显示区高度足以容纳两行文字`() {
        val minResultFontDu = 20f
        for (round in listOf(true, false)) {
            val layout = WatchLayout.of(round)
            val need = (EXPRESSION_ROW_DU + minResultFontDu) * MISANS_LINE_HEIGHT_RATIO
            assertTrue(
                "round=$round 显示区高度 ${layout.display.height} 不足以容纳两行（需要约 $need）",
                layout.display.height >= need,
            )
            val resultRowDu = layout.display.height - EXPRESSION_ROW_DU
            assertTrue("round=$round 结果行高度 $resultRowDu 过小", resultRowDu >= minResultFontDu)
        }
    }

    @Test
    fun `圆形屏各分区都不超出内容区上下界`() {
        val layout = WatchLayout.of(round = true)
        for ((name, zone) in zonesOf(layout)) {
            assertTrue("$name 上界为负", zone.top >= 0f)
            assertTrue("$name 下界 ${zone.bottom} 超出表盘", zone.bottom <= Disc.DESIGN)
        }
    }

    /**
     * 回归护栏：旧实现把整块内容按短边铺满（相当于宽度 384du 的矩形），
     * 圆屏上四角必然切出表盘——这正是旧版需要逐页写 `if (round) padding = 62f`
     * 这类魔数内收的根因。此处固化"满宽必然出圆"这一结论。
     */
    @Test
    fun `满宽矩形在圆形屏上必然切角`() {
        val halfWidth = Disc.DESIGN / 2f
        val topCornerInside = pointInsideDisc(halfWidth, 0f)
        assertTrue("满宽内容区顶角应当落在表盘外", !topCornerInside)
    }

    private fun zonesOf(layout: WatchLayout): List<Pair<String, Zone>> = listOf(
        "header" to layout.header,
        "display" to layout.display,
        "keypad" to layout.keypad,
        "actionRow" to layout.actionRow,
        "content" to layout.content,
    )

    private fun assertCornersInsideDisc(name: String, zone: Zone) {
        val half = zone.maxWidth / 2f
        for (y in listOf(zone.top, zone.bottom)) {
            assertTrue(
                "$name 的左下/右下角 (x=${-half}, y=$y) 落在表盘外",
                pointInsideDisc(half, y),
            )
        }
    }

    /** 以表盘圆心为原点，判断相对坐标是否在圆内 */
    private fun pointInsideDisc(dx: Float, y: Float): Boolean {
        val dy = y - Disc.CENTER
        return dx * dx + dy * dy <= Disc.RADIUS * Disc.RADIUS + 1e-3f
    }
}
