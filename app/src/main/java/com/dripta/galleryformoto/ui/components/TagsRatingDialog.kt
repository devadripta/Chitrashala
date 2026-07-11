package com.dripta.galleryformoto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.MediaMetaEntity
import kotlinx.coroutines.flow.Flow

val colorLabelPalette = mapOf(
    "red" to Color(0xFFE53935),
    "orange" to Color(0xFFFB8C00),
    "yellow" to Color(0xFFFDD835),
    "green" to Color(0xFF43A047),
    "blue" to Color(0xFF1E88E5),
    "purple" to Color(0xFF8E24AA)
)

@Composable
fun TagsRatingDialog(
    tags: Flow<List<String>>,
    meta: Flow<MediaMetaEntity?>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSetRating: (Int) -> Unit,
    onSetColorLabel: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val currentTags by tags.collectAsState(initial = emptyList())
    val currentMeta by meta.collectAsState(initial = null)
    var newTagText by remember { mutableStateOf("") }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags & Rating") },
        text = {
            Column {
                Text("Rating", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    for (star in 1..5) {
                        val filled = star <= (currentMeta?.rating ?: 0)
                        IconButton(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSetRating(if (filled && star == currentMeta?.rating) 0 else star)
                        }) {
                            Icon(
                                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$star stars",
                                tint = if (filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Text("Color label", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colorLabelPalette.forEach { (name, color) ->
                        val isSelected = currentMeta?.colorLabel == name
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { onSetColorLabel(if (isSelected) null else name) }
                        )
                    }
                }

                Text("Tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(currentTags) { tag ->
                        AssistChip(
                            onClick = { onRemoveTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("Add tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (newTagText.isNotBlank()) {
                            onAddTag(newTagText)
                            newTagText = ""
                        }
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
