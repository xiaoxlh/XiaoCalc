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

    /**
     * 交换（Material Symbols 的 swap_horiz）。
     *
     * 替掉原来的"换"字：一个汉字要占满整格才看得清，而箭头图形
     * 在所有语言里都读作"两边对调"，且不必担心字体子集缺字。
     */
    private const val SWAP_PATH =
        "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3z" +
            "M21 9l-3.99-4v3H10v2h7.01v3L21 9z"

    val Swap: ImageVector by lazy {
        vector(SWAP_PATH)
    }

    /**
     * 下拉指示（Material Symbols 的 arrow_drop_down）。
     *
     * 胶囊上只写 `m`、`CNY` 这样的符号时，填充色只能暗示"可点"；
     * 加一个小三角才明确读作"点我换一个"。
     */
    private const val CHEVRON_DOWN_PATH = "M7 10l5 5 5-5z"

    val ChevronDown: ImageVector by lazy {
        vector(CHEVRON_DOWN_PATH)
    }

    /** 刷新（Material Symbols 的 refresh）。货币页的"更新"原先是一个汉字，占满整格 */
    private const val REFRESH_PATH =
        "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.57 8 8 8" +
            "c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6" +
            "s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"

    val Refresh: ImageVector by lazy {
        vector(REFRESH_PATH)
    }

    /**
     * 工具面板入口（Material Symbols 的 apps：3×3 圆角方块阵列）。
     *
     * 底部操作行只有四个格子，用图形比再挤两个字"工具"更省地方，
     * 也不受字体子集是否收录该字影响。
     */
    private const val APPS_PATH =
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4z" +
            "m6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z"

    val Apps: ImageVector by lazy {
        vector(APPS_PATH)
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
