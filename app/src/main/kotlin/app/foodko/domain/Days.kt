package app.foodko.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A "day" is local midnight to midnight on the phone (spec §6). */
fun Instant.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

fun LocalDate.startInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = atStartOfDay(zone).toInstant()

/** Exclusive end: start of the next day. */
fun LocalDate.endInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = plusDays(1).startInstant(zone)

/** Every day from [from] to [to] inclusive, ascending. */
fun daysBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
