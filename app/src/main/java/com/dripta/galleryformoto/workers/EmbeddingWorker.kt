package com.dripta.galleryformoto.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.EmbeddingHelper
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.PhotoEmbeddingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val helper = EmbeddingHelper.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val allMedia = MediaStoreRepository.queryAllMedia(applicationContext)
        val photos = allMedia.filterNot { it.isVideo || it.id in hiddenIds }

        for (item in photos) {
            if (db.embeddingDao().getByMediaId(item.id) != null) continue
            try {
                val bitmap = com.dripta.galleryformoto.data.BitmapUtils.loadBitmap(applicationContext, item.uri, maxDimension = 512)
                val embedding = helper.generateImageEmbedding(bitmap)
                val buf = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                embedding.forEach { buf.putFloat(it) }
                db.embeddingDao().upsert(PhotoEmbeddingEntity(item.id, buf.array()))
            } catch (_: Exception) {}
        }
        helper.close()
        Result.success()
    }
}
