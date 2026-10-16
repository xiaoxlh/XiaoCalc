package com.example.xiaocalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xiaocalc.adaptive.Disc
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.Analysis
import com.example.xiaocalc.calc.AngleUnit
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.Dec
import com.example.xiaocalc.calc.EquationKeypad
import com.example.xiaocalc.calc.FunctionAnalysis
import com.example.xiaocalc.calc.GraphState
import com.example.xiaocalc.calc.NotablePoint
import com.example.xiaocalc.calc.PointKind
import com.example.xiaocalc.calc.Sample
import com.example.xiaocalc.ui.components.CalcDisplay
import com.example.xiaocalc.ui.components.CalcKeyButton
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.Keypad
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchVectorIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** 输入态额外操作行里的符号 */
private val GRAPH_SYMBOLS = listOf("^", "(", ")", "√")

/** 受角度制影响的函数；只有它们出现时才需要标出单位 */
private val TRIG_ANY = listOf("sin", "cos", "tan")

/**
 * 函数图像页。
 *
 * 两种状态，因为 480×480 的圆屏放不下"键盘 + 大图"：
 *  - **输入态**：复用方程页的布局（额外操作行放符号，键盘放 `x` 与数字）
 *  - **图像态**：把显示区与键盘**合并成一整块画布**，不新增布局字段
 *
 * 关键点坐标通过表冠在关键点之间循环，而不是列一个滚动清单：
 * 圆屏上竖向列表与画布抢空间，而"有用的点"通常只有两三个。
 */
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
    var keypadPage by rememberSaveable { mutableStateOf(com.example.xiaocalc.calc.KeypadPage.MAIN) }

    val expressionScroll = rememberScrollState()

    // 分析只在表达式或视图范围变化时重算：采样 401 点并求根，不该挂在每次重组上
    val analysis = remember(state.input, state.xMin, state.xMax, settings.angleUnit) {
        if (state.input.isBlank()) {
            Analysis(emptyList(), emptyList())
        } else {
            FunctionAnalysis.analyze(
                state.input,
                state.xMin,
                state.xMax,
                settings.angleUnit,
            )
        }
    }
    val points = analysis.points
    // 在**读取时**把下标收敛到有效范围，而不是用副作用去改写状态。
    // 原先写成 remember(points.size) { state.selectFirstIfNeeded(...) }：
    // remember 不是副作用 API（lint 的 RememberReturnType 直接报了错），
    // 而且返回值是 Unit，等于用一个不保证执行的缓存去做状态变更。
    val selectedIndex = state.clampSelection(points.size)

    RotaryHandler(AppPage.GRAPH) { pixels ->
        if (state.graphing) {
            // 图像态：表冠在关键点之间循环
            haptics(false)
            if (pixels > 0f) state.selectNext(points.size) else state.selectPrevious(points.size)
        } else {
            haptics(false)
            keypadPage = if (pixels > 0f) {
                com.example.xiaocalc.calc.KeypadPage.FUNCTIONS
            } else {
                com.example.xiaocalc.calc.KeypadPage.MAIN
            }
        }
    }

    Box(modifier.fillMaxSize()) {
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
                            if (state.graphing) state.exitGraph() else onNavigate(AppPage.CALCULATOR)
                        },
                        icon = WatchIcons.Back,
                        contentDescription = if (state.graphing) "返回编辑" else "返回计算器",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                center = { ClockText(time) },
            )

            if (state.graphing) {
                GraphBody(
                    layout = layout,
                    state = state,
                    analysis = analysis,
                    selectedIndex = selectedIndex,
                    hapticsEnabled = hapticsEnabled,
                    angleUnit = settings.angleUnit,
                )
            } else {
                InputBody(
                    layout = layout,
                    state = state,
                    keypadPage = keypadPage,
                    onSelectPage = { keypadPage = it },
                    hapticsEnabled = hapticsEnabled,
                    expressionScroll = expressionScroll,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ 输入态

@Composable
private fun InputBody(
    layout: WatchLayout,
    state: GraphState,
    keypadPage: com.example.xiaocalc.calc.KeypadPage,
    onSelectPage: (com.example.xiaocalc.calc.KeypadPage) -> Unit,
    hapticsEnabled: Boolean,
    expressionScroll: androidx.compose.foundation.ScrollState,
) {
    val extra = layout.extraRow
    if (extra != null) {
        Spacer(Modifier.height(scaled(extra.top - layout.header.bottom)))
        val gap = WatchLayout.KEY_GAP
        val itemWidth = (extra.maxWidth - (GRAPH_SYMBOLS.size - 1) * gap) / GRAPH_SYMBOLS.size
        Row(
            modifier = Modifier.width(scaled(extra.maxWidth)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaled(gap)),
        ) {
            for (symbol in GRAPH_SYMBOLS) {
                SymbolKey(
                    label = symbol,
                    widthDu = itemWidth,
                    heightDu = extra.height,
                    hapticsEnabled = hapticsEnabled,
                    onClick = { state.insert(symbol) },
                )
            }
        }
        Spacer(Modifier.height(scaled(layout.display.top - extra.bottom)))
    } else {
        Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
    }

    CalcDisplay(
        expression = state.input,
        result = "",
        errorMessage = if (state.input.isBlank()) "输入含 x 的解析式" else null,
        zone = layout.display,
        expressionScroll = expressionScroll,
        resultScroll = expressionScroll,
    )

    Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))
    Keypad(
        page = keypadPage,
        rows = layout.keyRows,
        hapticsEnabled = hapticsEnabled,
        onKey = state::onKey,
        keysOverride = EquationKeypad,
    )

    Spacer(Modifier.height(scaled(layout.actionRow.top - layout.keypad.bottom)))
    InputActionBar(
        zone = layout.actionRow,
        hapticsEnabled = hapticsEnabled,
        onClear = state::clear,
        onBackspace = state::backspace,
        onPi = { state.insert("π") },
        onGraph = { state.enterGraph() },
    )
}

