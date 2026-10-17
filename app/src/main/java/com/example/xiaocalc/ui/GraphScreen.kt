package com.example.xiaocalc.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.xiaocalc.adaptive.Disc
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.Analysis
import com.example.xiaocalc.calc.AngleUnit
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.Dec
import com.example.xiaocalc.calc.FunctionAnalysis
import com.example.xiaocalc.calc.GraphFunction
import com.example.xiaocalc.calc.GraphState
import com.example.xiaocalc.calc.NotablePoint
import com.example.xiaocalc.calc.NumberFormatter
import com.example.xiaocalc.calc.Sample
import com.example.xiaocalc.design.curveColors
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchVectorIcons
import com.example.xiaocalc.ui.components.discClip
import com.example.xiaocalc.ui.components.overlayBlur
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val TRIG_ANY = listOf("sin", "cos", "tan")

// all sizes below are design units on the 384du design square
private const val SHEET_COLLAPSED_DU = 46f
private const val SHEET_EXPANDED_DU = 280f

private const val SHEET_BOTTOM_DU = 344f

private val SHEET_WIDTH_DU =
    Disc.chordInBand(SHEET_BOTTOM_DU - SHEET_EXPANDED_DU, SHEET_BOTTOM_DU) * 0.98f

private const val PLOT_TOP_DU = 52f
private const val PLOT_BOTTOM_DU = 250f

private const val POINT_TOP_DU = 252f

private const val SHEET_ANIM_MS = 240

@Composable
fun GraphScreen(
    state: GraphState,
    settings: CalcSettings,
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalWatchMetrics.current
    val layout = remember(metrics.layout.round) { WatchLayout.equation(metrics.layout.round) }
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val colors = curveColors(MaterialTheme.colorScheme.primary)
    val drawable = state.drawable

    val curves = state.functions.withIndex().filter { it.value.isDrawable }
    val curveColors = curves.map { colors[it.index % colors.size] }

    var sheetOpen by rememberSaveable { mutableStateOf(drawable.isEmpty()) }

    var focusRow by remember { mutableStateOf(-1) }

    val analyses = remember(curves.map { it.value.expression }, state.xMin, state.xMax, settings.angleUnit) {
        curves.map {
            FunctionAnalysis.analyze(it.value.expression, state.xMin, state.xMax, settings.angleUnit)
        }
    }
    val active = state.activeCurve
    val points = analyses.getOrNull(active)?.points.orEmpty()

    val selectedIndex = state.clampSelection(points.size)

    // auto-fit only while the user has not taken over the view
    val fittedY = FunctionAnalysis.viewRange(analyses.flatMap { it.samples })
    val exprKey = curves.map { it.value.expression }
    LaunchedEffect(exprKey) {
        if (state.autoFit && exprKey.isNotEmpty()) state.fitY(fittedY.first, fittedY.second)
    }

    RotaryHandler(AppPage.GRAPH) { pixels ->
        haptics(false)
        if (!sheetOpen) {
            if (pixels > 0f) state.selectNext(points.size) else state.selectPrevious(points.size)
        }
    }

    val sheetHeight by animateDpAsState(
        targetValue = scaled(if (sheetOpen) SHEET_EXPANDED_DU else SHEET_COLLAPSED_DU),
        animationSpec = tween(SHEET_ANIM_MS),
        label = "sheetHeight",
    )

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .discClip(metrics.round)
                .overlayBlur(active = sheetOpen, enabled = settings.blurEnabled),
        ) {
            PlotCanvas(
                analyses = analyses,
                colors = curveColors,
                active = active,
                selected = selectedIndex,
                xMin = state.xMin,
                xMax = state.xMax,
                yMin = state.yMin,
                yMax = state.yMax,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (size.width <= 0 || size.height <= 0) return@detectTransformGestures
                            val top = size.height * PLOT_TOP_DU / Disc.DESIGN
                            val height = size.height * (PLOT_BOTTOM_DU - PLOT_TOP_DU) / Disc.DESIGN
                            if (height <= 0f) return@detectTransformGestures
                            if (pan != Offset.Zero) {
                                state.panBy(pan.x / size.width, pan.y / height)
                            }
                            if (zoom != 1f) {
                                state.zoomAt(
                                    factor = zoom,
                                    xFraction = (centroid.x / size.width).coerceIn(0f, 1f),
                                    yFraction = ((centroid.y - top) / height).coerceIn(0f, 1f),
                                )
                            }
                        }
                    },
            )
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                            contentDescription = "返回计算器",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    center = { ClockText(time) },
                )
                Spacer(Modifier.height(scaled(POINT_TOP_DU - layout.header.bottom)))
                PointLine(
                    label = state.editing + 1,
                    expression = state.functions.getOrNull(state.editing)?.expression.orEmpty(),
                    point = points.getOrNull(selectedIndex),
                    index = selectedIndex,
                    count = points.size,
                    xMin = state.xMin,
                    xMax = state.xMax,
                    angleUnit = settings.angleUnit,
                    color = colors.getOrNull(state.editing) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            }
        }
        GraphSheet(
            state = state,
            colors = colors,
            height = sheetHeight,
            open = sheetOpen,
            focusRow = focusRow,
            onFocused = { focusRow = -1 },
            onAdd = {
                if (state.addFunction()) {
                    haptics(true)
                    focusRow = state.functions.lastIndex
                }
            },
            onReset = {
                state.resetView()
                val samples = curves.flatMap {
                    FunctionAnalysis.sample(
                        it.value.expression,
                        GraphState.DEFAULT_X_MIN,
                        GraphState.DEFAULT_X_MAX,
                        settings.angleUnit,
                    )
                }
                val range = FunctionAnalysis.viewRange(samples)
                if (samples.isNotEmpty()) state.fitY(range.first, range.second)
            },
            onOpenChange = { open ->
                haptics(open)
                sheetOpen = open
            },
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scaled(Disc.DESIGN - SHEET_BOTTOM_DU)),
        )
    }
}

