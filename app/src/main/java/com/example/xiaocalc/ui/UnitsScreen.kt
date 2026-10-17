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

private enum class UnitEnd { FROM, TO }

private enum class PickerPane { UNITS, CATEGORY }

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
        if (pane == PickerPane.CATEGORY) pane = PickerPane.UNITS else pickerOpen = false
    }

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
            Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
            CalcDisplay(
                expression = converter.input,
                result = convertedText.orEmpty(),
                errorMessage = (value as? InputValue.Bad)?.let { errorText(it.error) },
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = resultScroll,
                resultPrefix = "",
                expressionRowDu = rowHeight,
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
            Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))
            Keypad(
                page = keypadPage,
                rows = layout.keyRows,
                hapticsEnabled = hapticsEnabled,
                onKey = { key ->
                    when (key) {
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

    label = { "${it.label}  (${it.symbol})" },
    onSelect = onSelect,
    onDismiss = onDismiss,
    scrollState = scrollState,
    titleAction = categoryAction,
)
