package com.dripta.galleryformoto.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.components.MemoriesRow

@Composable
fun PhotosScreen(
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    onSearchClick: () -> Unit,
    onCleanupClick: () -> Unit,
    onStoriesClick: () -> Unit,
    onTrashClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalCleanup by viewModel.totalCleanupCount.collectAsState()
    val stories by viewModel.stories.collectAsState()
    var cleanupBannerVisible by remember { mutableStateOf(false) }
    var storiesBannerVisible by remember { mutableStateOf(false) }
    var cleanupBannerShow by remember { mutableStateOf(false) }
    var storiesBannerShow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshCleanupSuggestions()
    }

    LaunchedEffect(totalCleanup) {
        if (totalCleanup > 0) {
            cleanupBannerVisible = true
            kotlinx.coroutines.delay(250)
            cleanupBannerShow = true
        } else {
            cleanupBannerShow = false
            cleanupBannerVisible = false
        }
    }

    LaunchedEffect(stories.size) {
        if (stories.isNotEmpty()) {
            storiesBannerVisible = true
            kotlinx.coroutines.delay(500)
            storiesBannerShow = true
        } else {
            storiesBannerShow = false
            storiesBannerVisible = false
        }
    }

    MediaListScreen(
        title = "Photos",
        items = items,
        viewModel = viewModel,
        mode = ScreenMode.ALL,
        onItemClick = onItemClick,
        onSearchClick = onSearchClick,
        onTrashClick = onTrashClick,
        modifier = modifier,
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                AnimatedVisibility(
                    visible = cleanupBannerVisible && cleanupBannerShow,
                    enter = expandVertically(spring(dampingRatio = 0.6f)) + fadeIn(tween(300)),
                    exit = shrinkVertically(spring(dampingRatio = 0.8f)) + fadeOut(tween(200))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable(onClick = onCleanupClick),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AutoDelete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart Cleanup", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "You have $totalCleanup items to review",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    cleanupBannerShow = false
                                    cleanupBannerVisible = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = storiesBannerVisible && storiesBannerShow,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.5f)) + fadeIn(tween(400)),
                    exit = shrinkVertically(spring(dampingRatio = 0.8f)) + fadeOut(tween(200))
                ) {
                    val latestStory = stories.firstOrNull()
                    if (latestStory != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable(onClick = onStoriesClick),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("New Story", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        latestStory.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        storiesBannerShow = false
                                        storiesBannerVisible = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                MemoriesRow(
                    items = items,
                    onMemoryClick = { memoryItems ->
                        viewModel.setActiveViewerList(memoryItems)
                        onItemClick(memoryItems, 0)
                    }
                )
            }
        }
    )
}
