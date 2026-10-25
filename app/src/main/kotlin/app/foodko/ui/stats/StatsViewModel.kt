package app.foodko.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.domain.DailyTotals
import app.foodko.domain.MacroSplit
import app.foodko.domain.Totals
import app.foodko.domain.WeightPoint
import app.foodko.domain.averagePerLoggedDay
import app.foodko.domain.dailySeries
import app.foodko.domain.joinMeals
import app.foodko.domain.macroSplit
import app.foodko.domain.toLocalDate
import app.foodko.domain.weightSeries
import app.foodko.ui.components.StatsRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.WEEK,
    val daily: List<DailyTotals> = emptyList(),
    val average: Totals? = null,
    val loggedDays: Int = 0,
    val split: MacroSplit? = null,
    val target: Double? = null,
    val weights: List<WeightPoint> = emptyList(),
)

class StatsViewModel(session: UserSession, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.WEEK)

    val uiState: StateFlow<StatsUiState> = combine(
        range, session.allMeals, session.foodIndex, session.allWeights, session.userSettings,
    ) { r, meals, index, weights, settings ->
        if (meals == null || index == null || weights == null) return@combine StatsUiState(range = r)
        val today = LocalDate.now(clock)
        val from = r.days?.let { today.minusDays(it - 1) }
            ?: listOfNotNull(
                meals.firstOrNull()?.timestamp?.toInstant()?.toLocalDate(clock.zone),
                weights.firstOrNull()?.timestamp?.toInstant()?.toLocalDate(clock.zone),
            ).minOrNull() ?: today
        val joined = joinMeals(meals, index)
        val daily = dailySeries(joined, from, today, clock.zone)
        val logged = daily.filter { it.mealCount > 0 }
        val sum = logged.fold(Totals()) { acc, d -> acc + d.totals }
        val weightPoints = weightSeries(weights.filter { !it.timestamp.toInstant().toLocalDate(clock.zone).isBefore(from) })
        StatsUiState(
            loading = false,
            range = r,
            daily = daily,
            average = daily.averagePerLoggedDay(),
            loggedDays = logged.size,
            split = sum.macroSplit(),
            target = settings?.dailyKcalTarget,
            weights = weightPoints,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setRange(r: StatsRange) { range.value = r }
}
