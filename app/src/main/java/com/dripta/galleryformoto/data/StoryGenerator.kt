package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

object StoryGenerator {

    data class StoryEvent(
        val title: String,
        val dateStart: Long,
        val dateEnd: Long,
        val locationName: String?,
        val coverMediaId: Long,
        val mediaItems: List<MediaItem>
    )

    data class MontageProgress(val frameIndex: Int, val totalFrames: Int)

    private val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dateRangeFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

    fun detectEvents(
        mediaItems: List<MediaItem>,
        epsTimeDays: Double = 3.0,
        epsSpaceDeg: Double = 0.5,
        minSamples: Int = 5
    ): List<StoryEvent> {
        if (mediaItems.isEmpty()) return emptyList()

        val sorted = mediaItems.sortedBy { it.dateMillis }
        val n = sorted.size
        val timeScale = 365.0 * 86400000.0

        data class Point(val idx: Int, val t: Double, val lat: Double, val lng: Double)

        val points = sorted.mapIndexed { i, item ->
            Point(i, item.dateMillis / timeScale, item.latitude ?: 0.0, item.longitude ?: 0.0)
        }

        val epsTime = epsTimeDays / 365.0
        val epsSpace = epsSpaceDeg

        val visited = BooleanArray(n)
        val clusterIds = IntArray(n) { -1 }
        var clusterCount = 0

        fun distance(a: Point, b: Point): Double {
            val dt = abs(a.t - b.t)
            val hasCoords = a.lat != 0.0 || a.lng != 0.0 || b.lat != 0.0 || b.lng != 0.0
            if (!hasCoords) return dt / epsTime
            val ds = sqrt((a.lat - b.lat) * (a.lat - b.lat) + (a.lng - b.lng) * (a.lng - b.lng))
            return sqrt((dt / epsTime) * (dt / epsTime) + (ds / epsSpace) * (ds / epsSpace))
        }

        fun neighbors(pi: Point): List<Point> =
            points.filter { distance(pi, it) <= 1.0 }

        for (i in points.indices) {
            if (visited[i]) continue
            visited[i] = true
            val nbrs = neighbors(points[i])
            if (nbrs.size + 1 < minSamples) continue

            clusterIds[i] = clusterCount
            val queue = nbrs.toMutableList()
            while (queue.isNotEmpty()) {
                val p = queue.removeAt(0)
                if (visited[p.idx]) {
                    if (clusterIds[p.idx] == -1) clusterIds[p.idx] = clusterCount
                    continue
                }
                visited[p.idx] = true
                clusterIds[p.idx] = clusterCount
                val pnbrs = neighbors(p)
                if (pnbrs.size + 1 >= minSamples) queue.addAll(pnbrs)
            }
            clusterCount++
        }

        val clusterGroups = (0 until clusterCount).map { cid ->
            sorted.filterIndexed { i, _ -> clusterIds[i] == cid }.sortedBy { it.dateMillis }
        }.filter { it.size >= minSamples }

        return clusterGroups.map { items ->
            val first = items.first()
            val last = items.last()
            val location = if (first.latitude != null && first.longitude != null) {
                String.format(Locale.US, "%.4f, %.4f", first.latitude, first.longitude)
            } else null

            StoryEvent(
                title = buildEventTitle(first, last),
                dateStart = first.dateMillis,
                dateEnd = last.dateMillis,
                locationName = location,
                coverMediaId = selectCoverPhoto(items).id,
                mediaItems = items
            )
        }.sortedByDescending { it.dateStart }
    }

    private fun buildEventTitle(first: MediaItem, last: MediaItem): String {
        return when {
            first.dateMillis / 86400000L == last.dateMillis / 86400000L ->
                dateRangeFormatter.format(Date(first.dateMillis))
            first.dateMillis / 2592000000L == last.dateMillis / 2592000000L ->
                "${dateRangeFormatter.format(Date(first.dateMillis))} – ${dateRangeFormatter.format(Date(last.dateMillis))}, ${dateFormatter.format(Date(first.dateMillis))}"
            else ->
                "${dateRangeFormatter.format(Date(first.dateMillis))} – ${dateRangeFormatter.format(Date(last.dateMillis))}"
        }
    }

    private fun selectCoverPhoto(items: List<MediaItem>): MediaItem {
        val nonVideo = items.filterNot { it.isVideo }
        return if (nonVideo.isNotEmpty()) nonVideo[nonVideo.size / 2] else items.first()
    }

    fun selectBestPhotos(mediaItems: List<MediaItem>, limit: Int = 30): List<MediaItem> {
        if (mediaItems.size <= limit) return mediaItems
        val sorted = mediaItems.sortedBy { it.dateMillis }
        val step = sorted.size.toDouble() / limit
        val selected = mutableListOf<MediaItem>()
        for (i in 0 until limit) {
            val idx = (i * step).toInt().coerceIn(0, sorted.size - 1)
            selected.add(sorted[idx])
        }
        return selected.distinctBy { it.id }
    }

