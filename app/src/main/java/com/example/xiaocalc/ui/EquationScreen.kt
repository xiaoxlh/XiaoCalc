package com.example.xiaocalc.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.xiaocalc.adaptive.LocalWatchMetrics
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey
import com.example.xiaocalc.calc.CalcSettings
import com.example.xiaocalc.calc.EquationError
import com.example.xiaocalc.calc.EquationKeypad
import com.example.xiaocalc.calc.EquationMode
import com.example.xiaocalc.calc.EquationRoot
import com.example.xiaocalc.calc.EquationSolution
import com.example.xiaocalc.calc.EquationSolver
import com.example.xiaocalc.calc.EquationState
import com.example.xiaocalc.calc.KeypadPage
import com.example.xiaocalc.calc.LinearSystemSolver
import com.example.xiaocalc.calc.SystemSolution
import com.example.xiaocalc.ui.components.CalcDisplay
import com.example.xiaocalc.ui.components.CalcKeyButton
import com.example.xiaocalc.ui.components.ClockText
import com.example.xiaocalc.ui.components.Keypad
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

/** 额外操作行里的符号键，随模式变化 */
private data class SymbolKey(val label: String, val insert: String)

private val SINGLE_SYMBOLS = listOf(
    SymbolKey("^", "^"),
    SymbolKey("(", "("),
    SymbolKey(")", ")"),
    SymbolKey("=", "="),
)

private val SYSTEM_SYMBOLS = listOf(
    SymbolKey("(", "("),
    SymbolKey(")", ")"),
    SymbolKey("=", "="),
    SymbolKey(";", ";"),
)

/**
 * 方程页。
 *
 * 结构与单位页同构：顶栏 → **额外操作行** → 显示区 → 键盘 → 底栏。
 *
 * 三条设计取舍：
 *
 * 1. **没有"求解"按钮**：结果是实时的（输入合法即出根），
 *    多一个确认键只会让人以为必须按它。键盘上的 `=`（求值）因此让位给未知数 `x`。
 *
 * 2. **字面等号 `=` 放在额外操作行**，与计算器的"求值 `=`"是两回事：
 *    前者插入一个字符，后者执行计算。二者配色也刻意不同。
 *
 * 3. **模式切换在顶栏**：一元与二元需要的符号键不同（`^` 对线性方程组无意义，
 *    而 `;` 只在一元下多余），额外行只有 4 个格位，放不下两套。
 */
