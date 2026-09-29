package com.example.xiaocalc.adaptive

import androidx.compose.runtime.Immutable

/**
 * 一个纵向分区的布局结果（设计单位）。
 *
 * @param top       距表盘顶端的位置
 * @param height    高度
 * @param maxWidth  该区间内允许的最大宽度——圆形屏取 [Disc.chordInBand]，方形屏取可用宽度
 */
@Immutable
data class Zone(
    val top: Float,
    val height: Float,
    val maxWidth: Float,
) {
    val bottom: Float get() = top + height
    val centerY: Float get() = top + height / 2f

    init {
        require(height > 0f) { "zone height must be positive" }
        require(maxWidth > 0f) { "zone maxWidth must be positive" }
    }
}

/**
 * 键盘的一行。
 *
 * 旧做法把整块键盘塞进一个**统一宽度的正方形**里：圆屏上正方形的边长受
 * 内接约束（R·√2），于是每一行都被最窄的那一行拖累，左右留下大片空白，
 * 整个键盘缩成中间一小块。
 *
 * 现在改为**逐行贴合弦长**：[maxWidth] 是该行纵向区间内最窄处的弦长，
 * 每行的键宽由该行自己的宽度等分。结果是一块贴合圆形的梯形键盘——
 * 中间两行最宽、往下逐行收窄，圆形屏上的留白被真正利用起来。
 */
@Immutable
data class KeyRow(
    val top: Float,
    val height: Float,
    /** 该行内容允许的最大宽度 */
    val maxWidth: Float,
    /** 该行单个按键的宽度 */
    val keyWidth: Float,
    val columns: Int,
) {
    val bottom: Float get() = top + height
    val width: Float get() = columns * keyWidth + (columns - 1) * WatchLayout.KEY_GAP
}

/**
 * 整套表盘布局方案。由 [WatchLayout.of] 依"圆形 / 方形"推导，
 * 页面只读取这里的结果，不再自行判断形状、更不写 `if (round) padding = 62f` 这类魔数。
 *
 * 纵向结构（自上而下）：顶部时钟 → 显示区 → 键盘（逐行贴合）→ 底部操作行。
 */
