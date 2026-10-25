package app.foodko.domain

import app.foodko.data.model.Food

/** One ingredient of a dish: a food and its raw weight. */
data class Ingredient(val food: Food, val grams: Double)

/** Values per 100 g of a finished dish. Macros are null when any ingredient doesn't declare them. */
data class Per100g(
    val kcal: Double,
    val carbs: Double?,
    val fat: Double?,
    val protein: Double?,
    val fibre: Double?,
)

/**
 * Nutrition per 100 g of a dish made from [ingredients] that weighs [cookedGrams] when finished.
 * The finished weight differs from the sum of the ingredients (water added or evaporated), which
 * is why it is measured rather than derived. Null when there is nothing to compute from.
 *
 * A macro missing on any ingredient makes the result's macro null rather than a silently low
 * number; the user can still type a value in by hand.
 */
fun composePer100g(ingredients: List<Ingredient>, cookedGrams: Double): Per100g? {
    if (ingredients.isEmpty() || cookedGrams <= 0.0 || !cookedGrams.isFinite()) return null
    val parts = ingredients.map { it.food.nutritionFor(it.grams) }
    val scale = 100.0 / cookedGrams
    fun macro(pick: (Nutrition) -> Double?): Double? {
        var sum = 0.0
        for (n in parts) sum += pick(n) ?: return null
        return sum * scale
    }
    return Per100g(
        kcal = parts.sumOf { it.kcal } * scale,
        carbs = macro { it.carbs },
        fat = macro { it.fat },
        protein = macro { it.protein },
        fibre = macro { it.fibre },
    )
}
