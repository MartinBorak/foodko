package app.foodko.ui.meal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.data.model.MealEntry
import app.foodko.data.toTimestamp
import app.foodko.domain.FoodIndex
import app.foodko.domain.Numbers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MealFormViewModel(
    private val session: UserSession,
    private val mealId: String?,
    initialFoodId: String?,
    day: LocalDate?,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    val foodIndex: StateFlow<FoodIndex?> = session.foodIndex
    val settings = session.userSettings

    var foodId by mutableStateOf(initialFoodId)
        private set
    var grams by mutableStateOf("")
    var timestamp by mutableStateOf(defaultTimestamp(day, clock))
    var picking by mutableStateOf(initialFoodId == null && mealId == null)
        private set
    var query by mutableStateOf("")
    var loading by mutableStateOf(mealId != null)
        private set
    var saving by mutableStateOf(false)
        private set

    private var existing: MealEntry? = null

    init {
        if (mealId != null) {
            viewModelScope.launch {
                val meal = session.allMeals.filterNotNull().first().firstOrNull { it.id == mealId }
                if (meal != null) {
                    existing = meal
                    foodId = meal.foodId
                    grams = Numbers.editable(meal.grams)
                    timestamp = meal.timestamp.toInstant()
                }
                loading = false
            }
        }
    }

    val isEditing: Boolean get() = mealId != null

    fun pickFood(id: String) {
        foodId = id
        picking = false
        query = ""
    }

    fun startPicking() { picking = true }

    fun cancelPicking(): Boolean {
        if (foodId == null) return false
        picking = false
        return true
    }

    val parsedGrams: Double? get() = Numbers.parse(grams)?.takeIf { it > 0 }
    val canSave: Boolean get() = foodId != null && parsedGrams != null && !saving

    fun save(onDone: () -> Unit) {
        val g = parsedGrams ?: return
        val f = foodId ?: return
        saving = true
        viewModelScope.launch {
            val entry = (existing ?: MealEntry()).copy(foodId = f, grams = g, timestamp = timestamp.toTimestamp())
            session.meals.save(entry)
            session.settings.recordFoodUsed(settings.value?.lastUsedFoodIds.orEmpty(), f)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = mealId ?: return
        saving = true
        viewModelScope.launch {
            session.meals.delete(id)
            onDone()
        }
    }

    private companion object {
        /** New meal on a past day: that day at the current time of day. Otherwise now. */
        fun defaultTimestamp(day: LocalDate?, clock: Clock): Instant {
            val now = Instant.now(clock)
            if (day == null || day == LocalDate.now(clock)) return now
            return day.atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant()
        }
    }
}
