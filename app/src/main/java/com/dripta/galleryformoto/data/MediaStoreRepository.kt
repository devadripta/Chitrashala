package com.dripta.galleryformoto.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreRepository {

    suspend fun queryAllMedia(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        (queryImages(context) + queryVideos(context)).sortedByDescending { it.dateMillis }
    }

    private fun queryImages(context: Context): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH,
            "latitude",
            "longitude"
        )
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val latCol = cursor.getColumnIndex("latitude")
            val lngCol = cursor.getColumnIndex("longitude")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                items.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        dateMillis = cursor.getLong(dateCol) * 1000L,
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        isVideo = false,
                        latitude = if (latCol != -1) cursor.getDouble(latCol).takeIf { it != 0.0 } else null,
                        longitude = if (lngCol != -1) cursor.getDouble(lngCol).takeIf { it != 0.0 } else null,
                        relativePath = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    )
                )
            }
        }
        return items
    }

    private fun queryVideos(context: Context): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RELATIVE_PATH,
            "latitude",
            "longitude"
        )
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val latCol = cursor.getColumnIndex("latitude")
            val lngCol = cursor.getColumnIndex("longitude")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                items.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        dateMillis = cursor.getLong(dateCol) * 1000L,
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        isVideo = true,
                        durationMillis = cursor.getLong(durationCol),
                        latitude = if (latCol != -1) cursor.getDouble(latCol).takeIf { it != 0.0 } else null,
                        longitude = if (lngCol != -1) cursor.getDouble(lngCol).takeIf { it != 0.0 } else null,
                        relativePath = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    )
                )
            }
        }
        return items
    }
}