@Composable
private fun PointLine(
    label: Int,
    expression: String,
    point: NotablePoint?,
    index: Int,
    count: Int,
    xMin: BigDecimal,
    xMax: BigDecimal,
    angleUnit: AngleUnit,
    color: Color,
) {
    val deg = if (TRIG_ANY.any { expression.contains(it) }) {
        if (angleUnit == AngleUnit.DEG) "deg " else "rad "
    } else {
        ""
    }
    val title = when {
        expression.isBlank() -> "还没有函数"
        point == null -> "范围内没有可标注的点"
        else -> {
            val x = point.exactX ?: graphNumber(point.x)
            "y$label ${point.kind.label}  ($x, ${graphNumber(point.y)})"
        }
    }
    val subtitle = if (expression.isBlank()) {
        "点下方横条添加，最多 ${GraphState.MAX_FUNCTIONS} 条"
    } else {
        "$deg[${format(xMin)}, ${format(xMax)}]" + if (count > 0) "  ·  ${index + 1}/$count" else ""
    }
    Column(
        modifier = Modifier.width(scaled(SHEET_WIDTH_DU + 40f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (point != null) color else MaterialTheme.colorScheme.onSurface,
            fontSize = scaledSp(16f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = scaledSp(12f),
            maxLines = 1,
        )
    }
}

private fun format(value: BigDecimal): String {
    val text = value.stripTrailingZeros().toPlainString()
    return if (text.length > 7) value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() else text
}

private val GRAPH_FORMAT = CalcSettings(decimals = 6)

private fun graphNumber(value: BigDecimal): String = NumberFormatter.format(value, GRAPH_FORMAT)

@Composable
private fun PlotCanvas(
    analyses: List<Analysis>,
    colors: List<Color>,
    active: Int,
    selected: Int,
    xMin: BigDecimal,
    xMax: BigDecimal,
    yMin: BigDecimal,
    yMax: BigDecimal,
    modifier: Modifier = Modifier,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.background
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelSize = scaledSp(10f)
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val xLow = xMin.toDouble()
        val yLow = yMin.toDouble()
        val xSpan = xMax.subtract(xMin).toDouble()
        val ySpan = yMax.subtract(yMin).toDouble()
        if (xSpan <= 0.0 || ySpan <= 0.0) return@Canvas
        val top = h * PLOT_TOP_DU / Disc.DESIGN
        val bottom = h * PLOT_BOTTOM_DU / Disc.DESIGN
        val plotH = bottom - top
        if (plotH <= 0f) return@Canvas
        fun px(x: Double) = ((x - xLow) / xSpan * w).toFloat()
        fun py(y: Double) = (bottom - (y - yLow) / ySpan * plotH).toFloat()
        val xStep = niceStep(xSpan / 5.0)
        val yStep = niceStep(ySpan / 5.0)
        clipRect(top = top, bottom = bottom) {
            var gx = floor(xLow / xStep) * xStep
            while (gx <= xLow + xSpan) {
                val x = px(gx)
                drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                gx += xStep
            }
            var gy = floor(yLow / yStep) * yStep
            while (gy <= yLow + ySpan) {
                val y = py(gy)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                gy += yStep
            }
            val axisY = when {
                yLow > 0.0 -> top
                yLow + ySpan < 0.0 -> bottom
                else -> py(0.0)
            }
            val axisX = when {
                xLow > 0.0 -> 0f
                xLow + xSpan < 0.0 -> w
                else -> px(0.0)
            }
            drawLine(axisColor, Offset(0f, axisY), Offset(w, axisY), strokeWidth = 2f)
            drawLine(axisColor, Offset(axisX, top), Offset(axisX, bottom), strokeWidth = 2f)
            analyses.forEachIndexed { index, analysis ->
                val color = colors.getOrNull(index) ?: colors.last()
                val path = Path()
                var penDown = false
                var lastY = 0f
                val jumpLimit = plotH * 1.5f
                for (sample in analysis.samples) {
                    val y = sample.y
                    if (y == null) {
                        penDown = false
                        continue
                    }
                    val vy = py(y.toDouble())
                    val vx = px(sample.x.toDouble())
                    if (!vy.isFinite()) {
                        penDown = false
                        continue
                    }
                    if (penDown && abs(vy - lastY) > jumpLimit) penDown = false
                    if (penDown) path.lineTo(vx, vy) else path.moveTo(vx, vy)
                    penDown = true
                    lastY = vy
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = if (index == active) 4f else 2.5f),
                )
            }
            val points = analyses.getOrNull(active)?.points.orEmpty()
            val pointColor = colors.getOrNull(active)
            if (pointColor != null) {
                points.forEachIndexed { index, point ->
                    val center = Offset(px(point.x.toDouble()), py(point.y.toDouble()))
                    if (!center.x.isFinite() || !center.y.isFinite()) return@forEachIndexed
                    drawCircle(ringColor, radius = 7f, center = center)
                    drawCircle(pointColor, radius = 4.5f, center = center)
                    if (index == selected) {
                        drawCircle(Color.White, radius = 10f, center = center, style = Stroke(width = 2f))
                    }
                }
            }
            val style = TextStyle(color = labelColor, fontSize = labelSize)
            var tx = floor(xLow / xStep) * xStep
            while (tx <= xLow + xSpan) {
                if (tx != 0.0) {
                    val layout = measurer.measure(tickLabel(tx, xStep), style)
                    val cx = px(tx) - layout.size.width / 2f
                    val cy = (axisY + 2f).coerceIn(top, bottom - layout.size.height)
                    if (cx >= 0f && cx + layout.size.width <= w) {
                        drawText(layout, topLeft = Offset(cx, cy))
                    }
                }
                tx += xStep
            }
            var ty = floor(yLow / yStep) * yStep
            while (ty <= yLow + ySpan) {
                if (ty != 0.0) {
                    val layout = measurer.measure(tickLabel(ty, yStep), style)
                    val cx = (axisX - layout.size.width - 3f).coerceIn(0f, w - layout.size.width)
                    val cy = py(ty) - layout.size.height / 2f
                    if (cy >= top && cy + layout.size.height <= bottom) {
                        drawText(layout, topLeft = Offset(cx, cy))
                    }
                }
                ty += yStep
            }
        }
    }
}

