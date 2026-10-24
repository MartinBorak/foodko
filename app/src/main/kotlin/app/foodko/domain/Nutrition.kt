package app.foodko.domain

import app.foodko.data.model.Food

/** Nutrition of one meal. Macros are null when the food doesn't declare them. */
data class Nutrition(
    val kcal: Double,
    val carbs: Double?,
    val fat: Double?,
    val protein: Double?,
    val fibre: Double?,
) {
    val isComplete: Boolean
        get() = carbs != null && fat != null && protein != null && fibre != null
}

/** Summed nutrition over several meals. Missing macros count as 0 and set [incomplete]. */
data class Totals(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val protein: Double = 0.0,
    val fibre: Double = 0.0,
    val incomplete: Boolean = false,
) {
    operator fun plus(n: Nutrition) = Totals(
        kcal = kcal + n.kcal,
        carbs = carbs + (n.carbs ?: 0.0),
        fat = fat + (n.fat ?: 0.0),
        protein = protein + (n.protein ?: 0.0),
        fibre = fibre + (n.fibre ?: 0.0),
        incomplete = incomplete || !n.isComplete,
    )

    operator fun plus(o: Totals) = Totals(
        kcal + o.kcal, carbs + o.carbs, fat + o.fat, protein + o.protein, fibre + o.fibre,
        incomplete || o.incomplete,
    )

    fun dividedBy(n: Int) = if (n == 0) this else Totals(
        kcal / n, carbs / n, fat / n, protein / n, fibre / n, incomplete,
    )
}

fun Food.nutritionFor(grams: Double): Nutrition {
    val f = grams / 100.0
    return Nutrition(
        kcal = kcalPer100g * f,
        carbs = carbsPer100g?.times(f),
        fat = fatPer100g?.times(f),
        protein = proteinPer100g?.times(f),
        fibre = fibrePer100g?.times(f),
    )
}

fun Iterable<Nutrition>.totals(): Totals = fold(Totals()) { acc, n -> acc + n }

/** Share of energy from each macro, using 4/4/9 kcal per gram. Null when nothing is known. */
data class MacroSplit(val proteinPct: Double, val carbsPct: Double, val fatPct: Double)

fun Totals.macroSplit(): MacroSplit? {
    val p = protein * 4
    val c = carbs * 4
    val f = fat * 9
    val sum = p + c + f
    if (sum <= 0.0) return null
    return MacroSplit(p / sum * 100, c / sum * 100, f / sum * 100)
}
