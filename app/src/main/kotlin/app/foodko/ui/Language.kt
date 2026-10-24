package app.foodko.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Per-app language via the platform API (Android 13+). The OS persists the choice and recreates
 * the activity; the value is also mirrored in Firestore settings so a new phone picks it up.
 * `null` leaves the current choice alone, "" means "follow the system".
 */
fun applyAppLanguage(context: Context, tag: String?) {
    if (tag == null) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    val desired = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    if (manager.applicationLocales != desired) manager.applicationLocales = desired
}

/** Current per-app language tag, or "" when following the system. */
fun currentAppLanguage(context: Context): String {
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
    return if (locales == null || locales.isEmpty) "" else locales[0].toLanguageTag()
}
