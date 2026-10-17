package com.example.xiaocalc.ui.components

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
import com.example.xiaocalc.adaptive.Disc
import com.example.xiaocalc.adaptive.scaled
import com.example.xiaocalc.adaptive.scaledSp

/**
 * 通用的全屏选择器。
 *
 * 手表上没有下拉菜单的位置，因此采用"M3 模态底部面板"的等价形态：
 * 遮罩 + 居中面板 + 可滚动列表。带当前项高亮。
 *
 * 面板尺寸由**几何约束**决定（见 [PICKER_WIDTH_DU]）：矩形四角必须落在表盘圆内，
 * 且要留出可见余量。圆屏上这是唯一不能靠"看起来差不多"蒙过去的地方——
 * 差一点点就是"角被切掉"，而截图里一眼能看出来。
 *
 * @param scrollState 由调用方持有，以便表冠也能滚动列表——手表上用小手指精确拖动 12 项不现实
 */
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
    /**
     * 标题右侧的附加控件。
     *
     * 单位选择器用它放"当前类别"——点一下钻进类别列表，选完再回到单位列表。
     * 用同一个面板做两级选择，比在表盘上再挤一个常驻的类别按钮省地方，
     * 也比把 71 个单位平铺成一张长列表好找。
     */
    titleAction: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 遮罩：点击空白处关闭
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

/**
 * 面板宽度（设计单位）。
 *
 * 取值由**几何约束**反推，不是拍脑袋：宽 [PICKER_WIDTH_DU]、高约
 * [PICKER_WIDTH_DU] 的面板要完整落在半径 [Disc.RADIUS] 的圆内，需满足
 * `√((W/2)² + (H/2)²) ≤ R × 安全系数`。
 *
 * 初版取了 280，算下来 187 对 192——只差 5du。实际截图里四角几乎贴上圆周，
 * 是那种"要切不切"的观感。现在按 0.90 的安全系数留出余量，
 * 并由 `PickerOverlayGeometryTest` 守住这条不等式。
 */
internal const val PICKER_WIDTH_DU = 250f

/** 列表最大高度，超出即滚动（表头与内边距另占约 56du） */
internal const val PICKER_LIST_MAX_DU = 176f

/** 面板高度的上界（表头 + 内边距 + 列表上限），几何测试用它校验四角落在圆内 */
internal const val PICKER_TOTAL_HEIGHT_DU = PICKER_LIST_MAX_DU + 56f
