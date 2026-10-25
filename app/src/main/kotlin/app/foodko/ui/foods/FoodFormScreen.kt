package app.foodko.ui.foods

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.foodko.R
import app.foodko.UserSession
import app.foodko.data.model.Food
import app.foodko.domain.FoodIndex
import app.foodko.domain.Ingredient
import app.foodko.domain.Per100g
import app.foodko.domain.composePer100g
import app.foodko.domain.Numbers
import app.foodko.domain.normalizeForSearch
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.MacroRow
import app.foodko.ui.components.currentLocale
import app.foodko.ui.components.NumberField
import app.foodko.ui.meal.FoodPicker
import app.foodko.ui.sessionViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Editable food values as text; shared by the food form and the merge screen. */
class FoodFields {
    var label by mutableStateOf("")
    var kcal by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")
    var protein by mutableStateOf("")
    var fibre by mutableStateOf("")

    fun load(food: Food) {
        label = food.label
        kcal = Numbers.editable(food.kcalPer100g)
        carbs = Numbers.editable(food.carbsPer100g)
        fat = Numbers.editable(food.fatPer100g)
        protein = Numbers.editable(food.proteinPer100g)
        fibre = Numbers.editable(food.fibrePer100g)
    }

    private fun optional(text: String): Double? = Numbers.parse(text)?.takeIf { it >= 0 }

    val kcalValue: Double? get() = Numbers.parse(kcal)?.takeIf { it >= 0 }
    val labelValid: Boolean get() = label.isNotBlank()
    val kcalValid: Boolean get() = kcal.isBlank() || kcalValue != null
    fun optionalValid(text: String) = text.isBlank() || optional(text) != null

    val isValid: Boolean
        get() = labelValid && kcalValue != null &&
            listOf(carbs, fat, protein, fibre).all { optionalValid(it) }

    val macroSum: Double
        get() = listOf(carbs, fat, protein, fibre).sumOf { optional(it) ?: 0.0 }

    /** Applies the fields onto [base], keeping its id and bookkeeping. */
    fun applyTo(base: Food): Food = base.copy(
        label = label.trim(),
        labelLower = normalizeForSearch(label),
        kcalPer100g = kcalValue ?: 0.0,
        carbsPer100g = optional(carbs),
        fatPer100g = optional(fat),
        proteinPer100g = optional(protein),
        fibrePer100g = optional(fibre),
    )
}

/** What the food form screen shows: the form itself, or the ingredient calculator on top of it. */
enum class FormMode { FORM, INGREDIENTS, PICKING_INGREDIENT }

/** One row of the ingredient calculator; grams as typed. */
class IngredientRow(val foodId: String) {
    var grams by mutableStateOf("")
    val gramsValue: Double? get() = Numbers.parse(grams)?.takeIf { it > 0 }
}

class FoodFormViewModel(private val session: UserSession, private val foodId: String?, prefillLabel: String) : ViewModel() {

    val foodIndex = session.foodIndex
    val settings = session.userSettings
    val fields = FoodFields().apply { label = prefillLabel }
    var mode by mutableStateOf(FormMode.FORM)
        private set

