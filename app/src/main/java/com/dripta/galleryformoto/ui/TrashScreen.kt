package com.dripta.galleryformoto.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.components.MediaGrid
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<MediaItem>,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.refresh()
        selectedIds = emptySet()
    }

    fun performRestore() {
        selectedIds.forEach { id ->
            viewModel.restoreFromTrash(id)
        }
        selectedIds = emptySet()
    }

    fun performPermanentDelete() {
        scope.launch {
            val intentSender = viewModel.permanentlyDeleteTrashed(selectedIds.toList())
            if (intentSender != null) {
                deleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            } else {
                viewModel.refresh()
                selectedIds = emptySet()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (selectedIds.isEmpty()) "Bin" else "${selectedIds.size} selected") },
                navigationIcon = {
                    IconButton(onClick = if (selectedIds.isEmpty()) onBack else { { selectedIds = emptySet() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { performRestore() }) {
                            Icon(Icons.Filled.Restore, contentDescription = "Restore")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Delete permanently")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Bin is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            MediaGrid(
                items = items,
                selectedIds = selectedIds,
                modifier = Modifier.padding(padding),
                onClick = { item ->
                    if (selectedIds.isNotEmpty()) {
                        selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                    }
                },
                onLongClick = { item ->
                    selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
                },
                showHeaders = false
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete permanently?") },
            text = { Text("These items will be permanently removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    performPermanentDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
