package com.dripta.galleryformoto.ui

import android.location.Geocoder
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.MediaItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    items: List<MediaItem>,
    onPlaceClick: (List<MediaItem>, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val placeNames = remember { mutableStateMapOf<String, String>() }

    // Group items by rounded lat/lng (approx 11km precision)
    val grouped = remember(items) {
        items.filter { it.latitude != null && it.longitude != null }
            .groupBy { 
                "${(it.latitude!! * 10).toInt() / 10.0},${(it.longitude!! * 10).toInt() / 10.0}"
            }
    }

    LaunchedEffect(grouped.keys) {
        val geocoder = Geocoder(context, Locale.getDefault())
        grouped.keys.forEach { key ->
            val parts = key.split(",")
            val lat = parts[0].toDouble()
            val lng = parts[1].toDouble()
            try {
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val city = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea
                    if (city != null) placeNames[key] = city
                }
            } catch (e: Exception) {
                // Ignore geocoding errors
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(grouped.keys.toList(), key = { it }) { key ->
                val placeItems = grouped[key] ?: emptyList()
                val name = placeNames[key] ?: "Unknown Place"
                val coverItem = placeItems.first()

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val tileScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "placeTileScale"
                )

                Column(
                    modifier = Modifier
                        .animateItem()
                        .padding(8.dp)
                        .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { onPlaceClick(placeItems, name) }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        AsyncImage(
                            model = coverItem.uri,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            imageLoader = LocalImageLoader.current
                        )
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "${placeItems.size} items",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
