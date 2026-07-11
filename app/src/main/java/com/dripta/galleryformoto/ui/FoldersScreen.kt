package com.dripta.galleryformoto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.FolderNode
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.data.buildFolderTree
import com.dripta.galleryformoto.data.findNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: GalleryViewModel,
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allMedia by viewModel.visibleMedia.collectAsState()
    val tree = remember(allMedia) { allMedia.buildFolderTree() }
    val node = remember(tree, currentPath) { tree.findNode(currentPath) }

    val crumbs = remember(currentPath) {
        if (currentPath.isEmpty()) listOf("Folders" to "")
        else listOf("Folders" to "") + currentPath.split('/').runningReduce { acc, seg -> "$acc/$seg" }
            .mapIndexed { i, path -> currentPath.split('/')[i] to path }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(node?.name.takeUnless { it.isNullOrEmpty() } ?: "Folders") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(crumbs) { (label, path) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (path == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToPath(path) }
                            )
                            if (path != crumbs.last().second) {
                                Text(" > ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = node,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
            },
            label = "folderContent"
        ) { targetNode ->
            when {
                targetNode == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This folder no longer has any photos", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                targetNode.subfolders.isEmpty() && targetNode.directItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Empty folder", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (targetNode.subfolders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Folders",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(targetNode.subfolders, span = { GridItemSpan(1) }, key = { it.path }) { subfolder ->
                                FolderTile(
                                    subfolder = subfolder,
                                    onClick = { onNavigateToPath(subfolder.path) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        if (targetNode.directItems.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Photos & videos here",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(targetNode.directItems, span = { GridItemSpan(1) }, key = { it.id }) { mediaItem ->
                                DirectItemTile(
                                    item = mediaItem,
                                    onClick = { onItemClick(targetNode.directItems, targetNode.directItems.indexOf(mediaItem)) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTile(subfolder: FolderNode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "folderTileScale"
    )
    Column(
        modifier = modifier
            .padding(6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
            val cover = subfolder.totalItems.firstOrNull()
            if (cover != null) {
                AsyncImage(
                    model = cover.uri,
                    contentDescription = subfolder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            subfolder.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text("${subfolder.totalItems.size} items", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DirectItemTile(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "directItemScale"
    )
    Box(
        modifier = modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
