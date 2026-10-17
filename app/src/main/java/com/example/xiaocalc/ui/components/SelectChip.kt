package com.example.xiaocalc.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.calc.CalcKey

/**
 * 选择胶囊的层级。
 *
 * 单位/货币页顶部那一行原先三个按钮等大等亮，读起来像三个并列的开关，
 * 但它们的地位完全不同：**起算/目标单位是主要交互（每次换算都在看它），
 * 类别与汇率日期只是上下文**。用同一副面孔会让用户不知道该先点哪个。
 *
 * 两级即可，不做三级——手表上一行只放得下三四个格子，再细分就分辨不出来了。
 */
enum class ChipEmphasis {
    /** 主要项：最亮的中性色容器 + 主文字色 + 更大更粗的字 */
    PRIMARY,

    /** 次要项：下沉一档的容器 + 变体文字色 + 更小的字 */
    SECONDARY,
}

/**
 * 选择胶囊。
 *
 * 复用 [CalcKeyButton] 以继承按压缩放、涟漪与触感；形状用胶囊（50% 圆角），
 * 与键盘上按键的圆角矩形区分开——"点它会打开选择器"而不是"按下即生效"。
 *
 * @param label     胶囊上显示的字形（单位符号 `m`、币种代码 `CNY`、或中文的类别名）
 * @param description 读屏软件念的完整名称。只画符号时这一项是必需的，
 *                    否则读屏会把 `ft` 念成两个字母而不是"英尺"。
 */
@Composable
fun SelectChip(
    label: String,
    description: String,
    widthDu: Float,
    heightDu: Float,
    emphasis: ChipEmphasis,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** 是否在右侧画一个小三角，表明"点它会展开一个列表" */
    showChevron: Boolean = false,
) {
    CalcKeyButton(
        // 胶囊不是按键，[CalcKey] 只是 CalcKeyButton 的入参形态；
        // 颜色、字号、无障碍描述在这里全部被覆盖，kind 不参与任何决策。
        key = CalcKey.PageMain,
        widthDu = widthDu,
        heightDu = heightDu,
        hapticsEnabled = hapticsEnabled,
        onKey = { onClick() },
        modifier = modifier,
        glyphOverride = label,
        icon = icon,
        description = description,
        fontSizeOverride = emphasis.fontSizeFor(label),
        fontWeightOverride = FontWeight.Bold,
        shape = RoundedCornerShape(percent = 50),
        containerOverride = when (emphasis) {
            ChipEmphasis.PRIMARY -> MaterialTheme.colorScheme.surfaceContainerHighest
            ChipEmphasis.SECONDARY -> MaterialTheme.colorScheme.surfaceContainer
        },
        contentOverride = when (emphasis) {
            ChipEmphasis.PRIMARY -> MaterialTheme.colorScheme.onSurface
            ChipEmphasis.SECONDARY -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        trailingIcon = if (showChevron) WatchVectorIcons.ChevronDown else null,
    )
}

/**
 * 字号随层级与字数自适应。
 *
 * 主要项放得下两三个字符（`m`、`ft`、`km/h`），四个字符时必须让位，
 * 否则圆屏最外侧的胶囊会把字挤出边界。次要项天生小一档。
 */
@Composable
private fun ChipEmphasis.fontSizeFor(label: String): TextUnit = when (this) {
    ChipEmphasis.PRIMARY -> scaledSp(
        when (label.length) {
            0, 1, 2 -> 21f
            3 -> 18f
            else -> 15f
        },
    )

    ChipEmphasis.SECONDARY -> scaledSp(if (label.length <= 3) 15f else 12f)
}
