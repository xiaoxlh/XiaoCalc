package com.example.xiaocalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.InputValue
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.calc.UnitCategory
import com.example.xiaocalc.calc.UnitConverterState
import com.example.xiaocalc.calc.UnitDef
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.ActionBar
import com.example.xiaocalc.ui.components.CalcDisplay
import com.example.xiaocalc.ui.components.CalcKeyButton
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.Keypad
import com.example.xiaocalc.ui.components.PickerOverlay
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.RotaryTarget
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import com.example.xiaocalc.ui.components.rotaryTarget
import kotlinx.coroutines.launch

/** 单位页当前打开的选择器 */
private enum class PickerKind { CATEGORY, FROM, TO }

/**
 * 单位换算页。
 *
 * 纵向五段：顶栏（返回 + 时钟）→ **单位选择行** → 显示区 → 键盘 → 底部操作行。
 * 比计算器多出的"单位选择行"由 [WatchLayout.units] 提供。
 *
 * 三条设计取舍：
 *
 * 1. **单位选择器不塞进显示区**。显示区每行只有约 23du（≈14dp），
 *    低于手表可用的点按尺寸；选择行给到 44du（≈27.5dp），与底部按钮同级手感。
 *
 * 2. **结果是实时的，因此键盘上的 `=` 被改标为"换"**（交换起算/目标单位）。
 *    留一个按下去什么也不发生的 `=` 比换个标签更糟。
 *
 * 3. **输入框沿用计算器的求值器**，值可以是表达式（`100+20`、`2^10`），
 *    于是"先算再换算"不必来回切页。
 */
