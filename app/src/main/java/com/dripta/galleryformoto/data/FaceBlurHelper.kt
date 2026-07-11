package com.dripta.galleryformoto.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/** Detects faces in a photo (via ML Kit's on-device, bundled face detector) so they can be blurred for privacy. */
object FaceBlurHelper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()

    /** Returns one padded bounding box per detected face, in [bitmap]'s own pixel coordinates. */
    suspend fun detectFaceRects(bitmap: Bitmap): List<Rect> {
        val detector = FaceDetection.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return try {
            val faces = detector.process(inputImage).await()
            faces.map { face ->
                val box = face.boundingBox
                // Pad so the blur fully covers hair/ears/chin, not just the facial-landmark box.
                val padX = (box.width() * 0.2f).toInt()
                val padY = (box.height() * 0.25f).toInt()
                Rect(
                    (box.left - padX).coerceAtLeast(0),
                    (box.top - padY).coerceAtLeast(0),
                    (box.right + padX).coerceAtMost(bitmap.width),
                    (box.bottom + padY).coerceAtMost(bitmap.height)
                )
            }
        } finally {
            detector.close()
        }
    }
}
