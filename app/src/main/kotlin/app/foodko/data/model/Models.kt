package app.foodko.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

// Firestore document classes. Field names are the Firestore field names (spec §6).
// All need a no-arg constructor, hence the defaults.

/** Shared food database entry, `foods/{id}`. Values are per 100 g. */
data class Food(
    @DocumentId val id: String = "",
    val label: String = "",
    /** Lower-cased, diacritics-stripped label for search. */
    val labelLower: String = "",
    val kcalPer100g: Double = 0.0,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val fibrePer100g: Double? = null,
    /** Hidden from lists and the picker; meals keep referencing it. */
    val archived: Boolean = false,
    /** Set when this food was merged into another one; it then acts as a redirect. */
    val mergedInto: String? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

/** One logged meal, `users/{uid}/meals/{id}`. Nutrition is never stored; see domain/Nutrition.kt. */
data class MealEntry(
    @DocumentId val id: String = "",
    val foodId: String = "",
    val grams: Double = 0.0,
    val timestamp: Timestamp = Timestamp(0, 0),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

/** One weigh-in, `users/{uid}/weights/{id}`. */
data class WeightEntry(
    @DocumentId val id: String = "",
    val kg: Double = 0.0,
    val timestamp: Timestamp = Timestamp(0, 0),
    val note: String? = null,
)

/** Per-user settings on the `users/{uid}` document itself. */
data class UserSettings(
    val displayName: String? = null,
    /** BCP-47 tag ("en", "sk") or null for the system default. */
    val language: String? = null,
    val dailyKcalTarget: Double? = null,
    /** Most recently logged food ids, newest first, capped. */
    val lastUsedFoodIds: List<String> = emptyList(),
)
