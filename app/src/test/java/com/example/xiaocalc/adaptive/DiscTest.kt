package com.example.xiaocalc.adaptive

import com.example.xiaocalc.design.MISANS_LINE_HEIGHT_RATIO
import com.example.xiaocalc.ui.components.EXPRESSION_ROW_DU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(Disc.chord(80f), Disc.chordInBand(80f, 300f), 1e-3f)
        assertEquals(Disc.chord(360f), Disc.chordInBand(300f, 360f), 1e-3f)
    }

    @Test
    fun `区间弦长是区间内处处可用的最窄宽度`() {
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

    @Test
    fun `圆形屏每个分区的四角都在表盘内`() {
        val layout = WatchLayout.of(round = true)
        for ((name, zone) in zonesOf(layout)) {
            assertCornersInsideDisc(name, zone)
        }
    }

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

    @Test
    fun `满宽矩形在圆形屏上必然切角`() {
        val halfWidth = Disc.DESIGN / 2f
        val topCornerInside = pointInsideDisc(halfWidth, 0f)
        assertTrue("满宽内容区顶角应当落在表盘外", !topCornerInside)
    }

    @Test
    fun `单位页没有额外操作行且分区互不重叠`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.units(round)
            assertTrue("round=$round 单位页不应再有额外行", layout.extraRow == null)
            val zones = listOf(
                "header" to layout.header,
                "display" to layout.display,
                "keypad" to layout.keypad,
                "actionRow" to layout.actionRow,
            ).sortedBy { it.second.top }
            zones.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "round=$round ${a.first} 与 ${b.first} 纵向重叠：${a.second.bottom} > ${b.second.top}",
                    a.second.bottom <= b.second.top + 1e-3f,
                )
            }
            assertTrue(
                "round=$round 顶栏与显示区之间空了 ${layout.display.top - layout.header.bottom}du",
                layout.display.top - layout.header.bottom <= 16f,
            )
            assertTrue(
                "round=$round 键盘下沿越过操作行",
                layout.keypad.bottom <= layout.actionRow.top + 1e-3f,
            )
            assertTrue("round=$round 操作行超出表盘", layout.actionRow.bottom <= Disc.DESIGN)
            if (round) {
                zones.forEach { (name, zone) -> assertCornersInsideDisc(name, zone) }
            }
        }
    }

    @Test
    fun `单位页每一行都放得下胶囊并留足数字宽度`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.units(round)
            val row = layout.display.height / 2f
            assertTrue("round=$round 显示行只有 ${row}du", row >= 40f)
            val chip = row - WatchLayout.DISPLAY_CHIP_INSET
            assertTrue("round=$round 胶囊只有 ${chip}du 高", chip >= 30f)
            val numberWidth = layout.display.maxWidth - WatchLayout.DISPLAY_CHIP_WIDTH - WatchLayout.DISPLAY_CHIP_GAP
            assertTrue(
                "round=$round 胶囊占完后数字只剩 ${numberWidth}du",
                numberWidth >= 150f,
            )
        }
    }

    @Test
    fun `单位页把选择行还给显示区与键盘`() {
        for (round in listOf(true, false)) {
            val units = WatchLayout.units(round)
            val equation = WatchLayout.equation(round)
            assertTrue(
                "round=$round 单位页键高 ${units.keyRows.first().height} 小于方程页",
                units.keyRows.first().height >= equation.keyRows.first().height - 1e-3f,
            )
            assertTrue(
                "round=$round 单位页显示区不比方程页高",
                units.display.height > equation.display.height,
            )
        }
    }

    @Test
    fun `额外操作行落在表盘内且不与相邻分区重叠`() {
        for (round in listOf(true, false)) {
            val layout = WatchLayout.equation(round)
            val extra = layout.extraRow ?: error("round=$round 缺少额外操作行")
            assertTrue("round=$round 额外行与顶栏重叠", extra.top >= layout.header.bottom - 1e-3f)
            assertTrue("round=$round 额外行与显示区重叠", extra.bottom <= layout.display.top + 1e-3f)
            assertTrue("round=$round 额外行下方超出表盘", extra.bottom <= Disc.DESIGN)
            if (round) assertCornersInsideDisc("额外行", extra)
        }
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

    private fun pointInsideDisc(dx: Float, y: Float): Boolean {
        val dy = y - Disc.CENTER
        return dx * dx + dy * dy <= Disc.RADIUS * Disc.RADIUS + 1e-3f
    }
}
