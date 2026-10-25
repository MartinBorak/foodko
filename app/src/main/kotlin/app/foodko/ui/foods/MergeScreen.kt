package app.foodko.ui.foods

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.foodko.R
import app.foodko.domain.Numbers
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.currentLocale
import app.foodko.ui.sessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(ids: List<String>, onDone: () -> Unit) {
    val vm = sessionViewModel(key = "merge-" + ids.joinToString(",")) { MergeViewModel(it, ids) }
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (vm.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.merge_survivor_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                vm.foods.forEach { food ->
                    val count = vm.usage[food.id] ?: 0
                    ListItem(
                        modifier = Modifier.clickable { vm.choose(food.id) },
                        leadingContent = { RadioButton(selected = food.id == vm.survivorId, onClick = { vm.choose(food.id) }) },
                        headlineContent = { Text(food.label) },
                        supportingContent = {
                            Text(
                                "${Numbers.entered(food.kcalPer100g, locale)} ${stringResource(R.string.kcal_per_100g_short)} · " +
                                    pluralStringResource(R.plurals.used_in_my_meals, count, count),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.merge_result_values), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FoodFieldsEditor(vm.fields, duplicate = null, autoFocusLabel = false)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { vm.save(onDone) }, enabled = vm.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.merge_button, vm.foods.size, vm.foods.size))
            }
        }
    }
}
