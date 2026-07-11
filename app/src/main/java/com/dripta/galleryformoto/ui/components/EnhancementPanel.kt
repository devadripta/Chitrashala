package com.dripta.galleryformoto.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dripta.galleryformoto.data.EnhanceMode
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.ui.GalleryViewModel
import kotlinx.coroutines.launch

@Composable
fun EnhancementPanel(
    item: MediaItem,
    viewModel: GalleryViewModel,
    onResult: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var activeOperation by remember { mutableStateOf<String?>(null) }
    var denoiseStrength by remember { mutableStateOf(0.5f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI Enhancement",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        EnhancementButton(
            icon = Icons.Filled.Hd,
            label = "Super Resolution (2x)",
            description = "Upscale with AI detail recovery",
            isProcessing = activeOperation == "super_resolve",
            onClick = {
                scope.launch {
                    activeOperation = "super_resolve"
                    isProcessing = true
                    try {
                        val result = viewModel.enhancePhoto(item.uri, EnhanceMode.SUPER_RESOLVE)
                        onResult(result)
                        Toast.makeText(context, "Super resolution complete", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Enhancement failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                    activeOperation = null
                }
            }
        )

        EnhancementButton(
            icon = Icons.Filled.Colorize,
            label = "Colorize",
            description = "Add color to black & white photos",
            isProcessing = activeOperation == "colorize",
            onClick = {
                scope.launch {
                    activeOperation = "colorize"
                    isProcessing = true
                    try {
                        val result = viewModel.enhancePhoto(item.uri, EnhanceMode.COLORIZE)
                        onResult(result)
                        Toast.makeText(context, "Colorization complete", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Colorization failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                    activeOperation = null
                }
            }
        )

        EnhancementButton(
            icon = Icons.Filled.AutoFixHigh,
            label = "Smart Enhance",
            description = "Auto-adjust colors, contrast, and clarity",
            isProcessing = activeOperation == "smart_enhance",
            onClick = {
                scope.launch {
                    activeOperation = "smart_enhance"
                    isProcessing = true
                    try {
                        val result = viewModel.enhancePhoto(item.uri, EnhanceMode.SMART_ENHANCE)
                        onResult(result)
                        Toast.makeText(context, "Enhancement complete", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Enhancement failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                    activeOperation = null
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("De-noise", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(
            "Remove grain and noise from low-light photos",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Light", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = denoiseStrength,
                onValueChange = { denoiseStrength = it },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("Heavy", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = {
                scope.launch {
                    activeOperation = "denoise"
                    isProcessing = true
                    try {
                        val mode = when {
                            denoiseStrength < 0.33f -> EnhanceMode.DENOISE_LIGHT
                            denoiseStrength < 0.66f -> EnhanceMode.DENOISE_MEDIUM
                            else -> EnhanceMode.DENOISE_HEAVY
                        }
                        val result = viewModel.enhancePhoto(item.uri, mode)
                        onResult(result)
                        Toast.makeText(context, "De-noising complete", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "De-noising failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                    activeOperation = null
                }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (activeOperation == "denoise") {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Apply De-noise")
        }
    }
}

@Composable
private fun EnhancementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isProcessing,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.DarkGray
        )
    ) {
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
        } else {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(description, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}
