package com.dripta.galleryformoto.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.components.MediaGrid
import com.dripta.galleryformoto.ui.components.SelectionTopBar
import kotlinx.coroutines.launch

enum class ScreenMode { ALL, ALBUM, FAVORITES, HIDDEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    title: String,
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    mode: ScreenMode,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onTrashClick: (() -> Unit)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showHideConfirmation by remember { mutableStateOf(false) }
    var showBatchEdit by remember { mutableStateOf(false) }
    var showCollage by remember { mutableStateOf(false) }
    val gridColumns by viewModel.settings.gridColumns.collectAsState()

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        selectedIds = emptySet()
        viewModel.refresh()
    }

    fun performDelete() {
        val toDelete = items.filter { selectedIds.contains(it.id) }
        scope.launch {
            val intentSender = viewModel.deleteMedia(toDelete.map { it.uri })
            if (intentSender != null) {
                deleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            } else {
                selectedIds = emptySet()
                viewModel.refresh()
            }
        }
    }

    fun performShare() {
        val toShare = items.filter { selectedIds.contains(it.id) }
        scope.launch {
            val safeUris = toShare.map { item ->
                com.dripta.galleryformoto.data.ExifStripper.stripForShare(
                    context, item.uri, item.mimeType, item.displayName
                )
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(safeUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        if (onSearchClick != null) {
                            IconButton(onClick = onSearchClick) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                        if (onTrashClick != null && mode == ScreenMode.ALL) {
                            IconButton(onClick = onTrashClick) {
                                Icon(Icons.Filled.Delete, contentDescription = "Trash")
                            }
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            val gridOptions = listOf(2, 3, 4, 5)
                            gridOptions.forEach { cols ->
                                DropdownMenuItem(
                                    text = { Text("$cols columns") },
                                    onClick = {
                                        viewModel.settings.setGridColumns(cols)
                                        menuExpanded = false
                                    },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = gridColumns == cols,
                                            onClick = null
                                        )
                                    }
                                )
                            }
                            val themeMode by viewModel.settings.themeMode.collectAsState()
                            val themeModes = listOf(
                                com.dripta.galleryformoto.data.ThemeMode.SYSTEM to "System",
                                com.dripta.galleryformoto.data.ThemeMode.LIGHT to "Light",
                                com.dripta.galleryformoto.data.ThemeMode.DARK to "Dark"
                            )
                            themeModes.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.settings.setThemeMode(mode)
                                        menuExpanded = false
                                    },
                                    leadingIcon = {
                                        RadioButton(selected = themeMode == mode, onClick = null)
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    hideLabel = if (mode == ScreenMode.HIDDEN) "Unhide" else "Hide",
                    onClose = { selectedIds = emptySet() },
                    onFavorite = {
                        viewModel.setFavoriteForIds(selectedIds, mode != ScreenMode.FAVORITES)
                        selectedIds = emptySet()
                    },
                    onHide = {
                        if (mode == ScreenMode.HIDDEN) {
                            // Unhiding doesn't usually need confirmation, but Hiding does.
                            viewModel.setHiddenForIds(selectedIds, false)
                            selectedIds = emptySet()
                        } else {
                            showHideConfirmation = true
                        }
                    },
                    onDelete = { showDeleteConfirmation = true },
                    onShare = { performShare() },
                    onBatchEdit = { showBatchEdit = true },
                    onCollage = if (selectedIds.size in 2..6) { { showCollage = true } } else null
                )
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val (icon, message) = when (mode) {
                        ScreenMode.FAVORITES -> Icons.Outlined.FavoriteBorder to "Tap the heart on any photo\nto add it here"
                        ScreenMode.HIDDEN -> Icons.Outlined.VisibilityOff to "Nothing hidden yet"
                        else -> Icons.Outlined.PhotoLibrary to "No photos or videos yet"
                    }
                    Icon(
                        icon, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                MediaGrid(
                    items = items,
                    selectedIds = selectedIds,
                    columns = gridColumns,
                    state = gridState,
                    header = header,
                    onClick = { item ->
                        if (selectedIds.isNotEmpty()) {
                            selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                        } else {
                            onItemClick(items, items.indexOf(item))
                        }
                    },
                    onLongClick = { item ->
                        selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                    },
                    onDoubleTapFavorite = { item -> viewModel.toggleFavorite(item.id) },
                    onSwipeToHide = { item -> viewModel.toggleHidden(item.id) }
                )
                if (mode == ScreenMode.ALL) {
                    com.dripta.galleryformoto.ui.components.TimelineScrubber(
                        items = items,
                        gridState = gridState,
                        hasLeadingHeader = header != null,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Move ${selectedIds.size} items to Bin?") },
            text = { Text("You can restore them from the Bin within 30 days.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    performDelete()
                }) { Text("Move to Bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showHideConfirmation) {
        AlertDialog(
            onDismissRequest = { showHideConfirmation = false },
            title = { Text("Hide items?") },
            text = { Text("Move ${selectedIds.size} items to the Hidden album? You will need to unlock it to see them again.") },
            confirmButton = {
                TextButton(onClick = {
                    showHideConfirmation = false
                    viewModel.setHiddenForIds(selectedIds, true)
                    selectedIds = emptySet()
                }) { Text("Hide") }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showBatchEdit) {
        val toEdit = items.filter { selectedIds.contains(it.id) }
        if (toEdit.isNotEmpty()) {
            BatchEditDialog(
                items = toEdit,
                onDone = {
                    showBatchEdit = false
                    selectedIds = emptySet()
                    viewModel.refresh()
                },
                onCancel = { showBatchEdit = false }
            )
        }
    }

    if (showCollage) {
        val toCollage = items.filter { selectedIds.contains(it.id) && !it.isVideo }
        if (toCollage.size >= 2) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCollage = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                CollageScreen(
                    items = toCollage,
                    onSaved = {
                        showCollage = false
                        selectedIds = emptySet()
                        viewModel.refresh()
                    },
                    onCancel = { showCollage = false }
                )
            }
        } else {
            showCollage = false
        }
    }
}
