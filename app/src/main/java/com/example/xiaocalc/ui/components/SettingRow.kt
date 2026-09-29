package com.example.xiaocalc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp

/**
 * 设置项卡片：左标题/说明 + 右开关。
 *
 * 使用 MD3 的 [Switch]（自带状态动画与无障碍语义），但放在一个**缩放过的密度**里：
 * MD3 开关默认按 48dp 命中区设计，在 240dp 的表盘上会占据太多空间，
 * 这里统一缩到约 0.68，兼顾"仍是标准 MD3 组件"与手表上的密度。
 */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(20f)))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Switch, onClick = { onToggle(!checked) })
            .padding(
                start = scaled(18f),
                end = scaled(8f),
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
        DensityScale(SWITCH_DENSITY_SCALE) {
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
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
