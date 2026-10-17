package com.xiaoxlh.xiaocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.xiaoxlh.xiaocalc.adaptive.scaledSp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.xiaoxlh.xiaocalc.design.Motion
import com.xiaoxlh.xiaocalc.design.NumTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.xiaoxlh.xiaocalc.adaptive.WatchLayout
import com.xiaoxlh.xiaocalc.adaptive.Zone
import com.xiaoxlh.xiaocalc.adaptive.scaled
import com.xiaoxlh.xiaocalc.calc.CalcKey
import com.xiaoxlh.xiaocalc.calc.KeypadPage
import com.xiaoxlh.xiaocalc.ui.components.WatchVectorIcons.Backspace

@Composable
fun ActionBar(
    zone: Zone,
    page: KeypadPage,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    onSelectPage: (KeypadPage) -> Unit,
    modifier: Modifier = Modifier,

    onTools: (() -> Unit)? = null,

    toolsOpen: Boolean = false,
) {
    val items = WatchLayout.ACTION_ITEMS
    val gap = scaled(WatchLayout.KEY_GAP)
    val itemWidth = (zone.maxWidth - (items - 1) * WatchLayout.KEY_GAP) / items
    val toolsHaptics = rememberKeyHaptics(hapticsEnabled)

    Box(
        modifier = modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcKeyButton(
                key = CalcKey.Clear,
                widthDu = itemWidth,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = onKey,
                glyphOverride = "C",
                containerOverride = MaterialTheme.colorScheme.errorContainer,
                contentOverride = MaterialTheme.colorScheme.onErrorContainer,
                fontWeightOverride = FontWeight.Bold,
            )
            CalcKeyButton(
                key = CalcKey.Backspace,
                widthDu = itemWidth,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = onKey,
                icon = Backspace,
            )
            if (onTools == null) {
                PageChip(
                    label = "123",
                    selected = page == KeypadPage.MAIN,
                    widthDu = itemWidth,
                    heightDu = zone.height,
                    hapticsEnabled = hapticsEnabled,
                    onClick = { onSelectPage(KeypadPage.MAIN) },
                )
                PageChip(
                    label = "fx",
                    selected = page == KeypadPage.FUNCTIONS,
                    widthDu = itemWidth,
                    heightDu = zone.height,
                    hapticsEnabled = hapticsEnabled,
                    onClick = { onSelectPage(KeypadPage.FUNCTIONS) },
                )
            } else {
                PageToggleChip(
                    page = page,
                    widthDu = itemWidth,
                    heightDu = zone.height,
                    hapticsEnabled = hapticsEnabled,
                    onSelectPage = onSelectPage,
                )
                CalcKeyButton(
                    key = CalcKey.PageMain,
                    widthDu = itemWidth,
                    heightDu = zone.height,
                    hapticsEnabled = hapticsEnabled,
                    onKey = {
                        toolsHaptics(false)
                        onTools()
                    },
                    icon = WatchVectorIcons.Apps,
                    description = if (toolsOpen) "收起工具" else "工具",
                    shape = RoundedCornerShape(percent = CHIP_CORNER_PERCENT),
                    containerOverride = if (toolsOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentOverride = if (toolsOpen) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun PageToggleChip(
    page: KeypadPage,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onSelectPage: (KeypadPage) -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val target = if (page == KeypadPage.MAIN) KeypadPage.FUNCTIONS else KeypadPage.MAIN
    val currentLabel = if (page == KeypadPage.MAIN) "123" else "fx"
    val otherLabel = if (target == KeypadPage.MAIN) "123" else "fx"

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = Motion.press(),
        label = "pageChipScale",
    )

    Box(
        modifier = Modifier
            .width(scaled(widthDu))
            .height(scaled(heightDu))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = CHIP_CORNER_PERCENT))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = "当前是$currentLabel 页，切换到$otherLabel",
                onClick = {
                    haptics(false)
                    onSelectPage(target)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = currentLabel,
            transitionSpec = {
                (
                    fadeIn(tween(Motion.DURATION_SHORT3, delayMillis = SWAP_STAGGER_MS)) +
                        scaleIn(
                            initialScale = 0.72f,
                            animationSpec = tween(Motion.DURATION_SHORT4, delayMillis = SWAP_STAGGER_MS),
                        )
                    ).togetherWith(
                    fadeOut(tween(Motion.DURATION_SHORT2)) +
                        scaleOut(targetScale = 0.72f, animationSpec = tween(Motion.DURATION_SHORT3)),
                )
            },
            label = "pageChipLabel",
        ) { label ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = scaledSp(16f),
                fontWeight = FontWeight.Bold,
                style = NumTextStyle,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = scaled(3f), bottom = scaled(3f))
                .clip(RoundedCornerShape(scaled(5f)))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = scaled(3f), vertical = scaled(0.5f)),
        ) {
            AnimatedContent(
                targetState = otherLabel,
                transitionSpec = {
                    (
                        fadeIn(tween(Motion.DURATION_SHORT3, delayMillis = SWAP_STAGGER_MS)) +
                            scaleIn(
                                initialScale = 1.35f,
                                animationSpec = tween(Motion.DURATION_SHORT4, delayMillis = SWAP_STAGGER_MS),
                            )
                        ).togetherWith(
                        fadeOut(tween(Motion.DURATION_SHORT2)) +
                            scaleOut(targetScale = 1.35f, animationSpec = tween(Motion.DURATION_SHORT3)),
                    )
                },
                label = "pageChipBadge",
            ) { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = scaledSp(10f),
                    fontWeight = FontWeight.Bold,
                    style = NumTextStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val SWAP_STAGGER_MS = 40
@Composable
private fun PageChip(
    label: String,
    selected: Boolean,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    CalcKeyButton(
        key = if (label == "123") CalcKey.PageMain else CalcKey.PageFunctions,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(false)
            onClick()
        },
        glyphOverride = label,
        shape = RoundedCornerShape(percent = CHIP_CORNER_PERCENT),
        fontWeightOverride = if (selected) FontWeight.Bold else FontWeight.Medium,
        containerOverride = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentOverride = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
