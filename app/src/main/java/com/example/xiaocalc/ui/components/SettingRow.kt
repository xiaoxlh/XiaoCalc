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

/**
 * 设置项卡片：左标题/说明 + 右开关。
 *
 * 改用**官方手表组件** [SwitchButton]，而不是把常规 MD3 开关塞进一个缩放过的密度里。
 *
 * 旧做法（`DensityScale(0.68f)` + 常规 `Switch`）是个不得已的 hack：
 * MD3 开关按 48dp 命中区设计，在 240dp 的表盘上占太多空间，只能整体缩小；
 * 而缩小后的命中区与字号都不再符合任何一套规范。
 *
 * [SwitchButton] 是为手表尺寸设计的，自带 label / secondaryLabel 插槽，
 * 并带 Expressive 的按压形变（`SurfaceTransformation`）——
 * 这些都不是"看起来差不多"，而是规范里就有的东西。
 */
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

/** 在局部把 dp 的物理换算整体缩小，使内嵌的 MD3 组件贴合手表尺度 */
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

/**
 * 设置项卡片：左标题/说明 + 右侧色块，点击**循环切换**取值。
 *
 * 为什么不做成下拉选择器：设置页的列表已经由表冠驱动滚动，
 * 再叠一个可滚动浮层会出现"表冠到底在滚哪个"的歧义。
 * 主题只有 8 个、且是低频设置，循环点击的代价可以接受，
 * 而右侧色块让"当前是什么、下一步会变成什么"一眼可见。
 */
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

/** 分组标题 */
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


/**
 * 设置项：左标题 + 右当前取值，下面一条可拖动的档位滑杆。
 *
 * 为什么不用官方的 `androidx.wear.compose.material3.Slider`：
 * 那个组件**只有两端的 ± 按钮，根本没有拖动**（源码里没有任何 `detectDragGestures`），
 * 中间的条只是画出来的进度。而它每次调值会触发 `SegmentFrequentTick` /
 * `GestureEnd` 两种触感，在只调一格时听起来像"嗡"地长震一下。
 *
 * 自己要的东西很明确：拖得动、一次只"嗒"一下，因此这里自己画。
 */
@Composable
fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** 滑杆下方的说明 */
    description: String = "",
) {
    val haptics = rememberKeyHaptics(true)
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
                // 只给标题一档 weight：再叠一个 Spacer(weight) 会把剩余空间对半分，
                // "小数位数"这种四个字的标题会被截成"小数…"（真机上实测到过）
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
        Spacer(Modifier.height(scaled(4f)))
        StepSlider(
            value = value,
            valueRange = valueRange,
            steps = steps,
            // 一次调一格只给一次短震：长震会让人以为触发了别的操作
            onValueChange = {
                haptics(false)
                onValueChange(it)
            },
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

/**
 * 可拖动的档位滑杆。
 *
 * 点一下就跳到位、按住拖动连续调整；只在**取整后的档位真的变了**时才回调，
 * 因此触感与取值一样是一格一次，不会因为手指抖一下就连震。
 */
@Composable
private fun StepSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { scaled(THUMB_DU).toPx() }
    val trackHeightPx = with(density) { scaled(TRACK_DU).toPx() }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.onPrimary
    var widthPx by remember { mutableStateOf(0f) }

    // 手势回调是挂起在 pointerInput 里的，直接读 value 会拿到首次合成的旧值
    val current by rememberUpdatedState(value)

    val span = valueRange.endInclusive - valueRange.start
    val stepCount = steps + 1
    val stepSize = if (stepCount > 0) span / stepCount else span
    val fraction = if (span > 0f) (value - valueRange.start) / span else 0f

    fun snap(x: Float): Float? {
        if (widthPx <= thumbPx || stepSize <= 0f) return null
        val usable = widthPx - thumbPx
        val ratio = ((x - thumbPx / 2f) / usable).coerceIn(0f, 1f)
        val index = (ratio * stepCount).roundToInt().coerceIn(0, stepCount)
        return valueRange.start + index * stepSize
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(scaled(CONTROL_DU))
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = current,
                    range = valueRange,
                    steps = steps,
                )
                setProgress { target ->
                    onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    snap(offset.x)?.let { if (it != current) onValueChange(it) }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    snap(change.position.x)?.let { if (it != current) onValueChange(it) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = trackHeightPx
            val radius = trackHeight / 2f
            val left = thumbPx / 2f
            val right = size.width - thumbPx / 2f
            val centerY = size.height / 2f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, centerY - radius),
                size = Size((right - left).coerceAtLeast(0f), trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            val filled = left + (right - left) * fraction.coerceIn(0f, 1f)
            if (filled > left) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left, centerY - radius),
                    size = Size(filled - left, trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            drawCircle(
                color = thumbColor,
                radius = thumbPx / 2f,
                center = Offset(filled.coerceIn(left, right), centerY),
            )
        }
    }
}

/** 控件高度（设计单位） */
private const val CONTROL_DU = 46f

/** 轨道高度 */
private const val TRACK_DU = 10f

/** 滑块直径 */
private const val THUMB_DU = 20f