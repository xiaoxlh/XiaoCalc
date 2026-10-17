package com.xiaoxlh.xiaocalc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaoxlh.xiaocalc.adaptive.Disc
import com.xiaoxlh.xiaocalc.adaptive.scaled
import com.xiaoxlh.xiaocalc.adaptive.scaledSp

@Composable
fun <T> PickerOverlay(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,

    titleAction: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier.width(scaled(PICKER_WIDTH_DU)),
            shape = RoundedCornerShape(scaled(26f)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(vertical = scaled(8f))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = scaled(18f),
                            end = scaled(14f),
                            top = scaled(6f),
                            bottom = scaled(8f),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = scaledSp(19f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    titleAction?.invoke()
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = scaled(PICKER_LIST_MAX_DU))
                        .verticalScroll(scrollState),
                ) {
                    options.forEach { option ->
                        PickerItem(
                            text = label(option),
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = text, onClick = onClick)
            .padding(horizontal = scaled(18f), vertical = scaled(9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = scaledSp(21f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (selected) {
            Icon(
                imageVector = WatchIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = scaled(10f))
                    .size(scaled(20f)),
            )
        }
    }
}

internal const val PICKER_WIDTH_DU = 250f

internal const val PICKER_LIST_MAX_DU = 176f

internal const val PICKER_TOTAL_HEIGHT_DU = PICKER_LIST_MAX_DU + 56f
