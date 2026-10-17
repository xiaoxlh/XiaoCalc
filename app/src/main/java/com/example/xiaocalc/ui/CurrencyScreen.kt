package com.example.xiaocalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.Currencies
import com.example.xiaocalc.calc.Currency
import com.example.xiaocalc.calc.CurrencyState
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.calc.MainKeypad
import com.example.xiaocalc.calc.RateStatus
import com.example.xiaocalc.data.RateSource
import com.example.xiaocalc.design.Motion
import com.example.xiaocalc.ui.components.CalcDisplay
import com.example.xiaocalc.ui.components.CalcKeyButton
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
import com.example.xiaocalc.ui.components.WatchVectorIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import com.example.xiaocalc.ui.components.rotaryTarget
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CurrencyPicker { FROM, TO }

@Composable
fun CurrencyScreen(
    state: CurrencyState,
    source: RateSource,
    settings: CalcSettings,
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalWatchMetrics.current
    val layout = remember(metrics.layout.round) { WatchLayout.units(metrics.layout.round) }
    val haptics = rememberKeyHaptics(hapticsEnabled)

    var picker by remember { mutableStateOf<CurrencyPicker?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var consentOpen by remember { mutableStateOf(false) }
    var keypadPage by rememberSaveable { mutableStateOf(KeypadPage.MAIN) }
    var refreshTick by remember { mutableStateOf(0) }

    val pickerScroll = rememberScrollState()
    val expressionScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick, settings.allowNetwork) {
        if (refreshTick == 0) return@LaunchedEffect
        if (!state.beginRefresh(settings.allowNetwork)) {
            consentOpen = true
            return@LaunchedEffect
        }
        val fetched = withContext(Dispatchers.IO) {
            source.fetch(Currencies.BASE, Currencies.quoteCodes)
        }
        state.applyResult(fetched, "没取到汇率，请检查网络")
    }

    LaunchedEffect(Unit) {
        if (settings.allowNetwork && state.needsAutoRenew(LocalDate.now())) {
            refreshTick++
        }
    }

    RotaryHandler(AppPage.CURRENCY) { pixels ->
        if (pickerOpen) {
            scope.launch { pickerScroll.scrollBy(-pixels) }
        } else {
            haptics(false)
            keypadPage = if (pixels > 0f) KeypadPage.FUNCTIONS else KeypadPage.MAIN
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .overlayBlur(active = pickerOpen || consentOpen, enabled = settings.blurEnabled),
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
            val converted = state.convertedText(settings)
            val errorText = state.inputError(settings)
                ?: (state.status as? RateStatus.Failed)?.reason
            val rowHeight = layout.display.height / 2f
            val chipHeight = rowHeight - WatchLayout.DISPLAY_CHIP_INSET
            Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
            CalcDisplay(
                expression = state.input,
                result = converted ?: if (errorText == null) hintText(state) else "",
                errorMessage = errorText,
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = expressionScroll,
                resultPrefix = "",
                expressionRowDu = rowHeight,
                expressionMaxFontDu = 24f,
                expressionLeading = {
                    CurrencyChip(
                        code = state.from.code,
                        description = "起算货币，${state.from.display}",
                        heightDu = chipHeight,
                        hapticsEnabled = hapticsEnabled,
                        onClick = {
                            picker = CurrencyPicker.FROM
                            pickerOpen = true
                        },
                    )
                },
                resultLeading = {
                    CurrencyChip(
                        code = state.to.code,
                        description = "目标货币，${state.to.display}",
                        heightDu = chipHeight,
                        hapticsEnabled = hapticsEnabled,
                        onClick = {
                            picker = CurrencyPicker.TO
                            pickerOpen = true
                        },
                    )
                },
            )
            Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))
            Keypad(
                page = keypadPage,
                rows = layout.keyRows,
                hapticsEnabled = hapticsEnabled,
                onKey = { if (it == CalcKey.Equals) state.swap() else state.onKey(it) },
                angleUnit = settings.angleUnit,
                keysOverride = if (keypadPage == KeypadPage.MAIN) MainKeypad else null,
                iconOverrides = KEYPAD_ICON_OVERRIDES,
            )
            Spacer(Modifier.height(scaled(layout.actionRow.top - layout.keypad.bottom)))
            CurrencyActionBar(
                zone = layout.actionRow,
                hapticsEnabled = hapticsEnabled,
                onClear = state::clear,
                onBackspace = state::backspace,
                onRefresh = { refreshTick++ },
            )
            Spacer(Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT3)),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT2)),
        ) {
            PickerOverlay(
                title = if (picker == CurrencyPicker.FROM) "从哪种货币" else "换成哪种货币",
                options = Currencies.catalog,
                selected = if (picker == CurrencyPicker.FROM) state.from else state.to,
                label = { it.display },
                onSelect = { currency ->
                    if (picker == CurrencyPicker.FROM) state.selectFrom(currency) else state.selectTo(currency)
                    pickerOpen = false
                },
                onDismiss = { pickerOpen = false },
                scrollState = pickerScroll,
            )
        }
        AnimatedVisibility(
            visible = consentOpen,
            enter = fadeIn(Motion.enter(Motion.DURATION_SHORT3)),
            exit = fadeOut(Motion.exit(Motion.DURATION_SHORT2)),
        ) {
            ConsentPanel(
                onDecline = { consentOpen = false },
                onAccept = {
                    consentOpen = false
                    onAuthorize()
                    refreshTick++
                },
            )
        }
    }
}

