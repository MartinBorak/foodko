package app.foodko.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foodko.R
import app.foodko.domain.Numbers
import app.foodko.domain.change
import app.foodko.domain.macroTargetsFor
import app.foodko.domain.toLocalDate
import app.foodko.ui.components.BarChart
import app.foodko.ui.components.EmptyText
import app.foodko.ui.components.LineChart
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.MacroRow
import app.foodko.ui.components.MacroSplitBar
import app.foodko.ui.components.RangeSelector
import app.foodko.ui.components.Stat
import app.foodko.ui.components.currentLocale
import app.foodko.ui.components.formatShortDate
import app.foodko.ui.sessionViewModel
import app.foodko.ui.theme.dangerColor
import app.foodko.ui.theme.warningColor

/** Bar chart of one macro's grams per day with its derived target line. */
@Composable
private fun MacroDailyCard(
    title: String,
    values: List<Double>,
    target: Double?,
    state: StatsUiState,
    overColor: Color = dangerColor(),
) {
    val locale = currentLocale()
    val g = stringResource(R.string.grams_unit)
    StatsCard(title) {
        if (state.loggedDays == 0) {
            EmptyText(stringResource(R.string.no_data_yet))
        } else {
            BarChart(
                values = values,
                labels = state.daily.map { formatShortDate(it.date, locale) },
                target = target,
                height = 140.dp,
                formatValue = { "${Numbers.computed(it, locale)} $g" },
                overColor = overColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onSettings: () -> Unit) {
    val vm = sessionViewModel { StatsViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val kcal = stringResource(R.string.kcal_unit)
    val kg = stringResource(R.string.kg_unit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stats)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RangeSelector(state.range, vm::setRange)

            StatsCard(stringResource(R.string.daily_kcal)) {
                if (state.loggedDays == 0) {
                    EmptyText(stringResource(R.string.no_data_yet))
                } else {
                    BarChart(
                        values = state.daily.map { it.totals.kcal },
                        labels = state.daily.map { formatShortDate(it.date, locale) },
                        target = state.target,
                        formatValue = { "${Numbers.kcal(it, locale)} $kcal" },
                    )
                    Text(
                        stringResource(R.string.tap_bar_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StatsCard(stringResource(R.string.average_per_logged_day)) {
                val avg = state.average
                if (avg == null) {
                    EmptyText(stringResource(R.string.no_data_yet))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(kcal, Numbers.kcal(avg.kcal, locale), emphasis = true)
                        state.target?.takeIf { it > 0 }?.let { t ->
                            Stat(stringResource(R.string.vs_target), (if (avg.kcal - t > 0) "+" else "") + Numbers.kcal(avg.kcal - t, locale))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    MacroRow(avg)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(R.plurals.logged_days, state.loggedDays, state.loggedDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StatsCard(stringResource(R.string.macro_split)) {
                val split = state.split
                if (split == null) {
                    EmptyText(stringResource(R.string.no_data_yet))
                } else {
                    MacroSplitBar(split, stringResource(R.string.protein), stringResource(R.string.carbs), stringResource(R.string.fat))
                }
            }

            // Daily intake per macro, same order as the Today grid; targets derived from the kcal target.
            val macroTargets = state.target?.takeIf { it > 0 }?.let { macroTargetsFor(it) }
            MacroDailyCard(stringResource(R.string.daily_carbs), state.daily.map { it.totals.carbs }, macroTargets?.carbs, state)
            MacroDailyCard(stringResource(R.string.daily_fat), state.daily.map { it.totals.fat }, macroTargets?.fat, state)
            MacroDailyCard(stringResource(R.string.daily_protein), state.daily.map { it.totals.protein }, macroTargets?.protein, state, overColor = warningColor())
            MacroDailyCard(stringResource(R.string.daily_fibre), state.daily.map { it.totals.fibre }, macroTargets?.fibre, state, overColor = warningColor())

            StatsCard(stringResource(R.string.weight_trend)) {
                if (state.weights.isEmpty()) {
                    EmptyText(stringResource(R.string.no_data_yet))
                } else {
                    state.weights.change()?.let { delta ->
                        Text(
                            (if (delta > 0) "+" else "") + Numbers.entered(delta, locale) + " $kg",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    LineChart(
                        points = state.weights.map { it.time.toEpochMilli() to it.kg },
                        smoothed = state.weights.map { it.time.toEpochMilli() to it.movingAverage },
                        startLabel = formatShortDate(state.weights.first().time.toLocalDate(), locale),
                        endLabel = formatShortDate(state.weights.last().time.toLocalDate(), locale),
                    )
                    Text(
                        stringResource(R.string.moving_average_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
