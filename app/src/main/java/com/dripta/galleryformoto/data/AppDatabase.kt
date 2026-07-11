package com.dripta.galleryformoto.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ── Existing entities ──────────────────────────────────────────────────────────

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val mediaId: Long)

@Entity(tableName = "hidden")
data class HiddenEntity(@PrimaryKey val mediaId: Long)

@Entity(tableName = "image_labels", primaryKeys = ["mediaId", "label"])
data class ImageLabelEntity(
    val mediaId: Long,
    val label: String
)

@Entity(tableName = "indexed_media")
data class IndexedMediaEntity(
    @PrimaryKey val mediaId: Long
)

// ── New entities for AI features ───────────────────────────────────────────────

@Entity(tableName = "photo_embeddings")
data class PhotoEmbeddingEntity(
    @PrimaryKey val mediaId: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray
)

@Entity(tableName = "photo_categories", primaryKeys = ["mediaId", "category"])
data class PhotoCategoryEntity(
    val mediaId: Long,
    val category: String,
    val confidence: Float
)

@Entity(tableName = "photo_hashes")
data class PhotoHashEntity(
    @PrimaryKey val mediaId: Long,
    val perceptualHash: Long,
    val differenceHash: Long,
    val thumbnailPath: String?
)

@Entity(tableName = "photo_locations")
data class PhotoLocationEntity(
    @PrimaryKey val mediaId: Long,
    val latitude: Double,
    val longitude: Double
)

@Entity(tableName = "photo_quality")
data class PhotoQualityEntity(
    @PrimaryKey val mediaId: Long,
    val sharpnessScore: Float,
    val isScreenshot: Boolean,
    val isBurstCandidate: Boolean,
    val exposureScore: Float
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateStart: Long,
    val dateEnd: Long,
    val locationName: String?,
    val coverMediaId: Long,
    val mediaIdsJson: String,
    val musicTrack: String?,
    val videoUri: String?
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val deletedAtMillis: Long
)

@Entity(tableName = "duplicate_groups")
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val representativeMediaId: Long,
    val duplicateMediaIdsJson: String,
    val groupType: String
)

@Entity(tableName = "indexing_queue", primaryKeys = ["mediaId", "taskType"])
data class IndexingQueueEntity(
    val mediaId: Long,
    val taskType: String
)

// ── Tags & Ratings ─────────────────────────────────────────────────────────────

// ── Existing DAOs ──────────────────────────────────────────────────────────────

@Dao
interface FavoriteDao {
    @Query("SELECT mediaId FROM favorites")
    fun getAllIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)
}

@Dao
interface HiddenDao {
    @Query("SELECT mediaId FROM hidden")
    fun getAllIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: HiddenEntity)

    @Query("DELETE FROM hidden WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)
}

@Dao
interface LabelDao {
    @Query("SELECT mediaId FROM image_labels WHERE label LIKE '%' || :query || '%'")
    suspend fun searchMediaIds(query: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(labels: List<ImageLabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markIndexed(entity: IndexedMediaEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM indexed_media WHERE mediaId = :mediaId)")
    suspend fun isIndexed(mediaId: Long): Boolean

    @Query("SELECT label FROM image_labels WHERE mediaId = :mediaId")
    suspend fun getLabelsForMedia(mediaId: Long): List<String>

    @Query("DELETE FROM image_labels WHERE mediaId = :mediaId")
    suspend fun deleteLabelsForMedia(mediaId: Long)

    @Query("DELETE FROM indexed_media WHERE mediaId = :mediaId")
    suspend fun clearIndexed(mediaId: Long)
}

// ── New DAOs ───────────────────────────────────────────────────────────────────

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoEmbeddingEntity)

    @Query("SELECT * FROM photo_embeddings WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: Long): PhotoEmbeddingEntity?

    @Query("SELECT * FROM photo_embeddings")
    suspend fun getAll(): List<PhotoEmbeddingEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PhotoCategoryEntity>)

    @Query("SELECT * FROM photo_categories WHERE mediaId = :mediaId")
    fun getCategoriesForMedia(mediaId: Long): Flow<List<PhotoCategoryEntity>>

    @Query("SELECT DISTINCT mediaId FROM photo_categories WHERE category = :category")
    suspend fun getMediaIdsByCategory(category: String): List<Long>

    @Query("SELECT DISTINCT category FROM photo_categories ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT mediaId FROM photo_categories WHERE category = :category ORDER BY confidence DESC LIMIT 1")
    suspend fun getCoverMediaId(category: String): Long?

    @Query("SELECT COUNT(DISTINCT mediaId) FROM photo_categories WHERE category = :category")
    fun getCountByCategory(category: String): Flow<Int>
}

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoLocationEntity)

    @Query("SELECT * FROM photo_locations")
    fun getAll(): Flow<List<PhotoLocationEntity>>

    @Query("SELECT mediaId FROM photo_locations")
    suspend fun getAllIndexedIds(): List<Long>

    @Query("DELETE FROM photo_locations WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)
}

@Dao
interface HashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoHashEntity)

    @Query("SELECT * FROM photo_hashes")
    suspend fun findAll(): List<PhotoHashEntity>

    @Query("DELETE FROM photo_hashes WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)
}

