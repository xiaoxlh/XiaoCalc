package com.example.xiaocalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.example.xiaocalc.adaptive.Disc
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.EquationError
import com.example.xiaocalc.calc.EquationMode
import com.example.xiaocalc.calc.EquationRoot
import com.example.xiaocalc.calc.EquationSolution
import com.example.xiaocalc.calc.EquationSolver
import com.example.xiaocalc.calc.EquationState
import com.example.xiaocalc.calc.EquationSteps
import com.example.xiaocalc.calc.LinearSystemSolver
import com.example.xiaocalc.calc.SystemSolution
import com.example.xiaocalc.design.NumTextStyle
import com.example.xiaocalc.ui.components.CalcKeyButton
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.RotaryHandler
import com.example.xiaocalc.ui.components.RotaryTarget
import com.example.xiaocalc.ui.components.WatchHeader
import com.example.xiaocalc.ui.components.WatchIconButton
import com.example.xiaocalc.ui.components.WatchIcons
import com.example.xiaocalc.ui.components.WatchVectorIcons
import com.example.xiaocalc.ui.components.rememberKeyHaptics
import com.example.xiaocalc.ui.components.rotaryTarget
import java.math.BigDecimal
import kotlinx.coroutines.launch

@Composable
fun EquationScreen(
    state: EquationState,
    settings: CalcSettings,
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val focusManager = LocalFocusManager.current
    val resultScroll = rememberScrollState()

    var outcome by remember {
        mutableStateOf(
            if (state.solverInput.isBlank()) {
                null
            } else {
                solveEquation(state.solverInput, state.mode, settings)
            },
        )
    }
    var stepsOpen by remember { mutableStateOf(false) }

    val zones = remember { equationZones() }
    val scope = rememberCoroutineScope()

    RotaryHandler(AppPage.EQUATION) { pixels ->
        if (resultScroll.maxValue > 0) {
            scope.launch { resultScroll.scrollBy(-pixels) }
        } else {
            haptics(false)
        }
    }

    fun solve() {
        haptics(true)
        focusManager.clearFocus()
        val result = solveEquation(state.solverInput, state.mode, settings)
        stepsOpen = false
        outcome = result
        scope.launch { resultScroll.scrollTo(0) }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(scaled(zones.header.top)))
            WatchHeader(
                zone = zones.header,
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
            Spacer(Modifier.height(scaled(zones.form.top - zones.header.bottom)))
            InputFields(
                state = state,
                zone = zones.form,
                hapticsEnabled = hapticsEnabled,
            )
            Spacer(Modifier.height(scaled(zones.symbols.top - zones.form.bottom)))
            SymbolStrip(
                zone = zones.symbols,
                hapticsEnabled = hapticsEnabled,
                onInsert = { state.insert(it) },
            )
            Spacer(Modifier.height(scaled(zones.mode.top - zones.symbols.bottom)))
            ModeSwitch(
                mode = state.mode,
                zone = zones.mode,
                hapticsEnabled = hapticsEnabled,
                onSelect = {
                    focusManager.clearFocus()
                    outcome = null
                    state.selectMode(it)
                },
            )
            Spacer(Modifier.height(scaled(zones.solve.top - zones.mode.bottom)))
            Box(
                modifier = Modifier
                    .width(scaled(zones.solve.maxWidth))
                    .height(scaled(zones.solve.height)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                CalcKeyButton(
                    key = CalcKey.Equals,
                    widthDu = SOLVE_WIDTH_DU,
                    heightDu = zones.solve.height,
                    hapticsEnabled = hapticsEnabled,
                    onKey = { solve() },
                    icon = WatchVectorIcons.ArrowForward,
                    description = "求解",
                )
            }
            Spacer(Modifier.height(scaled(zones.result.top - zones.solve.bottom)))
            ResultArea(
                zone = zones.result,
                outcome = outcome,
                stepsOpen = stepsOpen,
                onToggleSteps = { stepsOpen = !stepsOpen },
                scrollState = resultScroll,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

private data class EquationZones(
    val header: Zone,
    val form: Zone,
    val symbols: Zone,
    val mode: Zone,
    val solve: Zone,
    val result: Zone,
)

private fun equationZones(): EquationZones {
    fun zone(top: Float, height: Float) =
        Zone(top, height, Disc.chordInBand(top, top + height) * WatchLayout.BAND_SAFETY)

    return EquationZones(
        header = zone(16f, 40f),
        form = zone(62f, 86f),
        symbols = zone(154f, 30f),
        mode = zone(190f, 38f),
        solve = zone(234f, 40f),
        result = zone(280f, 72f),
    )
}

private const val SOLVE_WIDTH_DU = 62f

@Composable
private fun InputFields(
    state: EquationState,
    zone: Zone,
    hapticsEnabled: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val system = state.mode == EquationMode.SYSTEM
    val fieldHeight = if (system) (zone.height - FIELD_GAP_DU) / 2f else 56f
    val texts = if (system) state.systemTexts else listOf(state.singleText)

    Column(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        verticalArrangement = Arrangement.Center,
    ) {
        texts.forEachIndexed { index, text ->
            if (index > 0) Spacer(Modifier.height(scaled(FIELD_GAP_DU)))
            EquationField(
                text = text,
                placeholder = if (system) {
                    if (index == 0) "第一个方程，如 2x+y=5" else "第二个方程，如 x-y=1"
                } else {
                    "如 x^2-3x+2=0"
                },
                heightDu = fieldHeight,
                hapticsEnabled = hapticsEnabled,
                onTextChange = { state.setText(index, it) },
                onFocus = { state.focus(index) },
                onDismiss = { focusManager.clearFocus() },
            )
        }
    }
}

@Composable
private fun EquationField(
    text: String,
    placeholder: String,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onFocus: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val haptics = rememberKeyHaptics(hapticsEnabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaled(heightDu))
            .clip(RoundedCornerShape(scaled(16f)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(17f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = scaled(14f)),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = {
                haptics(false)
                onTextChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaled(14f))
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocus() },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(21f),
                fontWeight = FontWeight.Medium,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            onTextLayout = { },
        )
    }
}

private const val FIELD_GAP_DU = 8f

private val SYMBOLS = listOf("^", "(", ")", "=", "x", "+", "−", "×", "÷", "√", "π", ".")

@Composable
private fun SymbolStrip(
    zone: Zone,
    hapticsEnabled: Boolean,
    onInsert: (String) -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val gap = WatchLayout.KEY_GAP
    val chipWidth = 40f
    val stripScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(stripScroll),
            horizontalArrangement = Arrangement.spacedBy(scaled(gap)),
        ) {
            for (symbol in SYMBOLS) {
                CalcKeyButton(
                    key = CalcKey.PageMain,
                    widthDu = chipWidth,
                    heightDu = zone.height,
                    hapticsEnabled = hapticsEnabled,
                    onKey = {
                        haptics(false)
                        onInsert(if (symbol == "−") "-" else if (symbol == "×") "*" else if (symbol == "÷") "/" else symbol)
                    },
                    glyphOverride = symbol,
                    fontSizeOverride = scaledSp(18f),
                    fontWeightOverride = FontWeight.Bold,
                    shape = RoundedCornerShape(percent = 32),
                    containerOverride = MaterialTheme.colorScheme.surfaceContainer,
                    contentOverride = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(
    mode: EquationMode,
    zone: Zone,
    hapticsEnabled: Boolean,
    onSelect: (EquationMode) -> Unit,
) {
    val gap = WatchLayout.KEY_GAP
    val free = zone.maxWidth - gap
    val progress by animateFloatAsState(
        targetValue = if (mode == EquationMode.SYSTEM) 1f else 0f,
        animationSpec = com.example.xiaocalc.design.Motion.standard(
            com.example.xiaocalc.design.Motion.DURATION_MEDIUM1,
        ),
        label = "modeSplit",
    )

    val singleWidth = free * (0.56f - 0.10f * progress)
    val systemWidth = free * (0.44f + 0.10f * progress)

    Row(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        horizontalArrangement = Arrangement.spacedBy(scaled(gap)),
    ) {
        ModeSlot(
            label = EquationMode.SINGLE.label,
            selected = mode == EquationMode.SINGLE,
            widthDu = singleWidth,
            heightDu = zone.height,
            hapticsEnabled = hapticsEnabled,
            onClick = { onSelect(EquationMode.SINGLE) },
        )
        ModeSlot(
            label = EquationMode.SYSTEM.label,
            selected = mode == EquationMode.SYSTEM,
            widthDu = systemWidth,
            heightDu = zone.height,
            hapticsEnabled = hapticsEnabled,
            onClick = { onSelect(EquationMode.SYSTEM) },
        )
    }
}

@Composable
private fun ModeSlot(
    label: String,
    selected: Boolean,
    widthDu: Float,
    heightDu: Float,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    CalcKeyButton(
        key = CalcKey.PageMain,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = {
            haptics(false)
            onClick()
        },
        glyphOverride = label,
        description = label,
        fontSizeOverride = scaledSp(if (selected) 21f else 18f),
        fontWeightOverride = if (selected) FontWeight.Bold else FontWeight.Medium,
        shape = RoundedCornerShape(percent = 30),
        containerOverride = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentOverride = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ResultArea(
    zone: Zone,
    outcome: EquationOutcome?,
    stepsOpen: Boolean,
    onToggleSteps: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height))
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (outcome == null) {
            Text(
                text = "输入方程后按右边的箭头",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(16f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = scaled(10f)),
            )
            return@Column
        }
        val error = outcome.errorText
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = scaledSp(19f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = NumTextStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = outcome.text,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = scaledSp(21f),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                style = NumTextStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (outcome.steps.isNotEmpty()) {
            Spacer(Modifier.height(scaled(6f)))
            StepToggle(open = stepsOpen, onClick = onToggleSteps)
            AnimatedVisibility(
                visible = stepsOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = scaled(4f)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    outcome.steps.forEach { line ->
                        Text(
                            text = line,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = scaledSp(15f),
                            style = NumTextStyle,
                            modifier = Modifier.padding(vertical = scaled(1f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepToggle(open: Boolean, onClick: () -> Unit) {
    val haptics = rememberKeyHaptics(true)
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = com.example.xiaocalc.design.Motion.enter(
            com.example.xiaocalc.design.Motion.DURATION_SHORT3,
        ),
        label = "stepChevron",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(role = Role.Button, onClickLabel = "过程", onClick = {
                haptics(false)
                onClick()
            })
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = scaled(12f), vertical = scaled(4f)),
    ) {
        Text(
            text = "过程",
            color = MaterialTheme.colorScheme.primary,
            fontSize = scaledSp(15f),
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = WatchVectorIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = scaled(3f))
                .size(scaled(14f))
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

private data class EquationOutcome(
    val text: String,
    val errorText: String?,
    val steps: List<String> = emptyList(),
)

private fun solveEquation(
    input: String,
    mode: EquationMode,
    settings: CalcSettings,
): EquationOutcome {
    if (input.isBlank()) {
        return EquationOutcome("", "先输入一个方程")
    }
    return when (mode) {
        EquationMode.SINGLE -> when (
            val solution = EquationSolver.solve(input, settings.angleUnit)
        ) {
            is EquationSolution.Solved -> EquationOutcome(
                text = rootsText(solution.roots),
                errorText = null,
                steps = EquationSteps.build(input, settings.angleUnit),
            )
            is EquationSolution.Failed -> EquationOutcome("", equationErrorText(solution.error))
        }
        EquationMode.SYSTEM -> when (
            val solution = LinearSystemSolver.solve(input, settings.angleUnit)
        ) {
            is SystemSolution.Unique -> {
                val x = solution.xExact ?: solution.xDisplay
                val y = solution.yExact ?: solution.yDisplay
                EquationOutcome("x = $x, y = $y", null)
            }
            SystemSolution.Infinite -> EquationOutcome("", "有无穷多组解")
            SystemSolution.NoSolution -> EquationOutcome("", "无解")
            is SystemSolution.Failed -> EquationOutcome("", equationErrorText(solution.error))
        }
    }
}

private fun rootsText(roots: List<EquationRoot>): String {
    val ordered = roots.sortedWith(compareBy({ rootReal(it) }, { rootImaginary(it) }))
    return "x = " + ordered.joinToString(", ") { it.exact ?: it.display }
}

private fun rootReal(root: EquationRoot): BigDecimal = when (root) {
    is EquationRoot.Real -> root.value
    is EquationRoot.Complex -> root.real
}

private fun rootImaginary(root: EquationRoot): BigDecimal = when (root) {
    is EquationRoot.Real -> BigDecimal.ZERO
    is EquationRoot.Complex -> root.imaginary
}

private fun equationErrorText(error: EquationError): String = when (error) {
    EquationError.EMPTY -> ""
    EquationError.NO_EQUALS -> "需要等号，例如 x^2-1=0"
    EquationError.MULTIPLE_EQUALS -> "等号太多"
    EquationError.NOT_POLYNOMIAL -> "无法解析为多项式方程"
    EquationError.DEGREE_TOO_HIGH -> "方程次数过高"
    EquationError.NOT_CONVERGED -> "求根未收敛"
    EquationError.EQUATION_COUNT -> "需要两个方程"
    EquationError.IDENTITY -> "这是恒等式"
    EquationError.NO_SOLUTION -> "无解"
}
