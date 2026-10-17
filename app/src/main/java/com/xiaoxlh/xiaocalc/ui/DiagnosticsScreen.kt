package com.xiaoxlh.xiaocalc.ui

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
import com.xiaoxlh.xiaocalc.BuildConfig
import com.xiaoxlh.xiaocalc.adaptive.LocalWatchMetrics
import com.xiaoxlh.xiaocalc.adaptive.scaled
import com.xiaoxlh.xiaocalc.adaptive.scaledSp
import com.xiaoxlh.xiaocalc.design.Motion
import com.xiaoxlh.xiaocalc.ui.components.ClockText
import com.xiaoxlh.xiaocalc.ui.components.PageTitle
import com.xiaoxlh.xiaocalc.ui.components.RotaryScrollHandler
import com.xiaoxlh.xiaocalc.ui.components.WatchHeader
import com.xiaoxlh.xiaocalc.ui.components.WatchIconButton
import com.xiaoxlh.xiaocalc.ui.components.WatchIcons
import com.xiaoxlh.xiaocalc.ui.components.WatchScrollIndicator
import com.xiaoxlh.xiaocalc.ui.components.WatchVectorIcons
import com.xiaoxlh.xiaocalc.ui.components.rememberKeyHaptics
import kotlinx.coroutines.delay

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
