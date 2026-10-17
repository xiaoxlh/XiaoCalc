package com.xiaoxlh.xiaocalc.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.xiaoxlh.xiaocalc.calc.ThemeColor

const val MAX_FONT_SCALE = 1.15f

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

    val scheme = remember(themeColor) { colorSchemeFor(themeColor) }
    CompositionLocalProvider(LocalDensity provides clamped) {
        MaterialTheme(
            colorScheme = scheme,
            typography = XiaoCalcTypography,
        ) {
            WearTheme(themeColor, content)
        }
    }
}
