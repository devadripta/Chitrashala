package com.dripta.galleryformoto.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dripta.galleryformoto.data.BitmapUtils
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.data.collageTemplatesFor
import com.dripta.galleryformoto.data.renderCollage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    items: List<MediaItem>,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates = remember(items.size) { collageTemplatesFor(items.size) }
    var selectedTemplateIndex by remember { mutableStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    val template = templates[selectedTemplateIndex.coerceIn(0, templates.size - 1)]

    fun save() {
        scope.launch {
            isSaving = true
            try {
                val bitmaps = items.map { BitmapUtils.loadBitmap(context, it.uri, maxDimension = 1600) }
                val collage = renderCollage(bitmaps, template)
                BitmapUtils.saveBitmapWithName(context, collage, "Collage_${System.currentTimeMillis()}.jpg")
                Toast.makeText(context, "Collage saved", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't create collage: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collage") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else {
                        IconButton(onClick = { save() }) {
                            Icon(Icons.Filled.Check, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AnimatedContent(
                    targetState = template,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "collageTemplate"
                ) { targetTemplate ->
                    CollagePreview(items = items, template = targetTemplate)
                }
            }

            if (templates.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(templates.size) { index ->
                        val isSelected = index == selectedTemplateIndex
                        val chipScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "chipScale"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedTemplateIndex = index }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(templates[index].name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollagePreview(items: List<MediaItem>, template: com.dripta.galleryformoto.data.CollageTemplate) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        template.slots.forEachIndexed { index, slot ->
            val item = items.getOrNull(index) ?: return@forEachIndexed
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { (slot.left * widthPx).toDp() },
                        y = with(density) { (slot.top * heightPx).toDp() }
                    )
                    .size(
                        width = with(density) { ((slot.right - slot.left) * widthPx).toDp() },
                        height = with(density) { ((slot.bottom - slot.top) * heightPx).toDp() }
                    )
                    .padding(2.dp)
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