// ------------------------------------------------------------------ 图像态

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GraphBody(
    layout: WatchLayout,
    state: GraphState,
    analysis: Analysis,
    selectedIndex: Int,
    hapticsEnabled: Boolean,
    angleUnit: AngleUnit,
) {
    val points = analysis.points
    // 画布占用"顶栏之下 → 操作行之上"的整段，但要**先给点信息行留出高度**。
    // 初版把画布按 header.bottom..keypad.bottom 满铺，结果画布 + 点信息行
    // 把操作行顶到了 334du（应在 310du），底部的按钮被圆形屏幕裁掉了一半。
    val gap = 6f
    val pointLineHeight = 34f
    val top = layout.header.bottom + 2f
    val bottom = layout.actionRow.top - gap - pointLineHeight
    val available = bottom - top
    val side = minOf(available, Disc.chordInBand(top, bottom + pointLineHeight) * WatchLayout.BAND_SAFETY)
    val zone = Zone(top, side, side)

    Spacer(Modifier.height(scaled(top - layout.header.bottom)))
    GraphCanvas(
        analysis = analysis,
        xMin = state.xMin,
        xMax = state.xMax,
        selected = selectedIndex,
        zone = zone,
    )

    // 点信息行固定在操作行正上方，与画布之间留 [gap]
    Spacer(Modifier.height(scaled(layout.actionRow.top - pointLineHeight - zone.bottom)))
    SelectedPointLine(
        point = points.getOrNull(selectedIndex),
        xMin = state.xMin,
        xMax = state.xMax,
        total = points.size,
        angleUnit = angleUnit,
        expression = state.input,
    )

    Spacer(Modifier.height(scaled(gap)))
    GraphActionBar(
        zone = layout.actionRow,
        state = state,
        hapticsEnabled = hapticsEnabled,
    )
}

/**
 * 当前选中点的坐标。
 *
 * 放在画布下方而不是叠在图上：圆屏边缘的弦长最短，叠字要么被裁、
 * 要么压住曲线本身。
 */
