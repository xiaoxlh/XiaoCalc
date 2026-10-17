package com.example.xiaocalc.calc

import androidx.compose.runtime.Immutable

@Immutable
data class CalcSettings(
    val keepRoot: Boolean = false,

    val scientific: Boolean = false,

    val keepSymbols: Boolean = false,

    val angleUnit: AngleUnit = AngleUnit.DEG,

    val groupDigits: Boolean = true,

    val haptics: Boolean = true,

    val themeColor: ThemeColor = ThemeColor.RED,

    val allowNetwork: Boolean = false,

    val decimals: Int = FULL,

    val blurEnabled: Boolean = true,
) {
    companion object {
        const val FULL = 16
        const val KEY_ROOT = "root"
        const val KEY_SCIENTIFIC = "scientific"
        const val KEY_SYMBOLS = "symbols"
        const val KEY_ANGLE = "angle_unit"
        const val KEY_GROUP = "group_digits"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME = "theme_color"
        const val KEY_NETWORK = "allow_network"
        const val KEY_DECIMALS = "decimals"
        const val KEY_BLUR = "blur_enabled"
    }
}