@Immutable
data class WatchLayout(
    val round: Boolean,
    /** 内容区左右安全边距（方形屏用；圆形屏由弦长接管） */
    val sideMargin: Float,
    /** 顶栏：时钟居中，右侧溢出菜单 */
    val header: Zone,
    val display: Zone,
    /** 键盘整体所占区间（用于页面排布） */
    val keypad: Zone,
    /** 键盘的每一行，逐行贴合弦长 */
    val keyRows: List<KeyRow>,
    /** 底部操作行：C / ⌫ / 键盘分页 */
    val actionRow: Zone,
    /** 子页面（历史 / 设置 / 欢迎）的可滚动内容区 */
    val content: Zone,
) {
    val keypadColumns: Int get() = keyRows.firstOrNull()?.columns ?: WatchLayout.COLUMNS
    val keypadRows: Int get() = keyRows.size
    val keyGap: Float get() = WatchLayout.KEY_GAP

    /** 底部操作行的 4 个等宽槽位 */
    fun actionItemWidth(items: Int = ACTION_ITEMS): Float =
        (actionRow.maxWidth - (items - 1) * keyGap) / items

    companion object {

        const val COLUMNS = 4
        const val ROWS = 4
        const val KEY_GAP = 5f
        const val ACTION_ITEMS = 4

        /**
         * 顶栏图标/文字按钮的命中区边长（设计单位）。
         *
         * 布局与 UI 共用这一个常量：`Modifier.size()` 会被父级约束收紧，
         * 顶栏高度一旦小于命中区，圆形按钮就会被压成椭圆、可点面积随之缩水。
         * [DiscTest] 对"顶栏高度 ≥ 命中区"做了断言，避免这个耦合被无声改坏。
         */
        const val HEADER_ICON_TOUCH = 36f

        /**
         * 弦长安全系数。
         *
         * [Disc.chordInBand] 给出的是"恰好贴着表盘边缘"的宽度；实测截图时发现
         * 内容四角会精确落在圆周上、与边框零间距。乘一个系数留出可见留白，
         * 既避免视觉上的"要切不切"，也吸收浮点与取整误差。
         */
        const val BAND_SAFETY = 0.96f

        // ---------------- 圆形屏（D = 384 即表盘外接正方形，R = 192） ----------------
        // 顶栏不能贴到最顶端：y=8 处弦长只有 105du，居中的时钟右侧仅剩约 26du，
        // 放不下 36du 的溢出菜单命中区（会与时钟字形重叠）。下移到 y=16 后有 147du，
        // 时钟两侧各余约 47du，从容放下菜单按钮。
        private const val R_HEADER_TOP = 16f
        private const val R_HEADER_HEIGHT = 40f
        // 显示区高度 = 表达式行 + 结果行。两行都必须容纳 MiSans 的**真实行高（1.326em）**，
        // 否则 horizontalScroll 会裁掉逗号/括号的下伸部分（实测：千分位逗号被裁成圆点）。
        private const val R_DISPLAY_TOP = 60f
        private const val R_DISPLAY_HEIGHT = 70f
        private const val R_KEYPAD_TOP = 134f
        private const val R_KEYPAD_HEIGHT = 172f
        private const val R_ACTION_TOP = 310f
        private const val R_ACTION_HEIGHT = 42f
        // 子页面内容区（历史 / 设置 / 欢迎）：不必延伸到表盘最底部——
        // 越靠下弦长越短，[56,352] 只有 204du，会让设置项标题被省略号截断。
        // 收到 [56,330] 后有 256du，宽度够、高度仍富余。
        private const val R_CONTENT_TOP = 56f
        private const val R_CONTENT_HEIGHT = 274f

        // ---------------- 方形 / 异形屏 ----------------
        private const val S_MARGIN = 16f
        private const val S_HEADER_TOP = 16f
        private const val S_HEADER_HEIGHT = 42f
        private const val S_DISPLAY_TOP = 62f
        private const val S_DISPLAY_HEIGHT = 76f
        private const val S_KEYPAD_TOP = 142f
        private const val S_KEYPAD_HEIGHT = 176f
        private const val S_ACTION_TOP = 322f
        private const val S_ACTION_HEIGHT = 42f
        private const val S_CONTENT_TOP = 62f
        private const val S_CONTENT_HEIGHT = 280f

        fun of(round: Boolean): WatchLayout = if (round) roundLayout() else squareLayout()

        private fun roundLayout(): WatchLayout = WatchLayout(
            round = true,
            // 圆形屏不再做整体缩放：宽度全部交给弦长约束，避免"先缩 0.95 再逐页内收"的双重收缩
            sideMargin = 0f,
            header = band(R_HEADER_TOP, R_HEADER_HEIGHT),
            display = band(R_DISPLAY_TOP, R_DISPLAY_HEIGHT),
            keypad = band(R_KEYPAD_TOP, R_KEYPAD_HEIGHT),
            keyRows = roundKeyRows(R_KEYPAD_TOP, R_KEYPAD_HEIGHT),
            actionRow = band(R_ACTION_TOP, R_ACTION_HEIGHT),
            content = band(R_CONTENT_TOP, R_CONTENT_HEIGHT),
        )

        private fun squareLayout(): WatchLayout {
            val available = Disc.DESIGN - 2f * S_MARGIN
            return WatchLayout(
                round = false,
                sideMargin = S_MARGIN,
                header = Zone(S_HEADER_TOP, S_HEADER_HEIGHT, available),
                display = Zone(S_DISPLAY_TOP, S_DISPLAY_HEIGHT, available),
                keypad = Zone(S_KEYPAD_TOP, S_KEYPAD_HEIGHT, available),
                keyRows = squareKeyRows(S_KEYPAD_TOP, S_KEYPAD_HEIGHT, available),
                // 动作行与键盘网格**等宽**（而不是各自按长宽比算）：
                // 两者行高不同，各算各的会得到 300du vs 282du，
                // 底下一行比上面网格还宽，方屏上肉眼可见。
                actionRow = Zone(
                    S_ACTION_TOP,
                    S_ACTION_HEIGHT,
                    squareGridWidth(S_KEYPAD_HEIGHT, available),
                ),
                content = Zone(S_CONTENT_TOP, S_CONTENT_HEIGHT, available),
            )
        }

        /** 圆形屏：逐行按该行纵向区间的最窄弦长定宽 */
        private fun roundKeyRows(top: Float, height: Float): List<KeyRow> =
            rowSpecs(top, height) { rowTop, rowBottom ->
                Disc.chordInBand(rowTop, rowBottom) * BAND_SAFETY
            }

        /** 方形屏键盘行的最大长宽比：没有圆形约束时，硬铺满会得到 2.2:1 的"宽条" */
        private const val S_MAX_KEY_ASPECT = 1.7f

        /**
         * 方形屏：不逐行贴弦长（没有圆），而是把行宽收敛到"键长宽比 ≤ [S_MAX_KEY_ASPECT]"。
         *
         * 圆屏必须铺满——那是贴合圆形构图的一部分；方屏铺满只会得到又扁又宽的键。
         * 收敛后行居中，两侧留白，观感与手感都更像正常键盘。
         */
        private fun squareKeyRows(top: Float, height: Float, available: Float): List<KeyRow> {
            val width = squareGridWidth(height, available)
            return rowSpecs(top, height) { _, _ -> width }
        }

        /** 方形屏键盘网格的宽度：由"行高 × 最大长宽比"推出，动作行复用它以保证对齐 */
        private fun squareGridWidth(keypadHeight: Float, available: Float): Float {
            val rowHeight = (keypadHeight - (ROWS - 1) * KEY_GAP) / ROWS
            val widthByAspect = COLUMNS * rowHeight * S_MAX_KEY_ASPECT +
                (COLUMNS - 1) * KEY_GAP
            return minOf(available, widthByAspect)
        }

        private inline fun rowSpecs(
            top: Float,
            height: Float,
            widthAt: (rowTop: Float, rowBottom: Float) -> Float,
        ): List<KeyRow> {
            val rowHeight = (height - (ROWS - 1) * KEY_GAP) / ROWS
            return (0 until ROWS).map { index ->
                val rowTop = top + index * (rowHeight + KEY_GAP)
                val rowBottom = rowTop + rowHeight
                val width = widthAt(rowTop, rowBottom)
                KeyRow(
                    top = rowTop,
                    height = rowHeight,
                    maxWidth = width,
                    keyWidth = (width - (COLUMNS - 1) * KEY_GAP) / COLUMNS,
                    columns = COLUMNS,
                )
            }
        }

        private fun band(top: Float, height: Float) =
            Zone(top, height, Disc.chordInBand(top, top + height) * BAND_SAFETY)
    }
}
