package com.xiaoxlh.xiaocalc.adaptive

import androidx.compose.runtime.Immutable

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

@Immutable
data class KeyRow(
    val top: Float,
    val height: Float,

    val maxWidth: Float,

    val keyWidth: Float,
    val columns: Int,
) {
    val bottom: Float get() = top + height
    val width: Float get() = columns * keyWidth + (columns - 1) * WatchLayout.KEY_GAP
}

@Immutable
data class WatchLayout(
    val round: Boolean,

    val sideMargin: Float,

    val header: Zone,
    val display: Zone,

    val keypad: Zone,

    val keyRows: List<KeyRow>,

    val actionRow: Zone,

    val content: Zone,

    val extraRow: Zone? = null,
) {
    val keypadColumns: Int get() = keyRows.firstOrNull()?.columns ?: WatchLayout.COLUMNS
    val keypadRows: Int get() = keyRows.size
    val keyGap: Float get() = WatchLayout.KEY_GAP

    fun actionItemWidth(items: Int = ACTION_ITEMS): Float =
        (actionRow.maxWidth - (items - 1) * keyGap) / items

    companion object {
        const val COLUMNS = 4
        const val ROWS = 4
        const val KEY_GAP = 5f
        const val ACTION_ITEMS = 4
        const val HEADER_ICON_TOUCH = 36f
        const val BAND_SAFETY = 0.96f
        const val DISPLAY_CHIP_WIDTH = 74f
        const val DISPLAY_CHIP_INSET = 8f
        const val DISPLAY_CHIP_GAP = 7f
        private const val R_HEADER_TOP = 16f
        private const val R_HEADER_HEIGHT = 40f
        private const val R_DISPLAY_TOP = 60f
        private const val R_DISPLAY_HEIGHT = 70f
        private const val R_KEYPAD_TOP = 134f
        private const val R_KEYPAD_HEIGHT = 172f
        private const val R_ACTION_TOP = 310f
        private const val R_ACTION_HEIGHT = 42f
        private const val R_CONTENT_TOP = 56f
        private const val R_CONTENT_HEIGHT = 274f
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
        private const val U2_DISPLAY_TOP = 68f
        private const val U2_DISPLAY_HEIGHT = 86f
        private const val U2_KEYPAD_TOP = 158f
        private const val U2_KEYPAD_HEIGHT = 148f
        private const val SU2_DISPLAY_TOP = 70f
        private const val SU2_DISPLAY_HEIGHT = 86f
        private const val SU2_KEYPAD_TOP = 160f
        private const val SU2_KEYPAD_HEIGHT = 148f
        private const val U_PICKER_TOP = 58f
        private const val U_PICKER_HEIGHT = 44f
        private const val U_DISPLAY_TOP = 104f
        private const val U_DISPLAY_HEIGHT = 56f
        private const val U_KEYPAD_TOP = 162f
        private const val U_KEYPAD_HEIGHT = 144f
        private const val U_ACTION_TOP = 310f
        private const val U_ACTION_HEIGHT = 42f
        private const val SU_PICKER_TOP = 60f
        private const val SU_PICKER_HEIGHT = 44f
        private const val SU_DISPLAY_TOP = 106f
        private const val SU_DISPLAY_HEIGHT = 60f
        private const val SU_KEYPAD_TOP = 168f
        private const val SU_KEYPAD_HEIGHT = 148f
        private const val SU_ACTION_TOP = 322f
        private const val SU_ACTION_HEIGHT = 42f
        fun of(round: Boolean): WatchLayout = if (round) roundLayout() else squareLayout()
        private fun withExtraRow(round: Boolean): WatchLayout = if (round) {
            WatchLayout(
                round = true,
                sideMargin = 0f,
                header = band(R_HEADER_TOP, R_HEADER_HEIGHT),
                extraRow = band(U_PICKER_TOP, U_PICKER_HEIGHT),
                display = band(U_DISPLAY_TOP, U_DISPLAY_HEIGHT),
                keypad = band(U_KEYPAD_TOP, U_KEYPAD_HEIGHT),
                keyRows = roundKeyRows(U_KEYPAD_TOP, U_KEYPAD_HEIGHT),
                actionRow = band(U_ACTION_TOP, U_ACTION_HEIGHT),
                content = band(R_CONTENT_TOP, R_CONTENT_HEIGHT),
            )
        } else {
            val available = Disc.DESIGN - 2f * S_MARGIN
            WatchLayout(
                round = false,
                sideMargin = S_MARGIN,
                header = Zone(S_HEADER_TOP, S_HEADER_HEIGHT, available),
                extraRow = Zone(SU_PICKER_TOP, SU_PICKER_HEIGHT, available),
                display = Zone(SU_DISPLAY_TOP, SU_DISPLAY_HEIGHT, available),
                keypad = Zone(SU_KEYPAD_TOP, SU_KEYPAD_HEIGHT, available),
                keyRows = squareKeyRows(SU_KEYPAD_TOP, SU_KEYPAD_HEIGHT, available),
                actionRow = Zone(
                    SU_ACTION_TOP,
                    SU_ACTION_HEIGHT,
                    squareGridWidth(SU_KEYPAD_HEIGHT, available),
                ),
                content = Zone(S_CONTENT_TOP, S_CONTENT_HEIGHT, available),
            )
        }
        fun units(round: Boolean): WatchLayout = if (round) {
            WatchLayout(
                round = true,
                sideMargin = 0f,
                header = band(R_HEADER_TOP, R_HEADER_HEIGHT),
                display = band(U2_DISPLAY_TOP, U2_DISPLAY_HEIGHT),
                keypad = band(U2_KEYPAD_TOP, U2_KEYPAD_HEIGHT),
                keyRows = roundKeyRows(U2_KEYPAD_TOP, U2_KEYPAD_HEIGHT),
                actionRow = band(R_ACTION_TOP, R_ACTION_HEIGHT),
                content = band(R_CONTENT_TOP, R_CONTENT_HEIGHT),
            )
        } else {
            val available = Disc.DESIGN - 2f * S_MARGIN
            WatchLayout(
                round = false,
                sideMargin = S_MARGIN,
                header = Zone(S_HEADER_TOP, S_HEADER_HEIGHT, available),
                display = Zone(SU2_DISPLAY_TOP, SU2_DISPLAY_HEIGHT, available),
                keypad = Zone(SU2_KEYPAD_TOP, SU2_KEYPAD_HEIGHT, available),
                keyRows = squareKeyRows(SU2_KEYPAD_TOP, SU2_KEYPAD_HEIGHT, available),
                actionRow = Zone(
                    SU_ACTION_TOP,
                    SU_ACTION_HEIGHT,
                    squareGridWidth(SU2_KEYPAD_HEIGHT, available),
                ),
                content = Zone(S_CONTENT_TOP, S_CONTENT_HEIGHT, available),
            )
        }
        fun equation(round: Boolean): WatchLayout = withExtraRow(round)
        private fun roundLayout(): WatchLayout = WatchLayout(
            round = true,
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
                actionRow = Zone(
                    S_ACTION_TOP,
                    S_ACTION_HEIGHT,
                    squareGridWidth(S_KEYPAD_HEIGHT, available),
                ),
                content = Zone(S_CONTENT_TOP, S_CONTENT_HEIGHT, available),
            )
        }
        private fun roundKeyRows(top: Float, height: Float): List<KeyRow> =
            rowSpecs(top, height) { rowTop, rowBottom ->
                Disc.chordInBand(rowTop, rowBottom) * BAND_SAFETY
            }
        private const val S_MAX_KEY_ASPECT = 1.7f
        private fun squareKeyRows(top: Float, height: Float, available: Float): List<KeyRow> {
            val width = squareGridWidth(height, available)
            return rowSpecs(top, height) { _, _ -> width }
        }
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