@Composable
fun EquationScreen(
    state: EquationState,
    settings: CalcSettings,
    time: String,
    hapticsEnabled: Boolean,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalWatchMetrics.current
    val layout = remember(metrics.layout.round) { WatchLayout.equation(metrics.layout.round) }
    val haptics = rememberKeyHaptics(hapticsEnabled)

    val expressionScroll = rememberScrollState()
    val resultScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val outcome = remember(state.input, state.mode, settings.angleUnit) {
        solveEquation(state.input, state.mode, settings)
    }

    RotaryHandler(AppPage.EQUATION) { pixels ->
        when (
            rotaryTarget(
                expressionScrollable = expressionScroll.maxValue > 0,
                resultScrollable = resultScroll.maxValue > 0,
            )
        ) {
            RotaryTarget.EXPRESSION -> scope.launch { expressionScroll.scrollBy(-pixels) }
            RotaryTarget.RESULT -> scope.launch { resultScroll.scrollBy(-pixels) }
            // 本页只有一个键盘，没有分页可切：拧表冠时给一次触感，
            // 保证"拧了有反应"恒成立，而不是像坏了一样
            RotaryTarget.KEYPAD_PAGE -> haptics(false)
        }
    }

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
                // 模式切换**不放在顶栏**：顶栏弦长只有约 147du，时钟居中后
                // 两侧各余约 46du，塞不下一个 60du 的按钮——实测截图里它会压在时钟上，
                // 把 "10:24" 挤成 "10:2"。改为并入额外操作行的第一个格位。
                center = { ClockText(time) },
            )

            // ------------------------------------------------------ 额外操作行
            val extra = layout.extraRow
            if (extra != null) {
                Spacer(Modifier.height(scaled(extra.top - layout.header.bottom)))
                val symbols = if (state.mode == EquationMode.SINGLE) SINGLE_SYMBOLS else SYSTEM_SYMBOLS
                val gap = WatchLayout.KEY_GAP
                // 第一格是模式切换，其余是符号键
                val slots = symbols.size + 1
                val itemWidth = (extra.maxWidth - (slots - 1) * gap) / slots
                Row(
                    modifier = Modifier.width(scaled(extra.maxWidth)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(scaled(gap)),
                ) {
                    ModeChip(
                        label = state.mode.label,
                        widthDu = itemWidth,
                        heightDu = extra.height,
                        hapticsEnabled = hapticsEnabled,
                        onClick = { state.toggleMode() },
                    )
                    for (symbol in symbols) {
                        SymbolChip(
                            symbol = symbol,
                            widthDu = itemWidth,
                            heightDu = extra.height,
                            hapticsEnabled = hapticsEnabled,
                            onClick = { state.insert(symbol.insert) },
                        )
                    }
                }
                Spacer(Modifier.height(scaled(layout.display.top - extra.bottom)))
            } else {
                Spacer(Modifier.height(scaled(layout.display.top - layout.header.bottom)))
            }

            // ---------------------------------------------------------- 显示区
            CalcDisplay(
                expression = state.input,
                result = outcome.text,
                errorMessage = outcome.errorText,
                zone = layout.display,
                expressionScroll = expressionScroll,
                resultScroll = resultScroll,
                // 结果本身写成 "x = 1, 2"，不要再叠一个前缀
                resultPrefix = "",
            )

            // ------------------------------------------------------------ 键盘
            Spacer(Modifier.height(scaled(layout.keypad.top - layout.display.bottom)))
            Keypad(
                page = KeypadPage.MAIN,
                rows = layout.keyRows,
                hapticsEnabled = hapticsEnabled,
                onKey = state::onKey,
                angleUnit = settings.angleUnit,
                // 本页只有一套键位，没有分页可切
                keysOverride = EquationKeypad,
            )

            // ---------------------------------------------------------- 操作行
            Spacer(Modifier.height(scaled(layout.actionRow.top - layout.keypad.bottom)))
            EquationActionBar(
                zone = layout.actionRow,
                hapticsEnabled = hapticsEnabled,
                onClear = state::clear,
                onBackspace = state::backspace,
                onSqrt = { state.insert("√(") },
                onPi = { state.insert("π") },
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------- 求解

private data class EquationOutcome(val text: String, val errorText: String?)

private fun solveEquation(input: String, mode: EquationMode, settings: CalcSettings): EquationOutcome {
    if (input.isBlank()) return EquationOutcome("", null)
    return when (mode) {
        EquationMode.SINGLE -> when (val solution = EquationSolver.solve(input, settings.angleUnit)) {
            is EquationSolution.Solved -> EquationOutcome(rootsText(solution.roots), null)
            is EquationSolution.Failed -> EquationOutcome("", equationErrorText(solution.error))
        }

        EquationMode.SYSTEM -> when (val solution = LinearSystemSolver.solve(input, settings.angleUnit)) {
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

/**
 * 把根拼成一行。
 *
 * 用 `x = 1, 2, 3` 而不是带下标的 `x₁=1`：下标字符（U+2081…）在 MiSans 里未必齐全，
 * 一旦缺字就会显示成方框——而这类问题在代码评审时看不出来，只有截图能发现。
 *
 * **按数值升序排列**：求解器给出的次序是"判别式 +√D 在前"，
 * 于是整数根会显示成 `2, 1`，读起来别扭。排序是展示层的事，因此放在这里。
 * 精确形式优先于小数。
 */
private fun rootsText(roots: List<EquationRoot>): String {
    val ordered = roots.sortedWith(
        compareBy({ rootReal(it) }, { rootImaginary(it) }),
    )
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
    EquationError.EQUATION_COUNT -> "需要两个方程，用 ; 分隔"
    EquationError.IDENTITY -> "这是恒等式"
    EquationError.NO_SOLUTION -> "无解"
}

// ------------------------------------------------------------------ 小组件

/** 额外操作行第一个格位：模式切换（一元 / 二元） */
@Composable
private fun ModeChip(
    label: String,
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
        shape = RoundedCornerShape(percent = 50),
        fontSizeOverride = scaledSp(16f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = MaterialTheme.colorScheme.primary,
        contentOverride = MaterialTheme.colorScheme.onPrimary,
    )
}

/** 额外操作行里的符号键 */
@Composable
private fun SymbolChip(
    symbol: SymbolKey,
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
        glyphOverride = symbol.label,
        fontSizeOverride = scaledSp(20f),
        fontWeightOverride = FontWeight.Bold,
        containerOverride = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentOverride = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 方程页底栏：`C` / `⌫` / `√` / `π`。
 *
 * 后两个不是"分页"而是**插入符号**——它们在一元方程里常用，
 * 而额外操作行的 4 个格位已经被 `^ ( ) =` 占满。
 */
@Composable
private fun EquationActionBar(
    zone: Zone,
    hapticsEnabled: Boolean,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSqrt: () -> Unit,
    onPi: () -> Unit,
) {
    val haptics = rememberKeyHaptics(hapticsEnabled)
    val gap = scaled(WatchLayout.KEY_GAP)
    val itemWidth = (zone.maxWidth - 3 * WatchLayout.KEY_GAP) / 4f

    Box(
        modifier = Modifier
            .width(scaled(zone.maxWidth))
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            CalcKeyButton(
                key = CalcKey.Clear,
                widthDu = itemWidth,
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
                widthDu = itemWidth,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = { haptics(false); onBackspace() },
                icon = WatchVectorIcons.Backspace,
            )
            CalcKeyButton(
                key = CalcKey.SquareRoot,
                widthDu = itemWidth,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = {
                    haptics(false)
                    onSqrt()
                },
                glyphOverride = "√",
            )
            CalcKeyButton(
                key = CalcKey.Pi,
                widthDu = itemWidth,
                heightDu = zone.height,
                hapticsEnabled = hapticsEnabled,
                onKey = {
                    haptics(false)
                    onPi()
                },
                glyphOverride = "π",
            )
        }
    }
}
