package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import java.io.InputStream

class MediaIndexer(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun indexMedia(item: MediaItem) {
        if (db.labelDao().isIndexed(item.id)) return

        try {
            val bitmap = loadBitmap(item.uri) ?: return
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            
            val labelEntities = labels.map { label ->
                ImageLabelEntity(mediaId = item.id, label = label.text.lowercase())
            }
            
            db.labelDao().insertLabels(labelEntities)
            db.labelDao().markIndexed(IndexedMediaEntity(item.id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4 // Downsample for performance
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            null
        }
    }
}
