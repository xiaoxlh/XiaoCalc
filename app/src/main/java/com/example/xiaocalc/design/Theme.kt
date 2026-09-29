package com.example.xiaocalc.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 应用主题。
 *
 * 手表场景固定使用暗色方案（OLED 省电 + 夜间可读），因此不跟随系统切换；
 * 同时限制系统字体缩放上限——排版尺寸由设计坐标驱动，
 * 若用户把系统字号拉到 2.0x，固定高度的显示区会被裁切。
 * 这里把上限收到 [MAX_FONT_SCALE]，再叠加以"自适应缩字号"兜底。
 */
const val MAX_FONT_SCALE = 1.15f

@Composable
fun XiaoCalcTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val clamped = Density(
        density = density.density,
        fontScale = density.fontScale.coerceIn(0.85f, MAX_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides clamped) {
        MaterialTheme(
            colorScheme = XiaoCalcDarkColorScheme,
            typography = XiaoCalcTypography,
            content = content,
        )
    }
}
