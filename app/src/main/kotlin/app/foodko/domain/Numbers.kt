package app.foodko.domain

import java.text.NumberFormat
import java.util.Locale

/**
 * Number display and input rules (spec §6): whole numbers show no decimal part, otherwise trailing
 * zeros are trimmed, max 1 decimal for computed values and max 2 for values as entered.
 * Input accepts both ',' and '.' as the decimal separator.
 */
object Numbers {

    fun format(value: Double, maxDecimals: Int, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = maxDecimals
            minimumFractionDigits = 0
            isGroupingUsed = false
            roundingMode = java.math.RoundingMode.HALF_UP
        }
        // Avoid "-0" after rounding tiny negatives.
        val rounded = nf.format(value)
        return if (rounded == "-0") "0" else rounded
    }

    /** Macro grams in meal nutrition, totals, averages. */
    fun computed(value: Double, locale: Locale = Locale.getDefault()) = format(value, 1, locale)

    /** Calories are always whole numbers; a tenth of a kcal is noise. */
    fun kcal(value: Double, locale: Locale = Locale.getDefault()) = format(value, 0, locale)

    /** Food values, weight, grams: shown as the user entered them. */
    fun entered(value: Double, locale: Locale = Locale.getDefault()) = format(value, 2, locale)

    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * Text to prefill an editable field with. Uses the app locale's decimal separator (Slovak
     * users see "4,1"); [parse] accepts both separators, so round-tripping is safe either way.
     */
    fun editable(value: Double?, locale: Locale = Locale.getDefault()): String =
        value?.let { format(it, 2, locale) } ?: ""
}
