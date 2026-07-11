package com.dripta.galleryformoto.ui

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dripta.galleryformoto.data.Album
import com.dripta.galleryformoto.data.CleanupSuggestions
import com.dripta.galleryformoto.data.DuplicateGroupEntity
import com.dripta.galleryformoto.data.EnhanceMode
import com.dripta.galleryformoto.data.GalleryRepository
import com.dripta.galleryformoto.data.MediaItem
import com.dripta.galleryformoto.data.StoryEntity
import com.dripta.galleryformoto.data.groupByAlbum
import com.dripta.galleryformoto.workers.CategorizationWorker
import com.dripta.galleryformoto.workers.DuplicateDetectionWorker
import com.dripta.galleryformoto.data.ImageLabelingWorker
import com.dripta.galleryformoto.workers.EmbeddingWorker
import com.dripta.galleryformoto.workers.QualityAnalysisWorker
import com.dripta.galleryformoto.workers.StoryGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = GalleryRepository(application)
    val settings = com.dripta.galleryformoto.data.SettingsRepository(application)

    private val allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val isLoading = MutableStateFlow(true)

    val favoriteIds: StateFlow<Set<Long>> = repo.favoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val hiddenIds: StateFlow<Set<Long>> = repo.hiddenIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val trashIds: StateFlow<Set<Long>> = repo.trashIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val visibleMedia: StateFlow<List<MediaItem>> = combine(allMedia, hiddenIds, trashIds) { all, hidden, trashed ->
        all.filterNot { hidden.contains(it.id) || trashed.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = visibleMedia.map { it.groupByAlbum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMedia: StateFlow<List<MediaItem>> = combine(visibleMedia, favoriteIds) { media, favs ->
        media.filter { favs.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenMedia: StateFlow<List<MediaItem>> = combine(allMedia, hiddenIds, trashIds) { all, hidden, trashed ->
        all.filter { hidden.contains(it.id) && !trashed.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedMedia: StateFlow<List<MediaItem>> = combine(allMedia, trashIds) { all, trashed ->
        all.filter { trashed.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var activeViewerList: List<MediaItem> = emptyList()
        private set

    fun setActiveViewerList(list: List<MediaItem>) {
        activeViewerList = list
    }

    var editingItem: MediaItem? = null
        private set

    fun setEditingItem(item: MediaItem) {
        editingItem = item
    }

    // ── Semantic Search ─────────────────────────────────────────────────────

    private val semanticQuery = MutableStateFlow("")
    val semanticResults: StateFlow<List<MediaItem>> = semanticQuery
        .debounce(500)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                kotlinx.coroutines.flow.flow {
                    val visibleIds = visibleMedia.value.map { it.id }
                    val matchedIds = repo.searchBySemantics(query, visibleIds)
                    emit(visibleMedia.value.filter { it.id in matchedIds })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSemanticQuery(query: String) {
        semanticQuery.value = query
    }

    // ── Label-based search (legacy, still functional) ───────────────────────

    private val searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<MediaItem>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                kotlinx.coroutines.flow.flow {
                    val ids = repo.searchByLabel(query).toSet() + repo.searchTags(query).toSet()
                    emit(visibleMedia.value.filter { it.id in ids })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    // ── Categories / Smart Albums ───────────────────────────────────────────

    fun smartAlbumMedia(category: String): StateFlow<List<MediaItem>> {
        return repo.getSmartAlbumMedia(category, visibleMedia.value.map { it.id }.toSet())
            .map { ids -> visibleMedia.value.filter { it.id in ids } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    suspend fun getAllCategories(): List<String> = repo.getAllCategories()

    suspend fun getCategoryCoverMediaId(category: String): Long? = repo.getCategoryCoverMediaId(category)

    fun getCategoryCount(category: String) = repo.getCategoryCount(category)

    // ── Cleanup ─────────────────────────────────────────────────────────────

    private val _cleanupSuggestions = MutableStateFlow<CleanupSuggestions?>(null)
    val cleanupSuggestions: StateFlow<CleanupSuggestions?> = _cleanupSuggestions

    private val _totalCleanupCount = MutableStateFlow(0)
    val totalCleanupCount: StateFlow<Int> = _totalCleanupCount

    fun refreshCleanupSuggestions() {
        viewModelScope.launch {
            _cleanupSuggestions.value = repo.getCleanupSuggestions()
            _totalCleanupCount.value = repo.getTotalCleanupCount()
        }
    }

    // ── Duplicates ──────────────────────────────────────────────────────────

    val duplicateGroups: StateFlow<List<DuplicateGroupEntity>> =
        repo.getDuplicateGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDuplicatesExcept(keepId: Long, duplicateIds: List<Long>, onIntentSender: (IntentSender) -> Unit) {
        viewModelScope.launch {
            val sender = repo.deleteDuplicatesExcept(keepId, duplicateIds)
            if (sender != null) onIntentSender(sender)
            refreshCleanupSuggestions()
        }
    }

    // ── Stories ─────────────────────────────────────────────────────────────

    val stories: StateFlow<List<StoryEntity>> =
        repo.getAllStories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteStory(storyId: Long) {
        viewModelScope.launch {
            repo.deleteStory(storyId)
        }
    }

    // ── Enhancement ─────────────────────────────────────────────────────────

    suspend fun enhancePhoto(uri: Uri, mode: EnhanceMode): Bitmap =
        repo.enhancePhoto(uri, mode)

    // ── Lifecycle ───────────────────────────────────────────────────────────

    private var indexingScheduled = false

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            allMedia.value = repo.loadAllMedia()
            isLoading.value = false
        }
        if (!indexingScheduled) {
            indexingScheduled = true
            scheduleIndexing()
        }
        refreshCleanupSuggestions()
    }

    private fun scheduleIndexing() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val locationExtraction = OneTimeWorkRequestBuilder<com.dripta.galleryformoto.workers.LocationExtractionWorker>()
            .setConstraints(constraints)
            .build()
        val imageLabeling = OneTimeWorkRequestBuilder<ImageLabelingWorker>()
            .setConstraints(constraints)
            .build()
        val barcodeScan = OneTimeWorkRequestBuilder<com.dripta.galleryformoto.workers.BarcodeScanningWorker>()
            .setConstraints(constraints)
            .build()
        val embedding = OneTimeWorkRequestBuilder<EmbeddingWorker>()
            .setConstraints(constraints)
            .build()
        val categorization = OneTimeWorkRequestBuilder<CategorizationWorker>()
            .setConstraints(constraints)
            .build()
        val duplicateDetection = OneTimeWorkRequestBuilder<DuplicateDetectionWorker>()
            .setConstraints(constraints)
            .build()
        val qualityAnalysis = OneTimeWorkRequestBuilder<QualityAnalysisWorker>()
            .setConstraints(constraints)
            .build()
        val storyGeneration = OneTimeWorkRequestBuilder<StoryGenerationWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .beginUniqueWork(
                "gallery_indexing",
                ExistingWorkPolicy.KEEP,
                locationExtraction
            )
            .then(imageLabeling)
            .then(barcodeScan)
            .then(embedding)
            .then(categorization)
            .then(duplicateDetection)
            .then(qualityAnalysis)
            .then(storyGeneration)
            .enqueue()
    }

    // ── Core actions ────────────────────────────────────────────────────────

    fun toggleFavorite(mediaId: Long) {
        viewModelScope.launch {
            val isFav = favoriteIds.value.contains(mediaId)
            repo.setFavorite(mediaId, !isFav)
        }
    }

    fun toggleHidden(mediaId: Long) {
        viewModelScope.launch {
            val isHidden = hiddenIds.value.contains(mediaId)
            repo.setHidden(mediaId, !isHidden)
        }
    }

    fun restoreFromTrash(mediaId: Long) {
        viewModelScope.launch {
            repo.restoreFromTrash(mediaId)
            refresh()
        }
    }

    suspend fun permanentlyDeleteTrashed(mediaIds: List<Long>): IntentSender? = repo.permanentlyDeleteTrashed(mediaIds)
    suspend fun emptyTrash(): IntentSender? = repo.emptyTrash()

    fun setFavoriteForIds(ids: Set<Long>, isFavorite: Boolean) {
        viewModelScope.launch {
            ids.forEach { repo.setFavorite(it, isFavorite) }
        }
    }

    fun setHiddenForIds(ids: Set<Long>, isHidden: Boolean) {
        viewModelScope.launch {
            ids.forEach { repo.setHidden(it, isHidden) }
        }
    }

    suspend fun deleteMedia(uris: List<Uri>): IntentSender? = repo.deleteMedia(uris)

    suspend fun deleteAlbum(albumId: Long): IntentSender? {
        val uris = visibleMedia.value.filter { it.bucketId == albumId }.map { it.uri }
        return if (uris.isNotEmpty()) repo.deleteMedia(uris) else null
    }

    suspend fun renameMedia(uri: Uri, newName: String): IntentSender? = repo.renameMedia(uri, newName)
    suspend fun moveMedia(uris: List<Uri>, targetFolderPath: String): IntentSender? = repo.moveMedia(uris, targetFolderPath)
    suspend fun copyMedia(uri: Uri, targetFolderPath: String) = repo.copyMedia(uri, targetFolderPath)

    // ── Tags & Ratings ──────────────────────────────────────────────────────────

    fun tagsForMedia(mediaId: Long) = repo.tagsForMedia(mediaId)
    fun metaForMedia(mediaId: Long) = repo.metaForMedia(mediaId)

    fun addTag(mediaId: Long, tag: String) {
        viewModelScope.launch { repo.addTag(mediaId, tag) }
    }

    fun removeTag(mediaId: Long, tag: String) {
        viewModelScope.launch { repo.removeTag(mediaId, tag) }
    }

    fun setRating(mediaId: Long, rating: Int) {
        viewModelScope.launch { repo.setRating(mediaId, rating) }
    }

    fun setColorLabel(mediaId: Long, colorLabel: String?) {
        viewModelScope.launch { repo.setColorLabel(mediaId, colorLabel) }
    }

    suspend fun getAllTags(): List<String> = repo.getAllTags()

    suspend fun getMediaWithMinRating(minRating: Int): List<MediaItem> {
        val ids = repo.getMediaIdsWithMinRating(minRating).toSet()
        return visibleMedia.value.filter { it.id in ids }
    }
}
