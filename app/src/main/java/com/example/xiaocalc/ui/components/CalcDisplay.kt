package com.example.xiaocalc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.MISANS_LINE_HEIGHT_RATIO
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.design.NumTextStyle
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CalcDisplay(
    expression: String,
    result: String,
    errorMessage: String?,
    zone: Zone,

    expressionScroll: ScrollState,

    resultScroll: ScrollState,
    modifier: Modifier = Modifier,

    resultPrefix: String = "= ",

    expressionLeading: (@Composable () -> Unit)? = null,

    resultLeading: (@Composable () -> Unit)? = null,

    expressionRowDu: Float = EXPRESSION_ROW_DU,

    expressionMaxFontDu: Float = EXPRESSION_MAX_FONT_DU,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val resultColor = MaterialTheme.colorScheme.onBackground
    val expressionColor = MaterialTheme.colorScheme.onSurfaceVariant

    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(durationMillis = 360, easing = LinearEasing))
        } else {
            shake.snapTo(0f)
        }
    }
    val density = LocalDensity.current
    val shakePx = with(density) { scaled(7f).toPx() }

    val resultMaxFontDu = (zone.height - expressionRowDu) / MISANS_LINE_HEIGHT_RATIO * 0.98f

    Column(
        modifier = modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(scaled(expressionRowDu)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expressionLeading != null) {
                expressionLeading()
                Spacer(Modifier.width(scaled(WatchLayout.DISPLAY_CHIP_GAP)))
            }
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val available = maxWidth
                Box(
                    modifier = Modifier.horizontalScroll(expressionScroll, reverseScrolling = true),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    AutoSizeText(
                        text = expression,
                        maxWidth = available,
                        maxFontSize = scaledSp(expressionMaxFontDu),
                        minFontSize = scaledSp(13f),
                        color = expressionColor,
                        fontWeight = FontWeight.Medium,
                        style = NumTextStyle,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        val visual = ResultVisual(
            text = errorMessage ?: if (result.isEmpty()) "" else "$resultPrefix$result",
            isError = errorMessage != null,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    translationX = if (errorMessage == null) {
                        0f
                    } else {
                        sin(shake.value * PI * 6).toFloat() * shakePx * (1f - shake.value)
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (resultLeading != null) {
                    resultLeading()
                    Spacer(Modifier.width(scaled(WatchLayout.DISPLAY_CHIP_GAP)))
                }
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    val available = maxWidth
                    AnimatedContent(
                        targetState = visual,
                        transitionSpec = {
                            (
                                fadeIn(Motion.enter(Motion.DURATION_SHORT4)) +
                                    scaleIn(
                                        initialScale = 0.90f,
                                        animationSpec = Motion.enter(Motion.DURATION_MEDIUM1),
                                    )
                                ).togetherWith(fadeOut(Motion.exit(Motion.DURATION_SHORT3)))
                        },
                        label = "result",
                    ) { state ->
                        Box(
                            modifier = Modifier.horizontalScroll(resultScroll, reverseScrolling = true),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            AutoSizeText(
                                text = state.text,
                                maxWidth = available,
                                maxFontSize = scaledSp(
                                    if (state.isError) ERROR_MAX_FONT_DU else resultMaxFontDu,
                                ),
                                minFontSize = scaledSp(15f),
                                color = if (state.isError) errorColor else resultColor,
                                fontWeight = FontWeight.Black,
                                style = NumTextStyle,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ResultVisual(val text: String, val isError: Boolean)

internal const val EXPRESSION_ROW_DU = 28f
internal const val EXPRESSION_MAX_FONT_DU = EXPRESSION_ROW_DU / MISANS_LINE_HEIGHT_RATIO

private const val ERROR_MAX_FONT_DU = 28f
