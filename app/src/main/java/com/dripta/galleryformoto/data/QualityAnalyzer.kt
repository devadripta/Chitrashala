package com.dripta.galleryformoto.data

import android.graphics.Bitmap

object QualityAnalyzer {

    /**
     * Laplacian variance, higher = sharper. Normalised to 0..1 range.
     * Values below 0.3 are typically noticeably blurry.
     */
    fun computeSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val scaled = if (w * h > 512 * 512) Bitmap.createScaledBitmap(bitmap, 512, 512, true) else bitmap
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
        }

        val sw = scaled.width
        val sh = scaled.height
        var sum = 0.0
        var count = 0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val center = gray[y * sw + x]
                val laplacian = 4 * center -
                    gray[(y - 1) * sw + x] - gray[(y + 1) * sw + x] -
                    gray[y * sw + (x - 1)] - gray[y * sw + (x + 1)]
                sum += (laplacian * laplacian).toDouble()
                count++
            }
        }
        val variance = sum / count
        return (variance / 2500.0).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Heuristic screenshot detection:
     * - Aspect ratio matches common phone screen ratios (16:9, 19.5:9, etc.)
     * - OR image dimensions are within a few pixels of the device's screen size
     * - AND low color entropy (screenshots tend to have flat regions)
     */
    fun detectScreenshot(bitmap: Bitmap, deviceWidth: Int = 0, deviceHeight: Int = 0): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val ratio = w.toFloat() / h

        val isScreenAspect = (ratio in 0.45f..0.62f) || (ratio in 1.6f..2.3f)
        val matchesDevice = deviceWidth > 0 && deviceHeight > 0 &&
            (Math.abs(w - deviceWidth) < 50 || Math.abs(h - deviceWidth) < 50) &&
            (Math.abs(h - deviceHeight) < 50 || Math.abs(w - deviceHeight) < 50)

        if (!isScreenAspect && !matchesDevice) return false

        // Quick color variance check on a downsampled version
        val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val pixels = IntArray(64 * 64)
        sample.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        for (p in pixels) {
            rSum += (p shr 16) and 0xFF
            gSum += (p shr 8) and 0xFF
            bSum += p and 0xFF
        }
        val n = pixels.size
        val rMean = rSum / n; val gMean = gSum / n; val bMean = bSum / n
        var rVar = 0.0; var gVar = 0.0; var bVar = 0.0
        for (p in pixels) {
            val dr = ((p shr 16) and 0xFF) - rMean
            val dg = ((p shr 8) and 0xFF) - gMean
            val db = (p and 0xFF) - bMean
            rVar += dr * dr; gVar += dg * dg; bVar += db * db
        }
        val avgVariance = (rVar + gVar + bVar) / (3 * n)
        return avgVariance < 1000.0 // low color entropy threshold
    }

    /**
     * Detects burst-shot groups: photos from the same bucket taken within [maxGapMs] of each other.
     * Returns clusters of 3+ sequential photos within the gap.
     */
    fun detectBurstGroups(mediaList: List<MediaItem>, maxGapMs: Long = 2000L, minGroupSize: Int = 3): List<List<MediaItem>> {
        val sorted = mediaList.sortedBy { it.dateMillis }
        val groups = mutableListOf<List<MediaItem>>()
        var current = mutableListOf<MediaItem>()

        for (i in sorted.indices) {
            if (current.isEmpty()) {
                current.add(sorted[i])
                continue
            }
            val prev = current.last()
            if (prev.bucketId == sorted[i].bucketId && sorted[i].dateMillis - prev.dateMillis <= maxGapMs) {
                current.add(sorted[i])
            } else {
                if (current.size >= minGroupSize) groups.add(current.toList())
                current = mutableListOf(sorted[i])
            }
        }
        if (current.size >= minGroupSize) groups.add(current.toList())
        return groups
    }

    /**
     * Histogram-based exposure score. 0 = severely under/over-exposed, 1 = well-exposed.
     * Uses luminance histogram skew as a proxy.
     */
    fun computeExposure(bitmap: Bitmap): Float {
        val sample = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val pixels = IntArray(128 * 128)
        sample.getPixels(pixels, 0, 128, 0, 0, 128, 128)
        val hist = IntArray(256)
        for (p in pixels) {
            val lum = (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
            hist[lum.coerceIn(0, 255)]++
        }
        val total = pixels.size
        var shadows = 0; var midtones = 0; var highlights = 0
        for (i in 0..85) shadows += hist[i]
        for (i in 86..170) midtones += hist[i]
        for (i in 171..255) highlights += hist[i]
        val shadowRatio = shadows.toFloat() / total
        val highlightRatio = highlights.toFloat() / total

        // Penalize if >60% of pixels are shadows or >60% are highlights
        val shadowPenalty = if (shadowRatio > 0.6f) 1f - shadowRatio else 0f
        val highlightPenalty = if (highlightRatio > 0.6f) 1f - highlightRatio else 0f
        return (1f - (shadowPenalty + highlightPenalty)).coerceIn(0f, 1f)
    }
}
