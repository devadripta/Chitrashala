package com.dripta.galleryformoto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.data.groupByDate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A right-edge fast-scroll rail: drag anywhere along it to jump through [items] (which must already
 * be sorted, newest first, matching the grid's own order), with a floating date bubble showing where
 * you'd land. Only rendered when there's enough content to make fast-scrolling worthwhile.
 *
 * [MediaGrid] inserts one full-span date-header cell before every date group (and one more for its
 * own optional [hasLeadingHeader] slot), so a plain `items.indexOf` would drift further out of sync
 * with every group boundary. This replicates that exact same grouping to compute the real
 * [LazyGridState] item index to scroll to.
 */
@Composable
fun TimelineScrubber(
    items: List<MediaItem>,
    gridState: LazyGridState,
    hasLeadingHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (items.size < 40) return

    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var lastTickLabel by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("MMM yyyy", Locale.getDefault()) }

    // gridIndexOfMediaIndex[i] = the actual LazyVerticalGrid item index that items[i] renders at.
    val gridIndexOfMediaIndex = remember(items, hasLeadingHeader) {
        val grouped = items.groupByDate()
        val mapping = IntArray(items.size)
        var gridIndex = if (hasLeadingHeader) 1 else 0
        var mediaIndex = 0
        for ((date, group) in grouped) {
            if (date.isNotEmpty()) gridIndex++ // the date header cell itself
            for (i in group.indices) {
                mapping[mediaIndex] = gridIndex
                gridIndex++
                mediaIndex++
            }
        }
        mapping
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(28.dp)) {
        val trackHeightPx = constraints.maxHeight.toFloat()

        fun jumpTo(fraction: Float) {
            val clamped = fraction.coerceIn(0f, 1f)
            dragFraction = clamped
            val mediaIndex = (clamped * (items.size - 1)).toInt().coerceIn(0, items.size - 1)
            val label = formatter.format(items[mediaIndex].dateMillis)
            if (label != lastTickLabel) {
                lastTickLabel = label
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            scope.launch { gridState.scrollToItem(gridIndexOfMediaIndex[mediaIndex]) }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .pointerInput(items) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            jumpTo(offset.y / trackHeightPx)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            jumpTo(change.position.y / trackHeightPx)
                        }
                    )
                }
        )

        if (isDragging) {
            val index = (dragFraction * (items.size - 1)).toInt().coerceIn(0, items.size - 1)
            val label = remember(index) { formatter.format(items[index].dateMillis) }
            val bubbleY = (dragFraction * trackHeightPx)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bubbleWidthPx = with(density) { 140.dp.toPx() }
            val bubbleHalfHeightPx = with(density) { 20.dp.toPx() }

            Surface(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((-bubbleWidthPx).toInt(), (bubbleY - bubbleHalfHeightPx).toInt()) },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
