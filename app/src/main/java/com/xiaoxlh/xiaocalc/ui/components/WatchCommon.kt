package com.xiaoxlh.xiaocalc.ui.components

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
import com.xiaoxlh.xiaocalc.adaptive.Zone
import com.xiaoxlh.xiaocalc.adaptive.WatchLayout
import com.xiaoxlh.xiaocalc.adaptive.scaled
import com.xiaoxlh.xiaocalc.adaptive.scaledSp
import com.xiaoxlh.xiaocalc.design.NumTextStyle

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

object WatchIcons {
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val More: ImageVector = Icons.Filled.MoreVert
    val Clear: ImageVector = Icons.Filled.Clear
    val Delete: ImageVector = Icons.Filled.Delete
    val Close: ImageVector = Icons.Filled.Close
    val Check: ImageVector = Icons.Filled.Check
}

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