private fun tickLabel(value: Double, step: Double): String {
    if (!value.isFinite()) return ""
    val decimals = if (step >= 1.0) 0 else (-floor(log10(step))).toInt().coerceIn(0, 6)
    val rounded = BigDecimal.valueOf(value).setScale(decimals, java.math.RoundingMode.HALF_UP)

    if (rounded.signum() == 0 && value != 0.0) return String.format(java.util.Locale.US, "%.1e", value)
    val plain = rounded.stripTrailingZeros().toPlainString()
    return if (plain.length <= 8) plain else String.format(java.util.Locale.US, "%.0e", value)
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0 || !raw.isFinite()) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

@Composable
private fun GraphSheet(
    state: GraphState,
    colors: List<Color>,
    height: Dp,
    open: Boolean,
    focusRow: Int,
    onFocused: () -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
    onOpenChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(scaled(SHEET_WIDTH_DU))
            .height(height)
            .clip(RoundedCornerShape(scaled(26f)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(scaled(SHEET_EXPANDED_DU)),
        ) {
            SheetBar(
                state = state,
                colors = colors,
                open = open,
                onOpenChange = onOpenChange,
                hapticsEnabled = hapticsEnabled,
            )
            SheetTools(
                state = state,
                onAdd = onAdd,
                onReset = onReset,
                hapticsEnabled = hapticsEnabled,
            )
            FunctionList(state = state, colors = colors, focusRow = focusRow, onFocused = onFocused)
        }
    }
}

