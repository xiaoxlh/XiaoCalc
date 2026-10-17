package com.example.xiaocalc.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.example.xiaocalc.calc.ThemeColor

/**
 * 结构色与强调色现在**同源**。
 *
 * 早期版本把"导航/状态"交给一个固定的品牌蓝，只让"运算与结果"跟着主题变，
 * 理由是"两色分工能分清哪个是导航、哪个是运算"。实际用下来这个理由站不住：
 * 换了主题之后，页面上总有几个按钮还是蓝的——用户看到的是"主题没生效"，
 * 而不是"这里在表达结构"。所以现在 primary / secondary / tertiary 三族
 * 全部由 [ThemeColor] 派生，只靠亮度与容器层级区分层次。
 *
 * [ThemeColor.RED] 仍然与旧版的主色值一致，升级后不会突然变得陌生。
 */
fun colorSchemeFor(theme: ThemeColor): ColorScheme = darkColorScheme(
    // 主色：当前页、选中态、分段控件、滚动指示
    primary = Color(theme.accent),
    onPrimary = Color(theme.onAccent),
    primaryContainer = Color(theme.accentContainer),
    onPrimaryContainer = Color(theme.onAccentContainer),

    // 次色：与主色同源，用于"="与运算符——同一族颜色，靠容器深浅区分
    secondary = Color(theme.accent),
    onSecondary = Color(theme.onAccent),
    secondaryContainer = Color(theme.accentContainer),
    onSecondaryContainer = Color(theme.onAccentContainer),

    // 第三色：滚动条等辅助指示
    tertiary = Color(theme.onAccentContainer),
    onTertiary = Color(theme.accentContainer),
    tertiaryContainer = Color(theme.accentContainer),
    onTertiaryContainer = Color(theme.onAccentContainer),

    // 纯黑背景：OLED 手表省电，同时给键盘留出足够对比
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB9B9B9),

    // 容器层级：数字键 → 运算符键 → 卡片，逐级抬升
    surfaceContainerLowest = Color(0xFF060606),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF303030),

    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF333333),

    // error 一族保持独立：`C`（清空）用它表达"不可逆操作"，
    // 不该跟着主题变——那会让"清空"看起来只是个普通彩色键
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xCC000000),
)
