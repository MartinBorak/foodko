package app.foodko.domain

/** Daily macro targets in grams, derived from the kcal target (spec decision 9). */
data class MacroTargets(val protein: Double, val carbs: Double, val fat: Double, val fibre: Double)

/**
 * Macro targets from a daily kcal target. The energy shares are the same for everyone (the
 * reference ranges are roughly protein 10–35 %, carbs 45–65 %, fat 20–35 % of energy); these are
 * mid-range defaults: 20 / 50 / 30 % at 4 / 4 / 9 kcal per gram, and 14 g fibre per 1000 kcal.
 */
fun macroTargetsFor(kcalTarget: Double): MacroTargets = MacroTargets(
    protein = kcalTarget * PROTEIN_SHARE / 4,
    carbs = kcalTarget * CARBS_SHARE / 4,
    fat = kcalTarget * FAT_SHARE / 9,
    fibre = kcalTarget / 1000 * FIBRE_G_PER_1000_KCAL,
)

const val PROTEIN_SHARE = 0.20
const val CARBS_SHARE = 0.50
const val FAT_SHARE = 0.30
const val FIBRE_G_PER_1000_KCAL = 14.0

/**
 * A daily target is a limit (kcal, carbs, fat: going over is bad) or a goal (protein, fibre:
 * reaching it is good, and there is no upper cap). Spec decision 9.
 */
enum class TargetKind {
    Limit, Goal;

    /** Whether [value] has crossed [target]: gone above a limit, or reached a goal. */
    fun crossed(value: Double, target: Double): Boolean = if (this == Limit) value > target else value >= target
}
