package com.example.xiaocalc.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌色：沿用旧版强调色（蓝 #2C56FF / 红 #FF5454），
 * 但按 Material 3 的色彩角色（role）重新分配到暗色方案中，
 * 使"主操作=蓝、运算符/结果=红"的语义得以保留并获得统一层次。
 */
val BrandBlue = Color(0xFF2C56FF)
val BrandRed = Color(0xFFFF5454)

/** 运算符文字色：比填充红更亮，保证在深色按键上的可读性（对比度 ≈ 7.6:1） */
val OperatorRed = Color(0xFFFF6B6B)

val XiaoCalcDarkColorScheme = darkColorScheme(
    // 主色：品牌蓝 —— 欢迎页主按钮、开关选中、键盘"函数"键容器
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF17225E),
    onPrimaryContainer = Color(0xFFB9C6FF),

    // 次色：品牌红 —— "=" 键填充、运算符文字与容器
    secondary = BrandRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF481616),
    onSecondaryContainer = Color(0xFFFFB4B4),

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

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xCC000000),
)
