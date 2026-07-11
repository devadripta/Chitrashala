package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap

/**
 * CLIP-style semantic search (natural-language photo search).
 *
 * Not implemented: a real on-device CLIP vision+text encoder model would need to be sourced,
 * converted to .tflite, and bundled, that hasn't been done. Every method below is a safe
 * no-op so the rest of the app builds and runs; semantic search stays disabled until a real
 * model is wired in here.
 */
class EmbeddingHelper private constructor() {

    fun generateImageEmbedding(bitmap: Bitmap): FloatArray = FloatArray(EMBEDDING_DIM)

    fun generateTextEmbedding(text: String): FloatArray = FloatArray(EMBEDDING_DIM)

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float = 0f

    fun close() {}

    companion object {
        private const val EMBEDDING_DIM = 512

        @Volatile
        private var instance: EmbeddingHelper? = null

        fun getInstance(context: Context): EmbeddingHelper =
            instance ?: synchronized(this) {
                instance ?: EmbeddingHelper().also { instance = it }
            }
    }
}
