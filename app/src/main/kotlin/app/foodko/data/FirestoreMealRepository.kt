package app.foodko.data

import app.foodko.data.model.MealEntry
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** [MealRepository] on Cloud Firestore. One live listener for everything; the data set is small (spec §10). */
class FirestoreMealRepository(private val db: FirebaseFirestore, uid: String) : MealRepository {

    private val collection = db.collection("users").document(uid).collection("meals")

    override val all: Flow<List<MealEntry>> =
        collection.orderBy("timestamp", Query.Direction.ASCENDING).snapshots<MealEntry>()

    override suspend fun save(meal: MealEntry): String {
        val doc = if (meal.id.isEmpty()) collection.document() else collection.document(meal.id)
        doc.set(meal.copy(id = doc.id, updatedAt = null)).await()
        return doc.id
    }

    override suspend fun delete(id: String) {
        collection.document(id).delete().await()
    }

    /** References of this user's meals pointing at any of [foodIds]. */
    suspend fun findByFoodIds(foodIds: List<String>): List<DocumentReference> =
        foodIds.distinct().chunked(WHERE_IN_LIMIT).flatMap { chunk ->
            collection.whereIn("foodId", chunk).get().await().documents.map { it.reference }
        }

    suspend fun repoint(refs: List<DocumentReference>, newFoodId: String) {
        refs.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(it, "foodId", newFoodId) }
            batch.commit().await()
        }
    }

    override suspend fun repoint(meals: List<MealEntry>, mapping: Map<String, String>) {
        val updates = meals.mapNotNull { m -> mapping[m.foodId]?.let { m.id to it } }
        updates.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (id, foodId) -> batch.update(collection.document(id), "foodId", foodId) }
            batch.commit().await()
        }
    }
}