@Composable
private fun SheetBar(
    state: GraphState,
    colors: List<Color>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val active = state.editing.coerceIn(0, state.functions.lastIndex)
    val text = state.functions.getOrNull(active)?.expression.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaled(SHEET_COLLAPSED_DU))
            .pointerInput(open) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -DRAG_THRESHOLD_PX) onOpenChange(true)
                        if (total > DRAG_THRESHOLD_PX) onOpenChange(false)
                    },
                ) { _, dragAmount -> total += dragAmount }
            }
            .clickable {
                haptics(open)
                onOpenChange(!open)
            }
            .padding(horizontal = scaled(14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(scaled(10f))
                .clip(CircleShape)
                .background(
                    if (text.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        colors.getOrNull(active) ?: MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
        )
        Spacer(Modifier.width(scaled(9f)))
        Text(
            text = if (text.isBlank()) "添加函数" else "y${active + 1} = $text",
            color = if (text.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = scaledSp(16f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(scaled(6f)))
        Icon(
            imageVector = WatchVectorIcons.ChevronDown,
            contentDescription = if (open) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(scaled(22f))
                .rotate(if (open) 0f else 180f),
        )
    }
}

@Composable
private fun SheetTools(
    state: GraphState,
    onAdd: () -> Unit,
    onReset: () -> Unit,
    hapticsEnabled: Boolean,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val full = state.functions.size >= GraphState.MAX_FUNCTIONS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaled(SHEET_TOOLS_DU))
            .padding(horizontal = scaled(12f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scaled(8f)),
    ) {
        SheetTool(
            icon = WatchVectorIcons.Plus,
            description = if (full) "已达上限" else "添加函数",
            emphasis = true,
            enabled = !full,
            onClick = onAdd,
        )
        SheetTool(
            icon = WatchVectorIcons.Remove,
            description = "缩小",
            onClick = {
                haptics(false)
                state.zoom(-2)
            },
        )
        SheetTool(
            icon = WatchVectorIcons.Plus,
            description = "放大",
            onClick = {
                haptics(false)
                state.zoom(2)
            },
        )
        SheetTool(
            icon = WatchVectorIcons.Refresh,
            description = "重置视图",
            onClick = {
                haptics(false)
                onReset()
            },
        )
    }
}

@Composable
private fun SheetTool(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    emphasis: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(scaled(SHEET_TOOL_DU))
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceContainer
                    emphasis -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                emphasis -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(scaled(20f)),
        )
    }
}

@Composable
private fun FunctionList(
    state: GraphState,
    colors: List<Color>,
    focusRow: Int,
    onFocused: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = scaled(10f)),
    ) {
        state.functions.forEachIndexed { index, function ->
            FunctionRow(
                index = index,
                function = function,
                color = colors.getOrNull(index) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                selected = index == state.editing,
                autoFocus = index == focusRow,
                onFocused = onFocused,
                state = state,
            )
        }
        Spacer(Modifier.height(scaled(8f)))
    }
}

@Composable
private fun FunctionRow(
    index: Int,
    function: GraphFunction,
    color: Color,
    selected: Boolean,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    state: GraphState,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            onFocused()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaled(ROW_HEIGHT_DU))
            .clip(RoundedCornerShape(scaled(12f)))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            )
            .padding(start = scaled(10f), end = scaled(4f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(scaled(10f))
                .clip(CircleShape)
                .background(if (function.isDrawable) color else MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(scaled(8f)))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (function.expression.isEmpty()) {
                Text(
                    text = "y${index + 1} = 输入含 x 的式子",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = scaledSp(14f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = function.expression,
                onValueChange = { state.update(index, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) state.selectFunction(index) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = scaledSp(16f),
                    fontWeight = FontWeight.Medium,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Spacer(Modifier.width(scaled(2f)))
        Box(
            modifier = Modifier
                .size(scaled(28f))
                .clip(CircleShape)
                .clickable { state.removeFunction(index) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = WatchVectorIcons.Close,
                contentDescription = "删除 y${index + 1}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaled(15f)),
            )
        }
    }
}

private const val SHEET_TOOLS_DU = 42f

private const val SHEET_TOOL_DU = 34f

private const val ROW_HEIGHT_DU = 44f

private const val DRAG_THRESHOLD_PX = 40f
