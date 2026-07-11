package com.dripta.galleryformoto.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateMillis: Long,
    val bucketId: Long,
    val bucketName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMillis: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** e.g. "Pictures/WhatsApp Images/Sent/", the folder path this file physically lives under. */
    val relativePath: String = ""
)

data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val itemCount: Int
)

fun List<MediaItem>.groupByAlbum(): List<Album> =
    groupBy { it.bucketId }
        .map { (bucketId, items) ->
            val sorted = items.sortedByDescending { it.dateMillis }
            sorted.first().dateMillis to Album(
                id = bucketId,
                name = sorted.first().bucketName,
                coverUri = sorted.first().uri,
                itemCount = sorted.size
            )
        }
        .sortedByDescending { it.first }
        .map { it.second }

fun List<MediaItem>.groupByDate(): Map<String, List<MediaItem>> {
    val formatter = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
    val today = formatter.format(System.currentTimeMillis())
    val yesterday = formatter.format(System.currentTimeMillis() - 86400000)
    
    return groupBy {
        val dateStr = formatter.format(it.dateMillis)
        when (dateStr) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> dateStr
        }
    }
}
