package com.dripta.galleryformoto.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dripta.galleryformoto.data.BitmapUtils
import com.dripta.galleryformoto.data.FaceBlurHelper
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.components.EnhancementPanel
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.launch

private enum class EditTool { ADJUST, CROP, FILTERS, ENHANCE, CREATIVE }

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    item: MediaItem,
    viewModel: GalleryViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoEditor by remember { mutableStateOf<PhotoEditor?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var selectedTool by remember { mutableStateOf(EditTool.ADJUST) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var selectedFilter by remember { mutableStateOf(FilterPreset.NONE) }
    var showOriginal by remember { mutableStateOf(false) }
    var showBatchApplyDialog by remember { mutableStateOf(false) }
    var isBatchApplying by remember { mutableStateOf(false) }
    val batchCandidates = remember(viewModel.activeViewerList, item) {
        viewModel.activeViewerList.filter { it.id != item.id && !it.isVideo }
    }

    var creativeTool by remember { mutableStateOf("brush") }

    androidx.compose.runtime.LaunchedEffect(item.uri) {
        workingBitmap = BitmapUtils.loadBitmap(context, item.uri)
        isLoading = false
    }

    fun saveResult() {
        val bitmap = workingBitmap ?: return
        scope.launch {
            isSaving = true
            val matrix = buildAdjustMatrix(brightness, contrast, saturation, selectedFilter)
            val baked = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(baked)
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(matrix.values)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            BitmapUtils.saveBitmapAsNewMedia(context, baked, item.displayName)
            isSaving = false
            onSaved()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Edit") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                }
            },
            actions = {
                if (batchCandidates.isNotEmpty() && (selectedTool == EditTool.ADJUST || selectedTool == EditTool.FILTERS)) {
                    IconButton(onClick = { showBatchApplyDialog = true }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy to other photos", tint = Color.White)
                    }
                }
                if (selectedTool == EditTool.CREATIVE && photoEditor != null) {
                    IconButton(onClick = { photoEditor?.undo() }) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                    }
                    IconButton(onClick = { photoEditor?.redo() }) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo", tint = Color.White)
                    }
                }
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), color = Color.White)
                } else {
                    IconButton(onClick = { saveResult() }, enabled = workingBitmap != null) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bitmap = workingBitmap
            when {
                isLoading -> CircularProgressIndicator(color = Color.White)
                bitmap != null && selectedTool == EditTool.CREATIVE -> {
                    AndroidView(
                        factory = { ctx ->
                            PhotoEditorView(ctx).also { view ->
                                view.source.setImageBitmap(bitmap)
                                val editor = PhotoEditor.Builder(ctx, view)
                                    .setPinchTextScalable(true)
                                    .build()
                                photoEditor = editor
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                bitmap != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (showOriginal) null else ColorFilter.colorMatrix(
                            buildAdjustMatrix(brightness, contrast, saturation, selectedFilter)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showOriginal) {
                        Text(
                            "Original",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Bottom tool panel
        when (selectedTool) {
            EditTool.ADJUST -> AdjustPanel(
                brightness = brightness, onBrightnessChange = { brightness = it },
                contrast = contrast, onContrastChange = { contrast = it },
                saturation = saturation, onSaturationChange = { saturation = it }
            )
            EditTool.CROP -> CropPanel(
                onRotate = { workingBitmap?.let { workingBitmap = rotateBitmap(it, 90f) } },
                onCrop = { /* use easycrop */ }
            )
            EditTool.FILTERS -> FiltersPanel(selected = selectedFilter, onSelect = { selectedFilter = it })
            EditTool.ENHANCE -> EnhancementPanel(
                item = item, viewModel = viewModel,
                onResult = { enhancedBitmap ->
                    workingBitmap = enhancedBitmap
                    Toast.makeText(context, "Enhancement applied", Toast.LENGTH_SHORT).show()
                }
            )
            EditTool.CREATIVE -> CreativePanel(
                creativeTool = creativeTool,
                onSelectTool = { creativeTool = it },
                photoEditor = photoEditor,
                context = context
            )
        }

        ToolTabRow(selected = selectedTool, onSelect = { selectedTool = it })
    }

    if (showBatchApplyDialog) {
        AlertDialog(
            onDismissRequest = { showBatchApplyDialog = false },
            title = { Text("Apply to ${batchCandidates.size} photos?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        isBatchApplying = true
                        val matrix = buildAdjustMatrix(brightness, contrast, saturation, selectedFilter)
                        var count = 0
                        for (target in batchCandidates) {
                            try {
                                val bmp = BitmapUtils.loadBitmap(context, target.uri)
                                val baked = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                                val cvs = android.graphics.Canvas(baked)
                                val p = android.graphics.Paint().apply {
                                    colorFilter = android.graphics.ColorMatrixColorFilter(matrix.values)
                                }
                                cvs.drawBitmap(bmp, 0f, 0f, p)
                                BitmapUtils.saveBitmapAsNewMedia(context, baked, target.displayName)
                                count++
                            } catch (_: Exception) {}
                        }
                        showBatchApplyDialog = false
                        viewModel.refresh()
                        Toast.makeText(context, "Applied to $count photos", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showBatchApplyDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToolTabRow(selected: EditTool, onSelect: (EditTool) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ToolTabItem(EditTool.ADJUST, Icons.Filled.Tune, "Adjust", selected, onSelect)
        ToolTabItem(EditTool.CROP, Icons.Filled.Crop, "Crop", selected, onSelect)
        ToolTabItem(EditTool.FILTERS, Icons.Filled.PhotoFilter, "Filters", selected, onSelect)
        ToolTabItem(EditTool.ENHANCE, Icons.Filled.AutoFixHigh, "Enhance", selected, onSelect)
        ToolTabItem(EditTool.CREATIVE, Icons.Filled.Brush, "Creative", selected, onSelect)
    }
}

@Composable
private fun ToolTabItem(tool: EditTool, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: EditTool, onSelect: (EditTool) -> Unit) {
    val isSelected = tool == selected
    val tint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
        label = "toolTabTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "toolTabScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onSelect(tool) }.padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AdjustPanel(
    brightness: Float, onBrightnessChange: (Float) -> Unit,
    contrast: Float, onContrastChange: (Float) -> Unit,
    saturation: Float, onSaturationChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp)) {
        LabeledSlider("Brightness", brightness, -100f..100f, onBrightnessChange)
        LabeledSlider("Contrast", contrast, -100f..100f, onContrastChange)
        LabeledSlider("Saturation", saturation, 0f..2f, onSaturationChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CropPanel(onRotate: () -> Unit, onCrop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onRotate) {
            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
        }
        Button(onClick = onCrop) { Text("Crop") }
    }
}

@Composable
private fun FiltersPanel(selected: FilterPreset, onSelect: (FilterPreset) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(FilterPreset.entries) { preset ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
                    .clickable { onSelect(preset) },
                contentAlignment = Alignment.Center
            ) {
                Text(preset.label, color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CreativePanel(
    creativeTool: String,
    onSelectTool: (String) -> Unit,
    photoEditor: PhotoEditor?,
    context: android.content.Context
) {
    val editor = photoEditor
    Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("brush" to "Draw", "text" to "Text", "emoji" to "Emoji", "eraser" to "Eraser", "shape" to "Shapes", "undo" to "Undo")
                .forEach { (id, name) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelectTool(id) }.padding(4.dp)) {
                        val isSel = creativeTool == id
                        val icon = when (id) {
                            "brush" -> Icons.Filled.Brush
                            "text" -> Icons.Filled.TextFields
                            "emoji" -> Icons.Filled.EmojiEmotions
                            "eraser" -> Icons.Filled.AutoFixHigh
                            "shape" -> Icons.Filled.Crop
                            else -> Icons.Filled.Undo
                        }
                        Icon(icon, null, tint = if (isSel) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(24.dp))
                        Text(name, color = if (isSel) MaterialTheme.colorScheme.primary else Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
        }

        Spacer(Modifier.height(8.dp))

        when (creativeTool) {
            "brush" -> {
                Text("Tap below to start drawing on the photo", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = { editor?.setBrushDrawingMode(true) }) { Text("Enable Drawing") }
            }
            "text" -> {
                var textVal by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textVal, onValueChange = { textVal = it },
                        label = { Text("Add text", color = Color.White) }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (textVal.isNotBlank()) { editor?.addText(textVal, android.graphics.Color.WHITE); textVal = "" }
                    }) { Text("Add", color = Color.White) }
                }
            }
            "emoji" -> {
                val emojis = listOf("\uD83D\uDE00", "\uD83D\uDE0D", "\uD83D\uDE02", "\uD83D\uDE0E", "\uD83E\uDD70", "\uD83D\uDE22", "\uD83D\uDE31", "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDE80", "\uD83C\uDF89", "\uD83D\uDD25", "\uD83D\uDC96", "\uD83C\uDF38", "\uD83C\uDF0D")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(emojis) { emoji ->
                        Text(emoji, modifier = Modifier.clickable { editor?.addEmoji(emoji) }.padding(4.dp), fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
                    }
                }
            }
            "eraser" -> {
                Button(onClick = { editor?.brushEraser() }) { Text("Eraser mode") }
            }
            "shape" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ShapeType.Brush to "Free", ShapeType.Oval to "Oval", ShapeType.Rectangle to "Rect", ShapeType.Line to "Line").forEach { (type, name) ->
                        TextButton(onClick = { editor?.setShape(ShapeBuilder().withShapeType(type).withShapeSize(30f).withShapeOpacity(100)) }) {
                            Text(name, color = Color.White)
                        }
                    }
                }
            }
            "undo" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { editor?.undo() }) { Text("Undo") }
                    Button(onClick = { editor?.redo() }) { Text("Redo") }
                }
            }
        }
    }
}
