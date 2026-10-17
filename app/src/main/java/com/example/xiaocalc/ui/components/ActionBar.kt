package com.example.xiaocalc.ui.components

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
import com.example.xiaocalc.adaptive.scaledSp
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
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.design.NumTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.ui.components.WatchVectorIcons.Backspace

/**
 * 表盘底部的操作行：`C`、`⌫`、以及键盘分页 `123` / `fx`。
 *
 * 这一行放在圆屏最下方的"透镜"区域——那里弦长只有约 190du，
 * 放不下 4 个方键，但放 4 个矮键正好，而且宽度由该区间的弦长铺满。
 *
 * `C` / `⌫` 原本挤在顶栏里（顶栏的弦长只有约 105du，三个元素已经是极限）；
 * 把它们下移到这里之后，顶栏只剩居中的时钟与右侧溢出菜单，透气多了。
 */
@Composable
fun ActionBar(
    zone: Zone,
    page: KeypadPage,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    onSelectPage: (KeypadPage) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 工具面板的入口。非 null 时，后两格变成"分页切换 + 工具"。
     *
     * 计算器主页要用它；单位/货币页没有工具面板，仍用 `123` / `fx` 两个独立分页胶囊。
     * 四格是这条行能容纳的上限（该区间弦长约 190du），所以两个分页键只能合成一格。
     */
    onTools: (() -> Unit)? = null,
    /** 工具格是否正占据键盘区。为 true 时工具键点亮，表示"再按一下收起" */
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
                // "清空"是不可逆操作，按 M3 的 error 角色给足强调。
                // 与运算符的砖红(#6B1D1D)刻意区分：这里更亮，且文字接近白色。
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
                    key = CalcKey.PageMain, // 仅占位：颜色、图标、描述全被覆盖
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
                    // 展开时点亮：此时键盘被顶替，必须让"为什么键都不见了"有答案
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

/**
 * 合并后的分页开关。
 *
 * ## 键面画"当前页"，角标画"可切过去的那一页"
 *
 * 这一格同时承担两件事：告诉用户现在在哪里（123 / fx），以及提示按下去会怎样。
 * 只写一个就会丢信息——只写当前页，用户不知道它能按；只写目标页，
 * 用户又会把"fx"误读成"现在是函数页"。
 *
 * 因此：**主体用强调色写当前页**（强调色 = "这是你所在的位置"），
 * 右下角一个**中性色小角标**写另一页（中性色 = "顺带说一句，还能切过去"）。
 * 两种颜色分工，恰好也对应两句话的分量。
 */
/**
 * 合并后的分页开关（带动画）。
 *
 * ## 键面画"当前页"，角标画"可切过去的那一页"
 *
 * 这一格同时承担两件事：告诉用户现在在哪里（123 / fx），以及提示按下去会怎样。
 * 只写一个就会丢信息——只写当前页，用户不知道它能按；只写目标页，
 * 用户又会把"fx"误读成"现在是函数页"。
 *
 * ## 为什么手动搭而不是复用 [CalcKeyButton]
 *
 * 切换时**两个标签要对调**：大字缩下去变成角标，角标涨上来变成大字。
 * `CalcKeyButton` 的标签是静态 Text，做不了这件事。这里自己搭一层，
 * 用 [AnimatedContent] 给两边各自做"淡出+缩放到 0.75 / 淡入+从 0.75 放大"，
 * 方向相反、时长错开 40ms，读起来就是一次交换，而不是两个字一起闪。
 */
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
                // 进来的略微滞后，出去的先让位：看起来是"接替"而不是"叠化"
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

        // 角标：不接手势，点它等同于点整个按钮
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

/** 交换动画里两边的错峰：出去的先走，进来的稍后接上 */
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