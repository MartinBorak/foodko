package app.foodko.ui.foods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.foodko.domain.Numbers
import app.foodko.domain.normalizeForSearch
import app.foodko.ui.components.LoadingBox
import app.foodko.ui.components.NumberField
import app.foodko.ui.sessionViewModel
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

class FoodFormViewModel(private val session: UserSession, private val foodId: String?, prefillLabel: String) : ViewModel() {

    val foodIndex = session.foodIndex
    val fields = FoodFields().apply { label = prefillLabel }
    var loading by mutableStateOf(foodId != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var existing by mutableStateOf<Food?>(null)
        private set

    /** How many of *my* meals reference this food (the other user's are invisible). */
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
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = sessionViewModel(key = "food-${foodId ?: "new"}") { FoodFormViewModel(it, foodId, prefillLabel) }
    val index by vm.foodIndex.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEditing) R.string.edit_food else R.string.new_food)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            FoodFieldsEditor(vm.fields, duplicate = vm.duplicateOf(currentIndex), autoFocusLabel = !vm.isEditing)
            Spacer(Modifier.height(24.dp))
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
