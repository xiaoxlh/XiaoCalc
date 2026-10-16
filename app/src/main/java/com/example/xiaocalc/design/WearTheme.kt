package com.example.xiaocalc.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import com.example.xiaocalc.calc.ThemeColor

/**
 * Wear Material 3 主题。
 *
 * ## 为什么是"嵌套"而不是"替换"
 *
 * 本工程的界面绝大部分是手写的（键盘、圆盘布局、显示区），读的是常规
 * `androidx.compose.material3.MaterialTheme`；而官方手表组件读的是
 * `androidx.wear.compose.material3.MaterialTheme`——**两个是各自独立的主题**，
 * 角色名也不同（Wear 多了 `primaryDim`、`surfaceContainerLow` 等）。
 *
 * 直接换成 Wear 主题意味着改动约二十个文件；而**嵌套**可以让两者共存：
 * 下面是常规主题（现有界面零改动），这里是 Wear 主题（官方组件用），
 * 两份配色由同一份调色板生成，因此不会出现颜色对不上的情况。
 *
 * 这样官方组件可以逐页引入，每一步都能用快照单独验证。
 *
 * ## 动效
 *
 * 用 [MotionScheme.expressive]：Expressive 的弹性与位移曲线是这一版设计规范的核心，
 * 而它体现在**组件的默认动画**里——只有把动效方案交给 Wear 主题，官方组件才会用它。
 */
@Composable
internal fun WearTheme(themeColor: ThemeColor, content: @Composable () -> Unit) {
    val scheme = remember(themeColor) { wearColorSchemeFor(themeColor) }
    MaterialTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * 把手写的调色板映射到 Wear 的角色。
 *
 * `*Dim` 是 Wear 独有的"弱化"角色（用于禁用态与次级表面），
 * 常规 Material 3 里没有对应项，映射到各自的容器色是最接近的语义。
 */
private fun wearColorSchemeFor(theme: ThemeColor): ColorScheme = ColorScheme().copy(
    // 主色：品牌蓝 —— 与常规主题保持一致
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF17225E),
    onPrimaryContainer = Color(0xFFB9C6FF),
    primaryDim = Color(0xFF17225E),

    // 次色：强调色 —— 随主题变化
    secondary = Color(theme.accent),
    onSecondary = Color(theme.onAccent),
    secondaryContainer = Color(theme.accentContainer),
    onSecondaryContainer = Color(theme.onAccentContainer),
    secondaryDim = Color(theme.accentContainer),

    tertiary = Color(0xFF7FD1FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF004C69),
    onTertiaryContainer = Color(0xFFC4E7FF),
    tertiaryDim = Color(0xFF004C69),

    // 表面层级：与常规主题逐值相同，保证同屏两类组件看不出色差
    background = Color.Black,
    onBackground = Color.White,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF252525),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB9B9B9),

    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF333333),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    errorDim = Color(0xFF8C1D18),
)
