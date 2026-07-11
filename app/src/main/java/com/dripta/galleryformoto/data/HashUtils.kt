package com.dripta.galleryformoto.data

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sqrt

object HashUtils {

    data class PhashResult(val hash: Long, val width: Int, val height: Int)

    /**
     * Perceptual hash via 32×32 DCT → keep top-left 8×8 → median threshold → 64-bit.
     * Resistant to scaling, minor colour/compression changes.
     */
    fun pHash(bitmap: Bitmap, size: Int = 32, highSize: Int = 8): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val lum = DoubleArray(size * size) { i ->
            val p = pixels[i]
            (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF))
        }

        val dct = DoubleArray(size * size)
        for (u in 0 until size) {
            for (v in 0 until size) {
                var sum = 0.0
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        sum += lum[x * size + y] * cos((2 * x + 1) * u * Math.PI / (2 * size)) * cos((2 * y + 1) * v * Math.PI / (2 * size))
                    }
                }
                dct[u * size + v] = sum * (if (u == 0) 1.0 / sqrt(size.toDouble()) else sqrt(2.0 / size)) * (if (v == 0) 1.0 / sqrt(size.toDouble()) else sqrt(2.0 / size))
            }
        }

        val lowFreq = DoubleArray(highSize * highSize) { dct[(it / highSize) * size + (it % highSize)] }
        lowFreq[0] = 0.0 // exclude DC
        val median = lowFreq.sortedArray().let { it[it.size / 2] }

        var hash = 0L
        for (i in 0 until highSize * highSize) {
            if (lowFreq[i] > median) hash = hash or (1L shl i)
        }
        return hash
    }

    /**
     * Difference hash: 9×8 cells, compare horizontal neighbours → 64-bit.
     * Simpler and faster than pHash; complementary for near-dupe detection.
     */
    fun dHash(bitmap: Bitmap, width: Int = 9, height: Int = 8): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val lum = pixels.map { p ->
            (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
        }

        var hash = 0L
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val idx = y * width + x
                if (lum[idx] > lum[idx + 1]) hash = hash or (1L shl (y * (width - 1) + x))
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Finds groups of near-duplicate photos.
     * Grouping thresholds:
     *   identical:  dHash distance = 0 AND pHash distance = 0
     *   near_dupe:  dHash distance ≤ 5 OR pHash distance ≤ 10
     *   burst:      not yet grouped, but stored in quality table
     */
    fun findDuplicateGroups(
        hashes: List<PhotoHashEntity>,
        nearDupeDHashThreshold: Int = 5,
        nearDupePHashThreshold: Int = 10
    ): List<DuplicateGroupEntity> {
        val groups = mutableListOf<DuplicateGroupEntity>()
        val visited = mutableSetOf<Long>()

        for (i in hashes.indices) {
            val a = hashes[i]
            if (a.mediaId in visited) continue
            val matches = mutableListOf<Long>()
            for (j in hashes.indices) {
                if (i == j) continue
                val b = hashes[j]
                if (b.mediaId in visited) continue
                val dDist = hammingDistance(a.differenceHash, b.differenceHash)
                val pDist = hammingDistance(a.perceptualHash, b.perceptualHash)
                val groupType = when {
                    dDist == 0 && pDist == 0 -> "identical"
                    dDist <= nearDupeDHashThreshold || pDist <= nearDupePHashThreshold -> "near_duplicate"
                    else -> null
                }
                if (groupType != null) matches.add(b.mediaId)
            }
            if (matches.isNotEmpty()) {
                groups.add(
                    DuplicateGroupEntity(
                        representativeMediaId = a.mediaId,
                        duplicateMediaIdsJson = matches.joinToString(prefix = "[", postfix = "]"),
                        groupType = if (matches.any { id ->
                            val b = hashes.find { it.mediaId == id }!!
                            hammingDistance(a.differenceHash, b.differenceHash) == 0 &&
                            hammingDistance(a.perceptualHash, b.perceptualHash) == 0
                        }) "identical" else "near_duplicate"
                    )
                )
                visited.add(a.mediaId)
                visited.addAll(matches)
            }
        }
        return groups
    }
}
