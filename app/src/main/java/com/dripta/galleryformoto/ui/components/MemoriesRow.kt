package com.dripta.galleryformoto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.LocalImageLoader
import java.util.Calendar

@Composable
fun MemoriesRow(
    items: List<MediaItem>,
    onMemoryClick: (List<MediaItem>) -> Unit
) {
    val memories = remember(items) {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        items.filter { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.DAY_OF_MONTH) == currentDay
        }.groupBy { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(Calendar.YEAR)
        }.mapNotNull { (year, itemsInYear) ->
            val yearsAgo = now.get(Calendar.YEAR) - year
            if (yearsAgo > 0) {
                Memory(
                    title = "$yearsAgo years ago",
                    items = itemsInYear,
                    cover = itemsInYear.first()
                )
            } else null
        }.sortedBy { it.items.first().dateMillis }
    }

    if (memories.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(memories) { memory ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onMemoryClick(memory.items) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFF2196F3))), CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = memory.cover.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            imageLoader = LocalImageLoader.current
                        )
                    }
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

data class Memory(
    val title: String,
    val items: List<MediaItem>,
    val cover: MediaItem
)
