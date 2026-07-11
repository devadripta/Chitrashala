package com.dripta.galleryformoto.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    /** Decodes [uri] downsampled so its longest edge is at most [maxDimension], to avoid OOM on high-MP camera photos. */
    suspend fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = 3000): Bitmap =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longestEdge = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (longestEdge / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Could not decode image")
            decoded.copy(Bitmap.Config.ARGB_8888, true)
        }

    /** Simple box blur (3 passes approximates a Gaussian) that works on any API level. */
    fun boxBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val w = source.width
        val h = source.height
        var pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            pixels = boxBlurPass(pixels, w, h, radius)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val horizontal = IntArray(pixels.size)
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                for (dx in -radius..radius) {
                    val xi = min(max(x + dx, 0), w - 1)
                    val p = pixels[rowStart + xi]
                    a += (p shr 24) and 0xFF
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                horizontal[rowStart + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        val result = IntArray(pixels.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                for (dy in -radius..radius) {
                    val yi = min(max(y + dy, 0), h - 1)
                    val p = horizontal[yi * w + x]
                    a += (p shr 24) and 0xFF
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                result[y * w + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        return result
    }

    /** Saves [bitmap] as a brand-new MediaStore entry; the original file referenced by [sourceUri] is never touched. */
    suspend fun saveBitmapAsNewMedia(
        context: Context,
        bitmap: Bitmap,
        sourceDisplayName: String
    ): Uri {
        val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
        return saveBitmapWithName(context, bitmap, "${baseName}_edited_${System.currentTimeMillis()}.jpg")
    }

    /** Saves [bitmap] as a brand-new MediaStore entry using [displayName] verbatim (no source photo to derive a name from, e.g. collages). */
    suspend fun saveBitmapWithName(context: Context, bitmap: Bitmap, displayName: String): Uri = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Chitrashala")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("Failed to create media entry")

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: error("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        uri
    }
}
