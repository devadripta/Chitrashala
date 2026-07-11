package com.dripta.galleryformoto.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.StoryEntity
import com.dripta.galleryformoto.data.StoryGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class StoryGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val allMedia = MediaStoreRepository.queryAllMedia(applicationContext)
        val visibleMedia = allMedia.filterNot { it.id in hiddenIds }

        val events = StoryGenerator.detectEvents(visibleMedia)
        if (events.isEmpty()) return@withContext Result.success()

        val outputDir = File(applicationContext.filesDir, "stories")
        outputDir.mkdirs()

        for (event in events) {
            val bestPhotos = StoryGenerator.selectBestPhotos(event.mediaItems)
            val outputFile = File(outputDir, "story_${event.dateStart}.mp4")

            try {
                val videoUri = StoryGenerator.generateMontage(applicationContext, bestPhotos, outputFile)
                db.storyDao().insert(
                    StoryEntity(
                        title = event.title,
                        dateStart = event.dateStart,
                        dateEnd = event.dateEnd,
                        locationName = event.locationName,
                        coverMediaId = event.coverMediaId,
                        mediaIdsJson = bestPhotos.joinToString(prefix = "[", postfix = "]") { it.id.toString() },
                        musicTrack = null,
                        videoUri = videoUri.toString()
                    )
                )
            } catch (_: Exception) {}
        }

        Result.success()
    }
}
