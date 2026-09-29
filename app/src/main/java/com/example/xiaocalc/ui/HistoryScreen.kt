package com.example.xiaocalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.HistoryEntry
import com.example.xiaocalc.design.NumTextStyle
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.HintText
import com.example.xiaocalc.ui.components.PageTitle
import com.example.xiaocalc.ui.components.RotaryScrollHandler
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchScrollIndicator
import com.example.xiaocalc.ui.components.rememberKeyHaptics

/**
 * 历史记录。
 *
 * 旧实现的历史只存在内存里，退出即清空，且条目是拼好的字符串。
 * 这里改为结构化条目 + SharedPreferences 持久化，并且**点击条目可回填表达式**——
 * 这是历史记录真正的用处。
 */
@Composable
fun HistoryScreen(
    controller: CalculatorState,
    time: String,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWatchMetrics.current.layout
    val haptics = rememberKeyHaptics(controller.settings.haptics)
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val entries = controller.history

    Box(modifier.fillMaxSize()) {
        // 表冠 / 旋转表圈滚动列表
        RotaryScrollHandler(AppPage.HISTORY, scroll)
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
                trailing = {
                    if (entries.isNotEmpty()) {
                        WatchIconButton(
                            onClick = {
                                haptics(true)
                                controller.clearHistory()
                            },
                            icon = WatchIcons.Delete,
                            contentDescription = "清空历史",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            Spacer(Modifier.height(scaled(layout.content.top - layout.header.bottom)))

            Column(
                modifier = Modifier
                    .width(scaled(layout.content.maxWidth))
                    .height(scaled(layout.content.height))
                    // onSizeChanged 必须放在 verticalScroll **外侧**：
                    // 放在内侧量到的是"内容总高"，而滚动条滑块占比需要的是"可视高"，
                    // 用错会得到恒为满格的假滚动条。
                    .onSizeChanged { viewport = it }
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageTitle("历史记录")
                Spacer(Modifier.height(scaled(10f)))

                if (entries.isEmpty()) {
                    Spacer(Modifier.height(scaled(20f)))
                    HintText("暂无历史记录\n完成一次计算后会显示在这里", Modifier.fillMaxWidth())
                } else {
                    entries.forEach { entry ->
                        HistoryCard(
                            entry = entry,
                            onClick = {
                                haptics(false)
                                controller.reuse(entry)
                                onNavigate(AppPage.CALCULATOR)
                            },
                        )
                        Spacer(Modifier.height(scaled(8f)))
                    }
                }
                Spacer(Modifier.height(scaled(12f)))
            }
        }

        // 滚动指示：覆盖整个设计坐标系，弧线贴合表盘右缘
        WatchScrollIndicator(scroll = scroll, viewportPx = viewport.height.toFloat())
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(18f)))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = scaled(14f), vertical = scaled(9f)),
        verticalArrangement = Arrangement.spacedBy(scaled(2f)),
    ) {
        Text(
            text = entry.expression,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = scaledSp(16f),
            fontWeight = FontWeight.Normal,
            style = NumTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "= ${entry.result}",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = scaledSp(22f),
            fontWeight = FontWeight.Bold,
            style = NumTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