@Composable
private fun SelectedPointLine(
    point: NotablePoint?,
    xMin: BigDecimal,
    xMax: BigDecimal,
    total: Int,
    angleUnit: AngleUnit,
    expression: String,
) {
    // 表达式含三角函数时标出当前角度制：sin(x) 在角度制下周期是 360，
    // 在 [-10,10] 里只画出一小段，不说明的话会让人以为图画错了
    val unitHint = if (TRIG_ANY.any { expression.contains(it) }) {
        if (angleUnit == AngleUnit.DEG) "deg " else "rad "
    } else {
        ""
    }
    val range = "$unitHint[${format(xMin)}, ${format(xMax)}]"
    val text = if (point == null) {
        "范围内没有可标注的点"
    } else {
        val x = point.exactX ?: point.xDisplay
        "${point.kind.label}  ($x, ${point.yDisplay})   ${total} 个"
    }
    Column(
        modifier = Modifier.width(scaled(280f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = scaledSp(17f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = range,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = scaledSp(13f),
            maxLines = 1,
        )
    }
}

private fun format(value: BigDecimal): String {
    val text = value.stripTrailingZeros().toPlainString()
    return if (text.length > 7) value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() else text
}

// ------------------------------------------------------------------ 画布

/**
 * 绘图区。
 *
 * 纵轴范围用**第 98 百分位**而不是最大最小值：`1÷x` 这类函数在极点附近会取到
 * 天文数字，用极值定范围会让整条曲线压成一条线。取百分位等于自动剔除极点附近那几个采样。
 */
@Composable
private fun GraphCanvas(
    analysis: Analysis,
    xMin: BigDecimal,
    xMax: BigDecimal,
    selected: Int,
    zone: Zone,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.secondary
    val selectedColor = MaterialTheme.colorScheme.secondary

    val range = remember(analysis) { yRangeOf(analysis.samples) }

    Canvas(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val xSpan = xMax.subtract(xMin).toDouble()
        if (xSpan <= 0.0) return@Canvas
        val yMin = range.first.toDouble()
        val yMax = range.second.toDouble()
        val ySpan = yMax - yMin
        if (ySpan <= 0.0) return@Canvas

        fun px(x: Double) = ((x - xMin.toDouble()) / xSpan * w).toFloat()
        fun py(y: Double) = (h - (y - yMin) / ySpan * h).toFloat()

        // 网格
        val step = niceStep(xSpan / 4.0)
        var gx = floor(xMin.toDouble() / step) * step
        while (gx <= xMax.toDouble()) {
            val x = px(gx)
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            gx += step
        }
        var gy = floor(yMin / step) * step
        while (gy <= yMax) {
            val y = py(gy)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            gy += step
        }

        // 坐标轴
        if (yMin <= 0.0 && yMax >= 0.0) {
            val y = py(0.0)
            drawLine(axisColor, Offset(0f, y), Offset(w, y), strokeWidth = 2f)
        }
        if (xMin.toDouble() <= 0.0 && xMax.toDouble() >= 0.0) {
            val x = px(0.0)
            drawLine(axisColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
        }

        // 曲线：遇到无定义点或跨屏跳变时断开，否则会画出一条横贯画面的假线段
        val path = Path()
        var penDown = false
        var lastY = 0f
        val jumpLimit = h * 1.5f
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
        drawPath(path, curveColor, style = Stroke(width = 3f))

        // 关键点
        analysis.points.forEachIndexed { index, point ->
            val cx = px(point.x.toDouble())
            val cy = py(point.y.toDouble())
            if (!cx.isFinite() || !cy.isFinite()) return@forEachIndexed
            val isSelected = index == selected
            drawCircle(
                color = if (isSelected) selectedColor else pointColor,
                radius = if (isSelected) 7f else 5f,
                center = Offset(cx, cy),
            )
            if (isSelected) {
                drawCircle(
                    color = selectedColor,
                    radius = 12f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

/**
 * 纵轴范围：取 |y| 的第 98 百分位作为半幅，并保证包含 0。
 *
 * 用最大最小值会在 `1÷x` 这类函数上失控——极点附近单个采样就能把范围拉到 1e30。
 *
 * **不要**再给半幅加"至少等于横轴跨度"的下限：那是我加错过的一条约束。
 * 它会让 `sin(x)` 在角度制下的 ±0.17 被硬撑到 ±20，整条曲线压成一条直线。
 * 当时写的理由是"否则 x² 会被压平"，但 x² 的纵轴本来就远大于横轴，根本用不到这条下限。
 */
private fun yRangeOf(samples: List<Sample>): Pair<BigDecimal, BigDecimal> {
    val magnitudes = samples.mapNotNull { it.y?.abs() }.sorted()
    if (magnitudes.isEmpty()) return BigDecimal("-1") to BigDecimal("1")

    val index = ((magnitudes.size - 1) * 0.98).toInt().coerceIn(0, magnitudes.size - 1)
    // 留 10% 余量，让极值点不至于正好贴在画布边缘
    var half = Dec.mul(magnitudes[index], PADDING)
    if (half.signum() == 0) half = BigDecimal.ONE
    return half.negate() to half
}

private val PADDING: BigDecimal = BigDecimal("1.1")

/** 取 1 / 2 / 5 × 10ⁿ 中最接近 raw 的步长 */
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

// ------------------------------------------------------------------ 按钮

@Composable
private fun InputActionBar(
    zone: Zone,
    hapticsEnabled: Boolean,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onPi: () -> Unit,
    onGraph: () -> Unit,
) {
    ActionRow(zone) { width ->
        ActionKey("C", width, zone.height, hapticsEnabled, onClear, errorTone = true)
        ActionKey(null, width, zone.height, hapticsEnabled, onBackspace, backspace = true)
        ActionKey("π", width, zone.height, hapticsEnabled, onPi)
        ActionKey(
            label = "绘图",
            widthDu = width,
            heightDu = zone.height,
            hapticsEnabled = hapticsEnabled,
            onClick = onGraph,
            accentTone = true,
        )
    }
}

@Composable
private fun GraphActionBar(zone: Zone, state: GraphState, hapticsEnabled: Boolean) {
    ActionRow(zone) { width ->
        ActionKey("编辑", width, zone.height, hapticsEnabled, { state.exitGraph() })
        ActionKey("缩小", width, zone.height, hapticsEnabled, { state.zoom(-2) })
        ActionKey("放大", width, zone.height, hapticsEnabled, { state.zoom(2) })
        ActionKey("重置", width, zone.height, hapticsEnabled, { state.resetView() }, accentTone = true)
    }
}

@Composable
private fun ActionRow(zone: Zone, content: @Composable (widthDu: Float) -> Unit) {
    val gap = WatchLayout.KEY_GAP
    val width = (zone.maxWidth - 3 * gap) / 4f
    Box(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(scaled(gap))) {
            content(width)
        }
    }
}

@Composable
private fun ActionKey(
    label: String?,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    backspace: Boolean = false,
    errorTone: Boolean = false,
    accentTone: Boolean = false,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val container = when {
        errorTone -> MaterialTheme.colorScheme.errorContainer
        accentTone -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when {
        errorTone -> MaterialTheme.colorScheme.onErrorContainer
        accentTone -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    CalcKeyButton(
        key = CalcKey.PageMain,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(accentTone)
            onClick()
        },
        glyphOverride = if (backspace) null else label,
        icon = if (backspace) WatchVectorIcons.Backspace else null,
        // 短标签用大字，中文双字用中等字号
        fontSizeOverride = scaledSp(if ((label?.length ?: 1) <= 1) 22f else 17f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = container,
        contentOverride = content,
    )
}

/** 输入态额外操作行里的符号键 */
@Composable
private fun SymbolKey(
    label: String,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    CalcKeyButton(
        key = CalcKey.PageMain,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(false)
            onClick()
        },
        glyphOverride = label,
        fontSizeOverride = scaledSp(20f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentOverride = MaterialTheme.colorScheme.onSurface,
    )
}
