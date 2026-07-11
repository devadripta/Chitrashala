package com.dripta.galleryformoto.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.HashUtils
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.PhotoHashEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DuplicateDetectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val allMedia = MediaStoreRepository.queryAllMedia(applicationContext)
        val photos = allMedia.filterNot { it.isVideo || it.id in hiddenIds }

        val existingHashes = db.hashDao().findAll().associateBy { it.mediaId }

        for (item in photos) {
            if (item.id in existingHashes) continue
            try {
                val bitmap = com.dripta.galleryformoto.data.BitmapUtils.loadBitmap(applicationContext, item.uri, maxDimension = 512)
                val pHash = HashUtils.pHash(bitmap)
                val dHash = HashUtils.dHash(bitmap)
                db.hashDao().upsert(
                    PhotoHashEntity(item.id, pHash, dHash, null)
                )
            } catch (_: Exception) {}
        }

        val allHashes = db.hashDao().findAll()
        val duplicateGroups = HashUtils.findDuplicateGroups(allHashes)
        if (duplicateGroups.isNotEmpty()) {
            db.duplicateGroupDao().clear()
            db.duplicateGroupDao().insertAll(duplicateGroups)
        }

        Result.success()
    }
}
