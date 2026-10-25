package app.foodko.domain

/** How many recently used foods the picker remembers (spec §6, `lastUsedFoodIds`). */
const val RECENT_LIMIT = 12

/** [foodId] moved to the front of [current], duplicates dropped, capped at [limit]. */
fun pushRecent(current: List<String>, foodId: String, limit: Int = RECENT_LIMIT): List<String> =
    (listOf(foodId) + current.filter { it != foodId }).take(limit)
