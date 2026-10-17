package com.xiaoxlh.xiaocalc.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizeText(
    text: String,
    maxWidth: Dp,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }

    val fontSize = remember(text, widthPx, maxFontSize, minFontSize, style, fontWeight, maxLines) {
        fitFontSize(
            measurer = measurer,
            text = text,
            widthPx = widthPx,
            maxSp = maxFontSize.value,
            minSp = minFontSize.value,
            baseStyle = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
        )
    }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

private fun fitFontSize(
    measurer: TextMeasurer,
    text: String,
    widthPx: Float,
    maxSp: Float,
    minSp: Float,
    baseStyle: TextStyle,
    fontWeight: FontWeight?,
    maxLines: Int,
): Float {
    if (text.isEmpty() || widthPx <= 0f) return maxSp
    val style = baseStyle.copy(fontWeight = fontWeight ?: baseStyle.fontWeight)

    fun widthAt(size: Float): Float = measurer.measure(
        text = AnnotatedString(text),
        style = style.copy(fontSize = size.sp),
        maxLines = maxLines,
        softWrap = false,
    ).size.width.toFloat()

    if (widthAt(maxSp) <= widthPx) return maxSp
    var low = minSp
    var high = maxSp
    repeat(BINARY_SEARCH_STEPS) {
        val mid = (low + high) / 2f
        if (widthAt(mid) <= widthPx) low = mid else high = mid
    }
    return low
}

private const val BINARY_SEARCH_STEPS = 10
