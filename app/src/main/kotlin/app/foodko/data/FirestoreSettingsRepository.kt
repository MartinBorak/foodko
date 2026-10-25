package app.foodko.data

import app.foodko.data.model.UserSettings
import app.foodko.domain.pushRecent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** [SettingsRepository] on Cloud Firestore. */
class FirestoreSettingsRepository(db: FirebaseFirestore, uid: String) : SettingsRepository {

    val document = db.collection("users").document(uid)

    /** Errors here (permission denied) mean "not allowlisted". */
    override val settings: Flow<UserSettings> = document.snapshots().map { snap ->
        snap?.toObject(UserSettings::class.java) ?: UserSettings()
    }

    suspend fun update(fields: Map<String, Any?>) {
        document.set(fields, SetOptions.merge()).await()
    }

    override suspend fun setDisplayName(name: String?) = update(mapOf("displayName" to name?.trim()?.ifEmpty { null }))

    override suspend fun setLanguage(tag: String?) = update(mapOf("language" to tag))

    override suspend fun setDailyKcalTarget(kcal: Double?) = update(mapOf("dailyKcalTarget" to kcal))

    override suspend fun recordFoodUsed(current: List<String>, foodId: String) {
        val next = pushRecent(current, foodId)
        if (next != current) update(mapOf("lastUsedFoodIds" to next))
    }

    override suspend fun setLastUsed(ids: List<String>) = update(mapOf("lastUsedFoodIds" to ids))
}
