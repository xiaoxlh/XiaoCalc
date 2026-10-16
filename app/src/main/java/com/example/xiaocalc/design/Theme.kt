package com.example.xiaocalc.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.xiaocalc.calc.ThemeColor

/**
 * 系统字体缩放的接受上限。
 *
 * 排版尺寸由设计坐标驱动，若用户把系统字号拉到 2.0x，固定高度的显示区会被裁切。
 * 这里把上限收到本值，再叠加以"自适应缩字号"兜底。
 */
const val MAX_FONT_SCALE = 1.15f

/**
 * 应用主题。
 *
 * 手表场景固定使用暗色方案（OLED 省电 + 夜间可读），因此不跟随系统切换。
 *
 * @param themeColor 强调色主题。默认红，与旧版逐值相同
 */
@Composable
fun XiaoCalcTheme(
    themeColor: ThemeColor = ThemeColor.RED,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val clamped = Density(
        density = density.density,
        fontScale = density.fontScale.coerceIn(0.85f, MAX_FONT_SCALE),
    )
    // 配色方案只在主题变化时重建：它是个几十字段的对象，
    // 挂在每次重组上会把整棵树的 MaterialTheme 都标记为变化
    val scheme = remember(themeColor) { colorSchemeFor(themeColor) }
    CompositionLocalProvider(LocalDensity provides clamped) {
        MaterialTheme(
            colorScheme = scheme,
            typography = XiaoCalcTypography,
            content = content,
        )
    }
}
