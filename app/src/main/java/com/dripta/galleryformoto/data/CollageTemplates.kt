package com.dripta.galleryformoto.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.sqrt

/** A single photo's position within a collage, as fractions (0f..1f) of the final canvas. */
data class CollageSlot(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class CollageTemplate(val name: String, val slots: List<CollageSlot>)

/** Hand-picked layouts for small counts; a plain grid for anything larger. */
fun collageTemplatesFor(count: Int): List<CollageTemplate> = when (count) {
    2 -> listOf(
        CollageTemplate("Side by side", listOf(CollageSlot(0f, 0f, 0.5f, 1f), CollageSlot(0.5f, 0f, 1f, 1f))),
        CollageTemplate("Stacked", listOf(CollageSlot(0f, 0f, 1f, 0.5f), CollageSlot(0f, 0.5f, 1f, 1f)))
    )
    3 -> listOf(
        CollageTemplate(
            "Big + 2", listOf(
                CollageSlot(0f, 0f, 0.6f, 1f),
                CollageSlot(0.6f, 0f, 1f, 0.5f),
                CollageSlot(0.6f, 0.5f, 1f, 1f)
            )
        ),
        CollageTemplate(
            "Top + 2", listOf(
                CollageSlot(0f, 0f, 1f, 0.6f),
                CollageSlot(0f, 0.6f, 0.5f, 1f),
                CollageSlot(0.5f, 0.6f, 1f, 1f)
            )
        )
    )
    4 -> listOf(
        CollageTemplate(
            "2x2 grid", listOf(
                CollageSlot(0f, 0f, 0.5f, 0.5f), CollageSlot(0.5f, 0f, 1f, 0.5f),
                CollageSlot(0f, 0.5f, 0.5f, 1f), CollageSlot(0.5f, 0.5f, 1f, 1f)
            )
        )
    )
    else -> {
        val cols = ceil(sqrt(count.toDouble())).toInt()
        val rows = ceil(count / cols.toDouble()).toInt()
        val slots = (0 until count).map { i ->
            val r = i / cols
            val c = i % cols
            CollageSlot(c.toFloat() / cols, r.toFloat() / rows, (c + 1).toFloat() / cols, (r + 1).toFloat() / rows)
        }
        listOf(CollageTemplate("Grid", slots))
    }
}

/** The largest centered crop of [bitmap] matching [dstAspect] (width/height), for a center-crop fill. */
private fun centerCropSrcRect(bitmap: Bitmap, dstAspect: Float): Rect {
    val srcAspect = bitmap.width.toFloat() / bitmap.height
    return if (srcAspect > dstAspect) {
        val cropWidth = (bitmap.height * dstAspect).toInt().coerceAtMost(bitmap.width)
        val left = (bitmap.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, bitmap.height)
    } else {
        val cropHeight = (bitmap.width / dstAspect).toInt().coerceAtMost(bitmap.height)
        val top = (bitmap.height - cropHeight) / 2
        Rect(0, top, bitmap.width, top + cropHeight)
    }
}

/** Composites [bitmaps] (same order as [template].slots) into a single square canvas, center-cropped to fill each slot. */
fun renderCollage(bitmaps: List<Bitmap>, template: CollageTemplate, canvasSize: Int = 1600, gapPx: Int = 8): Bitmap {
    val result = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    template.slots.forEachIndexed { index, slot ->
        val bitmap = bitmaps.getOrNull(index) ?: return@forEachIndexed
        val dstRect = RectF(
            slot.left * canvasSize + gapPx / 2f,
            slot.top * canvasSize + gapPx / 2f,
            slot.right * canvasSize - gapPx / 2f,
            slot.bottom * canvasSize - gapPx / 2f
        )
        val dstAspect = (dstRect.right - dstRect.left) / (dstRect.bottom - dstRect.top)
        val srcRect = centerCropSrcRect(bitmap, dstAspect)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
    return result
}
