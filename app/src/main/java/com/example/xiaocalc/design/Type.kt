package com.example.xiaocalc.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.xiaocalc.R

/**
 * MiSans 静态多字重（已子集化，仅保留应用实际渲染字符）。
 * 四个字面按真实字重登记，Compose 依 [FontWeight] 精确命中，无需合成加粗。
 */
val XiaoCalcFontFamily = FontFamily(
    Font(R.font.misans_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(R.font.misans_medium, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(R.font.misans_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(R.font.misans_heavy, weight = FontWeight.Black, style = FontStyle.Normal),
)

/**
 * MiSans 的真实行高比 = (hhea ascent 1044 + |descent 282|) / unitsPerEm 1000。
 *
 * 单行文本需要 `字号 × 该系数` 的纵向空间，否则下伸字形（逗号、括号、√）
 * 会被 `horizontalScroll` 之类的裁剪容器切掉。布局与测试共用此常量。
 */
const val MISANS_LINE_HEIGHT_RATIO = 1.326f

/**
 * 数字排版特性。
 *
 * 只启用 `tnum`（等宽数字）——输入与结果刷新时字宽不跳动，这是计算器的关键体验。
 *
 * 刻意**不**启用 MiSans 的 `ss01`：该特性会把千分位逗号替换成圆点、
 * 把 `÷` 替换成"圆点除号"，在计算器语境下会造成
 * `8.369.910` 与小数、`÷` 与 `+` 的歧义（截图复核时实际观察到过）。
 */
const val NUM_FEATURES = "\"tnum\" 1"

/** 仅携带数字特性的样式，供 `Text(style = NumTextStyle)` 叠加使用 */
val NumTextStyle = TextStyle(fontFeatureSettings = NUM_FEATURES)

/**
 * 把默认排版条目改成 MiSans，并**清掉 M3 自带的固定行高与字距**。
 *
 * 这一步很关键：M3 的 `bodyMedium.lineHeight` 是固定的 20sp，与本应用"用设计坐标
 * 驱动字号"的做法冲突——同一行文本在 10sp 与 44sp 下都会被撑到 20sp 行框，
 * 既让单行文本虚占高度（实测把欢迎页的主按钮挤成 83×49 的椭圆），
 * 也让 `CalcDisplay` 按 1.326em 推算的行高失去意义。
 *
 * 置为 `Unspecified` 后，行高回到字体自身的 ascender/descender = 1.326em，
 * 与 [com.example.xiaocalc.ui.components.CalcDisplay] 的推算一致。
 */
private fun TextStyle.misans() = copy(
    fontFamily = XiaoCalcFontFamily,
    lineHeight = TextUnit.Unspecified,
    letterSpacing = TextUnit.Unspecified,
)

/**
 * 全局字体基线：所有 MD3 组件的默认字体族都指向 MiSans。
 * 注意：应用自身的排版尺寸全部由设计坐标（见 adaptive 包）驱动，
 * 这里只负责"字体族 + 兜底尺寸"，不参与具体屏布局。
 */
val XiaoCalcTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.misans(),
        displayMedium = displayMedium.misans(),
        displaySmall = displaySmall.misans(),
        headlineLarge = headlineLarge.misans(),
        headlineMedium = headlineMedium.misans(),
        headlineSmall = headlineSmall.misans(),
        titleLarge = titleLarge.misans(),
        titleMedium = titleMedium.misans(),
        titleSmall = titleSmall.misans(),
        bodyLarge = bodyLarge.misans(),
        bodyMedium = bodyMedium.misans(),
        bodySmall = bodySmall.misans(),
        labelLarge = labelLarge.misans(),
        labelMedium = labelMedium.misans(),
        labelSmall = labelSmall.misans(),
    )
}

/** 时间/时钟排版：等宽数字，避免每分钟宽度跳动 */
val ClockTextStyle = TextStyle(
    fontFamily = XiaoCalcFontFamily,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = NUM_FEATURES,
    fontSize = 14.sp,
)
