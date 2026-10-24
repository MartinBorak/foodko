package app.foodko.data

import app.foodko.data.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Settings fields on the `users/{uid}` document. */
class SettingsRepository(db: FirebaseFirestore, uid: String) {

    val document = db.collection("users").document(uid)

    /** Defaults until the document exists. Errors here (permission denied) mean "not allowlisted". */
    val settings: Flow<UserSettings> = document.snapshots().map { snap ->
        snap?.toObject(UserSettings::class.java) ?: UserSettings()
    }

    suspend fun update(fields: Map<String, Any?>) {
        document.set(fields, SetOptions.merge()).await()
    }

    suspend fun setDisplayName(name: String?) = update(mapOf("displayName" to name?.trim()?.ifEmpty { null }))

    suspend fun setLanguage(tag: String?) = update(mapOf("language" to tag))

    suspend fun setDailyKcalTarget(kcal: Double?) = update(mapOf("dailyKcalTarget" to kcal))

    /** Moves [foodId] to the front of the recent list, keeping at most [RECENT_LIMIT]. */
    suspend fun recordFoodUsed(current: List<String>, foodId: String) {
        val next = (listOf(foodId) + current.filter { it != foodId }).take(RECENT_LIMIT)
        if (next != current) update(mapOf("lastUsedFoodIds" to next))
    }

    suspend fun setLastUsed(ids: List<String>) = update(mapOf("lastUsedFoodIds" to ids))

    companion object {
        const val RECENT_LIMIT = 12
    }
}
