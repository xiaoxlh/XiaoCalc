package com.example.xiaocalc.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

/** 受角度制影响的函数；只有它们出现时才需要标出单位 */
private val TRIG_ANY = listOf("sin", "cos", "tan")

/** 抽屉收起/展开的高度 */
private const val SHEET_COLLAPSED_DU = 46f
private const val SHEET_EXPANDED_DU = 280f

/** 抽屉底边距表盘顶端的距离；留 40du 让它像一张浮在表盘里的卡片 */
private const val SHEET_BOTTOM_DU = 344f

/**
 * 抽屉宽度：取它纵向区间内**最窄**处的弦长。
 *
 * 抽屉展开时顶到 64du，那里表盘已经很窄；按最窄处取宽度，
 * 收起与展开就是同一个宽度，动画过程中不会左右抽动。
 */
private val SHEET_WIDTH_DU =
    Disc.chordInBand(SHEET_BOTTOM_DU - SHEET_EXPANDED_DU, SHEET_BOTTOM_DU) * 0.98f

/** 绘图区（占满表盘宽度，因此必须配合 `discClip` 才不会被画到屏幕外） */
private const val PLOT_TOP_DU = 52f
private const val PLOT_BOTTOM_DU = 250f

/** 关键点信息行 */
private const val POINT_TOP_DU = 252f

private const val SHEET_ANIM_MS = 240

/**
 * 函数图像页。
 *
 * ## 为什么没有键盘
 *
 * 圆屏 480×480，键盘一摆，画布就只剩一条横带。画图真正需要的是"看"，
 * 而不是"敲"——所以这里不摆键盘，表达式放在底部抽屉里改，
 * 用系统输入法（与方程页同一套做法），抽屉收起时整块圆屏都是画布。
 *
 * ## 为什么是抽屉而不是独立编辑页
 *
 * 画图和改公式是一件事的两面：改完立刻想看图，看完立刻想改。
 * 拆成两个页面就变成"改 → 返回 → 看 → 再进"的来回翻页；
 * 抽屉在同一个屏上完成这件事，且收起的代价只有 46du。
 *
 * 表冠在抽屉收起时于当前曲线的关键点之间循环：圆屏上列清单和画布抢空间，
 * 而"有用的点"通常只有两三个。
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
    val colors = curveColors(MaterialTheme.colorScheme.primary)
    val drawable = state.drawable

    // 颜色按**行号**分配（第 3 行恒为第 3 色），而不是按"第几条能画的"：
    // 否则列表里留一个空行，下面的行号与曲线颜色就整体错开一位——
    // 界面上写着 y2，画出来却是第 1 色，没人能对上。
    // 代价是空行会占掉一个颜色，但空行本来就不画，看不出来。
    val curves = state.functions.withIndex().filter { it.value.isDrawable }
    val curveColors = curves.map { colors[it.index % colors.size] }

    // 首次进入（还一条能画的都没有）时展开抽屉：否则用户面对一块空白画布，
    // 而"编辑"这件事藏在一块什么也没写的横条里
    var sheetOpen by rememberSaveable { mutableStateOf(drawable.isEmpty()) }

    // 刚点"+"新增的那一行要立刻接上输入法：加一条函数的目的就是写它，
    // 再让用户点一下输入框只是多一步（而表盘上一共只有 144dp 宽）
    var focusRow by remember { mutableStateOf(-1) }

    // 分析只在表达式或视图范围变化时重算：每条采样 401 点并求根，
    // 不该挂在每次重组上（抽屉里的光标移动也会触发重组）
    val analyses = remember(curves.map { it.value.expression }, state.xMin, state.xMax, settings.angleUnit) {
        curves.map {
            FunctionAnalysis.analyze(it.value.expression, state.xMin, state.xMax, settings.angleUnit)
        }
    }
    val active = state.activeCurve
    val points = analyses.getOrNull(active)?.points.orEmpty()
    // 在**读取时**把下标收敛到有效范围，而不是用副作用去改写状态
    val selectedIndex = state.clampSelection(points.size)

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
        // 画布与信息行垫在抽屉之下；抽屉展开时整体模糊退后
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

// ---------------------------------------------------------------- 关键点信息

/**
 * 当前选中点的坐标。
 *
 * 放在画布下方而不是叠在图上：圆屏边缘的弦长最短，叠字要么被裁、
 * 要么压住曲线本身。
 */
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
    // 表达式含三角函数时标出当前角度制：sin(x) 在角度制下周期是 360，
    // 在 [-10,10] 里只画出一小段，不说明的话会让人以为图画错了
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

/**
 * 关键点坐标的显示精度。
 *
 * 计算内核给的是 16 位有效数字，但那是"结果"的规格；图上标一行
 * `(−9.42477796076938, 0)` 在 480px 的屏幕上一行放不下，也不是这个页面
 * 要回答的问题（"零点在哪"）。6 位小数足够读出位置，要精确值去方程页。
 */
private val GRAPH_FORMAT = CalcSettings(decimals = 6)

private fun graphNumber(value: BigDecimal): String = NumberFormatter.format(value, GRAPH_FORMAT)

// -------------------------------------------------------------------- 画布

