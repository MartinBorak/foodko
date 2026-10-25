package app.foodko.data

import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.data.model.WeightEntry
import app.foodko.domain.normalizeForSearch
import app.foodko.domain.pushRecent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// In-memory repositories with the same observable behaviour as the Firestore ones: every write
// is reflected in the flow right away, ids are assigned on first save.

class FakeFoodRepository(initial: List<Food> = emptyList()) : FoodRepository {
    val state = MutableStateFlow(initial)
    override val foods: Flow<List<Food>> get() = state
    private var nextId = 1

    fun byId(id: String): Food = state.value.first { it.id == id }

    override suspend fun save(food: Food, uid: String): String {
        val id = food.id.ifEmpty { "food-${nextId++}" }
        val prepared = food.copy(
            id = id,
            label = food.label.trim(),
            labelLower = normalizeForSearch(food.label),
            createdBy = food.createdBy.ifEmpty { uid },
        )
        state.update { list -> list.filter { it.id != id } + prepared }
        return id
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
    }
}

class FakeMealRepository(initial: List<MealEntry> = emptyList()) : MealRepository {
    val state = MutableStateFlow(initial)
    override val all: Flow<List<MealEntry>> get() = state
    private var nextId = 1

    /** Each call to [repoint], for asserting that the healer only writes when needed. */
    val repointCalls = mutableListOf<Map<String, String>>()

    override suspend fun save(meal: MealEntry): String {
        val id = meal.id.ifEmpty { "meal-${nextId++}" }
        state.update { list -> list.filter { it.id != id } + meal.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filter { it.id != id } }
    }

    override suspend fun repoint(meals: List<MealEntry>, mapping: Map<String, String>) {
        repointCalls += mapping
        val ids = meals.map { it.id }.toSet()
        state.update { list ->
            list.map { m -> if (m.id in ids && m.foodId in mapping) m.copy(foodId = mapping.getValue(m.foodId)) else m }
        }
    }
}

class FakeWeightRepository(initial: List<WeightEntry> = emptyList()) : WeightRepository {
    val state = MutableStateFlow(initial)
    override val all: Flow<List<WeightEntry>> get() = state
    private var nextId = 1

    override suspend fun save(entry: WeightEntry): String {
        val id = entry.id.ifEmpty { "weight-${nextId++}" }
        state.update { list -> list.filter { it.id != id } + entry.copy(id = id) }
        return id
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filter { it.id != id } }
    }
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> get() = state

    /** Each list written through [setLastUsed] (the healer's path), in order. */
    val lastUsedWrites = mutableListOf<List<String>>()

    override suspend fun setDisplayName(name: String?) = state.update { it.copy(displayName = name?.trim()?.ifEmpty { null }) }
    override suspend fun setLanguage(tag: String?) = state.update { it.copy(language = tag) }
    override suspend fun setDailyKcalTarget(kcal: Double?) = state.update { it.copy(dailyKcalTarget = kcal) }

    override suspend fun recordFoodUsed(current: List<String>, foodId: String) {
        val next = pushRecent(current, foodId)
        if (next != current) state.update { it.copy(lastUsedFoodIds = next) }
    }

    override suspend fun setLastUsed(ids: List<String>) {
        lastUsedWrites += ids
        state.update { it.copy(lastUsedFoodIds = ids) }
    }
}

/** Records merge requests instead of performing them; the Firestore batch is not under test. */
class RecordingMerger : FoodMerger {
    data class Call(val survivor: Food, val mergedIds: List<String>, val lastUsedFoodIds: List<String>)

    val calls = mutableListOf<Call>()

    override suspend fun merge(survivor: Food, mergedIds: List<String>, lastUsedFoodIds: List<String>) {
        calls += Call(survivor, mergedIds, lastUsedFoodIds)
    }
}
