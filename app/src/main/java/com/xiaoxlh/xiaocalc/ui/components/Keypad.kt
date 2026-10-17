package com.xiaoxlh.xiaocalc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.xiaoxlh.xiaocalc.adaptive.KeyRow
import com.xiaoxlh.xiaocalc.adaptive.WatchLayout
import com.xiaoxlh.xiaocalc.adaptive.scaled
import com.xiaoxlh.xiaocalc.adaptive.scaledSp
import com.xiaoxlh.xiaocalc.calc.AngleUnit
import com.xiaoxlh.xiaocalc.calc.CalcKey
import com.xiaoxlh.xiaocalc.calc.KeyKind
import com.xiaoxlh.xiaocalc.calc.KeypadPage
import com.xiaoxlh.xiaocalc.calc.keypadFor
import com.xiaoxlh.xiaocalc.design.Motion
import com.xiaoxlh.xiaocalc.design.NumTextStyle

@Composable
fun Keypad(
    page: KeypadPage,
    rows: List<KeyRow>,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,

    angleUnit: AngleUnit = AngleUnit.DEG,

    glyphOverrides: Map<CalcKey, String> = emptyMap(),

    iconOverrides: Map<CalcKey, ImageVector> = emptyMap(),

    keysOverride: List<CalcKey>? = null,
) {
    val gap = scaled(WatchLayout.KEY_GAP)
    val totalHeight = scaled(
        rows.sumOf { it.height.toDouble() }.toFloat() +
            (rows.size - 1).coerceAtLeast(0) * WatchLayout.KEY_GAP,
    )
    val widestRow = scaled(rows.maxOfOrNull { it.maxWidth } ?: 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val direction = if (targetState == KeypadPage.FUNCTIONS) 1 else -1
                (
                    slideInHorizontally(Motion.enter(Motion.DURATION_MEDIUM2)) { it * direction } +
                        fadeIn(Motion.enter(Motion.DURATION_SHORT4))
                    ).togetherWith(
                    slideOutHorizontally(Motion.exit(Motion.DURATION_SHORT4)) { -it * direction } +
                        fadeOut(Motion.exit(Motion.DURATION_SHORT3)),
                )
            },
            label = "keypadPage",
        ) { target ->
            val keys = keysOverride ?: keypadFor(target)
            Column(
                modifier = Modifier.width(widestRow),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.width(scaled(row.maxWidth)),
                        horizontalArrangement = Arrangement.spacedBy(
                            gap,
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        keys.drop(rowIndex * row.columns).take(row.columns).forEach { key ->
                            CalcKeyButton(
                                key = key,
                                widthDu = row.keyWidth,
                                heightDu = row.height,
                                hapticsEnabled = hapticsEnabled,
                                onKey = onKey,
                                glyphOverride = glyphOverrides[key] ?: key.dynamicGlyph(angleUnit),
                                icon = iconOverrides[key],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalcKeyButton(
    key: CalcKey,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    glyphOverride: String? = null,

    description: String? = null,

    icon: ImageVector? = null,
    containerOverride: Color? = null,
    contentOverride: Color? = null,
    fontSizeOverride: TextUnit? = null,

    shape: Shape = RoundedCornerShape(percent = KEY_CORNER_PERCENT),
    fontWeightOverride: FontWeight? = null,

    trailingIcon: ImageVector? = null,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = Motion.press(),
        label = "keyScale",
    )

    val container = containerOverride ?: keyContainerColor(key)
    val content = contentOverride ?: keyContentColor(key)
    val animatedContainer by animateColorAsState(
        targetValue = if (pressed) lerp(container, Color.White, 0.18f) else container,
        animationSpec = Motion.standard(Motion.DURATION_SHORT2),
        label = "keyContainer",
    )

    Box(
        modifier = modifier
            .width(scaled(widthDu))
            .height(scaled(heightDu))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(animatedContainer)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = description ?: key.label,
                onClick = {
                    haptics(key.kind == KeyKind.EQUALS)
                    onKey(key)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = description ?: key.label,
                tint = content,
                modifier = Modifier.size(scaled(minOf(widthDu, heightDu) * 0.46f)),
            )
            return@Box
        }
        val text = glyphOverride ?: key.label
        val fontSize = fontSizeOverride ?: scaledSp(
            labelFontDu(key, minOf(widthDu, heightDu)),
        )
        val weight = fontWeightOverride
            ?: if (key.kind == KeyKind.EQUALS) FontWeight.Bold else FontWeight.Medium
        if (trailingIcon == null) {
            Text(
                text = text,
                color = content,
                fontSize = fontSize,
                fontWeight = weight,
                style = NumTextStyle,
                maxLines = 1,
            )
            return@Box
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = content,
                fontSize = fontSize,
                fontWeight = weight,
                style = NumTextStyle,
                maxLines = 1,
            )
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .padding(start = scaled(2f))
                    .size(scaled(heightDu * 0.5f)),
            )
        }
    }
}

private fun labelFontDu(key: CalcKey, shortestDu: Float): Float = when (key.label.length) {
    1 -> shortestDu * 0.62f
    2 -> shortestDu * 0.46f
    else -> shortestDu * 0.34f
}

@Composable
internal fun keyContainerColor(key: CalcKey): Color = when (key.kind) {
    KeyKind.DIGIT -> MaterialTheme.colorScheme.surfaceContainerHighest

    KeyKind.OPERATOR -> MaterialTheme.colorScheme.secondaryContainer

    KeyKind.FUNCTION -> MaterialTheme.colorScheme.surfaceContainerHigh

    KeyKind.EQUALS -> MaterialTheme.colorScheme.secondary

    KeyKind.ACTION -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
internal fun keyContentColor(key: CalcKey): Color = when (key.kind) {
    KeyKind.DIGIT -> MaterialTheme.colorScheme.onSurface
    KeyKind.OPERATOR -> MaterialTheme.colorScheme.onSecondaryContainer
    KeyKind.FUNCTION -> MaterialTheme.colorScheme.onSurfaceVariant
    KeyKind.EQUALS -> MaterialTheme.colorScheme.onSecondary
    KeyKind.ACTION -> MaterialTheme.colorScheme.onSurfaceVariant
}

private const val KEY_CORNER_PERCENT = 32

internal val KEYPAD_ICON_OVERRIDES: Map<CalcKey, ImageVector> =
    mapOf(CalcKey.Equals to WatchVectorIcons.Swap)

internal const val CHIP_CORNER_PERCENT = 50

private fun CalcKey.dynamicGlyph(angleUnit: AngleUnit): String? = when (this) {
    CalcKey.AngleUnit -> if (angleUnit == AngleUnit.DEG) "deg" else "rad"
    else -> null
}
