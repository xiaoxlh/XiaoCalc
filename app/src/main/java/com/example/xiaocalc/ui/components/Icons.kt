package com.example.xiaocalc.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘图标集。
 *
 * 为什么退格不用字形：MiSans **不含 U+232B（⌫）**，
 * 旧版本把 `⌫` 当普通文字渲染，实际是靠系统字体回退"碰巧"显示出来的——
 * 换设备/换系统字体就可能变成方块。`tools/subset_font.py` 的字形校验会直接报出这一缺失，
 * 因此这里改为矢量路径，渲染结果与字体无关。
 */
object WatchVectorIcons {

    /** Material Symbols 的 backspace 轮廓 */
    private const val BACKSPACE_PATH =
        "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" +
            "M19 15.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12z"

    val Backspace: ImageVector by lazy {
        vector(BACKSPACE_PATH)
    }

    /** Material Symbols 的 content_copy 轮廓（两个叠放的圆角矩形） */
    private const val COPY_PATH =
        "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1z" +
            "M19 5H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2z" +
            "M19 21H8V7h11v14z"

    val Copy: ImageVector by lazy {
        vector(COPY_PATH)
    }

    private fun vector(path: String): ImageVector =
        ImageVector.Builder(
            name = "WatchIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                // 实际颜色由 Icon 的 tint 覆盖
                fill = SolidColor(Color.Black),
            )
        }.build()
}
