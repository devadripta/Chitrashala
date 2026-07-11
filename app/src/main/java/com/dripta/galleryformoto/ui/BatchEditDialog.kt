package com.dripta.galleryformoto.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dripta.galleryformoto.data.BitmapUtils
import com.dripta.galleryformoto.data.MediaItem
import kotlinx.coroutines.launch

/**
 * Adjust one representative photo (live preview), then bake the exact same brightness/contrast/
 * saturation/filter recipe onto every photo in [items] as new saved copies, "edit one, apply to
 * the rest" instead of repeating the same sliders one photo at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchEditDialog(
    items: List<MediaItem>,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewItem = items.first()

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var isApplying by remember { mutableStateOf(false) }
    var progressDone by remember { mutableIntStateOf(0) }

    LaunchedEffect(previewItem.uri) {
        previewBitmap = BitmapUtils.loadBitmap(context, previewItem.uri, maxDimension = 1200)
    }

    fun applyToAll() {
        scope.launch {
            isApplying = true
            progressDone = 0
            val matrix = buildAdjustMatrix(brightness, contrast, saturation, FilterPreset.NONE)
            for (item in items) {
                if (item.isVideo) {
                    progressDone++
                    continue
                }
                try {
                    val bitmap = BitmapUtils.loadBitmap(context, item.uri)
                    val baked = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(baked)
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(matrix.values)
                    }
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    BitmapUtils.saveBitmapAsNewMedia(context, baked, item.displayName)
                } catch (e: Exception) {
                    // Skip anything unreadable/corrupt; keep going for the rest of the batch.
                }
                progressDone++
            }
            isApplying = false
            onDone()
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TopAppBar(
                title = { Text("Batch edit · ${items.size} photos") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = previewItem.displayName,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(buildAdjustMatrix(brightness, contrast, saturation, FilterPreset.NONE)),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
                androidx.compose.animation.AnimatedVisibility(visible = isApplying, enter = fadeIn(), exit = fadeOut()) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                "Applying to $progressDone / ${items.size}",
                                color = Color.White,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp)) {
                Text(
                    "Preview shown on 1 photo. Applies to all ${items.size} as new saved copies.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BatchSlider("Brightness", brightness, -100f..100f) { brightness = it }
                BatchSlider("Contrast", contrast, -100f..100f) { contrast = it }
                BatchSlider("Saturation", saturation, 0f..2f) { saturation = it }
                Button(
                    onClick = { applyToAll() },
                    enabled = !isApplying,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Apply to ${items.size} photos")
                }
            }
        }
    }
}

@Composable
private fun BatchSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
