package com.dripta.galleryformoto.ui

import androidx.compose.ui.graphics.ColorMatrix

/** Shared between [EditorScreen]'s Adjust/Filters tabs and the batch-edit tool, so both bake identical results. */
enum class FilterPreset(val label: String) {
    NONE("None"),
    BW("B&W"),
    SEPIA("Sepia"),
    VIVID("Vivid"),
    COOL("Cool"),
    WARM("Warm")
}

fun filterMatrix(preset: FilterPreset): ColorMatrix = ColorMatrix().apply {
    when (preset) {
        FilterPreset.NONE -> reset()
        FilterPreset.BW -> setToSaturation(0f)
        FilterPreset.VIVID -> setToSaturation(1.5f)
        FilterPreset.SEPIA -> {
            reset()
            values[0] = 0.393f; values[1] = 0.769f; values[2] = 0.189f
            values[5] = 0.349f; values[6] = 0.686f; values[7] = 0.168f
            values[10] = 0.272f; values[11] = 0.534f; values[12] = 0.131f
        }
        FilterPreset.COOL -> {
            reset()
            values[4] = -10f
            values[14] = 15f
        }
        FilterPreset.WARM -> {
            reset()
            values[4] = 15f
            values[14] = -10f
        }
    }
}

fun buildAdjustMatrix(brightness: Float, contrast: Float, saturation: Float, preset: FilterPreset): ColorMatrix {
    val saturationMatrix = ColorMatrix().apply { setToSaturation(saturation) }
    val contrastScale = 1f + (contrast / 100f)
    val contrastTranslate = (1f - contrastScale) * 127.5f
    val contrastMatrix = ColorMatrix().apply {
        setToScale(contrastScale, contrastScale, contrastScale, 1f)
        this[0, 4] = contrastTranslate
        this[1, 4] = contrastTranslate
        this[2, 4] = contrastTranslate
    }
    val brightnessMatrix = ColorMatrix().apply {
        reset()
        this[0, 4] = brightness
        this[1, 4] = brightness
        this[2, 4] = brightness
    }
    val combined = ColorMatrix()
    combined *= brightnessMatrix
    combined *= contrastMatrix
    combined *= saturationMatrix
    combined *= filterMatrix(preset)
    return combined
}
