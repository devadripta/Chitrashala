package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pulls a single still frame out of a video at a given playback position. */
object VideoFrameExtractor {

    suspend fun extractFrame(context: Context, uri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            // OPTION_CLOSEST (not _SYNC) decodes the exact frame rather than snapping to the
            // nearest keyframe, so the saved still matches what the user actually paused on.
            retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
