package com.dripta.galleryformoto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.ui.LocalImageLoader

private val categoryIcons: Map<String, ImageVector> = mapOf(
    "document" to Icons.Filled.Description,
    "receipt" to Icons.Filled.Receipt,
    "food" to Icons.Filled.Fastfood,
    "pet" to Icons.Filled.Pets,
    "travel_landscape" to Icons.Filled.Landscape,
    "selfie" to Icons.Filled.Face,
    "screenshot" to Icons.Filled.Smartphone,
    "art_illustration" to Icons.AutoMirrored.Filled.Article,
    "indoor" to Icons.AutoMirrored.Filled.Article,
    "outdoor" to Icons.Filled.Park,
    "people_group" to Icons.Filled.Groups,
    "vehicle" to Icons.Filled.DirectionsCar,
    "nature" to Icons.Filled.Nature,
    "other" to Icons.AutoMirrored.Filled.Article
)

@Composable
fun SmartAlbumsSection(
    categories: List<String>,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            "Smart Albums",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                SmartAlbumCard(category = category, onClick = { onCategoryClick(category) })
            }
        }
    }
}

@Composable
private fun SmartAlbumCard(
    category: String,
    onClick: () -> Unit
) {
    val icon = categoryIcons[category] ?: Icons.AutoMirrored.Filled.Article
    val displayName = category.replace("_", " ").replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth()) {
                drawCircle(
                    color = Color(0xFF1A1A2E),
                    radius = size.minDimension / 2
                )
            }
            Icon(
                icon,
                contentDescription = displayName,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
