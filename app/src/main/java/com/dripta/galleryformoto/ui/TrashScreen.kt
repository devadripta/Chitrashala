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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var showEmptyBinConfirmation by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val allSelected = items.isNotEmpty() && selectedIds.size == items.size

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // The system dialog does not tell us whether the user confirmed, so reconcile the
        // trash table against what actually still exists rather than assuming success.
        viewModel.purgeMissingTrashEntries()
        selectedIds = emptySet()
    }

    fun performRestore() {
        viewModel.restoreAllFromTrash(selectedIds.toList())
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

    fun performEmptyBin() {
        scope.launch {
            val intentSender = viewModel.emptyTrash()
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
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (allSelected) "Deselect all" else "Select all") },
                                onClick = {
                                    menuExpanded = false
                                    selectedIds = if (allSelected) emptySet() else items.map { it.id }.toSet()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore all") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.restoreAllFromTrash(items.map { it.id })
                                    selectedIds = emptySet()
                                },
                                leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Empty Bin") },
                                onClick = {
                                    menuExpanded = false
                                    showEmptyBinConfirmation = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
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
            title = { Text(if (selectedIds.size == 1) "Delete permanently?" else "Delete ${selectedIds.size} items permanently?") },
            text = { Text("This cannot be undone. ${if (selectedIds.size == 1) "It" else "They"} will be removed from your device for good.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    performPermanentDelete()
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    if (showEmptyBinConfirmation) {
        AlertDialog(
            onDismissRequest = { showEmptyBinConfirmation = false },
            title = { Text("Empty Bin?") },
            text = { Text(if (items.size == 1) "The item in the Bin will be permanently removed from your device. This cannot be undone." else "All ${items.size} items will be permanently removed from your device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyBinConfirmation = false
                    performEmptyBin()
                }) { Text("Empty Bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyBinConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
