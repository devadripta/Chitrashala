package com.dripta.galleryformoto.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.Album
import com.dripta.galleryformoto.ui.components.SmartAlbumsSection
import kotlinx.coroutines.launch
import java.io.File
import android.os.Environment
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    viewModel: GalleryViewModel,
    onAlbumClick: (Album) -> Unit,
    onPlacesClick: () -> Unit,
    onFoldersClick: () -> Unit,
    onTrashClick: () -> Unit,
    onSmartAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var albumToDelete by remember { mutableStateOf<Album?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.refresh() }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Albums") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create Folder")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item(span = { GridItemSpan(2) }) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onPlacesClick,
                        label = { Text("Places") },
                        leadingIcon = { Icon(Icons.Filled.Place, null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    AssistChip(
                        onClick = onFoldersClick,
                        label = { Text("Folders") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    AssistChip(
                        onClick = onTrashClick,
                        label = { Text("Bin") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            item(span = { GridItemSpan(2) }) {
                var allCategories by remember { mutableStateOf(listOf<String>()) }
                LaunchedEffect(Unit) {
                    allCategories = viewModel.getAllCategories()
                }
                if (allCategories.isNotEmpty()) {
                    SmartAlbumsSection(
                        categories = allCategories,
                        onCategoryClick = onSmartAlbumClick
                    )
                }
            }
            items(albums, key = { it.id }) { album ->
                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val tileScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                    ),
                    label = "albumTileScale"
                )
                Column(
                    modifier = Modifier
                        .animateItem()
                        .padding(8.dp)
                        .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = { onAlbumClick(album) },
                            onLongClick = { albumToDelete = album }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            imageLoader = LocalImageLoader.current
                        )
                    }
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "${album.itemCount} items",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (albumToDelete != null) {
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            title = { Text("Delete Album?") },
            text = { Text("This will delete all ${albumToDelete?.itemCount} items in '${albumToDelete?.name}'.") },
            confirmButton = {
                TextButton(onClick = {
                    val albumId = albumToDelete?.id ?: return@TextButton
                    scope.launch {
                        val intentSender = viewModel.deleteAlbum(albumId)
                        if (intentSender != null) {
                            deleteLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                        albumToDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { albumToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val newDir = File(picturesDir, folderName)
                        if (!newDir.exists()) {
                            val created = newDir.mkdirs()
                            if (created) {
                                Toast.makeText(context, "Folder created. Add photos to see it here.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Folder already exists", Toast.LENGTH_SHORT).show()
                        }
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}
