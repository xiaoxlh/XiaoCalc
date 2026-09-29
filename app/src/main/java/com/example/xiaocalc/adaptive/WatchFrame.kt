package com.example.xiaocalc.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 当前屏幕的换算信息。
 *
 * @param layout 已按形状推导好的分区方案
 * @param scale  设计单位（du）→ dp 的系数；1du 在 240dp 表盘上约等于 0.625dp
 */
@Immutable
data class WatchMetrics(
    val layout: WatchLayout,
    val scale: Float,
) {
    val round: Boolean get() = layout.round
}

private val DefaultMetrics = WatchMetrics(WatchLayout.of(round = false), 1f)

val LocalWatchMetrics = staticCompositionLocalOf { DefaultMetrics }

/** 设计单位 → dp */
@Composable
fun scaled(value: Float): Dp = (value * LocalWatchMetrics.current.scale).dp

/** 设计单位 → sp（仍受系统字体缩放影响，缩放上限在 theme 中收敛） */
@Composable
fun scaledSp(value: Float): TextUnit = (value * LocalWatchMetrics.current.scale).sp

/** 设计单位宽度（`width` 语义） */
fun Modifier.widthDu(value: Float): Modifier = composed { width(scaled(value)) }

/** 设计单位宽度（`widthIn(max = )` 语义） */
fun Modifier.maxWidthDu(value: Float): Modifier = composed { widthIn(max = scaled(value)) }

/** 设计单位正方形尺寸 */
fun Modifier.sizeDu(value: Float): Modifier = composed { size(scaled(value)) }

/**
 * 把任意尺寸 / 形状的手表屏幕收敛为一块 **居中的正方形内容区**，
 * 并把该正方形解释为 384×384 的设计坐标。
 *
 * 关键取舍（相对旧实现）：
 *  - 圆形屏不再整体缩到 0.95 再逐页打魔数内边距，而是保留 1:1 并交由 [Disc] 的弦长约束；
 *  - 圆形屏不叠加 systemBars / displayCutout inset：
 *    手表圆屏的 bezel inset 是**环形**的，按矩形 inset 收缩会把圆心挤偏，
 *    而内接区域本就远离表盘边缘，无需再让位；
 *  - 方形/异形屏仍按 systemBars + displayCutout 收缩，保证不被状态栏遮挡。
 */
@Composable
fun WatchFrame(
    modifier: Modifier = Modifier,
    forceRound: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val detectedRound = remember(context) {
        runCatching { context.resources.configuration.isScreenRound }.getOrDefault(false)
    }
    val round = forceRound ?: detectedRound
    val layout = remember(round) { WatchLayout.of(round) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (round) Modifier
                else Modifier
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .windowInsetsPadding(WindowInsets.displayCutout),
            ),
    ) {
        val density = LocalDensity.current
        val sidePx = minOf(constraints.maxWidth, constraints.maxHeight).coerceAtLeast(1)
        val sideDp = with(density) { sidePx.toDp() }
        val scale = (sideDp.value / Disc.DESIGN).coerceAtLeast(0.01f)

        CompositionLocalProvider(LocalWatchMetrics provides WatchMetrics(layout, scale)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 内容区恒为正方形：圆形屏即表盘外接正方形，方形屏即短边
                Box(Modifier.size(sideDp), content = content)
            }
        }
    }
}

/** 便于单个组件测试/预览时直接提供度量值 */
@Composable
fun ProvideWatchMetrics(
    round: Boolean,
    scale: Float,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWatchMetrics provides WatchMetrics(WatchLayout.of(round), scale),
        content = content,
    )
}
