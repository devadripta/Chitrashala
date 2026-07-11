package com.dripta.galleryformoto.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Strips GPS location and camera-identifying EXIF metadata before sharing a photo, so sharing
 * to a social app or a contact doesn't leak where (and on what device) it was taken.
 *
 * The original file on disk is never touched, a stripped copy is written to the app's cache
 * and shared via [FileProvider] instead.
 */
object ExifStripper {

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF
    )

    private val CAMERA_TAGS = arrayOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SERIAL_NUMBER
    )

    /**
     * Returns a `content://` URI (via FileProvider) to a copy of the image at [sourceUri] with
     * GPS and camera-identifying EXIF tags removed. Only JPEG/PNG/WebP/HEIF support EXIF editing;
     * for anything else (including videos) the original [sourceUri] is returned unchanged.
     */
    suspend fun stripForShare(context: Context, sourceUri: Uri, mimeType: String, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            if (!mimeType.startsWith("image/")) return@withContext sourceUri

            val outDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val outFile = File(outDir, displayName)

            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext sourceUri

                val exif = ExifInterface(outFile.absolutePath)
                (GPS_TAGS + CAMERA_TAGS).forEach { tag -> exif.setAttribute(tag, null) }
                exif.saveAttributes()

                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            } catch (e: Exception) {
                // Metadata strip failed (unsupported format, corrupt file, etc.), fall back to
                // sharing the original rather than blocking the share entirely.
                sourceUri
            }
        }
}
