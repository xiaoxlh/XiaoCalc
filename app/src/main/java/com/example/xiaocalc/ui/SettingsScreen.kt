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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.example.xiaocalc.BuildConfig
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.ui.components.ChoiceRow
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.PageTitle
import com.example.xiaocalc.ui.components.RotaryScrollHandler
import com.example.xiaocalc.ui.components.SectionLabel
import com.example.xiaocalc.ui.components.SettingRow
import com.example.xiaocalc.ui.components.SliderRow
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchScrollIndicator
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import kotlin.math.roundToInt

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
                    .onSizeChanged { viewport = it }
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageTitle("设置")
                Spacer(Modifier.height(scaled(12f)))
                SectionLabel("计算规则", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "保留平方根",
                    description = "不再将平方根以近似值计入结果",
                    checked = settings.keepRoot,
                    onToggle = { value -> controller.updateSettings { it.copy(keepRoot = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "保留常数符号",
                    description = "不再将常数符号以近似值计入结果",
                    checked = settings.keepSymbols,
                    onToggle = { value -> controller.updateSettings { it.copy(keepSymbols = value) } },
                )
                Spacer(Modifier.height(scaled(14f)))
                SectionLabel("显示与反馈", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                SliderRow(
                    title = "小数位数",
                    valueLabel = "${settings.decimals}",
                    value = settings.decimals.toFloat(),
                    steps = CalcSettings.FULL - 1,
                    valueRange = 0f..CalcSettings.FULL.toFloat(),
                    onValueChange = { raw ->
                        val next = raw.roundToInt().coerceIn(0, CalcSettings.FULL)
                        if (next != settings.decimals) {
                            haptics(false)
                            controller.updateSettings { it.copy(decimals = next) }
                        }
                    },
                    description = if (settings.decimals >= CalcSettings.FULL) "16 位有效数字" else "",

                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "背景模糊",
                    description = "部分页面背景高级效果",
                    checked = settings.blurEnabled,
                    onToggle = { value -> controller.updateSettings { it.copy(blurEnabled = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "科学计数法",
                    description = "以 10ⁿ 的形式显示 ≥10000 的结果",
                    checked = settings.scientific,
                    onToggle = { value -> controller.updateSettings { it.copy(scientific = value) } },
                )
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "结果分节",
                    description = "在结果中每 3 位数字插入一个逗号",
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
                Spacer(Modifier.height(scaled(14f)))
                SectionLabel("外观", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                ChoiceRow(
                    title = "主题色",
                    description = "${settings.themeColor.label} · 点击切换",
                    swatch = Color(settings.themeColor.accent),
                    onClick = {
                        haptics(false)
                        controller.updateSettings { it.copy(themeColor = it.themeColor.next()) }
                    },
                )
                Spacer(Modifier.height(scaled(14f)))
                SectionLabel("网络", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(6f)))
                SettingRow(
                    title = "联网获取汇率",
                    description = if (settings.allowNetwork) {
                        "汇率每日自动获取"
                    } else {
                        "使用缓存汇率（如有）"
                    },
                    checked = settings.allowNetwork,
                    onToggle = { value ->
                        controller.updateSettings { it.copy(allowNetwork = value) }
                    },
                )
                Spacer(Modifier.height(scaled(16f)))
                SectionLabel("关于", Modifier.fillMaxWidth())
                Spacer(Modifier.height(scaled(8f)))
                AboutLine("XiaoCalc 小计算  ${BuildConfig.VERSION_NAME}")
                Spacer(Modifier.height(scaled(4f)))
                AboutLine("默认离线 · 不收集数据")
                Spacer(Modifier.height(scaled(4f)))
                AboutLine("仅货币汇率需联网，且默认关闭")
                Spacer(Modifier.height(scaled(4f)))
                AboutLine("© Xiaoxiao 2026")
                Spacer(Modifier.height(scaled(14f)))
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

@Composable
private fun DiagnosticsEntry(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = "诊断信息",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 32))
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
