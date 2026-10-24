package app.foodko.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.foodko.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun formatTime(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(instant.atZone(ZoneId.systemDefault()))

fun formatDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

fun formatShortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("d.M.", locale).format(date)

fun formatDateTime(instant: Instant, locale: Locale): String {
    val zoned = instant.atZone(ZoneId.systemDefault())
    return formatDate(zoned.toLocalDate(), locale) + " " + formatTime(instant, locale)
}

/** "Today", "Yesterday" or a date, with the weekday for anything else. */
@Composable
fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> DateTimeFormatter.ofPattern("EEE d. M. yyyy", currentLocale()).format(date)
    }
}
