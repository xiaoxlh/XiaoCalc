package com.example.xiaocalc.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.example.xiaocalc.calc.ThemeColor

/**
 * 品牌结构色：蓝 `#2C56FF`。
 *
 * 它**不随主题变化**。蓝在这里承担"当前状态"（页面胶囊、开关选中态），
 * 而强调色承担"运算与结果"。两色分工是刻意的——
 * 若把结构色也一起换掉，"哪个是导航、哪个是运算"就分不出来了。
 */
val BrandBlue = Color(0xFF2C56FF)

/**
 * 由主题生成配色方案。
 *
 * 只有 `secondary` 一族随主题变化；其余角色固定。
 * [ThemeColor.RED] 产出的方案与旧版**逐值相同**（升级后外观零变化）。
 */
fun colorSchemeFor(theme: ThemeColor): ColorScheme = darkColorScheme(
    // 主色：品牌蓝 —— 欢迎页主按钮、开关选中、键盘"函数"键容器
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF17225E),
    onPrimaryContainer = Color(0xFFB9C6FF),

    // 次色：强调色 —— "=" 键填充、运算符文字与容器
    secondary = Color(theme.accent),
    onSecondary = Color(theme.onAccent),
    secondaryContainer = Color(theme.accentContainer),
    onSecondaryContainer = Color(theme.onAccentContainer),

    // 第三色：用于滚动条等辅助指示
    tertiary = Color(0xFF7FD1FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF004C69),
    onTertiaryContainer = Color(0xFFC4E7FF),

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
