package com.example.xiaocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.example.xiaocalc.BuildConfig
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.AngleUnit
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.PageTitle
import com.example.xiaocalc.ui.components.RotaryScrollHandler
import com.example.xiaocalc.ui.components.SectionLabel
import com.example.xiaocalc.ui.components.SettingRow
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchScrollIndicator
import com.example.xiaocalc.ui.components.rememberKeyHaptics

/**
 * 设置页。
 *
 * 前三项沿用旧版语义与存储 key（升级不丢配置），并新增角度单位、千分位、触感三项。
 * 每一项都直接映射到 [CalcSettings] 的一个字段，不再有"设置页用下标对应控制器数组"的隐式耦合。
 */
@Composable
fun SettingsScreen(
    controller: CalculatorState,
    time: String,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWatchMetrics.current.layout
    val haptics = rememberKeyHaptics(controller.settings.haptics)
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val settings = controller.settings

    Box(modifier.fillMaxSize()) {
        // 表冠 / 旋转表圈滚动列表
        RotaryScrollHandler(AppPage.SETTINGS, scroll)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(scaled(layout.header.top)))
            WatchHeader(
                zone = layout.header,
                leading = {
                    WatchIconButton(
                        onClick = {
                            haptics(false)
                            onNavigate(AppPage.CALCULATOR)
                        },
                        icon = WatchIcons.Back,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                center = { ClockText(time) },
            )

            Spacer(Modifier.height(scaled(layout.content.top - layout.header.bottom)))

            Column(
                modifier = Modifier
                    .width(scaled(layout.content.maxWidth))
                    .height(scaled(layout.content.height))
                    // onSizeChanged 在 verticalScroll 外侧才量到"可视高"（见 HistoryScreen 的说明）
                    .onSizeChanged { viewport = it }
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageTitle("设置")
                Spacer(Modifier.height(scaled(12f)))

                SectionLabel("计算规则", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "保留根号",
                    description = "根号不换算成小数",
                    checked = settings.keepRoot,
                    onToggle = { value -> controller.updateSettings { it.copy(keepRoot = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "保留特殊符号",
                    description = "保留 π、e 等符号",
                    checked = settings.keepSymbols,
                    onToggle = { value -> controller.updateSettings { it.copy(keepSymbols = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "弧度制",
                    description = "三角函数按弧度计算",
                    checked = settings.angleUnit == AngleUnit.RAD,
                    onToggle = { value ->
                        controller.updateSettings {
                            it.copy(angleUnit = if (value) AngleUnit.RAD else AngleUnit.DEG)
                        }
                    },
                )

                Spacer(Modifier.height(scaled(14f)))
                SectionLabel("显示与反馈", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "科学计数法",
                    description = "≥10000 用 ×10ⁿ",
                    checked = settings.scientific,
                    onToggle = { value -> controller.updateSettings { it.copy(scientific = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "千分位分隔",
                    description = "结果加千分位逗号",
                    checked = settings.groupDigits,
                    onToggle = { value -> controller.updateSettings { it.copy(groupDigits = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "按键触感",
                    description = "按键时轻微振动",
                    checked = settings.haptics,
                    onToggle = { value -> controller.updateSettings { it.copy(haptics = value) } },
                )

                Spacer(Modifier.height(scaled(16f)))
                SectionLabel("关于", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(8f)))
                AboutLine("XiaoCalc 小计算  ${BuildConfig.VERSION_NAME}")
                Spacer(Modifier.height(scaled(4f)))
                AboutLine("离线计算器 · 不联网、不收集数据")
                Spacer(Modifier.height(scaled(4f)))
                AboutLine("© Xiaoxiao 2026")
                Spacer(Modifier.height(scaled(14f)))
                // 弱入口：普通用户不需要诊断信息，所以它是底部一行小字而非醒目卡片；
                // 但需要它的人（报 issue）一眼能找到——比"长按版本号"这类隐藏手势友好。
                DiagnosticsEntry(
                    onClick = {
                        haptics(false)
                        onNavigate(AppPage.DIAGNOSTICS)
                    },
                )
                Spacer(Modifier.height(scaled(16f)))
            }
        }

        WatchScrollIndicator(scroll = scroll, viewportPx = viewport.height.toFloat())
    }
}

@Composable
private fun AboutLine(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = scaled(4f)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = scaledSp(15f),
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Start,
    )
}

/** 设置页底部的诊断入口：弱化成"一行字"，但仍是带涟漪与触感的真实点击目标 */
@Composable
private fun DiagnosticsEntry(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = "诊断信息",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 32))
            // 顺序关键：先 clickable 再 padding，整行才是热区（否则只有文字可点）
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = "诊断信息",
                onClick = onClick,
            )
            .padding(vertical = scaled(8f), horizontal = scaled(4f)),
        color = MaterialTheme.colorScheme.primary,
        fontSize = scaledSp(15f),
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Start,
    )
}
