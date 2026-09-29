package com.example.xiaocalc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.xiaocalc.adaptive.KeyRow
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.AngleUnit
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.KeyKind
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.calc.keypadFor
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.design.NumTextStyle

/**
 * 键盘。布局是**数据驱动**的：行由 [KeyRow] 给出（每行自己的宽度与键宽），
 * 键面由 [CalcKey] 给出，配色由 [KeyKind] 决定。
 *
 * 关键点：每一行按**自己那一行的弦长**铺满，而不是所有行共用一个最小宽度。
 * 圆屏上这会把原本浪费在两侧的空白真正用起来（见 [KeyRow] 的说明）。
 */
@Composable
fun Keypad(
    page: KeypadPage,
    rows: List<KeyRow>,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前角度单位，只用于让 `deg` / `rad` 键的标签反映真实状态 */
    angleUnit: AngleUnit = AngleUnit.DEG,
) {
    val gap = scaled(WatchLayout.KEY_GAP)
    val totalHeight = scaled(
        rows.sumOf { it.height.toDouble() }.toFloat() +
            (rows.size - 1).coerceAtLeast(0) * WatchLayout.KEY_GAP,
    )
    val widestRow = scaled(rows.maxOfOrNull { it.maxWidth } ?: 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            // 翻页动画期间两张键盘会横向位移，必须裁掉溢出的部分：
            // 否则滑出的键会画到显示区、底部操作行甚至表盘外（逐帧录制时实测到过）。
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val direction = if (targetState == KeypadPage.FUNCTIONS) 1 else -1
                (
                    slideInHorizontally(Motion.enter(Motion.DURATION_MEDIUM2)) { it * direction } +
                        fadeIn(Motion.enter(Motion.DURATION_SHORT4))
                    ).togetherWith(
                    slideOutHorizontally(Motion.exit(Motion.DURATION_SHORT4)) { -it * direction } +
                        fadeOut(Motion.exit(Motion.DURATION_SHORT3)),
                )
            },
            label = "keypadPage",
        ) { target ->
            val keys = keypadFor(target)
            Column(
                modifier = Modifier.width(widestRow),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.width(scaled(row.maxWidth)),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        keys.drop(rowIndex * row.columns).take(row.columns).forEach { key ->
                            CalcKeyButton(
                                key = key,
                                widthDu = row.keyWidth,
                                heightDu = row.height,
                                hapticsEnabled = hapticsEnabled,
                                onKey = onKey,
                                glyphOverride = key.dynamicGlyph(angleUnit),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 键盘按键。
 *
 * 尺寸以**设计单位**传入并在内部换算，避免把 du / dp 混着算（字号尤其容易搞错）。
 * 键面现在是宽矩形而不是正方形，所以圆角用**短边百分比**而不是圆形裁剪——
 * 圆形裁剪会把这个比例压成椭圆。
 */
@Composable
fun CalcKeyButton(
    key: CalcKey,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    glyphOverride: String? = null,
    /** 用矢量图标代替文字字面（退格键必须如此：MiSans 不含 U+232B） */
    icon: ImageVector? = null,
    containerOverride: Color? = null,
    contentOverride: Color? = null,
    fontSizeOverride: TextUnit? = null,
    /** 形状覆盖：分页胶囊用胶囊形，与普通键的圆角矩形区分开 */
    shape: Shape = RoundedCornerShape(percent = KEY_CORNER_PERCENT),
    fontWeightOverride: FontWeight? = null,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = Motion.press(),
        label = "keyScale",
    )

    val container = containerOverride ?: keyContainerColor(key)
    val content = contentOverride ?: keyContentColor(key)
    val animatedContainer by animateColorAsState(
        targetValue = if (pressed) lerp(container, Color.White, 0.18f) else container,
        animationSpec = Motion.standard(Motion.DURATION_SHORT2),
        label = "keyContainer",
    )

    Box(
        modifier = modifier
            .width(scaled(widthDu))
            .height(scaled(heightDu))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(animatedContainer)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = key.label,
                onClick = {
                    // "=" 是最关键的一次确认操作，给更强的触感
                    haptics(key.kind == KeyKind.EQUALS)
                    onKey(key)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = key.label,
                tint = content,
                modifier = Modifier.size(scaled(minOf(widthDu, heightDu) * 0.46f)),
            )
            return@Box
        }
        Text(
            text = glyphOverride ?: key.label,
            color = content,
            fontSize = fontSizeOverride ?: scaledSp(
                labelFontDu(key, minOf(widthDu, heightDu)),
            ),
            fontWeight = fontWeightOverride
                ?: if (key.kind == KeyKind.EQUALS) FontWeight.Bold else FontWeight.Medium,
            style = NumTextStyle,
            maxLines = 1,
        )
    }
}

/** 字号随按键短边与标签长度自适应，避免逐键做文本测量 */
private fun labelFontDu(key: CalcKey, shortestDu: Float): Float = when (key.label.length) {
    1 -> shortestDu * 0.62f
    2 -> shortestDu * 0.46f
    else -> shortestDu * 0.34f
}

@Composable
internal fun keyContainerColor(key: CalcKey): Color = when (key.kind) {
    // 三级中性色阶，按"按压频次"分层——数字 > 函数 > 工具。
    // 函数键原先用 surfaceContainer(#1B1B1B)，在纯黑底上只比背景亮一点点，
    // 于是函数页整体发闷、反倒是几个红色运算符"跳"出来，看着像随机高亮。
    // 数字键最亮：最常按的一层
    KeyKind.DIGIT -> MaterialTheme.colorScheme.surfaceContainerHighest
    // 运算符用品牌红的容器色，语义与"结果强调"呼应
    KeyKind.OPERATOR -> MaterialTheme.colorScheme.secondaryContainer
    // 函数键比工具键亮一档：它们是函数页的主体内容
    KeyKind.FUNCTION -> MaterialTheme.colorScheme.surfaceContainerHigh
    // "=" 是主操作，直接填充品牌红
    KeyKind.EQUALS -> MaterialTheme.colorScheme.secondary
    // 工具键（C / ⌫ / 未选中的分页）下沉，不与内容抢注意力
    KeyKind.ACTION -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
internal fun keyContentColor(key: CalcKey): Color = when (key.kind) {
    KeyKind.DIGIT -> MaterialTheme.colorScheme.onSurface
    KeyKind.OPERATOR -> MaterialTheme.colorScheme.onSecondaryContainer
    KeyKind.FUNCTION -> MaterialTheme.colorScheme.onSurfaceVariant
    KeyKind.EQUALS -> MaterialTheme.colorScheme.onSecondary
    KeyKind.ACTION -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 圆角半径按短边的百分比取，宽键与窄键的观感才一致 */
private const val KEY_CORNER_PERCENT = 32

/** 分页胶囊：50% 圆角 = 胶囊形，用形状把"导航"与"按键"区分开 */
internal const val CHIP_CORNER_PERCENT = 50

/**
 * 标签需要随状态变化的键。
 *
 * 角度单位键原先键面写死 "deg"，切换后毫无变化——功能其实是好的
 * （`CalculatorState` 里有 `toggleAngleUnit()` 分支），但**按下去看不到任何反馈**，
 * 用户自然会认为按键坏了。改成显示**当前**单位，按下当场变化即是最直接的反馈。
 */
private fun CalcKey.dynamicGlyph(angleUnit: AngleUnit): String? = when (this) {
    CalcKey.AngleUnit -> if (angleUnit == AngleUnit.DEG) "deg" else "rad"
    else -> null
}
