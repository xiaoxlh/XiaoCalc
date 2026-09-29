package com.example.xiaocalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xiaocalc.adaptive.Disc
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcError
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalculatorState
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.ActionBar
import com.example.xiaocalc.ui.components.CalcDisplay
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.Keypad
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.RotaryTarget
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import com.example.xiaocalc.ui.components.rotaryTarget
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 计算器主页面。
 *
 * 纵向四段严格按 [com.example.xiaocalc.adaptive.WatchLayout] 落位：
 * 顶栏（时钟居中 + 溢出菜单）→ 显示区 → 键盘（逐行贴合弦长）→ 底部操作行（C / ⌫ / 分页）。
 *
 * 页面自身**不做任何形状判断**：圆屏适配全部由布局方案的弦长约束保证。
 */
@Composable
fun CalculatorScreen(
    controller: CalculatorState,
    time: String,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalWatchMetrics.current.layout
    val hapticsEnabled = controller.settings.haptics
    val haptics = rememberKeyHaptics(hapticsEnabled)
    var menuOpen by remember { mutableStateOf(false) }

    // 显示区的横向滚动由页面持有：表冠要能驱动它们（"能滚就滚，没得滚才换页"）
    val expressionScroll = rememberScrollState()
    val resultScroll = rememberScrollState()
    val rotaryScope = rememberCoroutineScope()
    val rotaryHaptics = rememberKeyHaptics(hapticsEnabled)

    BackHandler(enabled = menuOpen) { menuOpen = false }

    // 表冠：优先横向滚显示区；显示区没有可滚内容时才切换键盘分页。
    // 这样"拧了有反应"是恒成立的——否则在短表达式下拧表冠会像坏了一样。
    RotaryHandler(AppPage.CALCULATOR) { pixels ->
        when (
            rotaryTarget(
                expressionScrollable = expressionScroll.maxValue > 0,
                resultScrollable = resultScroll.maxValue > 0,
            )
        ) {
            RotaryTarget.EXPRESSION -> rotaryScope.launch { expressionScroll.scrollBy(-pixels) }
            RotaryTarget.RESULT -> rotaryScope.launch { resultScroll.scrollBy(-pixels) }
            RotaryTarget.KEYPAD_PAGE -> {
                rotaryHaptics(false)
                controller.onKey(
                    if (pixels > 0f) CalcKey.PageFunctions else CalcKey.PageMain,
                )
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(scaled(layout.header.top)))

            // 时钟居中置顶（center 槽位相对整条栏真居中，不受右侧菜单影响）
            WatchHeader(
                zone = layout.header,
                center = { ClockText(time) },
                trailing = {
                    WatchIconButton(
                        onClick = {
                            haptics(false)
                            menuOpen = true
                        },
                        icon = WatchIcons.More,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))

            CalcDisplay(
                expression = controller.displayExpression,
                result = controller.resultText,
                errorMessage = controller.error?.let(::errorText),
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = resultScroll,
            )

            Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))

            Keypad(
                page = controller.page,
                rows = layout.keyRows,
                hapticsEnabled = hapticsEnabled,
                onKey = controller::onKey,
                // 让 deg / rad 键的标签反映真实状态——它没有别的反馈方式
                angleUnit = controller.settings.angleUnit,
                // 滑动只是换页快捷方式；显式入口是底部操作行的 123 / fx
                modifier = Modifier.pointerInput(Unit) {
                    val threshold = 48f * density
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDragEnd = {
                            if (abs(accumulated) > threshold) {
                                controller.onKey(
                                    if (accumulated < 0) CalcKey.PageFunctions else CalcKey.PageMain,
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
                    )
                },
            )

            Spacer(Modifier.height(scaled(layout.actionRow.top - layout.keypad.bottom)))

            ActionBar(
                zone = layout.actionRow,
                page = controller.page,
                hapticsEnabled = hapticsEnabled,
                onKey = controller::onKey,
                onSelectPage = { page ->
                    controller.onKey(
                        if (page == KeypadPage.MAIN) CalcKey.PageMain else CalcKey.PageFunctions,
                    )
                },
            )

            Spacer(Modifier.weight(1f))
        }

        // ---------------------------------------------------------------- 遮罩
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT3)),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT2)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = false },
                    ),
            )
        }

        // ---------------------------------------------------------------- 菜单
        val origin = TransformOrigin(1f, 0f)
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT4)) +
                scaleIn(
                    initialScale = 0.85f,
                    transformOrigin = origin,
                    animationSpec = Motion.enter(Motion.DURATION_MEDIUM1),
                ),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT3)) +
                scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = origin,
                    animationSpec = Motion.exit(Motion.DURATION_SHORT3),
                ),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            CalculatorMenu(
                onHistory = {
                    menuOpen = false
                    onNavigate(AppPage.HISTORY)
                },
                onSettings = {
                    menuOpen = false
                    onNavigate(AppPage.SETTINGS)
                },
                topPadding = scaled(layout.header.bottom - 8f),
                // 菜单挂在显示区的右缘：顶栏很窄，挂它上面会让菜单被推到偏中间
                endPadding = scaled((Disc.DESIGN - layout.display.maxWidth) / 2f),
            )
        }
    }
}

@Composable
private fun CalculatorMenu(
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    topPadding: Dp,
    endPadding: Dp,
) {
    Surface(
        modifier = Modifier
            .padding(top = topPadding, end = endPadding)
            .width(scaled(MENU_WIDTH_DU)),
        shape = RoundedCornerShape(scaled(24f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = scaled(6f))) {
            MenuItem("历史记录", onHistory)
            MenuItem("设置", onSettings)
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = scaledSp(20f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = scaled(18f), vertical = scaled(11f)),
    )
}

private const val MENU_WIDTH_DU = 150f

/** 表冠一格（±1）对应显示区的横向滚动像素：约半个字符宽，手感跟手 */
private const val ROTARY_STEP_PIXELS = 14f

/**
 * 错误 → 文案。
 *
 * 旧实现把失败写死成结果字符串（"错误" / "NaN"），既无法区分原因，
 * 又会被后续输入当成有效操作数继续运算；这里失败是枚举，文案只属于 UI 层。
 */
fun errorText(error: CalcError): String = when (error) {
    CalcError.EMPTY -> ""
    CalcError.TOO_LONG -> "表达式过长"
    CalcError.BAD_CHARACTER -> "无法识别的符号"
    CalcError.UNEXPECTED_TOKEN -> "算式顺序有误"
    CalcError.MISSING_OPERAND -> "缺少数字"
    CalcError.UNMATCHED_PAREN -> "括号不匹配"
    CalcError.UNKNOWN_FUNCTION -> "未知函数"
    CalcError.DIVIDE_BY_ZERO -> "不能除以 0"
    CalcError.NEGATIVE_SQRT -> "负数不能开平方"
    CalcError.FACTORIAL_DOMAIN -> "阶乘仅支持 0-170 的整数"
    CalcError.NOT_FINITE -> "结果超出范围"
}
