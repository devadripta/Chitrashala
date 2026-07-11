package com.dripta.galleryformoto.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dripta.galleryformoto.data.AppDatabase
import com.dripta.galleryformoto.data.ImageLabelEntity
import com.dripta.galleryformoto.data.MediaStoreRepository
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class BarcodeScanningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val scanner = BarcodeScanning.getClient()
        val labelDao = db.labelDao()
        val hiddenIds = db.hiddenDao().getAllIds().first().toSet()
        val photos = MediaStoreRepository.queryAllMedia(applicationContext)
            .filterNot { it.isVideo || it.id in hiddenIds }

        for (item in photos) {
            val existing = labelDao.getLabelsForMedia(item.id)
            if (existing.any { it.startsWith("barcode:") }) continue
            try {
                val inputImage = InputImage.fromFilePath(applicationContext, item.uri)
                val barcodes = scanner.process(inputImage).await()
                val tags = barcodes.mapNotNull { barcode ->
                    when (barcode.valueType) {
                        Barcode.TYPE_URL -> "barcode:url"
                        Barcode.TYPE_WIFI -> "barcode:wifi"
                        Barcode.TYPE_CONTACT_INFO -> "barcode:contact"
                        Barcode.TYPE_PHONE -> "barcode:phone"
                        Barcode.TYPE_EMAIL -> "barcode:email"
                        Barcode.TYPE_TEXT -> "barcode:text"
                        else -> null
                    }
                }.distinct()
                if (tags.isNotEmpty()) {
                    labelDao.insertLabels(tags.map { ImageLabelEntity(item.id, it) })
                }
            } catch (_: Exception) {}
        }
        scanner.close()
        return Result.success()
    }
}
