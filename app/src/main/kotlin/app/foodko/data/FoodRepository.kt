package app.foodko.data

import app.foodko.data.model.Food
import app.foodko.domain.normalizeForSearch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** The shared `foods` collection. */
class FoodRepository(private val db: FirebaseFirestore) {

    private val collection = db.collection("foods")

    /** Every food document, including archived and redirected ones; FoodIndex sorts them out. */
    val foods: Flow<List<Food>> = collection.orderBy("labelLower", Query.Direction.ASCENDING).snapshots<Food>()

    /** Creates or updates a food; returns its id. Search key and timestamps are maintained here. */
    suspend fun save(food: Food, uid: String): String {
        val doc = if (food.id.isEmpty()) collection.document() else collection.document(food.id)
        val prepared = food.copy(
            id = doc.id,
            label = food.label.trim(),
            labelLower = normalizeForSearch(food.label),
            createdBy = food.createdBy.ifEmpty { uid },
            updatedAt = null, // @ServerTimestamp fills it in
        )
        doc.set(prepared).await()
        return doc.id
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        collection.document(id).update(mapOf("archived" to archived, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
    }

    /**
     * Merge (spec Flow 7). In one atomic batch: the survivor is updated with [survivor]'s values,
     * every id in [mergedIds] becomes a redirect to it, and the current user's meals and recent
     * list are rewritten. The other user's data is healed by [RedirectHealer] on their phone.
     */
    suspend fun merge(
        uid: String,
        survivor: Food,
        mergedIds: List<String>,
        meals: MealRepository,
        settings: SettingsRepository,
        lastUsedFoodIds: List<String>,
    ) {
        require(survivor.id.isNotEmpty()) { "survivor must be an existing food" }
        val losers = mergedIds.filter { it != survivor.id }.distinct()
        if (losers.isEmpty()) {
            save(survivor, uid)
            return
        }
        val mapping = losers.associateWith { survivor.id }
        val mealDocs = meals.findByFoodIds(losers)
        val newRecent = lastUsedFoodIds.map { mapping[it] ?: it }.distinct()

        // Firestore caps a batch at 500 writes; the fixed part is 1 + losers + 1.
        val batch = db.batch()
        batch.set(
            collection.document(survivor.id),
            survivor.copy(
                label = survivor.label.trim(),
                labelLower = normalizeForSearch(survivor.label),
                updatedAt = null,
            ),
        )
        losers.forEach { id ->
            batch.update(
                collection.document(id),
                mapOf(
                    "mergedInto" to survivor.id,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.set(settings.document, mapOf("lastUsedFoodIds" to newRecent), com.google.firebase.firestore.SetOptions.merge())
        val headroom = BATCH_LIMIT - (2 + losers.size)
        mealDocs.take(headroom).forEach { ref -> batch.update(ref, "foodId", survivor.id) }
        batch.commit().await()

        // Rare overflow (>~495 meals of one food): the rest is picked up by the healer, but do it now.
        if (mealDocs.size > headroom) {
            meals.repoint(mealDocs.drop(headroom), survivor.id)
        }
    }
}
