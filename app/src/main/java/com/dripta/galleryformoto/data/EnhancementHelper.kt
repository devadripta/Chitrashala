package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp

/**
 * Photo enhancement operations.
 *
 * [superResolve], [colorize], and [restoreFaces] are backed by real neural models
 * (ESRGAN/DeOldify/GFPGAN) in a full deployment, but no such models are bundled here, that
 * would need real trained .tflite files sourced and integrated separately. Until then:
 *   - [superResolve] falls back to a plain bicubic 2x upscale (genuinely useful, no model needed).
 *   - [colorize] and [restoreFaces] are no-ops (return the input unchanged).
 * [denoise] is a traditional non-local-means algorithm and needs no model, it fully works.
 */
class EnhancementHelper private constructor() {

    fun smartEnhance(bitmap: Bitmap): Bitmap {
        // Boost contrast, saturation and sharpen slightly via midtone stretch
        val matrix = android.graphics.ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0f, 0f, 0f, -10f,
                0f, 1.2f, 0f, 0f, -10f,
                0f, 0f, 1.2f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            val sat = android.graphics.ColorMatrix()
            sat.setSaturation(1.15f)
            postConcat(sat)
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun superResolve(bitmap: Bitmap): Bitmap =
        Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)

    fun colorize(bitmap: Bitmap): Bitmap {
        // Fake colorization for B&W by shifting to sepia/warm tone then boosting saturation
        val matrix = android.graphics.ColorMatrix().apply {
            setSaturation(1.2f)
            val warm = android.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.0f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            ))
            postConcat(warm)
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun restoreFaces(bitmap: Bitmap): Bitmap {
        // Stronger clarity boost and sharpening for facial features
        val matrix = android.graphics.ColorMatrix().apply {
            set(floatArrayOf(
                1.3f, 0f, 0f, 0f, -15f,
                0f, 1.3f, 0f, 0f, -15f,
                0f, 0f, 1.3f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ── De-noising (Non-Local Means) ────────────────────────────────────────────

    /**
     * Non-local means denoising. Traditional algorithm, no ML model needed.
     * [strength] 0.0 (none) to 1.0 (full):
     *   Light:  0.25, preserves detail
     *   Medium: 0.50
     *   Heavy:  0.80, maximum smoothing
     */
    fun denoise(bitmap: Bitmap, strength: Float = 0.5f): Bitmap {
        if (strength <= 0f) return bitmap
        val h = (strength * 30f + 3f).toDouble() // filter parameter
        val patchSize = 3
        val searchWindow = 7

        val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true)
        val w = scaled.width; val ht = scaled.height
        val pixels = IntArray(w * ht)
        scaled.getPixels(pixels, 0, w, 0, 0, w, ht)

        val src = Array(ht) { y -> Array(w) { x ->
            val p = pixels[y * w + x]
            floatArrayOf(
                ((p shr 16) and 0xFF).toFloat() / 255f,
                ((p shr 8) and 0xFF).toFloat() / 255f,
                (p and 0xFF).toFloat() / 255f
            )
        } }

        val dst = Array(ht) { Array(w) { FloatArray(3) } }
        for (y in 0 until ht) {
            for (x in 0 until w) {
                var totalWeight = 0f
                var r = 0f; var g = 0f; var b = 0f
                val syStart = (y - searchWindow).coerceAtLeast(0)
                val syEnd = (y + searchWindow).coerceAtMost(ht - 1)
                val sxStart = (x - searchWindow).coerceAtLeast(0)
                val sxEnd = (x + searchWindow).coerceAtMost(w - 1)

                for (sy in syStart..syEnd) {
                    for (sx in sxStart..sxEnd) {
                        var dist = 0f
                        var count = 0
                        for (dy in -patchSize..patchSize) {
                            val ny = y + dy; val nsy = sy + dy
                            if (ny < 0 || ny >= ht || nsy < 0 || nsy >= ht) continue
                            for (dx in -patchSize..patchSize) {
                                val nx = x + dx; val nsx = sx + dx
                                if (nx < 0 || nx >= w || nsx < 0 || nsx >= w) continue
                                val c1 = src[ny][nx]; val c2 = src[nsy][nsx]
                                val d = (c1[0] - c2[0]) * (c1[0] - c2[0]) +
                                    (c1[1] - c2[1]) * (c1[1] - c2[1]) +
                                    (c1[2] - c2[2]) * (c1[2] - c2[2])
                                dist += d
                                count++
                            }
                        }
                        if (count == 0) continue
                        val weight = exp((-dist / count) / h).toFloat()
                        val sp = src[sy][sx]
                        r += sp[0] * weight
                        g += sp[1] * weight
                        b += sp[2] * weight
                        totalWeight += weight
                    }
                }
                if (totalWeight > 0f) {
                    dst[y][x][0] = r / totalWeight
                    dst[y][x][1] = g / totalWeight
                    dst[y][x][2] = b / totalWeight
                } else {
                    val sp = src[y][x]
                    dst[y][x][0] = sp[0]
                    dst[y][x][1] = sp[1]
                    dst[y][x][2] = sp[2]
                }
            }
        }

        val result = Bitmap.createBitmap(w, ht, Bitmap.Config.ARGB_8888)
        for (y in 0 until ht) {
            for (x in 0 until w) {
                val r = (dst[y][x][0] * 255).toInt().coerceIn(0, 255)
                val g = (dst[y][x][1] * 255).toInt().coerceIn(0, 255)
                val b = (dst[y][x][2] * 255).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return result
    }

    fun close() {}

    companion object {
        @Volatile
        private var instance: EnhancementHelper? = null

        fun getInstance(context: Context): EnhancementHelper =
            instance ?: synchronized(this) {
                instance ?: EnhancementHelper().also { instance = it }
            }
    }
}
