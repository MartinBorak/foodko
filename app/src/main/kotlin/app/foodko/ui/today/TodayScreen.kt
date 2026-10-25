package app.foodko.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.foodko.R
import app.foodko.UserSession
import app.foodko.data.model.MealEntry
import app.foodko.data.toTimestamp
import app.foodko.domain.MealWithFood
import app.foodko.domain.Numbers
import app.foodko.domain.macroTargetsFor
import app.foodko.domain.Totals
import app.foodko.domain.endInstant
import app.foodko.domain.joinMeals
import app.foodko.domain.startInstant
import app.foodko.domain.totals
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.MacroRow
import app.foodko.ui.components.EmptyText
import app.foodko.ui.components.Stat
import app.foodko.ui.components.TargetRing
import app.foodko.ui.theme.warningColor
import app.foodko.ui.components.currentLocale
import app.foodko.ui.components.dayTitle
import app.foodko.ui.components.formatTime
import app.foodko.ui.sessionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class TodayUiState(
    val date: LocalDate,
    val loading: Boolean = false,
    val meals: List<MealWithFood> = emptyList(),
    val totals: Totals = Totals(),
    val target: Double? = null,
)

class TodayViewModel(private val session: UserSession) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<TodayUiState> = combine(
        date, session.allMeals, session.foodIndex, session.userSettings,
    ) { day, meals, index, settings ->
        if (meals == null || index == null) return@combine TodayUiState(day, loading = true)
        val start = day.startInstant()
        val end = day.endInstant()
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
            session.meals.save(MealEntry(foodId = foodId, grams = item.meal.grams, timestamp = Instant.now().toTimestamp()))
            session.settings.recordFoodUsed(session.userSettings.value?.lastUsedFoodIds.orEmpty(), foodId)
            date.value = LocalDate.now()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddMeal: (LocalDate) -> Unit,
    onEditMeal: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val vm = sessionViewModel { TodayViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var showDatePicker by remember { mutableStateOf(false) }
    var logAgainItem by remember { mutableStateOf<MealWithFood?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val loggedText = stringResource(R.string.logged_again)
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(dayTitle(state.date)) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.pick_date))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddMeal(state.date) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_meal)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.shiftDay(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.previous_day))
                }
                if (state.date != LocalDate.now()) {
                    TextButton(onClick = { vm.setDate(LocalDate.now()) }) { Text(stringResource(R.string.today)) }
                }
                IconButton(onClick = { vm.shiftDay(1) }, enabled = state.date < LocalDate.now()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next_day))
                }
            }

            if (state.loading) {
                LoadingBox()
                return@Column
            }

            TotalsCard(state.totals, state.target, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            if (state.meals.isEmpty()) {
                EmptyText(stringResource(R.string.no_meals_today))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.meals, key = { it.meal.id }) { item ->
                        MealRow(
                            item, locale,
                            onClick = { onEditMeal(item.meal.id) },
                            onLongClick = { logAgainItem = item },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(88.dp)) } // room for the FAB
                }
            }
        }
    }

    logAgainItem?.let { item ->
        AlertDialog(
            onDismissRequest = { logAgainItem = null },
            title = { Text(item.food?.label ?: stringResource(R.string.unknown_food)) },
            text = { Text(stringResource(R.string.log_again_body, Numbers.entered(item.meal.grams, locale))) },
            confirmButton = {
                TextButton(onClick = {
                    vm.logAgain(item)
                    logAgainItem = null
                    scope.launch { snackbar.showSnackbar(loggedText) }
                }) { Text(stringResource(R.string.log_again)) }
            },
            dismissButton = { TextButton(onClick = { logAgainItem = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun TotalsCard(totals: Totals, target: Double?, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val kcalUnit = stringResource(R.string.kcal_unit)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (target != null && target > 0) {
                // Ring: share of the daily target eaten; red once over it (spec §7, Today).
                val diff = target - totals.kcal
                val over = diff < 0
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TargetRing(totals.kcal, target)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "${Numbers.kcal(totals.kcal, locale)} / ${Numbers.kcal(target, locale)} $kcalUnit",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (over) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                        Text(
                            "${Numbers.kcal(kotlin.math.abs(diff), locale)} " +
                                stringResource(if (over) R.string.over_kcal else R.string.remaining_kcal).lowercase(locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(stringResource(R.string.eaten_kcal), Numbers.kcal(totals.kcal, locale), emphasis = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (target != null && target > 0) {
                val t = macroTargetsFor(target)
                Row(Modifier.fillMaxWidth()) {
                    MacroTargetCell(stringResource(R.string.carbs), totals.carbs, t.carbs, Modifier.weight(1f))
                    MacroTargetCell(stringResource(R.string.fat), totals.fat, t.fat, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    MacroTargetCell(stringResource(R.string.protein), totals.protein, t.protein, Modifier.weight(1f), overColor = warningColor())
                    MacroTargetCell(stringResource(R.string.fibre), totals.fibre, t.fibre, Modifier.weight(1f), overColor = warningColor())
                }
            } else {
                MacroRow(totals)
            }
            if (totals.incomplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.incomplete_macros),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One macro against its derived daily target: small ring, "eaten / target g", label.
 * [overColor] is red for the macros where going over matters, amber where it hardly does.
 */
@Composable
private fun MacroTargetCell(
    label: String,
    value: Double,
    target: Double,
    modifier: Modifier = Modifier,
    overColor: Color = MaterialTheme.colorScheme.error,
) {
    val locale = currentLocale()
    val g = stringResource(R.string.grams_unit)
    val over = value > target
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TargetRing(value, target, size = 36.dp, stroke = 5.dp, overColor = overColor)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "${Numbers.computed(value, locale)} / ${Numbers.kcal(target, locale)} $g",
                style = MaterialTheme.typography.titleSmall,
                color = if (over) overColor else Color.Unspecified,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealRow(item: MealWithFood, locale: java.util.Locale, onClick: () -> Unit, onLongClick: () -> Unit) {
    val g = stringResource(R.string.grams_unit)
    val kcal = stringResource(R.string.kcal_unit)
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        overlineContent = { Text(formatTime(item.time, locale)) },
        headlineContent = { Text(item.food?.label ?: stringResource(R.string.unknown_food)) },
        supportingContent = { Text("${Numbers.entered(item.meal.grams, locale)} $g") },
        trailingContent = {
            item.nutrition?.let { Text("${Numbers.kcal(it.kcal, locale)} $kcal", style = MaterialTheme.typography.titleMedium) }
        },
    )
}