@Dao
interface QualityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoQualityEntity)

    @Query("SELECT * FROM photo_quality WHERE sharpnessScore < :threshold")
    suspend fun findBlurry(threshold: Float = 0.3f): List<PhotoQualityEntity>

    @Query("SELECT * FROM photo_quality WHERE isScreenshot = 1")
    suspend fun findScreenshots(): List<PhotoQualityEntity>

    @Query("SELECT * FROM photo_quality WHERE exposureScore < :threshold")
    suspend fun findBadlyExposed(threshold: Float = 0.25f): List<PhotoQualityEntity>

    @Query("SELECT * FROM photo_quality")
    suspend fun findAll(): List<PhotoQualityEntity>
}

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY dateStart DESC")
    fun getAll(): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(story: StoryEntity): Long

    @Query("UPDATE stories SET videoUri = :videoUri WHERE id = :storyId")
    suspend fun updateVideoUri(storyId: Long, videoUri: String)

    @Delete
    suspend fun delete(story: StoryEntity)
}

@Dao
interface DuplicateGroupDao {
    @Query("SELECT * FROM duplicate_groups ORDER BY groupId")
    fun getAll(): Flow<List<DuplicateGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<DuplicateGroupEntity>)

    @Query("DELETE FROM duplicate_groups")
    suspend fun clear()
}

@Dao
interface IndexingQueueDao {
    @Query("SELECT * FROM indexing_queue")
    suspend fun getPending(): List<IndexingQueueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entity: IndexingQueueEntity)

    @Query("DELETE FROM indexing_queue WHERE mediaId = :mediaId AND taskType = :taskType")
    suspend fun markDone(mediaId: Long, taskType: String)

    @Query("DELETE FROM indexing_queue")
    suspend fun clear()
}

// ── Tags & Ratings ──────────────────────────────────────────────────────────────

@Entity(tableName = "media_tags", primaryKeys = ["mediaId", "tag"])
data class MediaTagEntity(
    val mediaId: Long,
    val tag: String
)

@Entity(tableName = "media_meta")
data class MediaMetaEntity(
    @PrimaryKey val mediaId: Long,
    val rating: Int = 0,
    val colorLabel: String? = null
)

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTag(entity: MediaTagEntity)

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId AND tag = :tag")
    suspend fun removeTag(mediaId: Long, tag: String)

    @Query("SELECT tag FROM media_tags WHERE mediaId = :mediaId")
    fun getTagsForMedia(mediaId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT tag FROM media_tags ORDER BY tag")
    suspend fun getAllTags(): List<String>

    @Query("SELECT mediaId FROM media_tags WHERE tag = :tag")
    suspend fun getMediaIdsForTag(tag: String): List<Long>

    @Query("SELECT mediaId FROM media_tags WHERE tag LIKE '%' || :query || '%'")
    suspend fun searchMediaIds(query: String): List<Long>

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId")
    suspend fun deleteAllForMedia(mediaId: Long)
}

@Dao
interface MediaMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaMetaEntity)

    @Query("SELECT * FROM media_meta WHERE mediaId = :mediaId")
    fun getForMedia(mediaId: Long): Flow<MediaMetaEntity?>

    @Query("SELECT * FROM media_meta WHERE mediaId = :mediaId")
    suspend fun getForMediaOnce(mediaId: Long): MediaMetaEntity?

    @Query("SELECT mediaId FROM media_meta WHERE rating >= :minRating")
    suspend fun getMediaIdsWithMinRating(minRating: Int): List<Long>

    @Query("SELECT mediaId FROM media_meta WHERE colorLabel = :colorLabel")
    suspend fun getMediaIdsWithColorLabel(colorLabel: String): List<Long>

    @Query("DELETE FROM media_meta WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)
}

// ── Trash DAO ───────────────────────────────────────────────────────────────────

@Dao
interface TrashDao {
    @Query("SELECT mediaId FROM trash")
    fun getAllIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: TrashEntity)

    @Query("SELECT * FROM trash ORDER BY deletedAtMillis DESC")
    fun getAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash WHERE mediaId = :mediaId")
    suspend fun getById(mediaId: Long): TrashEntity?

    @Query("DELETE FROM trash WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)

    @Query("DELETE FROM trash WHERE deletedAtMillis < :cutoffMillis")
    suspend fun deleteBefore(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM trash")
    fun count(): Flow<Int>
}

// ── Database ───────────────────────────────────────────────────────────────────

@Database(
    entities = [
        FavoriteEntity::class, HiddenEntity::class, ImageLabelEntity::class, IndexedMediaEntity::class,
        PhotoEmbeddingEntity::class, PhotoCategoryEntity::class, PhotoHashEntity::class,
        PhotoQualityEntity::class, TrashEntity::class, StoryEntity::class,
        DuplicateGroupEntity::class, IndexingQueueEntity::class,
        MediaTagEntity::class, MediaMetaEntity::class, PhotoLocationEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun hiddenDao(): HiddenDao
    abstract fun labelDao(): LabelDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun locationDao(): LocationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun hashDao(): HashDao
    abstract fun qualityDao(): QualityDao
    abstract fun tagDao(): TagDao
    abstract fun mediaMetaDao(): MediaMetaDao
    abstract fun storyDao(): StoryDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
    abstract fun indexingQueueDao(): IndexingQueueDao
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gallery.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
    }
}
