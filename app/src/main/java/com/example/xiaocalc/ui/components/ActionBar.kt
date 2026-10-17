package com.example.xiaocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * 键面写的是**按下去会到哪一页**（在数字页上写 `fx`，在函数页上写 `123`），
 * 而不是"当前在哪一页"——因为键盘内容本身已经说明了当前页；
 * 若这里再重复一次，四格里就有两格在说同一件事，而"能去哪"反而无处可见。
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
    CalcKeyButton(
        key = if (target == KeypadPage.MAIN) CalcKey.PageMain else CalcKey.PageFunctions,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(false)
            onSelectPage(target)
        },
        glyphOverride = if (target == KeypadPage.MAIN) "123" else "fx",
        shape = RoundedCornerShape(percent = CHIP_CORNER_PERCENT),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentOverride = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 分页胶囊。
 *
 * 与 [CalcKeyButton] 的区别**刻意做在形状上**：这里用 50% 圆角的胶囊形，
 * 普通按键是 32% 圆角的矩形。只靠颜色区分不够——未选中的胶囊底色
 * (surfaceContainerHigh) 与函数键完全相同，光看颜色根本分不出哪个是"翻页"。
 */
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
