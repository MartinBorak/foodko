package app.foodko.domain

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = Regex("\\p{Mn}+")

/** Lower-case and strip diacritics so "Skyr Vanilkový" matches "vanilkovy". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
