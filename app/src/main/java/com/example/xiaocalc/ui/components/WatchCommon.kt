package com.example.xiaocalc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.xiaocalc.adaptive.Zone
import com.example.xiaocalc.adaptive.WatchLayout
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp
import com.example.xiaocalc.design.NumTextStyle

/**
 * 触感反馈。
 *
 * 走 Compose 的 [LocalHapticFeedback]（底层是 `View.performHapticFeedback`），
 * 因此**不需要** VIBRATE 权限，也不会被"未声明权限"静默吞掉；
 * 同时天然遵循系统的触感开关。
 *
 * @return 一个 `(strong: Boolean) -> Unit` 的回调：true 表示确认类强反馈
 */
@Composable
fun rememberKeyHaptics(enabled: Boolean): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, enabled) {
        { strong: Boolean ->
            if (enabled) {
                haptic.performHapticFeedback(
                    if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                )
            }
        }
    }
}

/**
 * 表盘顶部操作栏。
 *
 * 三个插槽（leading / center / trailing）都被约束在 [Zone.maxWidth] 内——
 * 圆形屏上该值由弦长给出，因此**永远不可能**把按钮推到表盘外，
 * 页面无需再为圆屏写额外内边距。
 */
@Composable
fun WatchHeader(
    zone: Zone,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(scaled(zone.height)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(scaled(zone.maxWidth)).fillMaxHeight()) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaled(4f)),
                content = leading,
            )
            Box(Modifier.align(Alignment.Center), content = center)
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaled(4f)),
                content = trailing,
            )
        }
    }
}

/**
 * 圆形图标按钮。
 *
 * [touchSize] 是**命中区**（尽量接近 40dp 的可点按体验），[glyphSize] 是视觉尺寸；
 * 圆形屏空间紧张，靠"命中区略大于视觉"来兼顾手感与构图。
 *
 * 命中区必须 ≤ 顶栏分区高度：`Modifier.size()` 会被父级约束收紧，
 * 否则圆角会被压成椭圆、命中面积也会缩水。
 */
@Composable
fun WatchIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    touchSize: Dp = scaled(WatchLayout.HEADER_ICON_TOUCH),
    glyphSize: Dp = scaled(22f),
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(glyphSize),
        )
    }
}

/**
 * 顶栏里的「文字图标」按钮（如 `C`）。
 *
 * 计算器的清空用文字字形比矢量图标更易识别，也省下一个 vector 资源；
 * 字形来自随包字体的子集，渲染结果可控（退格键则必须用矢量，见 [WatchVectorIcons]）。
 */
@Composable
fun WatchGlyphButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    touchSize: Dp = scaled(WatchLayout.HEADER_ICON_TOUCH),
    glyphSize: androidx.compose.ui.unit.TextUnit = scaledSp(21f),
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = tint,
            fontSize = glyphSize,
            fontWeight = FontWeight.Bold,
            style = NumTextStyle,
            maxLines = 1,
        )
    }
}

/** 顶栏里的时钟文本 */
@Composable
fun ClockText(time: String, modifier: Modifier = Modifier) {
    Text(
        text = time,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = scaledSp(24f),
        fontWeight = FontWeight.Medium,
        style = NumTextStyle,
        maxLines = 1,
    )
}

/** 页面标题（历史/设置/欢迎页使用） */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = scaledSp(30f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

// 统一图标来源，避免每个页面各写一套 import。
// 仅使用 material-icons-core 自带的图标集（不引 material-icons-extended，省 ~30MB 包体）。
object WatchIcons {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val More: ImageVector = Icons.Filled.MoreVert
    val Clear: ImageVector = Icons.Filled.Clear
    val Delete: ImageVector = Icons.Filled.Delete
    val Close: ImageVector = Icons.Filled.Close
    val Check: ImageVector = Icons.Filled.Check
}

/** 供空态使用的小号说明文字 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        modifier = modifier,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = scaledSp(20f),
        fontWeight = FontWeight.Medium,
        maxLines = 2,
    )
}
