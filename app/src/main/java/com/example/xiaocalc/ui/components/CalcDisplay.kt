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

/**
 * 显示区：上一行表达式，下一行结果。
 *
 * 两条关键约束（都是实测截图/真机后确定的，不是拍脑袋）：
 *
 * 1. **行高必须容纳 MiSans 的真实行高 1.326em**（hhea ascent/descent = 1044/-282）。
 *    设计稿上"看起来"够高的行，实际会裁掉逗号、括号等下伸字形——
 *    现象是结果里的千分位逗号被截成圆点，极易与小数点混淆。
 *    因此这里按 `1.326 × 字号` 反推字号上限（见 [EXPRESSION_ROW_DU]）。
 *
 * 2. **`horizontalScroll` 会裁剪到容器边界**，所以滚动容器只能"包住文字"，
 *    不能 `fillMaxSize()`——否则即使外层给了足够高度，也会在滚动容器这一层被切掉。
 *
 * 超长内容：先把字号压到下限，仍放不下才允许左右滑动，且默认贴住右缘（最新字符可见）。
 */
@Composable
fun CalcDisplay(
    expression: String,
    result: String,
    errorMessage: String?,
    zone: Zone,
    /** 表达式行的横向滚动状态；由外部持有，以便表冠也能驱动它 */
    expressionScroll: ScrollState,
    /** 结果行的横向滚动状态；同上 */
    resultScroll: ScrollState,
    modifier: Modifier = Modifier,
    /**
     * 结果行的前缀。默认 `= `（计算器的"结果是"语义）。
     *
     * 方程页把它设为空串：那里结果本身写成 `x = 1, 2`，
     * 再叠一个前缀会变成 `= x = 1, 2`——两个等号，读起来是错的。
     */
    resultPrefix: String = "= ",
    /**
     * 表达式行左侧的附加内容（单位/货币选择胶囊）。
     *
     * 放在行内而不是另起一行，是因为"单位属于这个数"——把它挪到单独的顶栏后，
     * 用户得靠位置去对应哪个单位属于哪一行；贴在数字左边就没有这个问题，
     * 而且腾出的那一整行还给了键盘和大字号。
     */
    expressionLeading: (@Composable () -> Unit)? = null,
    /** 结果行左侧的附加内容（起算单位 / 目标货币） */
    resultLeading: (@Composable () -> Unit)? = null,
    /** 表达式行高。带胶囊时需要更高的行，才能让胶囊保留可点按的高度 */
    expressionRowDu: Float = EXPRESSION_ROW_DU,
    /** 表达式行字号上限 */
    expressionMaxFontDu: Float = EXPRESSION_MAX_FONT_DU,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val resultColor = MaterialTheme.colorScheme.onBackground
    val expressionColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 错误抖动：以 Animatable 驱动，错误出现时播放一次衰减正弦
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

    // 结果行拿到显示区的剩余高度；字号上限由该行高与 MiSans 行高比反推
    val resultMaxFontDu = (zone.height - expressionRowDu) / MISANS_LINE_HEIGHT_RATIO * 0.98f

    Column(
        modifier = modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // ---- 第 1 行：表达式（可选左侧胶囊 + 整宽右对齐）----
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

        // ---- 第 2 行：结果 / 错误 ----
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

/** 表达式行高；字号上限 = 行高 / 行高比。与 [com.example.xiaocalc.adaptive.WatchLayout] / 测试共享这条约束 */
internal const val EXPRESSION_ROW_DU = 28f
internal const val EXPRESSION_MAX_FONT_DU = EXPRESSION_ROW_DU / MISANS_LINE_HEIGHT_RATIO

private const val ERROR_MAX_FONT_DU = 28f
