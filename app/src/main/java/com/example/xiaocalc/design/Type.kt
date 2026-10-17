package com.example.xiaocalc.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.xiaocalc.R

val XiaoCalcFontFamily = FontFamily(
    Font(R.font.misans_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(R.font.misans_medium, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(R.font.misans_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(R.font.misans_heavy, weight = FontWeight.Black, style = FontStyle.Normal),
)

const val MISANS_LINE_HEIGHT_RATIO = 1.326f

const val NUM_FEATURES = "\"tnum\" 1"

val NumTextStyle = TextStyle(fontFeatureSettings = NUM_FEATURES)

private fun TextStyle.misans() = copy(
    fontFamily = XiaoCalcFontFamily,
    lineHeight = TextUnit.Unspecified,
    letterSpacing = TextUnit.Unspecified,
)

val XiaoCalcTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.misans(),
        displayMedium = displayMedium.misans(),
        displaySmall = displaySmall.misans(),
        headlineLarge = headlineLarge.misans(),
        headlineMedium = headlineMedium.misans(),
        headlineSmall = headlineSmall.misans(),
        titleLarge = titleLarge.misans(),
        titleMedium = titleMedium.misans(),
        titleSmall = titleSmall.misans(),
        bodyLarge = bodyLarge.misans(),
        bodyMedium = bodyMedium.misans(),
        bodySmall = bodySmall.misans(),
        labelLarge = labelLarge.misans(),
        labelMedium = labelMedium.misans(),
        labelSmall = labelSmall.misans(),
    )
}

val ClockTextStyle = TextStyle(
    fontFamily = XiaoCalcFontFamily,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = NUM_FEATURES,
    fontSize = 14.sp,
)