/**
 * 绘图区。
 *
 * 通铺整个表盘宽度（横向）并在 [PLOT_TOP_DU]..[PLOT_BOTTOM_DU] 上作纵向映射，
 * 再由 `discClip` 裁成圆——于是网格在赤道处最宽、在顶底处收窄，
 * 这正是圆屏上画图应有的样子，也比"缩成一块矩形贴在中间"用满了屏幕。
 *
 * 纵轴范围用**第 98 百分位**而不是最大最小值：`1÷x` 这类函数在极点附近会取到
 * 天文数字，用极值定范围会让整条曲线压成一条线。多条曲线共用一套纵轴，
 * 否则"谁高谁低"这种比较就失去意义。
 */
@Composable
private fun PlotCanvas(
    analyses: List<Analysis>,
    colors: List<Color>,
    active: Int,
    selected: Int,
    xMin: BigDecimal,
    xMax: BigDecimal,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.background

    val range = remember(analyses) { yRangeOf(analyses.flatMap { it.samples }) }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val xSpan = xMax.subtract(xMin).toDouble()
        val yMin = range.first.toDouble()
        val yMax = range.second.toDouble()
        val ySpan = yMax - yMin
        if (xSpan <= 0.0 || ySpan <= 0.0) return@Canvas

        val top = h * PLOT_TOP_DU / Disc.DESIGN
        val bottom = h * PLOT_BOTTOM_DU / Disc.DESIGN
        val plotH = bottom - top
        if (plotH <= 0f) return@Canvas

        fun px(x: Double) = ((x - xMin.toDouble()) / xSpan * w).toFloat()
        fun py(y: Double) = (bottom - (y - yMin) / ySpan * plotH).toFloat()

        // 网格：横竖两轴的步长各自取整——两条轴的量级通常差很远
        //（`x^2` 在 [-10,10] 上纵轴比横轴大一个数量级），共用步长会让一个方向挤成大格
        val xStep = niceStep(xSpan / 4.0)
        var gx = floor(xMin.toDouble() / xStep) * xStep
        while (gx <= xMax.toDouble()) {
            val x = px(gx)
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
            gx += xStep
        }
        val yStep = niceStep(ySpan / 4.0)
        var gy = floor(yMin / yStep) * yStep
        while (gy <= yMax) {
            val y = py(gy)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            gy += yStep
        }

        // 坐标轴
        if (yMin <= 0.0 && yMax >= 0.0) {
            val y = py(0.0)
            drawLine(axisColor, Offset(0f, y), Offset(w, y), strokeWidth = 2f)
        }
        if (xMin.toDouble() <= 0.0 && xMax.toDouble() >= 0.0) {
            val x = px(0.0)
            drawLine(axisColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2f)
        }

        analyses.forEachIndexed { index, analysis ->
            val color = colors.getOrNull(index) ?: colors.last()
            // 曲线：遇到无定义点或跨屏跳变时断开，否则会画出一条横贯画面的假线段
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
            // 选中的那条画粗一点：九条同屏时，光靠颜色找不出"我正在改哪条"
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = if (index == active) 4f else 2.5f),
            )
        }

        // 关键点只标当前曲线：九条曲线各标一遍，画面上会挂满圆点
        val points = analyses.getOrNull(active)?.points.orEmpty()
        val pointColor = colors.getOrNull(active) ?: return@Canvas
        points.forEachIndexed { index, point ->
            val center = Offset(px(point.x.toDouble()), py(point.y.toDouble()))
            if (!center.x.isFinite() || !center.y.isFinite()) return@forEachIndexed
            // 先垫一圈背景色，让圆点压在曲线上也看得清
            drawCircle(ringColor, radius = 7f, center = center)
            drawCircle(pointColor, radius = 4.5f, center = center)
            if (index == selected) {
                drawCircle(Color.White, radius = 10f, center = center, style = Stroke(width = 2f))
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

// -------------------------------------------------------------------- 抽屉

/**
 * 底部抽屉。
 *
 * 高度是按值动画的，但**内容一直组合着**：靠 `wrapContentHeight(unbounded = true)`
 * 让内部按展开高度排版，再让外层的圆角容器把它裁掉。
 * 这样收起/展开只是"露出多少"，而不是换一套内容——
 * 用 if 切换会在动画一开始就把列表抽走，看着像闪了一下。
 */
@Composable
private fun GraphSheet(
    state: GraphState,
    colors: List<Color>,
    height: Dp,
    open: Boolean,
    focusRow: Int,
    onFocused: () -> Unit,
    onAdd: () -> Unit,
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
                hapticsEnabled = hapticsEnabled,
            )
            FunctionList(state = state, colors = colors, focusRow = focusRow, onFocused = onFocused)
        }
    }
}

/** 收起时露出的那一行：颜色、当前函数、展开指示 */
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

/** 抽屉展开后的工具行：添加、缩小、放大、重置 */
@Composable
private fun SheetTools(
    state: GraphState,
    onAdd: () -> Unit,
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
                state.resetView()
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

/** 函数清单：每行一个输入框，左侧色点就是图例 */
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
    // 只在**这一行第一次被组合出来**时抢焦点：点"+"之后直接就能打字，
    // 而不必再瞄准一次 144dp 宽的一行。用 LaunchedEffect(Unit) 而不是
    // 监听 autoFocus 变化，是为了避免每次重组都重新弹一次输入法
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

/** 抽屉工具行的高度 */
private const val SHEET_TOOLS_DU = 42f

/** 工具按钮直径 */
private const val SHEET_TOOL_DU = 34f

/** 清单每行高度 */
private const val ROW_HEIGHT_DU = 44f

/** 拖拽判定阈值（像素）：小于它当作点击，避免轻微抖动就把抽屉打开 */
private const val DRAG_THRESHOLD_PX = 40f
