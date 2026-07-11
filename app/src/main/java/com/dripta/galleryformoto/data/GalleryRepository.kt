package com.dripta.galleryformoto.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CleanupSuggestions(
    val blurryMediaIds: Set<Long>,
    val screenshotIds: Set<Long>,
    val badlyExposedIds: Set<Long>,
    val duplicateGroups: List<DuplicateGroupEntity>,
    val burstGroupIds: Set<Long>
)

enum class EnhanceMode { SMART_ENHANCE, SUPER_RESOLVE, COLORIZE, RESTORE_FACES, DENOISE_LIGHT, DENOISE_MEDIUM, DENOISE_HEAVY }

class GalleryRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val enhancementHelper by lazy { EnhancementHelper.getInstance(context) }
    private val embeddingHelper by lazy { EmbeddingHelper.getInstance(context) }

    // ── Existing methods ────────────────────────────────────────────────────────

    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        // Return every item, including trashed/hidden ones. The ViewModel derives
        // visibleMedia (hides trashed + hidden), hiddenMedia, and trashedMedia from this
        // single source; filtering trashed out here would leave the Bin permanently empty.
        val items = MediaStoreRepository.queryAllMedia(context)
        val locations = db.locationDao().getAll().first().associateBy { it.mediaId }

        items.map { item ->
            val loc = locations[item.id]
            if (loc != null) item.copy(latitude = loc.latitude, longitude = loc.longitude) else item
        }
    }

    fun favoriteIds(): Flow<Set<Long>> = db.favoriteDao().getAllIds().map { it.toSet() }
    fun hiddenIds(): Flow<Set<Long>> = db.hiddenDao().getAllIds().map { it.toSet() }
    fun trashIds(): Flow<Set<Long>> = db.trashDao().getAllIds().map { it.toSet() }

    suspend fun searchByLabel(query: String): Set<Long> = withContext(Dispatchers.IO) {
        db.labelDao().searchMediaIds(query.trim().lowercase()).toSet()
    }

    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) db.favoriteDao().add(FavoriteEntity(mediaId)) else db.favoriteDao().remove(mediaId)
    }

    suspend fun setHidden(mediaId: Long, isHidden: Boolean) = withContext(Dispatchers.IO) {
        if (isHidden) {
            db.hiddenDao().add(HiddenEntity(mediaId))
            db.labelDao().deleteLabelsForMedia(mediaId)
            db.labelDao().clearIndexed(mediaId)
            db.tagDao().deleteAllForMedia(mediaId)
        } else {
            db.hiddenDao().remove(mediaId)
        }
    }

    suspend fun renameMedia(uri: Uri, newName: String): IntentSender? = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                context.contentResolver.update(uri, contentValues, null, null)
                null
            } catch (e: SecurityException) {
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
            }
        }
        try {
            context.contentResolver.update(uri, contentValues, null, null)
            null
        } catch (e: RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        }
    }

    suspend fun moveMedia(uris: List<Uri>, targetFolderPath: String): IntentSender? = withContext(Dispatchers.IO) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, targetFolderPath)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                uris.forEach { context.contentResolver.update(it, contentValues, null, null) }
                null
            } catch (e: SecurityException) {
                MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
            }
        }
        try {
            uris.forEach { context.contentResolver.update(it, contentValues, null, null) }
            null
        } catch (e: RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        }
    }

    suspend fun copyMedia(uri: Uri, targetFolderPath: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE
        )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(0)
                val mimeType = cursor.getString(1)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetFolderPath)
                }
                val collection = if (mimeType.startsWith("video")) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val newUri = resolver.insert(collection, contentValues)
                if (newUri != null) {
                    resolver.openInputStream(uri)?.use { input ->
                        resolver.openOutputStream(newUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    suspend fun deleteMedia(uris: List<Uri>): IntentSender? = withContext(Dispatchers.IO) {
        val allMedia = MediaStoreRepository.queryAllMedia(context)
        val now = System.currentTimeMillis()
        uris.forEach { uri ->
            val item = allMedia.find { it.uri == uri } ?: return@forEach
            db.trashDao().add(TrashEntity(item.id, uri.toString(), item.displayName, item.mimeType, now))
        }
        null
    }

    suspend fun restoreFromTrash(mediaId: Long) = withContext(Dispatchers.IO) {
        db.trashDao().remove(mediaId)
    }

    /**
     * Drops trash rows whose underlying file no longer exists in MediaStore.
     *
     * On Android 11+ a permanent delete hands the user a system confirmation dialog and we
     * return early, so we never learn whether they confirmed. Without this, the rows linger
     * forever after the files are gone. Call it once the delete flow returns.
     */
    suspend fun purgeMissingTrashEntries() = withContext(Dispatchers.IO) {
        val liveIds = MediaStoreRepository.queryAllMedia(context).map { it.id }.toSet()
        db.trashDao().getAll().first()
            .filterNot { it.mediaId in liveIds }
            .forEach { db.trashDao().remove(it.mediaId) }
    }

    suspend fun permanentlyDeleteTrashed(mediaIds: List<Long>): IntentSender? = withContext(Dispatchers.IO) {
        val trashItems = mediaIds.mapNotNull { db.trashDao().getById(it) }
        val uris = trashItems.map { Uri.parse(it.uri) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        uris.forEach { context.contentResolver.delete(it, null, null) }
        mediaIds.forEach { db.trashDao().remove(it) }
        null
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val items = db.trashDao().getAll().first()
        val uris = items.map { Uri.parse(it.uri) }
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return@withContext MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            }
            uris.forEach { context.contentResolver.delete(it, null, null) }
        }
        db.trashDao().deleteBefore(Long.MAX_VALUE)
        null
    }

    suspend fun deleteTrashOlderThan(cutoffMillis: Long) = withContext(Dispatchers.IO) {
        db.trashDao().deleteBefore(cutoffMillis)
    }

    // ── Semantic Search ─────────────────────────────────────────────────────────

    suspend fun searchBySemantics(query: String, visibleMediaIds: List<Long>, limit: Int = 50): List<Long> = withContext(Dispatchers.IO) {
        val textEmbedding = embeddingHelper.generateTextEmbedding(query)
        val allEmbeddings = db.embeddingDao().getAll().filter { it.mediaId in visibleMediaIds }
        allEmbeddings
            .map { entity ->
                val buf = ByteBuffer.wrap(entity.embedding).order(ByteOrder.LITTLE_ENDIAN)
                val embedding = FloatArray(512) { buf.float }
                val similarity = embeddingHelper.cosineSimilarity(textEmbedding, embedding)
                Pair(entity.mediaId, similarity)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // ── Categories ──────────────────────────────────────────────────────────────

    fun getCategoriesForMedia(mediaId: Long): Flow<List<PhotoCategoryEntity>> =
        db.categoryDao().getCategoriesForMedia(mediaId)

    fun getSmartAlbumMedia(category: String, visibleMediaIds: Set<Long>): Flow<List<Long>> =
        flow { emit(db.categoryDao().getMediaIdsByCategory(category).filter { it in visibleMediaIds }) }

    suspend fun getAllCategories(): List<String> = withContext(Dispatchers.IO) {
        db.categoryDao().getAllCategories().first()
    }

    suspend fun getCategoryCoverMediaId(category: String): Long? = withContext(Dispatchers.IO) {
        db.categoryDao().getCoverMediaId(category)
    }

    fun getCategoryCount(category: String): Flow<Int> =
        db.categoryDao().getCountByCategory(category)

    // ── Duplicates ──────────────────────────────────────────────────────────────

    fun getDuplicateGroups(): Flow<List<DuplicateGroupEntity>> =
        db.duplicateGroupDao().getAll()

    suspend fun deleteDuplicatesExcept(keepId: Long, duplicateIds: List<Long>): IntentSender? = withContext(Dispatchers.IO) {
        val allMedia = MediaStoreRepository.queryAllMedia(context)
        val uris = duplicateIds.mapNotNull { dupId -> allMedia.find { it.id == dupId }?.uri }
        if (uris.isEmpty()) return@withContext null
        deleteMedia(uris)
    }

    // ── Quality / Cleanup ───────────────────────────────────────────────────────

    suspend fun getCleanupSuggestions(): CleanupSuggestions = withContext(Dispatchers.IO) {
        val blurry = db.qualityDao().findBlurry().map { it.mediaId }.toSet()
        val screenshots = db.qualityDao().findScreenshots().map { it.mediaId }.toSet()
        val badlyExposed = db.qualityDao().findBadlyExposed().map { it.mediaId }.toSet()
        val duplicates = db.duplicateGroupDao().getAll().first()
        val burstCandidates = db.qualityDao().findAll().filter { it.isBurstCandidate }.map { it.mediaId }.toSet()

        CleanupSuggestions(
            blurryMediaIds = blurry,
            screenshotIds = screenshots,
            badlyExposedIds = badlyExposed,
            duplicateGroups = duplicates,
            burstGroupIds = burstCandidates
        )
    }

    suspend fun getTotalCleanupCount(): Int = withContext(Dispatchers.IO) {
        val blurry = db.qualityDao().findBlurry().size
        val screenshots = db.qualityDao().findScreenshots().size
        val badlyExposed = db.qualityDao().findBadlyExposed().size
        val duplicates = db.duplicateGroupDao().getAll().first().sumOf { group ->
            val ids = parseIdJson(group.duplicateMediaIdsJson)
            ids.size
        }
        blurry + screenshots + badlyExposed + duplicates
    }

    private fun parseIdJson(json: String): List<Long> {
        return json.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    // ── Stories ─────────────────────────────────────────────────────────────────

    fun getAllStories(): Flow<List<StoryEntity>> = db.storyDao().getAll()

    suspend fun deleteStory(storyId: Long) = withContext(Dispatchers.IO) {
        db.storyDao().getAll().first().find { it.id == storyId }?.let {
            db.storyDao().delete(it)
            it.videoUri?.let { uriStr ->
                try {
                    val file = java.io.File(Uri.parse(uriStr).path ?: "")
                    file.delete()
                } catch (_: Exception) {}
            }
        }
    }

    // ── Enhancement ─────────────────────────────────────────────────────────────

    suspend fun enhancePhoto(uri: Uri, mode: EnhanceMode): android.graphics.Bitmap = withContext(Dispatchers.IO) {
        val bitmap = BitmapUtils.loadBitmap(context, uri, maxDimension = 3000)
        when (mode) {
            EnhanceMode.SMART_ENHANCE -> enhancementHelper.smartEnhance(bitmap)
            EnhanceMode.SUPER_RESOLVE -> enhancementHelper.superResolve(bitmap)
            EnhanceMode.COLORIZE -> enhancementHelper.colorize(bitmap)
            EnhanceMode.RESTORE_FACES -> enhancementHelper.restoreFaces(bitmap)
            EnhanceMode.DENOISE_LIGHT -> enhancementHelper.denoise(bitmap, 0.25f)
            EnhanceMode.DENOISE_MEDIUM -> enhancementHelper.denoise(bitmap, 0.5f)
            EnhanceMode.DENOISE_HEAVY -> enhancementHelper.denoise(bitmap, 0.8f)
        }
    }

    // ── Tags & Ratings ──────────────────────────────────────────────────────────

    fun tagsForMedia(mediaId: Long): Flow<List<String>> = db.tagDao().getTagsForMedia(mediaId)

    suspend fun addTag(mediaId: Long, tag: String) = withContext(Dispatchers.IO) {
        val clean = tag.trim().lowercase()
        if (clean.isNotEmpty()) db.tagDao().addTag(MediaTagEntity(mediaId, clean))
    }

    suspend fun removeTag(mediaId: Long, tag: String) = withContext(Dispatchers.IO) {
        db.tagDao().removeTag(mediaId, tag.trim().lowercase())
    }

    suspend fun getAllTags(): List<String> = withContext(Dispatchers.IO) { db.tagDao().getAllTags() }

    suspend fun searchTags(query: String): List<Long> = withContext(Dispatchers.IO) {
        db.tagDao().searchMediaIds(query.trim().lowercase())
    }

    suspend fun getMediaIdsForTag(tag: String): List<Long> = withContext(Dispatchers.IO) {
        db.tagDao().getMediaIdsForTag(tag)
    }

    fun metaForMedia(mediaId: Long): Flow<MediaMetaEntity?> = db.mediaMetaDao().getForMedia(mediaId)

    suspend fun setRating(mediaId: Long, rating: Int) = withContext(Dispatchers.IO) {
        val existing = db.mediaMetaDao().getForMediaOnce(mediaId)
        db.mediaMetaDao().upsert(MediaMetaEntity(mediaId, rating, existing?.colorLabel))
    }

    suspend fun setColorLabel(mediaId: Long, colorLabel: String?) = withContext(Dispatchers.IO) {
        val existing = db.mediaMetaDao().getForMediaOnce(mediaId)
        db.mediaMetaDao().upsert(MediaMetaEntity(mediaId, existing?.rating ?: 0, colorLabel))
    }

    suspend fun getMediaIdsWithMinRating(minRating: Int): List<Long> = withContext(Dispatchers.IO) {
        db.mediaMetaDao().getMediaIdsWithMinRating(minRating)
    }
}
