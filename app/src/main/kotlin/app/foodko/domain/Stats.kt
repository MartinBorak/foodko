package app.foodko.domain

import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.WeightEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A meal joined with its (redirect-resolved) food. [food] is null only for dangling ids. */
data class MealWithFood(
    val meal: MealEntry,
    val food: Food?,
    val time: Instant,
) {
    val nutrition: Nutrition? = food?.nutritionFor(meal.grams)
}

fun joinMeals(meals: List<MealEntry>, index: FoodIndex): List<MealWithFood> =
    meals.map { m ->
        MealWithFood(
            meal = m,
            food = index.resolve(m.foodId),
            time = Instant.ofEpochSecond(m.timestamp.seconds, m.timestamp.nanoseconds.toLong()),
        )
    }

fun List<MealWithFood>.totals(): Totals = mapNotNull { it.nutrition }.totals()

data class DailyTotals(val date: LocalDate, val totals: Totals, val mealCount: Int)

/** One entry per day in [from]..[to], zero totals on days without meals. */
fun dailySeries(
    meals: List<MealWithFood>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<DailyTotals> {
    val byDay = meals.groupBy { it.time.toLocalDate(zone) }
    return daysBetween(from, to).map { day ->
        val dayMeals = byDay[day].orEmpty()
        DailyTotals(day, dayMeals.totals(), dayMeals.size)
    }
}

/**
 * Average per *logged* day. Days with no meals are far more likely unlogged than fasted, so they
 * are left out rather than dragging the average to zero. Null when no day has meals.
 */
fun List<DailyTotals>.averagePerLoggedDay(): Totals? {
    val logged = filter { it.mealCount > 0 }
    if (logged.isEmpty()) return null
    return logged.fold(Totals()) { acc, d -> acc + d.totals }.dividedBy(logged.size)
}

data class WeightPoint(val time: Instant, val kg: Double, val movingAverage: Double)

/**
 * Weight series with a trailing moving average over [windowDays] (time-based, so sparse weigh-ins
 * still work). Input order doesn't matter.
 */
fun weightSeries(entries: List<WeightEntry>, windowDays: Long = 7): List<WeightPoint> {
    val sorted = entries
        .map { Instant.ofEpochSecond(it.timestamp.seconds, it.timestamp.nanoseconds.toLong()) to it.kg }
        .sortedBy { it.first }
    val window = Duration.ofDays(windowDays)
    return sorted.mapIndexed { i, (time, kg) ->
        val from = time.minus(window)
        var sum = 0.0
        var n = 0
        var j = i
        while (j >= 0 && !sorted[j].first.isBefore(from)) {
            sum += sorted[j].second
            n++
            j--
        }
        WeightPoint(time, kg, sum / n)
    }
}

/** Last minus first weight in the series, null with fewer than two points. */
fun List<WeightPoint>.change(): Double? = if (size < 2) null else last().kg - first().kg
