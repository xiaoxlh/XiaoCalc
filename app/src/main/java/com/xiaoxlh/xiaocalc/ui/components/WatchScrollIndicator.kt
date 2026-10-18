package com.xiaoxlh.xiaocalc.ui.components

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
import com.xiaoxlh.xiaocalc.adaptive.LocalWatchMetrics
import com.xiaoxlh.xiaocalc.adaptive.scaled

@Composable
fun BoxScope.WatchScrollIndicator(
    scroll: ScrollState,
    viewportPx: Float,
    modifier: Modifier = Modifier,
) {
    val round = LocalWatchMetrics.current.round
    val pxPerDu = with(LocalDensity.current) { scaled(1f).toPx() }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.matchParentSize()) {
        if (scroll.maxValue <= 0) return@Canvas
        val progress = scroll.value / scroll.maxValue.toFloat()
        val thumbFraction = (viewportPx / (scroll.maxValue + viewportPx)).coerceIn(0.16f, 1f)
        val stroke = 9f * pxPerDu
        if (round) {
            val radius = (size.minDimension / 2f - 10f * pxPerDu).coerceAtLeast(stroke)
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
            val length = size.height / 4f
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

private const val ARC_START = -16f
private const val ARC_SWEEP = 32f
