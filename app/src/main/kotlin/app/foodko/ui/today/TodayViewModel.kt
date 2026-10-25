package app.foodko.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.data.model.MealEntry
import app.foodko.data.toTimestamp
import app.foodko.domain.MealWithFood
import app.foodko.domain.Totals
import app.foodko.domain.endInstant
import app.foodko.domain.joinMeals
import app.foodko.domain.startInstant
import app.foodko.domain.totals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val loading: Boolean = false,
    val meals: List<MealWithFood> = emptyList(),
    val totals: Totals = Totals(),
    val target: Double? = null,
)

class TodayViewModel(
    private val session: UserSession,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<TodayUiState> = combine(
        date, session.allMeals, session.foodIndex, session.userSettings,
    ) { day, meals, index, settings ->
        if (meals == null || index == null) return@combine TodayUiState(day, loading = true)
        val start = day.startInstant(clock.zone)
        val end = day.endInstant(clock.zone)
        val dayMeals = joinMeals(
            meals.filter { m ->
                val t = m.timestamp.toInstant()
                !t.isBefore(start) && t.isBefore(end)
            },
            index,
        ).sortedBy { it.time }
        TodayUiState(day, false, dayMeals, dayMeals.totals(), settings?.dailyKcalTarget)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date.value, loading = true))

    fun shiftDay(days: Long) { date.value = date.value.plusDays(days) }
    fun setDate(d: LocalDate) { date.value = d }

    /** Same food and grams, timestamp now (spec: quick re-log). */
    fun logAgain(item: MealWithFood) {
        val foodId = item.food?.id ?: item.meal.foodId
        viewModelScope.launch {
            session.meals.save(MealEntry(foodId = foodId, grams = item.meal.grams, timestamp = Instant.now(clock).toTimestamp()))
            session.settings.recordFoodUsed(session.userSettings.value?.lastUsedFoodIds.orEmpty(), foodId)
            date.value = LocalDate.now(clock)
        }
    }
}
