package app.foodko.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.data.model.WeightEntry
import app.foodko.domain.WeightPoint
import app.foodko.domain.toLocalDate
import app.foodko.domain.weightSeries
import app.foodko.ui.components.StatsRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class WeightUiState(
    val loading: Boolean = true,
    val entries: List<WeightEntry> = emptyList(),
    val series: List<WeightPoint> = emptyList(),
    val range: StatsRange = StatsRange.MONTH,
)

class WeightViewModel(
    private val session: UserSession,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.MONTH)

    val uiState: StateFlow<WeightUiState> = combine(session.allWeights, range) { entries, r ->
        if (entries == null) return@combine WeightUiState(range = r)
        val from = r.days?.let { LocalDate.now(clock).minusDays(it - 1) }
        val inRange = if (from == null) entries else entries.filter { !it.timestamp.toInstant().toLocalDate(clock.zone).isBefore(from) }
        WeightUiState(false, entries.sortedByDescending { it.timestamp }, weightSeries(inRange), r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun setRange(r: StatsRange) { range.value = r }

    fun save(entry: WeightEntry) = viewModelScope.launch { session.weights.save(entry) }
    fun delete(id: String) = viewModelScope.launch { session.weights.delete(id) }
}
