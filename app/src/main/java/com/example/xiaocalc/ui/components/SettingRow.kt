package com.example.xiaocalc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp

@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onToggle,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
fun DensityScale(factor: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density * factor,
            fontScale = density.fontScale,
        ),
        content = content,
    )
}

@Composable
fun ChoiceRow(
    title: String,
    description: String,
    swatch: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(20f)))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(
                start = scaled(18f),
                end = scaled(18f),
                top = scaled(8f),
                bottom = scaled(8f),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(scaled(2f)))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(scaled(24f))
                .clip(CircleShape)
                .background(swatch),
        )
    }
}

private const val SWITCH_DENSITY_SCALE = 0.68f

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = scaledSp(19f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,

    description: String = "",
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaled(18f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = scaledSp(21f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        DragAdjustableSlider(
            value = value,
            steps = steps,
            valueRange = valueRange,
            onValueChange = onValueChange,
        )
        if (description.isNotEmpty()) {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = scaled(18f)),
            )
        }
    }
}

@Composable
private fun DragAdjustableSlider(
    value: Float,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val haptics = rememberKeyHaptics(true)
    var widthPx by remember { mutableStateOf(0f) }
    val current by rememberUpdatedState(value)
    val density = LocalDensity.current
    val padPx = with(density) { SLIDER_BUTTON_DP.dp.toPx() }

    val span = valueRange.endInclusive - valueRange.start
    val stepCount = steps + 1
    val stepSize = if (stepCount > 0) span / stepCount else span

    fun snap(x: Float): Float? {
        if (widthPx <= 2 * padPx || stepSize <= 0f) return null
        val ratio = ((x - padPx) / (widthPx - 2 * padPx)).coerceIn(0f, 1f)
        val index = (ratio * stepCount).roundToInt().coerceIn(0, stepCount)
        return valueRange.start + index * stepSize
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    snap(change.position.x)?.let {
                        if (it != current) {
                            haptics(false)
                            onValueChange(it)
                        }
                    }
                }
            },
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            valueRange = valueRange,
        )
    }
}

private const val SLIDER_BUTTON_DP = 40f
