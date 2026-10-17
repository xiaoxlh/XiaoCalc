package com.example.xiaocalc.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey

enum class ChipEmphasis {
    PRIMARY,

    SECONDARY,
}

@Composable
fun SelectChip(
    label: String,
    description: String,
    widthDu: Float,
    heightDu: Float,
    emphasis: ChipEmphasis,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,

    showChevron: Boolean = false,
) {
    CalcKeyButton(
        key = CalcKey.PageMain,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = { onClick() },
        modifier = modifier,
        glyphOverride = label,
        icon = icon,
        description = description,
        fontSizeOverride = emphasis.fontSizeFor(label),
        fontWeightOverride = FontWeight.Bold,
        shape = RoundedCornerShape(percent = 50),
        containerOverride = when (emphasis) {
            ChipEmphasis.PRIMARY -> MaterialTheme.colorScheme.surfaceContainerHighest
            ChipEmphasis.SECONDARY -> MaterialTheme.colorScheme.surfaceContainer
        },
        contentOverride = when (emphasis) {
            ChipEmphasis.PRIMARY -> MaterialTheme.colorScheme.onSurface
            ChipEmphasis.SECONDARY -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        trailingIcon = if (showChevron) WatchVectorIcons.ChevronDown else null,
    )
}

@Composable
private fun ChipEmphasis.fontSizeFor(label: String): TextUnit = when (this) {
    ChipEmphasis.PRIMARY -> scaledSp(
        when (label.length) {
            0, 1, 2 -> 21f
            3 -> 18f
            else -> 15f
        },
    )

    ChipEmphasis.SECONDARY -> scaledSp(if (label.length <= 3) 15f else 12f)
}