private fun hintText(state: CurrencyState): String = when (state.status) {
    RateStatus.NotAuthorized -> "未授权联网 · 在用缓存汇率"
    is RateStatus.Loading -> "正在获取…"
    RateStatus.Idle, is RateStatus.Updated, is RateStatus.Failed ->
        if (state.table == null) "还没有汇率 · 点下方更新" else ""
}

@Composable
internal fun ConsentPanel(onDecline: () -> Unit, onAccept: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(scaled(300f)),
            shape = RoundedCornerShape(scaled(26f)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(scaled(20f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "联网获取汇率",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = scaledSp(22f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(scaled(10f)))
                Text(
                    text = "仅在需要时向 api.frankfurter.dev（欧洲央行等官方数据源）请求汇率，" +
                        "只带货币代码，不含任何设备或用户信息。\n" +
                        "同意后，汇率不是今天的时会自动更新一次；也可随时点刷新。" +
                        "可随时在设置中撤销。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = scaledSp(15f),
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(scaled(16f)))
                Row(horizontalArrangement = Arrangement.spacedBy(scaled(10f))) {
                    WideButton(
                        label = "暂不",
                        width = 130f,
                        onClick = onDecline,
                        accent = false,
                    )
                    WideButton(
                        label = "同意",
                        width = 130f,
                        onClick = onAccept,
                        accent = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun WideButton(label: String, width: Float, onClick: () -> Unit, accent: Boolean) {
    val haptics = rememberKeyHaptics(true)
    CalcKeyButton(
        key = CalcKey.PageMain,
        widthDu = width,
        heightDu = 40f,
        hapticsEnabled = true,
        onKey = {
            haptics(accent)
            onClick()
        },
        glyphOverride = label,
        fontSizeOverride = scaledSp(18f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = if (accent) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentOverride = if (accent) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun CurrencyActionBar(
    zone: Zone,
    hapticsEnabled: Boolean,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onRefresh: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val gap = WatchLayout.KEY_GAP
    val slot = (zone.maxWidth - 2 * gap) / 3f
    Box(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(scaled(gap))) {
            CalcKeyButton(
                key = CalcKey.Clear,
                widthDu = slot,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = { haptics(false); onClear() },
                glyphOverride = "C",
                containerOverride = MaterialTheme.colorScheme.errorContainer,
                contentOverride = MaterialTheme.colorScheme.onErrorContainer,
                fontWeightOverride = FontWeight.Bold,
            )
            CalcKeyButton(
                key = CalcKey.Backspace,
                widthDu = slot,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = { haptics(false); onBackspace() },
                icon = WatchVectorIcons.Backspace,
            )
            CalcKeyButton(
                key = CalcKey.PageMain,
                widthDu = slot,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = { haptics(true); onRefresh() },
                icon = WatchVectorIcons.Refresh,
                description = "更新汇率",
                containerOverride = MaterialTheme.colorScheme.secondary,
                contentOverride = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

@Composable
private fun CurrencyChip(
    code: String,
    description: String,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    SelectChip(
        label = code,
        description = description,
        widthDu = WatchLayout.DISPLAY_CHIP_WIDTH,
        heightDu = heightDu,
        emphasis = ChipEmphasis.PRIMARY,
        hapticsEnabled = hapticsEnabled,
        onClick = onClick,
        showChevron = true,
    )
}
