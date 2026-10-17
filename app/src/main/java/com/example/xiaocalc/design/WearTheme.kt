package com.example.xiaocalc.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import com.example.xiaocalc.calc.ThemeColor

@Composable
internal fun WearTheme(themeColor: ThemeColor, content: @Composable () -> Unit) {
    val scheme = remember(themeColor) { wearColorSchemeFor(themeColor) }
    MaterialTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

private fun wearColorSchemeFor(theme: ThemeColor): ColorScheme = ColorScheme().copy(
    primary = Color(theme.accent),
    onPrimary = Color(theme.onAccent),
    primaryContainer = Color(theme.accentContainer),
    onPrimaryContainer = Color(theme.onAccentContainer),
    primaryDim = Color(theme.accentContainer),

    secondary = Color(theme.accent),
    onSecondary = Color(theme.onAccent),
    secondaryContainer = Color(theme.accentContainer),
    onSecondaryContainer = Color(theme.onAccentContainer),
    secondaryDim = Color(theme.accentContainer),

    tertiary = Color(theme.onAccentContainer),
    onTertiary = Color(theme.accentContainer),
    tertiaryContainer = Color(theme.accentContainer),
    onTertiaryContainer = Color(0xFFC4E7FF),
    tertiaryDim = Color(0xFF004C69),

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
