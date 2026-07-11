package com.dripta.galleryformoto.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.data.groupByDate
import com.dripta.galleryformoto.ui.LocalImageLoader
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    selectedIds: Set<Long>,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    state: LazyGridState = rememberLazyGridState(),
    showHeaders: Boolean = true,
    header: @Composable (() -> Unit)? = null,
    shimmer: Boolean = false,
    onDoubleTapFavorite: ((MediaItem) -> Unit)? = null,
    onSwipeToHide: ((MediaItem) -> Unit)? = null
) {
    val grouped = remember(items, showHeaders) {
        if (showHeaders) items.groupByDate() else mapOf("" to items)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize()
    ) {
        if (shimmer && items.isEmpty()) {
            items(12) {
                ShimmerThumbnail()
            }
        }
        if (header != null) {
            item(span = { GridItemSpan(columns) }) {
                header()
            }
        }
        grouped.forEach { (date, mediaItems) ->
            if (date.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }, key = "header_$date") {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(mediaItems, key = { _, item -> item.id }) { _, item ->
                MediaThumbnail(
                    item = item,
                    selected = selectedIds.contains(item.id),
                    selectionActive = selectedIds.isNotEmpty(),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onDoubleClick = onDoubleTapFavorite?.let { callback -> { callback(item) } },
                    onSwipeToHide = onSwipeToHide?.let { callback -> { callback(item) } },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    item: MediaItem,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onSwipeToHide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showHeartBurst by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var hidden by remember { mutableStateOf(false) }
    var hasTriggeredSwipeHaptic by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 96.dp.toPx() }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (hidden) tween(200) else spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset"
    )
    val swipeAlpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(200),
        label = "swipeAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.6f),
        label = "thumbScale"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (selected) 0.3f else 0f,
        animationSpec = tween(150),
        label = "overlayAlpha"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.3f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.4f),
        label = "checkScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = tween(150),
        label = "borderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(150),
        label = "borderColor"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .scale(scale)
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer { alpha = swipeAlpha }
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .then(
                if (onSwipeToHide != null && !selectionActive) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            offsetX += delta
                            val pastThreshold = abs(offsetX) > swipeThresholdPx
                            if (pastThreshold && !hasTriggeredSwipeHaptic) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                hasTriggeredSwipeHaptic = true
                            } else if (!pastThreshold) {
                                hasTriggeredSwipeHaptic = false
                            }
                        },
                        onDragStopped = {
                            if (abs(offsetX) > swipeThresholdPx) {
                                hidden = true
                                scope.launch {
                                    kotlinx.coroutines.delay(150)
                                    onSwipeToHide()
                                }
                            } else {
                                offsetX = 0f
                            }
                        }
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onDoubleClick = onDoubleClick?.let { callback ->
                    {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        callback()
                        showHeartBurst = true
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (borderWidth > 0.dp) Modifier.background(borderColor) else Modifier
                )
                .padding(borderWidth)
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .size(512)
                    .precision(coil.size.Precision.INEXACT)
                    .crossfade(150)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                imageLoader = LocalImageLoader.current,
                loading = { ShimmerPlaceholder() }
            )
            if (item.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = formatDuration(item.durationMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            if (selectionActive && overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .graphicsLayer { scaleX = checkScale; scaleY = checkScale }
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
                )
            }

            AnimatedVisibility(
                visible = showHeartBurst,
                modifier = Modifier.align(Alignment.Center),
                enter = scaleIn(initialScale = 0.3f, animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.35f)) + fadeIn(tween(100)),
                exit = fadeOut(tween(250))
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Added to favorites",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun ShimmerThumbnail() {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
    ) {
        ShimmerPlaceholder()
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                    start = androidx.compose.ui.geometry.Offset(shimmerOffset * 1000f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmerOffset * 1000f + 1000f, 0f)
                )
            )
    )
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%d:%02d".format(minutes, seconds)
}