@Composable
fun UnitsScreen(
    converter: UnitConverterState,
    settings: CalcSettings,
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalWatchMetrics.current
    val layout = remember(metrics.layout.round) { WatchLayout.units(metrics.layout.round) }
    val haptics = rememberKeyHaptics(hapticsEnabled)

    var keypadPage by rememberSaveable { mutableStateOf(KeypadPage.MAIN) }

    // 用"布尔开关 + 独立保存的种类"而不是 `PickerKind?`：
    // 退出动画播放期间若用可空状态，内容会先变成 null，面板会闪一下空白。
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerKind by remember { mutableStateOf(PickerKind.CATEGORY) }
    fun openPicker(kind: PickerKind) {
        pickerKind = kind
        pickerOpen = true
    }

    val expressionScroll = rememberScrollState()
    val resultScroll = rememberScrollState()
    val pickerScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pickerOpen) { pickerOpen = false }

    // 表冠：选择器打开时滚列表；否则沿用计算器的"能滚就滚，没得滚才换页"
    RotaryHandler(AppPage.UNITS) { pixels ->
        if (pickerOpen) {
            scope.launch { pickerScroll.scrollBy(-pixels) }
        } else {
            when (
                rotaryTarget(
                    expressionScrollable = expressionScroll.maxValue > 0,
                    resultScrollable = resultScroll.maxValue > 0,
                )
            ) {
                RotaryTarget.EXPRESSION -> scope.launch { expressionScroll.scrollBy(-pixels) }
                RotaryTarget.RESULT -> scope.launch { resultScroll.scrollBy(-pixels) }
                RotaryTarget.KEYPAD_PAGE -> {
                    haptics(false)
                    keypadPage = if (pixels > 0f) KeypadPage.FUNCTIONS else KeypadPage.MAIN
                }
            }
        }
    }

    val value = converter.evaluate(settings)
    val convertedText = converter.convertedText(settings)

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
                            onNavigate(AppPage.CALCULATOR)
                        },
                        icon = WatchIcons.Back,
                        contentDescription = "返回计算器",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                center = { ClockText(time) },
            )

            // ------------------------------------------------------ 单位选择行
            val pickerZone = layout.pickerRow
            if (pickerZone != null) {
                Spacer(Modifier.height(scaled(pickerZone.top - layout.header.bottom)))
                val gap = WatchLayout.KEY_GAP
                val itemWidth = (pickerZone.maxWidth - 2 * gap) / 3f
                Row(
                    modifier = Modifier.width(scaled(pickerZone.maxWidth)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PickerChip(
                        text = converter.category.label,
                        widthDu = itemWidth,
                        heightDu = pickerZone.height,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { openPicker(PickerKind.CATEGORY) },
                    )
                    Spacer(Modifier.width(scaled(gap)))
                    PickerChip(
                        text = converter.from.label,
                        widthDu = itemWidth,
                        heightDu = pickerZone.height,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { openPicker(PickerKind.FROM) },
                    )
                    Spacer(Modifier.width(scaled(gap)))
                    PickerChip(
                        text = converter.to.label,
                        widthDu = itemWidth,
                        heightDu = pickerZone.height,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { openPicker(PickerKind.TO) },
                    )
                }
                Spacer(Modifier.height(scaled(layout.display.top - pickerZone.bottom)))
            } else {
                Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
            }

            // ---------------------------------------------------------- 显示区
            CalcDisplay(
                // 单位跟在数值后面，两行读起来才是完整的 "12.5 米 = 41.01 英尺"
                expression = if (converter.input.isEmpty()) {
                    ""
                } else {
                    "${converter.input} ${converter.from.symbol}"
                },
                result = convertedText?.let { "$it ${converter.to.symbol}" }.orEmpty(),
                errorMessage = (value as? InputValue.Bad)?.let { errorText(it.error) },
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = resultScroll,
            )

            // ------------------------------------------------------------ 键盘
            Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))
            Keypad(
                page = keypadPage,
                rows = layout.keyRows,
                hapticsEnabled = hapticsEnabled,
                onKey = { key ->
                    when (key) {
                        // 结果实时可见，`=` 的"求值"含义在此页不成立，改作交换单位
                        CalcKey.Equals -> {
                            haptics(true)
                            converter.swap()
                        }
                        CalcKey.PageMain -> keypadPage = KeypadPage.MAIN
                        CalcKey.PageFunctions -> keypadPage = KeypadPage.FUNCTIONS
                        else -> converter.onKey(key)
                    }
                },
                angleUnit = settings.angleUnit,
                glyphOverrides = KEYPAD_GLYPH_OVERRIDES,
            )

            // ---------------------------------------------------------- 操作行
            Spacer(Modifier.height(scaled(layout.actionRow.top - layout.keypad.bottom)))
            ActionBar(
                zone = layout.actionRow,
                page = keypadPage,
                hapticsEnabled = hapticsEnabled,
                onKey = converter::onKey,
                onSelectPage = { keypadPage = it },
            )

            Spacer(Modifier.weight(1f))
        }

        // ---------------------------------------------------------------- 选择器
        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT3)),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT2)),
        ) {
            when (pickerKind) {
                PickerKind.CATEGORY -> PickerOverlay(
                    title = "单位类别",
                    options = UnitCategory.entries,
                    selected = converter.category,
                    label = { it.label },
                    onSelect = {
                        converter.selectCategory(it)
                        pickerOpen = false
                    },
                    onDismiss = { pickerOpen = false },
                    scrollState = pickerScroll,
                )

                PickerKind.FROM -> UnitPicker(
                    title = "起算单位",
                    options = converter.units,
                    selected = converter.from,
                    onSelect = {
                        converter.selectFrom(converter.units.indexOf(it))
                        pickerOpen = false
                    },
                    onDismiss = { pickerOpen = false },
                    scrollState = pickerScroll,
                )

                PickerKind.TO -> UnitPicker(
                    title = "目标单位",
                    options = converter.units,
                    selected = converter.to,
                    onSelect = {
                        converter.selectTo(converter.units.indexOf(it))
                        pickerOpen = false
                    },
                    onDismiss = { pickerOpen = false },
                    scrollState = pickerScroll,
                )
            }
        }
    }
}

@Composable
private fun UnitPicker(
    title: String,
    options: List<UnitDef>,
    selected: UnitDef,
    onSelect: (UnitDef) -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState,
) = PickerOverlay(
    title = title,
    options = options,
    selected = selected,
    // 同时给出中文名与符号：符号是结果行里真正出现的那个，两者必须对得上
    label = { "${it.label}  (${it.symbol})" },
    onSelect = onSelect,
    onDismiss = onDismiss,
    scrollState = scrollState,
)

/**
 * 选择行里的胶囊按钮。
 *
 * 复用 [CalcKeyButton] 以继承按压回弹、触感与无障碍角色；
 * 形状用 50% 圆角（胶囊）——与普通按键的圆角矩形区分开，
 * 表示"点它会打开一个选择器"而不是"按下即生效"。
 */
@Composable
private fun PickerChip(
    text: String,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    CalcKeyButton(
        key = CalcKey.Clear, // 仅为无障碍标签占位，真实语义由 onClick 表达
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(false)
            onClick()
        },
        glyphOverride = text,
        shape = RoundedCornerShape(percent = 50),
        fontSizeOverride = scaledSp(19f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentOverride = MaterialTheme.colorScheme.onSurface,
    )
}

/** `=` 在本页改标为"换"：求值在此无意义，交换单位才是用户要的 */
private val KEYPAD_GLYPH_OVERRIDES: Map<CalcKey, String> = mapOf(CalcKey.Equals to "换")
