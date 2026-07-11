package com.dripta.galleryformoto.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.CleanupSuggestions
import com.dripta.galleryformoto.data.DuplicateGroupEntity
import com.dripta.galleryformoto.data.MediaItem
import kotlinx.coroutines.launch

private data class CleanupSection(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val count: Int,
    val description: String,
    val itemIds: Set<Long>,
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    viewModel: GalleryViewModel,
    allMedia: List<MediaItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions by viewModel.cleanupSuggestions.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedSections by remember { mutableStateOf(setOf<String>()) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshCleanupSuggestions()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.refresh()
        viewModel.refreshCleanupSuggestions()
    }

    val sections = remember(suggestions, allMedia) {
        if (suggestions == null) emptyList()
        else listOf(
            CleanupSection(
                "Blurry Photos",
                Icons.Filled.BrokenImage,
                suggestions!!.blurryMediaIds.size,
                "These photos appear blurry and may not be worth keeping.",
                suggestions!!.blurryMediaIds
            ),
            CleanupSection(
                "Screenshots",
                Icons.Filled.Screenshot,
                suggestions!!.screenshotIds.size,
                "Screenshots cluttering your photo library.",
                suggestions!!.screenshotIds
            ),
            CleanupSection(
                "Badly Exposed",
                Icons.Filled.CollectionsBookmark,
                suggestions!!.badlyExposedIds.size,
                "Under or over-exposed photos that are hard to see.",
                suggestions!!.badlyExposedIds
            ),
            CleanupSection(
                "Burst Shots",
                Icons.Filled.AutoDelete,
                suggestions!!.burstGroupIds.size,
                "Rapid-fire shots. Keep only the best one.",
                suggestions!!.burstGroupIds
            )
        )
    }

    var revealedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(sections.size) {
        revealedCount = 0
        sections.forEachIndexed { index, section ->
            if (section.count > 0) {
                kotlinx.coroutines.delay(80L * (index + 1))
                revealedCount = index + 1
            }
        }
    }

    val duplicateCount = remember(suggestions) {
        suggestions?.duplicateGroups?.sumOf { parseDuplicateCount(it.duplicateMediaIdsJson) } ?: 0
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Smart Cleanup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            val totalSelected = sections.sumOf { section ->
                if (section.itemIds.isNotEmpty() && section.title in selectedSections) section.count else 0
            } + if ("Duplicates" in selectedSections) duplicateCount else 0

            if (totalSelected > 0 && !isDeleting) {
                Button(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (totalSelected == 1) "Move 1 item to Bin" else "Move $totalSelected items to Bin")
                }
            }

            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text(if (totalSelected == 1) "Move to Bin?" else "Move $totalSelected items to Bin?") },
                    text = { Text(if (totalSelected == 1) "You can restore it from the Bin within 30 days." else "You can restore them from the Bin within 30 days.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirmation = false
                            scope.launch {
                                isDeleting = true
                                try {
                                    val uris = mutableListOf<Uri>()
                                    for (section in sections) {
                                        if (section.title in selectedSections) {
                                            uris.addAll(
                                                allMedia.filter { it.id in section.itemIds }.map { it.uri }
                                            )
                                        }
                                    }
                                    if ("Duplicates" in selectedSections) {
                                        suggestions?.duplicateGroups?.forEach { group ->
                                            parseIds(group.duplicateMediaIdsJson).forEach { dupId ->
                                                allMedia.find { it.id == dupId }?.uri?.let { uris.add(it) }
                                            }
                                        }
                                    }
                                    val intentSender = viewModel.deleteMedia(uris.distinct())
                                    if (intentSender != null) {
                                        deleteLauncher.launch(
                                            androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    } else {
                                        viewModel.refresh()
                                        viewModel.refreshCleanupSuggestions()
                                    }
                                } finally {
                                    isDeleting = false
                                }
                            }
                        }) { Text("Move to Bin", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                    }
                )
            }
        }
    ) { padding ->
        if (suggestions == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading suggestions...", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (sections.all { it.count == 0 } && duplicateCount == 0) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your library looks great!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nothing to clean up right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sections.forEachIndexed { index, section ->
                    if (section.count > 0) {
                        item(key = section.title) {
                            AnimatedVisibility(
                                visible = revealedCount > index,
                                enter = scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.55f)
                                ) + fadeIn(tween(300)),
                                exit = fadeOut(tween(150))
                            ) {
                                CleanupSectionCard(
                                    section = section,
                                    allMedia = allMedia,
                                    isSelected = section.title in selectedSections,
                                    onToggle = {
                                        selectedSections = if (section.title in selectedSections) {
                                            selectedSections - section.title
                                        } else {
                                            selectedSections + section.title
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (duplicateCount > 0) {
                    item(key = "duplicates") {
                        DuplicatesSectionCard(
                            groups = suggestions!!.duplicateGroups,
                            allMedia = allMedia,
                            isSelected = "Duplicates" in selectedSections,
                            onToggle = {
                                selectedSections = if ("Duplicates" in selectedSections) {
                                    selectedSections - "Duplicates"
                                } else {
                                    selectedSections + "Duplicates"
                                }
                            },
                            viewModel = viewModel,
                            deleteLauncher = deleteLauncher
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupSectionCard(
    section: CleanupSection,
    allMedia: List<MediaItem>,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        section.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${section.count} items",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val preview = allMedia.filter { it.id in section.itemIds }.take(10)
                items(preview) { item ->
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        imageLoader = LocalImageLoader.current
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicatesSectionCard(
    groups: List<DuplicateGroupEntity>,
    allMedia: List<MediaItem>,
    isSelected: Boolean,
    onToggle: () -> Unit,
    viewModel: GalleryViewModel,
    deleteLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    val totalDupes = groups.sumOf { parseDuplicateCount(it.duplicateMediaIdsJson) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Duplicates & Near-Duplicates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Found ${groups.size} groups with $totalDupes duplicate photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            }
        }
    }
}

private fun parseDuplicateCount(json: String): Int {
    val ids = parseIds(json)
    return ids.size
}

private fun parseIds(json: String): List<Long> {
    return json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
}