    suspend fun generateMontage(
        context: Context,
        photos: List<MediaItem>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val displayWidth = 1080
        val displayHeight = 1920
        val fps = 30
        val photoDurationMs = 2500L
        val transitionMs = 500L
        val totalFrames = photos.size * ((photoDurationMs + transitionMs) * fps / 1000).toInt()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoFormat = MediaFormat.createVideoFormat("video/avc", displayWidth, displayHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        var frameIndex = 0
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        val trackRef = IntRef()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            val bitmaps = mutableListOf<Bitmap>()
            for ((pi, photo) in photos.withIndex()) {
                if (photo.isVideo) continue
                try {
                    bitmaps.add(BitmapUtils.loadBitmap(context, photo.uri, 2000))
                    onProgress(0.05f + 0.15f * pi / photos.size)
                } catch (_: Exception) {}
            }

            if (bitmaps.isEmpty()) throw IllegalStateException("No valid photos for montage")

            for (pi in bitmaps.indices) {
                val photo = bitmaps[pi]
                val photoFrames = (photoDurationMs * fps / 1000).toInt()
                val fadeFrames = (transitionMs * fps / 1000).toInt()

                for (f in 0 until photoFrames) {
                    val canvas = inputSurface.lockCanvas(null) ?: break
                    canvas.drawColor(Color.BLACK)

                    val progress = f.toFloat() / photoFrames
                    drawKenBurnsFrame(canvas, photo, displayWidth, displayHeight, progress, pi * 137L + f * 7L, paint)

                    if (f >= photoFrames - fadeFrames && pi + 1 < bitmaps.size) {
                        val alpha = ((f - (photoFrames - fadeFrames)).toFloat() / fadeFrames * 255).toInt()
                        paint.alpha = alpha
                        drawKenBurnsFrame(canvas, bitmaps[pi + 1], displayWidth, displayHeight, 0f, (pi + 1) * 137L, paint)
                        paint.alpha = 255
                    }

                    inputSurface.unlockCanvasAndPost(canvas)

                    drainEncoder(codec, muxer, bufferInfo, trackRef)
                    frameIndex++
                    if (frameIndex % 30 == 0) {
                        onProgress(0.2f + 0.8f * frameIndex / totalFrames)
                    }
                }
            }

            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, trackRef, drainAll = true)

            inputSurface.release()
            codec.stop()
            codec.release()
            muxer.stop()
            muxer.release()
            onProgress(1f)
        } catch (e: Exception) {
            try { inputSurface.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            throw e
        }

        Uri.fromFile(outputFile)
    }

    private fun drawKenBurnsFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        targetW: Int,
        targetH: Int,
        progress: Float,
        seed: Long,
        paint: Paint
    ) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val targetAspect = targetW.toFloat() / targetH
        val srcAspect = srcW / srcH

        val zoomStart = 0.85f
        val zoomEnd = 0.72f
        val zoom = zoomStart + (zoomEnd - zoomStart) * progress

        val cropW: Float
        val cropH: Float
        if (srcAspect > targetAspect) {
            cropH = srcH * zoom
            cropW = cropH * targetAspect
        } else {
            cropW = srcW * zoom
            cropH = cropW / targetAspect
        }

        val rand = Random(seed)
        val startX = rand.nextFloat() * (srcW - cropW).coerceAtLeast(0f)
        val startY = rand.nextFloat() * (srcH - cropH).coerceAtLeast(0f)
        val endX = rand.nextFloat() * (srcW - cropW).coerceAtLeast(0f)
        val endY = rand.nextFloat() * (srcH - cropH).coerceAtLeast(0f)

        val cx = startX + (endX - startX) * progress
        val cy = startY + (endY - startY) * progress

        // Apply camera rotation effect for added motion (subtle ±3 degrees)
        val rotStart = rand.nextFloat() * 6f - 3f
        val rotEnd = rand.nextFloat() * 6f - 3f
        val rotation = rotStart + (rotEnd - rotStart) * progress
        canvas.save()
        canvas.rotate(rotation, targetW / 2f, targetH / 2f)

        val srcRect = Rect(cx.toInt(), cy.toInt(), (cx + cropW).toInt(), (cy + cropH).toInt())
        val dstRect = RectF(
            (targetW - targetW * (targetW.toFloat() / (cropW * targetAspect)).coerceAtMost(1f)) / 2f,
            (targetH - targetH * (targetH.toFloat() / (cropH / targetAspect)).coerceAtMost(1f)) / 2f,
            targetW + (targetW * (targetW.toFloat() / (cropW * targetAspect)).coerceAtMost(1f) - targetW) / 2f + targetW,
            targetH + (targetH * (targetH.toFloat() / (cropH / targetAspect)).coerceAtMost(1f) - targetH) / 2f + targetH
        ).let { RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()) }

        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        canvas.restore()
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: android.media.MediaCodec.BufferInfo,
        trackIndexRef: IntRef,
        drainAll: Boolean = false
    ) {
        val timeout = if (drainAll) 10_000L else 0L
        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, timeout)
            if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndexRef.value = muxer.addTrack(codec.outputFormat)
                muxer.start()
                continue
            }
            if (outputBufferId < 0) continue

            val outputBuffer = codec.getOutputBuffer(outputBufferId) ?: continue
            if (bufferInfo.size > 0 && trackIndexRef.value >= 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndexRef.value, outputBuffer, bufferInfo)
            }
            codec.releaseOutputBuffer(outputBufferId, false)

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            if (!drainAll) break
        }
    }

    private class IntRef(var value: Int = -1)
}
