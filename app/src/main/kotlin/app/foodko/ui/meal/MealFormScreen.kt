package app.foodko.ui.meal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.foodko.R
import app.foodko.data.model.Food
import app.foodko.domain.FoodIndex
import app.foodko.domain.Numbers
import app.foodko.domain.nutritionFor
import app.foodko.ui.components.DateTimeField
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.MacroRow
import app.foodko.ui.components.NumberField
import app.foodko.ui.components.currentLocale
import app.foodko.ui.sessionViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealFormScreen(
    mealId: String?,
    initialFoodId: String?,
    day: LocalDate?,
    pickedFoodId: StateFlow<String?>,
    onConsumePickedFood: () -> Unit,
    onCreateFood: (String) -> Unit,
    onDone: () -> Unit,
) {
    val vm = sessionViewModel(key = "meal-${mealId ?: "new"}") { MealFormViewModel(it, mealId, initialFoodId, day) }
    val index by vm.foodIndex.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picked by pickedFoodId.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(picked) {
        picked?.let {
            vm.pickFood(it)
            onConsumePickedFood()
        }
    }

    val title = when {
        vm.picking -> stringResource(R.string.pick_food)
        vm.isEditing -> stringResource(R.string.edit_meal)
        else -> stringResource(R.string.new_meal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (!(vm.picking && vm.cancelPicking())) onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (vm.isEditing && !vm.picking) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    // In the top bar so the keyboard never covers it.
                    if (!vm.picking && !vm.loading) {
                        TextButton(onClick = { vm.save(onDone) }, enabled = vm.canSave) { Text(stringResource(R.string.save)) }
                    }
                },
            )
        },
    ) { padding ->
        val currentIndex = index
        when {
            vm.loading || currentIndex == null -> LoadingBox(Modifier.padding(padding))
            vm.picking -> FoodPicker(
                index = currentIndex,
                recentIds = settings?.lastUsedFoodIds.orEmpty(),
                query = vm.query,
                onQueryChange = { vm.query = it },
                onPick = vm::pickFood,
                onCreate = { onCreateFood(vm.query) },
                modifier = Modifier.padding(padding),
            )
            else -> MealForm(vm, currentIndex, Modifier.padding(padding), onDone)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_meal_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MealForm(vm: MealFormViewModel, index: FoodIndex, modifier: Modifier, onDone: () -> Unit) {
    val locale = currentLocale()
    val food = vm.foodId?.let { index.resolve(it) }
    val gramsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!vm.isEditing) gramsFocus.requestFocus() }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth().clickable { vm.startPicking() }) {
            ListItem(
                headlineContent = { Text(food?.label ?: stringResource(R.string.unknown_food)) },
                supportingContent = {
                    food?.let { Text("${Numbers.entered(it.kcalPer100g, locale)} ${stringResource(R.string.kcal_per_100g_short)}") }
                },
                trailingContent = { Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.primary) },
            )
        }
        Spacer(Modifier.height(16.dp))
        NumberField(
            value = vm.grams,
            onValueChange = { vm.grams = it },
            label = stringResource(R.string.weight_grams),
            suffix = stringResource(R.string.grams_unit),
            imeAction = ImeAction.Done,
            focusRequester = gramsFocus,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        DateTimeField(value = vm.timestamp, onValueChange = { vm.timestamp = it })
        Spacer(Modifier.height(24.dp))

        val g = vm.parsedGrams
        if (food != null && g != null) {
            val n = food.nutritionFor(g)
            Text(stringResource(R.string.nutrition_preview), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("${Numbers.kcal(n.kcal, locale)} ${stringResource(R.string.kcal_unit)}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            MacroRow(n)
        }
    }
}

@Composable
fun FoodPicker(
    index: FoodIndex,
    recentIds: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val matches: List<Food> = index.search(query)
    val recent: List<Food> = if (query.isBlank()) recentIds.mapNotNull { index.resolve(it) }.filter { !it.archived }.distinctBy { it.id } else emptyList()
    val recentIdSet = recent.map { it.id }.toSet()
    val rest = matches.filter { it.id !in recentIdSet }
    val perHundred = stringResource(R.string.kcal_per_100g_short)

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_foods)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (recent.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.recent)) }
                items(recent, key = { "r-" + it.id }) { food -> FoodPickRow(food, perHundred, locale) { onPick(food.id) } }
                item { SectionHeader(stringResource(R.string.all_foods)) }
            }
            items(rest, key = { it.id }) { food -> FoodPickRow(food, perHundred, locale) { onPick(food.id) } }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onCreate),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    headlineContent = {
                        Text(
                            if (query.isBlank()) stringResource(R.string.create_food)
                            else stringResource(R.string.create_food_named, query.trim()),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FoodPickRow(food: Food, perHundred: String, locale: java.util.Locale, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(food.label) },
        supportingContent = {
            Column {
                Text("${Numbers.entered(food.kcalPer100g, locale)} $perHundred")
                food.note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
    )
}
