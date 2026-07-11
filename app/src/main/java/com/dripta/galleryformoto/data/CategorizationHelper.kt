package com.dripta.galleryformoto.data

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

class CategorizationHelper private constructor(context: Context) {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun classify(bitmap: Bitmap, minConfidence: Float = 0.5f): List<Pair<String, Float>> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return try {
            val labels = labeler.process(inputImage).await()
            labels
                .filter { it.confidence >= minConfidence }
                .mapNotNull { label ->
                    mapToCategory(label.text)?.let { category -> category to label.confidence }
                }
                .distinctBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun close() {
        labeler.close()
    }

    companion object {
        @Volatile
        private var instance: CategorizationHelper? = null

        fun getInstance(context: Context): CategorizationHelper =
            instance ?: synchronized(this) {
                instance ?: CategorizationHelper(context.applicationContext).also { instance = it }
            }

        private fun mapToCategory(mlkitLabel: String): String? {
            val l = mlkitLabel.lowercase()
            return when {
                l.contains("dog") || l.contains("cat") || l.contains("pet") ||
                l.contains("bird") || l.contains("fish") || l.contains("rabbit") ||
                l.contains("hamster") || l.contains("reptile") || l.contains("turtle") -> "pet"
                l.contains("food") || l.contains("meal") || l.contains("fruit") ||
                l.contains("vegetable") || l.contains("dessert") || l.contains("dish") ||
                l.contains("soup") || l.contains("pizza") || l.contains("pasta") ||
                l.contains("sandwich") || l.contains("burger") || l.contains("sushi") ||
                l.contains("cake") || l.contains("bread") || l.contains("cuisine") ||
                l.contains("beverage") || l.contains("drink") || l.contains("coffee") -> "food"
                l.contains("landscape") || l.contains("mountain") || l.contains("beach") ||
                l.contains("ocean") || l.contains("lake") || l.contains("river") ||
                l.contains("waterfall") || l.contains("sunset") || l.contains("sunrise") ||
                l.contains("sky") || l.contains("scenery") || l.contains("travel") -> "travel_landscape"
                l.contains("car") || l.contains("vehicle") || l.contains("motorcycle") ||
                l.contains("bicycle") || l.contains("truck") || l.contains("bus") ||
                l.contains("train") || l.contains("airplane") || l.contains("boat") ||
                l.contains("ship") || l.contains("transport") -> "vehicle"
                l.contains("document") || l.contains("paper") || l.contains("text") ||
                l.contains("book") || l.contains("magazine") || l.contains("newspaper") ||
                l.contains("letter") || l.contains("certificate") || l.contains("receipt") ||
                l.contains("invoice") || l.contains("form") -> "document"
                l.contains("person") || l.contains("people") || l.contains("group") ||
                l.contains("crowd") || l.contains("family") || l.contains("team") ||
                l.contains("audience") || l.contains("portrait") -> "people_group"
                l.contains("selfie") || l.contains("face") -> "selfie"
                l.contains("flower") || l.contains("plant") || l.contains("tree") ||
                l.contains("garden") || l.contains("forest") || l.contains("nature") ||
                l.contains("park") || l.contains("animal") || l.contains("wildlife") -> "nature"
                l.contains("art") || l.contains("painting") || l.contains("drawing") ||
                l.contains("illustration") || l.contains("sculpture") || l.contains("design") ||
                l.contains("graphic") || l.contains("sketch") || l.contains("poster") -> "art_illustration"
                l.contains("indoor") || l.contains("room") || l.contains("furniture") ||
                l.contains("kitchen") || l.contains("bathroom") || l.contains("bedroom") ||
                l.contains("living") || l.contains("office") || l.contains("building") -> "indoor"
                l.contains("outdoor") -> "outdoor"
                else -> null
            }
        }
    }
}
