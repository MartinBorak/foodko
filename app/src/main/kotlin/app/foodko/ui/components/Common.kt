package app.foodko.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foodko.R
import app.foodko.domain.Nutrition
import app.foodko.domain.Numbers
import app.foodko.domain.Totals

enum class StatsRange(val days: Long?, val labelRes: Int) {
    WEEK(7, R.string.range_7),
    MONTH(30, R.string.range_30),
    QUARTER(90, R.string.range_90),
    ALL(null, R.string.range_all),
}

@Composable
fun RangeSelector(selected: StatsRange, onSelect: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { i, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = StatsRange.entries.size),
            ) { Text(stringResource(range.labelRes)) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** One labelled number, used in the totals grid. */
@Composable
fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fat / carbs / protein / fibre row in grams. */
@Composable
fun MacroRow(fat: Double?, carbs: Double?, protein: Double?, fibre: Double?, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val g = stringResource(R.string.grams_unit)
    fun fmt(v: Double?) = if (v == null) "–" else Numbers.computed(v, locale) + " " + g
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(stringResource(R.string.fat), fmt(fat))
        Stat(stringResource(R.string.carbs), fmt(carbs))
        Stat(stringResource(R.string.protein), fmt(protein))
        Stat(stringResource(R.string.fibre), fmt(fibre))
    }
}

@Composable
fun MacroRow(n: Nutrition, modifier: Modifier = Modifier) = MacroRow(n.fat, n.carbs, n.protein, n.fibre, modifier)

@Composable
fun MacroRow(t: Totals, modifier: Modifier = Modifier) = MacroRow(t.fat, t.carbs, t.protein, t.fibre, modifier)
