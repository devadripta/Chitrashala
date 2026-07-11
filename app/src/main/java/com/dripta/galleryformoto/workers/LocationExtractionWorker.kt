package com.dripta.galleryformoto.workers

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.dripta.galleryformoto.data.PhotoLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * MediaStore stopped populating its LATITUDE/LONGITUDE columns on Android 10+ (privacy
 * redaction), so the Places feature needs to read GPS coordinates straight out of each
 * photo's EXIF data instead. Requires ACCESS_MEDIA_LOCATION to get the unredacted original
 * via [MediaStore.setRequireOriginal], without it this silently finds nothing, same as before.
 */
class LocationExtractionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val alreadyIndexed = db.locationDao().getAllIndexedIds().toSet()
        val photos = MediaStoreRepository.queryAllMedia(applicationContext)
            .filterNot { it.isVideo || it.id in hiddenIds || it.id in alreadyIndexed }

        var found = 0
        var errors = 0
        for (item in photos) {
            try {
                val originalUri = MediaStore.setRequireOriginal(item.uri)
                applicationContext.contentResolver.openInputStream(originalUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val latLong = exif.latLong
                    if (latLong != null) {
                        db.locationDao().upsert(PhotoLocationEntity(item.id, latLong[0], latLong[1]))
                        found++
                    }
                }
            } catch (e: Exception) {
                errors++
                if (errors <= 5) Log.e("LocationExtraction", "failed for ${item.uri}", e)
            }
        }
        Log.i("LocationExtraction", "processed=${photos.size} found=$found errors=$errors")

        Result.success()
    }
}