    /** Ingredient calculator (Flow 3a). Lives only as long as this screen; nothing is stored. */
    val ingredients = mutableStateListOf<IngredientRow>()
    var cookedGrams by mutableStateOf("")
    var query by mutableStateOf("")
    var loading by mutableStateOf(foodId != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var existing by mutableStateOf<Food?>(null)
        private set

    /** How many of *my* meals reference this food (other users' are invisible). */
    var myMealCount by mutableIntStateOf(0)
        private set

    init {
        if (foodId != null) {
            viewModelScope.launch {
                val index = session.foodIndex.filterNotNull().first()
                index.resolve(foodId)?.let { food ->
                    existing = food
                    fields.load(food)
                }
                myMealCount = session.allMeals.filterNotNull().first().count { index.resolveId(it.foodId) == foodId }
                loading = false
            }
        }
    }

    val isEditing: Boolean get() = foodId != null

    fun duplicateOf(index: FoodIndex): Food? {
        val key = normalizeForSearch(fields.label)
        if (key.isEmpty()) return null
        return index.visible.firstOrNull { it.labelLower == key && it.id != foodId }
    }

    fun save(onSaved: (String) -> Unit) {
        if (!fields.isValid || saving) return
        saving = true
        viewModelScope.launch {
            val id = session.foods.save(fields.applyTo(existing ?: Food()), session.uid)
            onSaved(id)
        }
    }

    fun startIngredients() { mode = FormMode.INGREDIENTS }

    fun startPickingIngredient() { query = ""; mode = FormMode.PICKING_INGREDIENT }

    fun addIngredient(id: String) {
        ingredients.add(IngredientRow(id))
        mode = FormMode.INGREDIENTS
    }

    fun removeIngredient(row: IngredientRow) { ingredients.remove(row) }

    /** One step back inside the screen; false when already on the form (caller leaves the screen). */
    fun back(): Boolean = when (mode) {
        FormMode.FORM -> false
        FormMode.INGREDIENTS -> { mode = FormMode.FORM; true }
        FormMode.PICKING_INGREDIENT -> { mode = FormMode.INGREDIENTS; true }
    }

    val cookedGramsValue: Double? get() = Numbers.parse(cookedGrams)?.takeIf { it > 0 }

    /** Resolved ingredients, or null while any row is incomplete or its food unknown. */
    fun resolvedIngredients(index: FoodIndex): List<Ingredient>? =
        ingredients.map { row ->
            val food = index.resolve(row.foodId) ?: return null
            val g = row.gramsValue ?: return null
            Ingredient(food, g)
        }

    fun composed(index: FoodIndex): Per100g? {
        val list = resolvedIngredients(index) ?: return null
        val cooked = cookedGramsValue ?: return null
        return composePer100g(list, cooked)
    }

    /** Copies the computed values into the form fields and returns to the form. */
    fun useComposed(index: FoodIndex) {
        val r = composed(index) ?: return
        fields.kcal = Numbers.editable(r.kcal)
        fields.carbs = Numbers.editable(r.carbs)
        fields.fat = Numbers.editable(r.fat)
        fields.protein = Numbers.editable(r.protein)
        fields.fibre = Numbers.editable(r.fibre)
        mode = FormMode.FORM
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val id = foodId ?: return
        saving = true
        viewModelScope.launch {
            session.foods.setArchived(id, archived)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodFormScreen(
    foodId: String?,
    prefillLabel: String,
    pickedFoodId: StateFlow<String?>,
    onConsumePickedFood: () -> Unit,
    onCreateFood: (String) -> Unit,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = sessionViewModel(key = "food-${foodId ?: "new"}") { FoodFormViewModel(it, foodId, prefillLabel) }
    val index by vm.foodIndex.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picked by pickedFoodId.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }

    // A food created from the ingredient picker ("Create new food") comes back through the back stack.
    LaunchedEffect(picked) {
        picked?.let {
            vm.addIngredient(it)
            onConsumePickedFood()
        }
    }
    BackHandler(enabled = vm.mode != FormMode.FORM) { vm.back() }

    val title = when (vm.mode) {
        FormMode.FORM -> stringResource(if (vm.isEditing) R.string.edit_food else R.string.new_food)
        FormMode.INGREDIENTS -> stringResource(R.string.from_ingredients)
        FormMode.PICKING_INGREDIENT -> stringResource(R.string.pick_food)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (!vm.back()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val currentIndex = index
        if (vm.loading || currentIndex == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        when (vm.mode) {
            FormMode.PICKING_INGREDIENT -> FoodPicker(
                index = currentIndex,
                recentIds = settings?.lastUsedFoodIds.orEmpty(),
                query = vm.query,
                onQueryChange = { vm.query = it },
                onPick = vm::addIngredient,
                onCreate = { onCreateFood(vm.query) },
                modifier = Modifier.padding(padding),
            )
            FormMode.INGREDIENTS -> IngredientCalculator(vm, currentIndex, Modifier.padding(padding))
            FormMode.FORM -> {}
        }
        if (vm.mode != FormMode.FORM) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            FoodFieldsEditor(vm.fields, duplicate = vm.duplicateOf(currentIndex), autoFocusLabel = !vm.isEditing)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::startIngredients) { Text(stringResource(R.string.calculate_from_ingredients)) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.save(onSaved) }, enabled = vm.fields.isValid && !vm.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
            val existing = vm.existing
            if (existing != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { if (existing.archived) vm.setArchived(false, onBack) else confirmArchive = true },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(if (existing.archived) R.string.unarchive else R.string.archive)) }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.archive_confirm_title)) },
            text = {
                Text(
                    if (vm.myMealCount > 0) pluralStringResource(R.plurals.archive_confirm_used, vm.myMealCount, vm.myMealCount)
                    else stringResource(R.string.archive_confirm_body),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmArchive = false; vm.setArchived(true, onBack) }) { Text(stringResource(R.string.archive)) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Ingredient calculator: raw ingredients with weights plus the weight of the finished dish give
 * the values per 100 g (spec Flow 3a). Nothing here is stored; "Use these values" fills the form.
 */
@Composable
private fun IngredientCalculator(vm: FoodFormViewModel, index: FoodIndex, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val g = stringResource(R.string.grams_unit)

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.ingredients_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (vm.ingredients.isEmpty()) {
            Text(stringResource(R.string.no_ingredients_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        vm.ingredients.forEach { row ->
            val food = index.resolve(row.foodId)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(food?.label ?: stringResource(R.string.unknown_food), style = MaterialTheme.typography.bodyLarge)
                    food?.let {
                        Text(
                            "${Numbers.entered(it.kcalPer100g, locale)} ${stringResource(R.string.kcal_per_100g_short)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NumberField(
                    value = row.grams,
                    onValueChange = { row.grams = it },
                    label = stringResource(R.string.weight_grams),
                    suffix = g,
                    isError = row.grams.isNotBlank() && row.gramsValue == null,
                    modifier = Modifier.width(120.dp),
                )
                IconButton(onClick = { vm.removeIngredient(row) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_ingredient))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = vm::startPickingIngredient, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" " + stringResource(R.string.add_ingredient))
        }

        Spacer(Modifier.height(24.dp))
        NumberField(
            value = vm.cookedGrams,
            onValueChange = { vm.cookedGrams = it },
            label = stringResource(R.string.cooked_weight),
            suffix = g,
            isError = vm.cookedGrams.isNotBlank() && vm.cookedGramsValue == null,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        val resolved = vm.resolvedIngredients(index)
        if (!resolved.isNullOrEmpty()) {
            val rawGrams = resolved.sumOf { it.grams }
            val rawKcal = resolved.sumOf { it.food.kcalPer100g * it.grams / 100.0 }
            Text(
                stringResource(R.string.ingredients_total, Numbers.entered(rawGrams, locale), Numbers.kcal(rawKcal, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        val result = vm.composed(index)
        if (result != null) {
            Text(stringResource(R.string.per_100g_result), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("${Numbers.kcal(result.kcal, locale)} ${stringResource(R.string.kcal_unit)}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            MacroRow(result.protein, result.carbs, result.fat, result.fibre)
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = { vm.useComposed(index) }, enabled = result != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.use_values))
        }
    }
}

/** The six food fields with validation hints. Reused by the merge screen. */
@Composable
fun FoodFieldsEditor(fields: FoodFields, duplicate: Food?, autoFocusLabel: Boolean, modifier: Modifier = Modifier) {
    val labelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocusLabel) labelFocus.requestFocus() }
    val g = stringResource(R.string.grams_unit)
    val invalid = stringResource(R.string.invalid_number)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = fields.label,
            onValueChange = { fields.label = it },
            label = { Text(stringResource(R.string.food_label)) },
            singleLine = true,
            isError = !fields.labelValid && fields.label.isNotEmpty(),
            supportingText = duplicate?.let { { Text(stringResource(R.string.duplicate_label_warning, it.label)) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(labelFocus),
        )
        Spacer(Modifier.height(8.dp))
        NumberField(
            value = fields.kcal,
            onValueChange = { fields.kcal = it },
            label = stringResource(R.string.kcal_per_100g),
            suffix = stringResource(R.string.kcal_unit),
            isError = !fields.kcalValid,
            supportingText = if (!fields.kcalValid) invalid else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.per_100g_optional), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(fields.protein, { fields.protein = it }, stringResource(R.string.protein), Modifier.weight(1f), suffix = g, isError = !fields.optionalValid(fields.protein))
            NumberField(fields.carbs, { fields.carbs = it }, stringResource(R.string.carbs), Modifier.weight(1f), suffix = g, isError = !fields.optionalValid(fields.carbs))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(fields.fat, { fields.fat = it }, stringResource(R.string.fat), Modifier.weight(1f), suffix = g, isError = !fields.optionalValid(fields.fat))
            NumberField(fields.fibre, { fields.fibre = it }, stringResource(R.string.fibre), Modifier.weight(1f), suffix = g, isError = !fields.optionalValid(fields.fibre), imeAction = ImeAction.Done)
        }
        if (fields.macroSum > 100.0) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.macros_exceed_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
