package com.example.xiaocalc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp

/**
 * 设置项卡片：左标题/说明 + 右开关。
 *
 * 改用**官方手表组件** [SwitchButton]，而不是把常规 MD3 开关塞进一个缩放过的密度里。
 *
 * 旧做法（`DensityScale(0.68f)` + 常规 `Switch`）是个不得已的 hack：
 * MD3 开关按 48dp 命中区设计，在 240dp 的表盘上占太多空间，只能整体缩小；
 * 而缩小后的命中区与字号都不再符合任何一套规范。
 *
 * [SwitchButton] 是为手表尺寸设计的，自带 label / secondaryLabel 插槽，
 * 并带 Expressive 的按压形变（`SurfaceTransformation`）——
 * 这些都不是"看起来差不多"，而是规范里就有的东西。
 */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onToggle,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** 在局部把 dp 的物理换算整体缩小，使内嵌的 MD3 组件贴合手表尺度 */
@Composable
fun DensityScale(factor: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density * factor,
            fontScale = density.fontScale,
        ),
        content = content,
    )
}

/**
 * 设置项卡片：左标题/说明 + 右侧色块，点击**循环切换**取值。
 *
 * 为什么不做成下拉选择器：设置页的列表已经由表冠驱动滚动，
 * 再叠一个可滚动浮层会出现"表冠到底在滚哪个"的歧义。
 * 主题只有 8 个、且是低频设置，循环点击的代价可以接受，
 * 而右侧色块让"当前是什么、下一步会变成什么"一眼可见。
 */
@Composable
fun ChoiceRow(
    title: String,
    description: String,
    swatch: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(20f)))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(
                start = scaled(18f),
                end = scaled(18f),
                top = scaled(8f),
                bottom = scaled(8f),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(scaled(2f)))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = scaledSp(15f),
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(scaled(24f))
                .clip(CircleShape)
                .background(swatch),
        )
    }
}

private const val SWITCH_DENSITY_SCALE = 0.68f

/** 分组标题 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = scaledSp(19f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/**
 * 设置项卡片：左标题 + 右当前取值，下面一条**官方手表滑杆**。
 *
 * 数值类设置（小数位数）用开关表达不了，用循环点击又要连点十几次，
 * 因此在表盘上直接拖一条滑杆最省事——[Slider] 自带 ± 图标，
 * 只想调一格时也不必精确拖动。
 *
 * 只有当 [steps] 超过 `SliderDefaults.MaxSegmentSteps`（8）时才会退化成
 * 连续滑杆；小数位有 17 档，正是那种情况——17 段小方块挤在 160dp 宽的表盘上
 * 根本分辨不出来，连续滑杆反而更好用。
 */
@Composable
fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaled(18f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = scaledSp(23f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = scaledSp(21f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            valueRange = valueRange,
        )
    }
}
