package com.example.xiaocalc.ui

import android.os.Build
import android.view.InputDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.xiaocalc.BuildConfig
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.PageTitle
import com.example.xiaocalc.ui.components.RotaryScrollHandler
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchScrollIndicator
import com.example.xiaocalc.ui.components.WatchVectorIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import kotlinx.coroutines.delay

/**
 * 诊断信息页。
 *
 * 存在的理由：这个应用要面对大量不同型号的手表，报 issue 的人没法描述
 * "屏幕是不是圆的""表冠能不能用"这类问题——而这些恰好是决定行为的关键。
 * 让用户**截一张图**就能把全部关键信息带给开发者。
 *
 * 由此推出两条设计约束：
 * 1. **弱入口**：普通用户不需要它，所以入口是设置页底部的一行小字，而不是一个醒目卡片；
 * 2. **尽量一屏放得下**：东西越多，用户越需要滚动+多次截图。行数刻意压在 6 行。
 *    同时仍按可滚动实现——小屏手表（如 200dp 级）放不下时不会截断内容。
 *
 * 圆屏适配不需要新代码：整页复用 [LocalWatchMetrics] 的 `content` 分区，
 * 与设置/历史页同构。
 */
@Composable
fun DiagnosticsScreen(
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWatchMetrics.current.layout
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var copied by remember { mutableStateOf(false) }
    val rows = rememberDeviceRows()

    // "已复制"是一次性反馈，自动回退，避免用户以为按钮卡在激活态
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Box(modifier.fillMaxSize()) {
        RotaryScrollHandler(AppPage.DIAGNOSTICS, scroll)

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
                            onNavigate(AppPage.SETTINGS)
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
                    .height(scaled(layout.content.height)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageTitle("诊断信息")
                Spacer(Modifier.height(scaled(6f)))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // onSizeChanged 在 verticalScroll 外侧才量到"可视高"（见 HistoryScreen）
                        .onSizeChanged { viewport = it }
                        .verticalScroll(scroll),
                ) {
                    rows.forEach { (label, value) -> DiagRow(label, value) }
                }

                Spacer(Modifier.height(scaled(6f)))
                CopyButton(
                    copied = copied,
                    onClick = {
                        haptics(true)
                        clipboard.setText(AnnotatedString(rows.toClipboardText()))
                        copied = true
                    },
                )
            }

            Spacer(Modifier.weight(1f))
        }

        WatchScrollIndicator(scroll = scroll, viewportPx = viewport.height.toFloat())
    }
}

private fun List<Pair<String, String>>.toClipboardText(): String =
    joinToString(separator = "\n") { (k, v) -> "$k: $v" } + "\n"

/** 诊断字段。刻意压在 6 行——一屏放得下，一张截图就能带走 */
@Composable
private fun rememberDeviceRows(): List<Pair<String, String>> {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val metrics = LocalWatchMetrics.current
    return remember(metrics, configuration) {
        val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
        val heightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()
        val dpi = (density.density * 160f).toInt()
        val shape = if (metrics.layout.round) "圆形" else "方形"
        listOf(
            "屏幕" to "${widthPx}×${heightPx}px · ${dpi}dpi · $shape",
            "缩放" to "%.3f".format(metrics.scale),
            "表冠" to if (hasRotaryEncoder()) "支持" else "不支持",
            "版本" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "系统" to "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }
}

/**
 * 是否存在旋转编码器输入设备。
 *
 * 这是整页最有价值的一行：本机实测表明，**有旋转编码器 ≠ 事件会派发给第三方应用**
 * （小米手表 OS 能识别表冠，但 Compose 收不到事件，最后只能在 Activity 层接）。
 * 所以这一行给的是"硬件有没有"，配合 README 的已知问题说明，能快速定位同类反馈。
 */
private fun hasRotaryEncoder(): Boolean =
    InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { device ->
            !device.isVirtual &&
                device.sources and InputDevice.SOURCE_ROTARY_ENCODER ==
                InputDevice.SOURCE_ROTARY_ENCODER
        } ?: false
    }

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = scaled(3f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = scaledSp(15f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.width(scaled(46f)),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = scaledSp(15f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 带图标的复制按钮；复制后就地变为"已复制"，不弹 Toast（手表上会遮挡且一闪而过） */
@Composable
private fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val container = if (copied) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (copied) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaled(46f))
            .clip(RoundedCornerShape(percent = 32))
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = "复制诊断信息",
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = WatchVectorIcons.Copy,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(scaled(19f)),
        )
        Spacer(Modifier.width(scaled(7f)))
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                fadeIn(Motion.enter(Motion.DURATION_SHORT3))
                    .togetherWith(fadeOut(Motion.exit(Motion.DURATION_SHORT2)))
            },
            label = "copyLabel",
        ) { done ->
            Text(
                text = if (done) "已复制" else "复制",
                color = content,
                fontSize = scaledSp(18f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
