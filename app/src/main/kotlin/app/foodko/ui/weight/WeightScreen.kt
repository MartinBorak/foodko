package app.foodko.ui.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foodko.R
import app.foodko.data.model.WeightEntry
import app.foodko.data.toTimestamp
import app.foodko.domain.Numbers
import app.foodko.domain.change
import app.foodko.domain.toLocalDate
import app.foodko.ui.components.DateTimeField
import app.foodko.ui.components.EmptyText
import app.foodko.ui.components.LineChart
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.NumberField
import app.foodko.ui.components.RangeSelector
import app.foodko.ui.components.Stat
import app.foodko.ui.components.currentLocale
import app.foodko.ui.components.formatDateTime
import app.foodko.ui.components.formatShortDate
import app.foodko.ui.sessionViewModel
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(onSettings: () -> Unit) {
    val vm = sessionViewModel { WeightViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val kg = stringResource(R.string.kg_unit)
    var quickEntry by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WeightEntry?>(null) }
    var pendingSanity by remember { mutableStateOf<WeightEntry?>(null) }

    fun submit(entry: WeightEntry) {
        if (entry.kg < 30 || entry.kg > 300) pendingSanity = entry else vm.save(entry)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_weight)) },
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberField(
                        value = quickEntry,
                        onValueChange = { quickEntry = it },
                        label = stringResource(R.string.weight_now),
                        suffix = kg,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f),
                    )
                    val parsed = Numbers.parse(quickEntry)?.takeIf { it > 0 }
                    Button(
                        onClick = {
                            parsed?.let { submit(WeightEntry(kg = it, timestamp = Instant.now().toTimestamp())) }
                            quickEntry = ""
                        },
                        enabled = parsed != null,
                    ) { Text(stringResource(R.string.add)) }
                }
            }
            item {
                val latest = state.entries.firstOrNull()
                val first = state.entries.lastOrNull()
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat(stringResource(R.string.latest_weight), latest?.let { "${Numbers.entered(it.kg, locale)} $kg" } ?: "–", emphasis = true)
                            if (latest != null && first != null && latest.id != first.id) {
                                Stat(stringResource(R.string.change_since_start), signed(latest.kg - first.kg, locale) + " $kg")
                            }
                            state.series.change()?.let { Stat(stringResource(R.string.change_in_range), signed(it, locale) + " $kg") }
                        }
                        Spacer(Modifier.height(16.dp))
                        RangeSelector(state.range, vm::setRange)
                        Spacer(Modifier.height(16.dp))
                        if (state.series.isEmpty()) {
                            EmptyText(stringResource(R.string.no_data_yet))
                        } else {
                            LineChart(
                                points = state.series.map { it.time.toEpochMilli() to it.kg },
                                smoothed = state.series.map { it.time.toEpochMilli() to it.movingAverage },
                                startLabel = formatShortDate(state.series.first().time.toLocalDate(), locale),
                                endLabel = formatShortDate(state.series.last().time.toLocalDate(), locale),
                            )
                            Text(
                                stringResource(R.string.moving_average_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (state.entries.isEmpty()) {
                item { EmptyText(stringResource(R.string.no_weights_yet)) }
            }
            items(state.entries, key = { it.id }) { entry ->
                ListItem(
                    modifier = Modifier.clickable { editing = entry },
                    headlineContent = { Text("${Numbers.entered(entry.kg, locale)} $kg") },
                    supportingContent = { Text(formatDateTime(entry.timestamp.toInstant(), locale) + (entry.note?.let { " · $it" } ?: "")) },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { entry ->
        WeightEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { editing = null; submit(it) },
            onDelete = { editing = null; vm.delete(entry.id) },
        )
    }

    pendingSanity?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingSanity = null },
            title = { Text(stringResource(R.string.weight_sanity_title)) },
            text = { Text(stringResource(R.string.weight_sanity_body, Numbers.entered(entry.kg, locale))) },
            confirmButton = { TextButton(onClick = { vm.save(entry); pendingSanity = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { pendingSanity = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun signed(value: Double, locale: java.util.Locale): String =
    (if (value > 0) "+" else "") + Numbers.entered(value, locale)

@Composable
private fun WeightEntryDialog(entry: WeightEntry, onDismiss: () -> Unit, onSave: (WeightEntry) -> Unit, onDelete: () -> Unit) {
    var kgText by remember { mutableStateOf(Numbers.editable(entry.kg)) }
    var time by remember { mutableStateOf(entry.timestamp.toInstant()) }
    val parsed = Numbers.parse(kgText)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_weight)) },
        text = {
            Column {
                NumberField(kgText, { kgText = it }, stringResource(R.string.weight), suffix = stringResource(R.string.kg_unit), imeAction = ImeAction.Done, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                DateTimeField(time, { time = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onSave(entry.copy(kg = it, timestamp = time.toTimestamp())) } }, enabled = parsed != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
