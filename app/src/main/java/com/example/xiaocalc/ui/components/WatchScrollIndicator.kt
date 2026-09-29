package com.example.xiaocalc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled

/**
 * 滚动指示器：圆形屏沿表盘右缘画成弧形，方形屏画成直条。
 *
 * 与旧实现的差别：画布覆盖**整个设计坐标系**（384×384），圆心即表盘圆心，
 * 半径直接取自内容区短边——旧实现把画布放在被 0.95 收缩过的内容盒里，
 * 却用未收缩的表盘半径算弧，导致滚动条浮在内容之外、与页面脱节。
 */
@Composable
fun BoxScope.WatchScrollIndicator(
    scroll: ScrollState,
    viewportPx: Float,
    modifier: Modifier = Modifier,
) {
    val round = LocalWatchMetrics.current.round
    val pxPerDu = with(LocalDensity.current) { scaled(1f).toPx() }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier.matchParentSize()) {
        if (scroll.maxValue <= 0) return@Canvas
        val progress = scroll.value / scroll.maxValue.toFloat()
        val thumbFraction = (viewportPx / (scroll.maxValue + viewportPx)).coerceIn(0.16f, 1f)
        val stroke = 6f * pxPerDu

        if (round) {
            val radius = (size.minDimension / 2f - 16f * pxPerDu).coerceAtLeast(stroke)
            val topLeft = Offset((size.width - radius * 2f) / 2f, (size.height - radius * 2f) / 2f)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(
                color = trackColor,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val thumbSweep = ARC_SWEEP * thumbFraction
            drawArc(
                color = thumbColor,
                startAngle = ARC_START + (ARC_SWEEP - thumbSweep) * progress,
                sweepAngle = thumbSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        } else {
            val length = size.height / 2f
            val x = size.width - 10f * pxPerDu
            val top = (size.height - length) / 2f
            drawLine(
                color = trackColor,
                start = Offset(x, top),
                end = Offset(x, top + length),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            val thumbLength = length * thumbFraction
            val thumbStart = top + (length - thumbLength) * progress
            drawLine(
                color = thumbColor,
                start = Offset(x, thumbStart),
                end = Offset(x, thumbStart + thumbLength),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val ARC_START = -32f
private const val ARC_SWEEP = 64f
