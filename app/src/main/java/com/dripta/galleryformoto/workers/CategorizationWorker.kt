package com.dripta.galleryformoto.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.CategorizationHelper
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.PhotoCategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CategorizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val helper = CategorizationHelper.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val allMedia = MediaStoreRepository.queryAllMedia(applicationContext)
        val photos = allMedia.filterNot { it.isVideo || it.id in hiddenIds }

        for (item in photos) {
            val existing = db.categoryDao().getCategoriesForMedia(item.id).first()
            if (existing.isNotEmpty()) continue
            try {
                val bitmap = com.dripta.galleryformoto.data.BitmapUtils.loadBitmap(applicationContext, item.uri, maxDimension = 512)
                val classifications = helper.classify(bitmap)
                if (classifications.isNotEmpty()) {
                    db.categoryDao().insertAll(
                        classifications.map { (category, confidence) ->
                            PhotoCategoryEntity(item.id, category, confidence)
                        }
                    )
                }
            } catch (_: Exception) {}
        }
        // helper is a process-wide singleton (model load is expensive); don't close it here,
        // it needs to stay usable for the next time this worker (or a future caller) runs.
        Result.success()
    }
}
