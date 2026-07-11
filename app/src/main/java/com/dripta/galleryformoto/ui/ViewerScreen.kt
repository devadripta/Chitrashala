package com.dripta.galleryformoto.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dripta.galleryformoto.data.MediaItem
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenEditor: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentItem by remember { derivedStateOf { items.getOrNull(pagerState.currentPage) } }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddToDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showHideConfirmation by remember { mutableStateOf(false) }
    var showTagsRatingDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingNewName by remember { mutableStateOf("") }
    val albums by viewModel.albums.collectAsState()

    // Set the instant back is requested. Flipping this pauses any playing video (see the
    // isActive passed to VideoPlayer) so the exit transition never animates a live video
    // surface, which is what made closing a video stutter.
    var smoothBack by remember { mutableStateOf(false) }
    LaunchedEffect(smoothBack) {
        if (smoothBack) {
            // One frame for the pause to take effect, then hand back to the navigator.
            kotlinx.coroutines.delay(16)
            onBack()
        }
    }
    fun goBack() = run { smoothBack = true }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.refresh()
        goBack()
    }

    val renameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            currentItem?.let { item ->
                scope.launch {
                    viewModel.renameMedia(item.uri, pendingNewName)
                    viewModel.refresh()
                }
            }
        }
    }

    fun performRename(item: MediaItem, newName: String) {
        scope.launch {
            val intentSender = viewModel.renameMedia(item.uri, newName)
            if (intentSender != null) {
                pendingNewName = newName
                renameLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                viewModel.refresh()
            }
        }
    }

    fun deleteCurrent(uri: Uri) {
        scope.launch {
            val intentSender = viewModel.deleteMedia(listOf(uri))
            if (intentSender != null) {
                deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                viewModel.refresh()
                goBack()
            }
        }
    }

    fun shareCurrent(item: MediaItem) {
        scope.launch {
            try {
                val safeUri = com.dripta.galleryformoto.data.ExifStripper.stripForShare(
                    context, item.uri, item.mimeType, item.displayName
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, safeUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't share: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun nearbyShareCurrent(item: MediaItem) {
        scope.launch {
            try {
                val safeUri = com.dripta.galleryformoto.data.ExifStripper.stripForShare(
                    context, item.uri, item.mimeType, item.displayName
                )
                // Nearby Share has no stable public "launch directly" API; this targets the
                // well-known package/action it registers under. If that ever changes (or isn't
                // present on a given device), we fall back to the normal share sheet, where
                // Nearby Share still shows up as one of the targets anyway.
                val directIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, safeUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.google.android.gms")
                    action = "com.google.android.gms.nearby.sharing.SEND"
                }
                if (directIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(directIntent)
                } else {
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_STREAM, safeUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(fallback, "Share via"))
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't share: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun editCurrent(item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Edit with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No editor available", Toast.LENGTH_SHORT).show()
        }
    }

    fun onHide() {
        val item = currentItem ?: return
        val wasHidden = hiddenIds.contains(item.id)
        viewModel.toggleHidden(item.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = if (wasHidden) "Unhidden" else "Hidden",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            ).also { result ->
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.toggleHidden(item.id)
                }
            }
        }
        if (!wasHidden) {
            onBack()
        }
    }

    // Standard back handler to ensure we clean up
    androidx.activity.compose.BackHandler { goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 96.dp)) }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1
            ) { page ->
                // No AnimatedContent here: a page's item never changes, so it only added a
                // scale/fade pass over the content (which made video surfaces jank) for nothing.
                run {
                    val targetItem = items[page]
                    if (targetItem.isVideo) {
                        VideoPlayer(
                            uri = targetItem.uri,
                            isCurrentPage = page == pagerState.currentPage && !smoothBack,
                            chromeVisible = chromeVisible,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onExtractFrame = { positionMs ->
                                scope.launch {
                                    val frame = com.dripta.galleryformoto.data.VideoFrameExtractor.extractFrame(context, targetItem.uri, positionMs)
                                    if (frame != null) {
                                        val baseName = targetItem.displayName.substringBeforeLast('.', targetItem.displayName)
                                        com.dripta.galleryformoto.data.BitmapUtils.saveBitmapWithName(context, frame, "${baseName}_frame_${positionMs}ms.jpg")
                                        viewModel.refresh()
                                        Toast.makeText(context, "Saved still to Photos", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Couldn't extract that frame", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))
                        ZoomableAsyncImage(
                            model = targetItem.uri,
                            contentDescription = targetItem.displayName,
                            modifier = Modifier.fillMaxSize(),
                            state = rememberZoomableImageState(zoomableState),
                            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 3f),
                            onClick = { chromeVisible = !chromeVisible }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        val item = currentItem
                        if (item != null) {
                            val isFav = favoriteIds.contains(item.id)
                            val favScale by animateFloatAsState(
                                targetValue = if (isFav) 1.15f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.4f),
                                label = "favScale"
                            )
                            IconButton(
                                onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); viewModel.toggleFavorite(item.id) },
                                modifier = Modifier.scale(favScale)
                            ) {
                                Icon(
                                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    "Favorite",
                                    tint = if (isFav) Color.Red else Color.White
                                )
                            }
                            IconButton(onClick = { 
                                val wasHidden = hiddenIds.contains(item.id)
                                if (wasHidden) {
                                    onHide() // Unhiding doesn't need confirmation
                                } else {
                                    showHideConfirmation = true
                                }
                            }) {
                                Icon(Icons.Filled.VisibilityOff, "Hide", tint = Color.White)
                            }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, "More", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuExpanded = false
                                        pendingNewName = item.displayName
                                        showRenameDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Info") },
                                    onClick = {
                                        menuExpanded = false
                                        showInfoDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Info, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tags & rating") },
                                    onClick = {
                                        menuExpanded = false
                                        showTagsRatingDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Star, null) }
                                )
                                if (!item.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("Set as wallpaper") },
                                        onClick = {
                                            menuExpanded = false
                                            try {
                                                context.startActivity(
                                                    android.content.Intent.createChooser(
                                                        android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                                                            addCategory(android.content.Intent.CATEGORY_DEFAULT)
                                                            setDataAndType(item.uri, item.mimeType)
                                                            putExtra("mimeType", item.mimeType)
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        },
                                                        "Set as wallpaper"
                                                    )
                                                )
                                            } catch (e: android.content.ActivityNotFoundException) {
                                                android.widget.Toast.makeText(context, "No wallpaper app found", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Wallpaper, null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Edit with external app") },
                                    onClick = {
                                        menuExpanded = false
                                        editCurrent(item)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nearby Share") },
                                    onClick = {
                                        menuExpanded = false
                                        nearbyShareCurrent(item)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Share, null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Transparent,
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                        .padding(bottom = 16.dp)
                ) {
                    val item = currentItem
                    if (item != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                        ) {
                            ActionButton(
                                icon = Icons.Filled.Share,
                                label = "Share",
                                onClick = { shareCurrent(item) }
                            )
                            ActionButton(
                                icon = Icons.Filled.Edit,
                                label = "Edit",
                                onClick = { onOpenEditor(item) }
                            )
                            ActionButton(
                                icon = Icons.Filled.LibraryAdd,
                                label = "Add to",
                                onClick = { showAddToDialog = true }
                            )
                            ActionButton(
                                icon = Icons.Filled.Delete,
                                label = "Delete",
                                onClick = { showDeleteConfirmation = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        currentItem?.let { item ->
            var nameText by remember { mutableStateOf(item.displayName) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename") },
                text = {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("New name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        performRename(item, nameText)
                        showRenameDialog = false
                    }) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (showInfoDialog) {
        currentItem?.let { item ->
            InfoDialog(item = item, onDismiss = { showInfoDialog = false })
        }
    }

    if (showAddToDialog) {
        currentItem?.let { item ->
            AddToAlbumDialog(
                albums = albums,
                onFolderSelected = { folderPath ->
                    scope.launch {
                        viewModel.copyMedia(item.uri, folderPath)
                        viewModel.refresh()
                        showAddToDialog = false
                        Toast.makeText(context, "Copied to $folderPath", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showAddToDialog = false }
            )
        }
    }

    if (showDeleteConfirmation) {
        currentItem?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Move to Bin?") },
                text = { Text("This ${if (item.isVideo) "video" else "photo"} goes to the Bin. You can restore it within 30 days.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirmation = false
                        deleteCurrent(item.uri)
                    }) { Text("Move to Bin", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (showHideConfirmation) {
        currentItem?.let { item ->
            AlertDialog(
                onDismissRequest = { showHideConfirmation = false },
                title = { Text("Hide item?") },
                text = { Text("Move this ${if (item.isVideo) "video" else "photo"} to the Hidden album?") },
                confirmButton = {
                    TextButton(onClick = {
                        showHideConfirmation = false
                        onHide()
                    }) { Text("Hide") }
                },
                dismissButton = {
                    TextButton(onClick = { showHideConfirmation = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (showTagsRatingDialog) {
        currentItem?.let { item ->
            com.dripta.galleryformoto.ui.components.TagsRatingDialog(
                tags = viewModel.tagsForMedia(item.id),
                meta = viewModel.metaForMedia(item.id),
                onAddTag = { tag -> viewModel.addTag(item.id, tag) },
                onRemoveTag = { tag -> viewModel.removeTag(item.id, tag) },
                onSetRating = { rating -> viewModel.setRating(item.id, rating) },
                onSetColorLabel = { label -> viewModel.setColorLabel(item.id, label) },
                onDismiss = { showTagsRatingDialog = false }
            )
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, label, tint = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun InfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    val dateStr = remember(item.dateMillis) {
        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.dateMillis))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Information") },
        text = {
            Column {
                InfoRow("Name", item.displayName)
                InfoRow("Date", dateStr)
                InfoRow("Type", item.mimeType)
                InfoRow("Folder", item.bucketName)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AddToAlbumDialog(
    albums: List<com.dripta.galleryformoto.data.Album>,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder") },
        text = {
            LazyColumn {
                item {
                    DropdownMenuItem(
                        text = { Text("Create New Folder") },
                        onClick = { showNewFolderDialog = true },
                        leadingIcon = { Icon(Icons.Filled.LibraryAdd, null) }
                    )
                }
                items(albums) { album ->
                    DropdownMenuItem(
                        text = { Text(album.name) },
                        onClick = { onFolderSelected("Pictures/${album.name}") } // Simplified path
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onFolderSelected("Pictures/$folderName")
                            showNewFolderDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VideoPlayer(
    uri: Uri,
    // Deliberately not named isActive: inside a LaunchedEffect that name resolves to
    // CoroutineScope.isActive (always true) and silently shadows the parameter.
    isCurrentPage: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onExtractFrame: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(uri))
            playWhenReady = false
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isReady by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }

    // Auto-hide chrome after 3 seconds while playing
    LaunchedEffect(chromeVisible, isPlaying) {
        if (chromeVisible && isPlaying) {
            kotlinx.coroutines.delay(3000)
            onToggleChrome()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isReady = state == Player.STATE_READY
                if (isReady) duration = exoPlayer.duration
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(uri) {
        onDispose {
            try { exoPlayer.stop() } catch (_: Exception) {}
            exoPlayer.release()
        }
    }

    LaunchedEffect(isReady, isCurrentPage) {
        exoPlayer.playWhenReady = isReady && isCurrentPage
    }

    LaunchedEffect(isPlaying, isCurrentPage) {
        while (isPlaying && isCurrentPage) {
            if (!isSeeking) currentPosition = exoPlayer.currentPosition
            kotlinx.coroutines.delay(100)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // Inflated (not constructed) so it picks up surface_type=texture_view; that
                // attribute is only read from XML, and a SurfaceView would jank on transitions.
                val view = android.view.LayoutInflater.from(ctx)
                    .inflate(com.dripta.galleryformoto.R.layout.video_player_view, null) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Tap anywhere to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onToggleChrome() }
        )

        // Center play/pause, large beautiful circle
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.6f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                IconButton(
                    onClick = { exoPlayer.seekBack() },
                    modifier = Modifier.size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.FastRewind, "Rewind -10s",
                        tint = Color.White, modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.playWhenReady = true
                    },
                    modifier = Modifier.size(64.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = { exoPlayer.seekForward() },
                    modifier = Modifier.size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.FastForward, "Forward +10s",
                        tint = Color.White, modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom bar, sleek with time labels
        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { v ->
                            isSeeking = true
                            currentPosition = v.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentPosition)
                            isSeeking = false
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(
                            onClick = {
                                exoPlayer.pause()
                                onExtractFrame(currentPosition)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Save frame",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            formatDuration(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
