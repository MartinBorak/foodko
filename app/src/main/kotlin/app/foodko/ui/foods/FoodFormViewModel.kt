package app.foodko.ui.foods

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.foodko.UserSession
import app.foodko.data.model.Food
import app.foodko.domain.FoodIndex
import app.foodko.domain.Ingredient
import app.foodko.domain.Per100g
import app.foodko.domain.composePer100g
import app.foodko.domain.Numbers
import app.foodko.domain.normalizeForSearch
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
    var note by mutableStateOf("")

    fun load(food: Food) {
        label = food.label
        note = food.note.orEmpty()
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
        note = note.trim().ifEmpty { null },
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

    /**
     * Another live food with the same label, ignoring case and diacritics. Labels are unique, so
     * this blocks saving (spec Flow 3). Archived foods don't count.
     */
    fun duplicateOf(index: FoodIndex): Food? {
        val key = normalizeForSearch(fields.label)
        if (key.isEmpty()) return null
        return index.visible.firstOrNull { it.labelLower == key && it.id != foodId }
    }

    /**
     * Existing foods matching the label typed for a new food, so it isn't added twice (spec
     * Flow 3). Same search as the food picker; from two characters, exact match first.
     */
    fun suggestions(index: FoodIndex): List<Food> {
        if (isEditing) return emptyList()
        val key = normalizeForSearch(fields.label)
        if (key.length < 2) return emptyList()
        return index.search(key).sortedBy { it.labelLower != key }.take(MAX_SUGGESTIONS)
    }

    fun canSave(index: FoodIndex): Boolean = fields.isValid && duplicateOf(index) == null && !saving

    fun save(onSaved: (String) -> Unit) {
        val index = foodIndex.value ?: return
        if (!canSave(index)) return
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

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}
