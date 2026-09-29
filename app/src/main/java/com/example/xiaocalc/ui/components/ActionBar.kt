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
) {
    val items = WatchLayout.ACTION_ITEMS
    val gap = scaled(WatchLayout.KEY_GAP)
    val itemWidth = (zone.maxWidth - (items - 1) * WatchLayout.KEY_GAP) / items

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
        }
    }
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
