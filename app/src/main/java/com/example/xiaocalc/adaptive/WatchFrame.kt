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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class WatchMetrics(
    val layout: WatchLayout,
    val scale: Float,
) {
    val round: Boolean get() = layout.round
}

private val DefaultMetrics = WatchMetrics(WatchLayout.of(round = false), 1f)

val LocalWatchMetrics = staticCompositionLocalOf { DefaultMetrics }

@Composable
fun scaled(value: Float): Dp = (value * LocalWatchMetrics.current.scale).dp

@Composable
fun scaledSp(value: Float): TextUnit = (value * LocalWatchMetrics.current.scale).sp

fun Modifier.widthDu(value: Float): Modifier = composed { width(scaled(value)) }

fun Modifier.maxWidthDu(value: Float): Modifier = composed { widthIn(max = scaled(value)) }

fun Modifier.sizeDu(value: Float): Modifier = composed { size(scaled(value)) }

@Composable
fun WatchFrame(
    modifier: Modifier = Modifier,
    forceRound: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val detectedRound = configuration.isScreenRound
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
                Box(Modifier.size(sideDp), content = content)
            }
        }
    }
}

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
