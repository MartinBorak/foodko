package app.foodko.data

import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.domain.FoodIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Self-healing half of a merge (spec Flow 7, decision 7). Whenever this user's meals or recent
 * list point at a food that has since been merged into another, rewrite them to the final id.
 * Runs for the life of the user session; after the rewrite the snapshots update and the mapping
 * becomes empty, so it naturally settles.
 */
class RedirectHealer(
    private val meals: MealRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start(
        scope: CoroutineScope,
        foodIndex: Flow<FoodIndex>,
        allMeals: Flow<List<MealEntry>>,
        userSettings: Flow<UserSettings>,
    ) {
        combine(foodIndex, allMeals, userSettings) { index, mealList, s -> Triple(index, mealList, s) }
            .debounce(1_000)
            .onEach { (index, mealList, s) ->
                if (index.isEmpty) return@onEach
                val mealMapping = index.redirectsAmong(mealList.map { it.foodId })
                if (mealMapping.isNotEmpty()) {
                    runCatching { meals.repoint(mealList, mealMapping) }
                }
                val recentMapping = index.redirectsAmong(s.lastUsedFoodIds)
                if (recentMapping.isNotEmpty()) {
                    val healed = s.lastUsedFoodIds.map { recentMapping[it] ?: it }.distinct()
                    runCatching { settings.setLastUsed(healed) }
                }
            }
            .launchIn(scope)
    }
}
