package app.foodko.data

import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

// What the ViewModels see of the data layer. The `Firestore*` classes implement these against
// Cloud Firestore; the unit tests use in-memory fakes (app/src/test/.../data/Fakes.kt).

/** The shared `foods` collection. */
interface FoodRepository {
    /** Every food document, including archived and redirected ones; FoodIndex sorts them out. */
    val foods: Flow<List<Food>>

    /** Creates or updates a food; returns its id. Search key and timestamps are maintained here. */
    suspend fun save(food: Food, uid: String): String

    suspend fun setArchived(id: String, archived: Boolean)
}

/** `users/{uid}/meals`. */
interface MealRepository {
    val all: Flow<List<MealEntry>>

    suspend fun save(meal: MealEntry): String

    suspend fun delete(id: String)

    /** Rewrites `foodId` for the given meals according to [mapping] (old id → final id). */
    suspend fun repoint(meals: List<MealEntry>, mapping: Map<String, String>)
}

/** `users/{uid}/weights`. */
interface WeightRepository {
    val all: Flow<List<WeightEntry>>

    suspend fun save(entry: WeightEntry): String

    suspend fun delete(id: String)
}

/** Settings fields on the `users/{uid}` document. */
interface SettingsRepository {
    /** Defaults until the document exists. */
    val settings: Flow<UserSettings>

    suspend fun setDisplayName(name: String?)

    suspend fun setLanguage(tag: String?)

    suspend fun setDailyKcalTarget(kcal: Double?)

    /** Moves [foodId] to the front of the recent list ([app.foodko.domain.pushRecent]). */
    suspend fun recordFoodUsed(current: List<String>, foodId: String)

    suspend fun setLastUsed(ids: List<String>)
}

/**
 * Merge (spec Flow 7): the survivor is updated with [survivor]'s values, every id in [mergedIds]
 * becomes a redirect to it, and the current user's meals and recent list are rewritten.
 */
fun interface FoodMerger {
    suspend fun merge(survivor: Food, mergedIds: List<String>, lastUsedFoodIds: List<String>)
}
