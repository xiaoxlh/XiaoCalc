package com.example.xiaocalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.scaled
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
import com.example.xiaocalc.ui.components.ChipEmphasis
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.KEYPAD_ICON_OVERRIDES
import com.example.xiaocalc.ui.components.Keypad
import com.example.xiaocalc.ui.components.PickerOverlay
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.RotaryTarget
import com.example.xiaocalc.ui.components.SelectChip
import com.example.xiaocalc.ui.components.overlayBlur
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import com.example.xiaocalc.ui.components.rotaryTarget
import kotlinx.coroutines.launch

/** 选择器要改的是哪一端 */
private enum class UnitEnd { FROM, TO }

/** 选择器当前显示的是单位列表还是类别列表 */
private enum class PickerPane { UNITS, CATEGORY }

/**
 * 单位换算页。
 *
 * 纵向四段：顶栏 → 显示区（两行，每行左侧一个单位胶囊）→ 键盘 → 底部操作行。
 *
 * ## 单位胶囊为什么贴在数字行左侧
 *
 * 早先的版本在显示区上方单独排一行"类别 / 起算 / 目标"三个胶囊。问题在于
 * **位置不表达归属**：三颗胶囊并排，用户得记住"中间那颗管上面那行"；
 * 而且那一行本身要吃掉 44du，等于用一整行换三个按钮。
 *
 * 现在把胶囊放进各自那一行的最左端——"这个数是什么单位"与"这个数"贴在一起，
 * 不需要任何记忆负担。右侧只剩纯数字，也能多出一点宽度给 16 位结果。
 * 省下的那一行还给了显示区与键盘：两行各 43du，胶囊仍有 35du 的可点按高度。
 *
 * ## 类别去哪里了
 *
 * 类别不是每次换算都要改的东西，没必要常驻。它变成了**单位选择器里的第二级**：
 * 选择器标题右侧那颗「长度 ⌄」点一下钻进类别列表，选完自动回到单位列表。
 * 同一个面板做两级，比在表盘上再挤一个常驻按钮省地方，也比把 71 个单位
 * 平铺成一张长列表好找。
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

    // 用"布尔开关 + 独立保存的枚举"而不是可空状态：
    // 退出动画播放期间若用可空值，内容会先变成 null，面板会闪一下空白。
    var pickerOpen by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(PickerPane.UNITS) }
    var end by remember { mutableStateOf(UnitEnd.FROM) }

    fun openPicker(target: UnitEnd) {
        end = target
        pane = PickerPane.UNITS
        pickerOpen = true
    }

    val expressionScroll = rememberScrollState()
    val resultScroll = rememberScrollState()
    val pickerScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pickerOpen) {
        // 在类别层按返回只退一层，回到单位列表——不会连整块面板一起关掉
        if (pane == PickerPane.CATEGORY) pane = PickerPane.UNITS else pickerOpen = false
    }

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

    // 两行等高；胶囊比行高略矮，上下各留一点呼吸空间
    val rowHeight = layout.display.height / 2f
    val chipHeight = rowHeight - WatchLayout.DISPLAY_CHIP_INSET

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .overlayBlur(active = pickerOpen, enabled = settings.blurEnabled),
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

            // ---------------------------------------------------------- 显示区
            Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
            CalcDisplay(
                // 单位已经画在胶囊上了，这里只留纯数字
                expression = converter.input,
                result = convertedText.orEmpty(),
                errorMessage = (value as? InputValue.Bad)?.let { errorText(it.error) },
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = resultScroll,
                resultPrefix = "",
                expressionRowDu = rowHeight,
                // 胶囊占了左侧，表达式字号再放大就会一路挤到胶囊上
                expressionMaxFontDu = 24f,
                expressionLeading = {
                    UnitChip(
                        unit = converter.from,
                        description = "起算单位，${converter.from.label}",
                        heightDu = chipHeight,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { openPicker(UnitEnd.FROM) },
                    )
                },
                resultLeading = {
                    UnitChip(
                        unit = converter.to,
                        description = "目标单位，${converter.to.label}",
                        heightDu = chipHeight,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { openPicker(UnitEnd.TO) },
                    )
                },
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
                iconOverrides = KEYPAD_ICON_OVERRIDES,
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
            if (pane == PickerPane.CATEGORY) {
                PickerOverlay(
                    title = "单位类别",
                    options = UnitCategory.entries,
                    selected = converter.category,
                    label = { it.label },
                    onSelect = {
                        converter.selectCategory(it)
                        // 选完类别就回到单位列表：此刻的意图是"换类别再挑单位"，
                        // 直接关掉面板等于让他重新点一次胶囊。
                        pane = PickerPane.UNITS
                    },
                    onDismiss = { pickerOpen = false },
                    scrollState = pickerScroll,
                )
            } else {
                UnitPicker(
                    title = if (end == UnitEnd.FROM) "起算单位" else "目标单位",
                    options = converter.units,
                    selected = if (end == UnitEnd.FROM) converter.from else converter.to,
                    onSelect = {
                        val index = converter.units.indexOf(it)
                        if (end == UnitEnd.FROM) {
                            converter.selectFrom(index)
                        } else {
                            converter.selectTo(index)
                        }
                        pickerOpen = false
                    },
                    onDismiss = { pickerOpen = false },
                    scrollState = pickerScroll,
                    categoryAction = {
                        CategoryDrilldownChip(
                            label = converter.category.label,
                            onClick = {
                                // 两级共用同一个滚动状态，回列表时先归零，
                                // 否则会停在类别列表滚到的位置（单位列表已被裁到看不见）
                                scope.launch { pickerScroll.scrollTo(0) }
                                pane = PickerPane.CATEGORY
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * 显示行左侧的单位胶囊。
 *
 * 宽度固定：两行胶囊等宽，右边的数字才会左右对齐，一眼就能上下对读。
 */
@Composable
private fun UnitChip(
    unit: UnitDef,
    description: String,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    SelectChip(
        label = unit.symbol,
        description = description,
        widthDu = WatchLayout.DISPLAY_CHIP_WIDTH,
        heightDu = heightDu,
        emphasis = ChipEmphasis.PRIMARY,
        hapticsEnabled = hapticsEnabled,
        onClick = onClick,
        showChevron = true,
    )
}

/** 选择器标题右侧的「当前类别 ⌄」，点一下钻进类别列表 */
@Composable
internal fun CategoryDrilldownChip(label: String, onClick: () -> Unit) {
    SelectChip(
        label = label,
        description = "切换单位类别，当前$label",
        widthDu = 78f,
        heightDu = 30f,
        emphasis = ChipEmphasis.SECONDARY,
        hapticsEnabled = true,
        onClick = onClick,
        showChevron = true,
    )
}

@Composable
internal fun UnitPicker(
    title: String,
    options: List<UnitDef>,
    selected: UnitDef,
    onSelect: (UnitDef) -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState,
    categoryAction: @Composable () -> Unit,
) = PickerOverlay(
    title = title,
    options = options,
    selected = selected,
    // 同时给出中文名与符号：符号是胶囊上真正显示的那个，两者必须对得上
    label = { "${it.label}  (${it.symbol})" },
    onSelect = onSelect,
    onDismiss = onDismiss,
    scrollState = scrollState,
    titleAction = categoryAction,
)


