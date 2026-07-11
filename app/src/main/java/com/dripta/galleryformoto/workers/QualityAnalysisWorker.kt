package com.dripta.galleryformoto.workers

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.PhotoQualityEntity
import com.dripta.galleryformoto.data.QualityAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class QualityAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val allMedia = MediaStoreRepository.queryAllMedia(applicationContext)
        val photos = allMedia.filterNot { it.isVideo || it.id in hiddenIds }

        val deviceWidth: Int
        val deviceHeight: Int
        val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            deviceWidth = metrics.widthPixels
            deviceHeight = metrics.heightPixels
        } else {
            deviceWidth = 0
            deviceHeight = 0
        }

        val existingQuality = db.qualityDao().findAll().associateBy { it.mediaId }.toMutableMap()

        for (item in photos) {
            if (item.id in existingQuality) continue
            try {
                val bitmap = com.dripta.galleryformoto.data.BitmapUtils.loadBitmap(applicationContext, item.uri, maxDimension = 1024)
                val sharpness = QualityAnalyzer.computeSharpness(bitmap)
                val isScreenshot = QualityAnalyzer.detectScreenshot(bitmap, deviceWidth, deviceHeight)
                val exposure = QualityAnalyzer.computeExposure(bitmap)
                val entity = PhotoQualityEntity(
                    mediaId = item.id,
                    sharpnessScore = sharpness,
                    isScreenshot = isScreenshot,
                    isBurstCandidate = false,
                    exposureScore = exposure
                )
                db.qualityDao().upsert(entity)
                existingQuality[item.id] = entity
            } catch (_: Exception) {}
        }

        val burstGroups = QualityAnalyzer.detectBurstGroups(photos)
        for (group in burstGroups) {
            for (item in group) {
                // Mark each member as burst candidate
                val existing = existingQuality[item.id]
                if (existing != null && !existing.isBurstCandidate) {
                    val updated = existing.copy(isBurstCandidate = true)
                    db.qualityDao().upsert(updated)
                    existingQuality[item.id] = updated
                }
            }
        }

        Result.success()
    }
}
