package com.dripta.galleryformoto.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

private const val MIN_CONFIDENCE = 0.6f

class ImageLabelingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val labelDao = db.labelDao()
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val photos = MediaStoreRepository.queryAllMedia(applicationContext)
            .filterNot { it.isVideo || it.id in hiddenIds }
        for (item in photos) {
            if (labelDao.isIndexed(item.id)) continue

            try {
                val inputImage = InputImage.fromFilePath(applicationContext, item.uri)
                val labels = labeler.process(inputImage).await()
                val entities = labels
                    .filter { it.confidence >= MIN_CONFIDENCE }
                    .map { ImageLabelEntity(item.id, it.text.lowercase()) }
                if (entities.isNotEmpty()) labelDao.insertLabels(entities)
            } catch (_: Exception) {}
            labelDao.markIndexed(IndexedMediaEntity(item.id))
        }

        labeler.close()
        return Result.success()
    }
}
