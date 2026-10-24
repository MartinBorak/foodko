package app.foodko.domain

import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.WeightEntry
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class StatsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Bratislava")
    private val bread = Food(id = "bread", label = "Bread", kcalPer100g = 250.0, carbsPer100g = 50.0, fatPer100g = 2.0, proteinPer100g = 8.0, fibrePer100g = 3.0)
    private val index = FoodIndex(listOf(bread, Food(id = "oldbread", mergedInto = "bread")))

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Timestamp {
        val instant = date.atTime(time).atZone(zone).toInstant()
        return Timestamp(instant.epochSecond, instant.nano)
    }

    private fun meal(foodId: String, grams: Double, date: LocalDate, time: LocalTime = LocalTime.NOON) =
        MealEntry(id = "$foodId-$date-$time", foodId = foodId, grams = grams, timestamp = at(date, time))

    private val monday = LocalDate.of(2026, 10, 19)

    @Test
    fun `daily series zero-fills days without meals and resolves redirects`() {
        val meals = joinMeals(
            listOf(
                meal("bread", 100.0, monday),
                meal("oldbread", 100.0, monday, LocalTime.of(23, 30)),
                meal("bread", 200.0, monday.plusDays(2)),
            ),
            index,
        )
        val series = dailySeries(meals, monday, monday.plusDays(2), zone)
        assertEquals(3, series.size)
        assertEquals(500.0, series[0].totals.kcal, 1e-9)
        assertEquals(2, series[0].mealCount)
        assertEquals(0.0, series[1].totals.kcal, 1e-9)
        assertEquals(0, series[1].mealCount)
        assertEquals(500.0, series[2].totals.kcal, 1e-9)
    }

    @Test
    fun `day boundaries are local midnight`() {
        // 00:30 local on Tuesday is still Monday in UTC; must be counted as Tuesday.
        val meals = joinMeals(listOf(meal("bread", 100.0, monday.plusDays(1), LocalTime.of(0, 30))), index)
        val series = dailySeries(meals, monday, monday.plusDays(1), zone)
        assertEquals(0, series[0].mealCount)
        assertEquals(1, series[1].mealCount)
    }

    @Test
    fun `average ignores unlogged days`() {
        val meals = joinMeals(
            listOf(meal("bread", 100.0, monday), meal("bread", 300.0, monday.plusDays(2))),
            index,
        )
        val avg = dailySeries(meals, monday, monday.plusDays(6), zone).averagePerLoggedDay()!!
        assertEquals(500.0, avg.kcal, 1e-9)
        assertNull(dailySeries(emptyList(), monday, monday, zone).averagePerLoggedDay())
    }

    @Test
    fun `weight moving average is time based`() {
        val entries = listOf(
            WeightEntry(id = "1", kg = 90.0, timestamp = at(monday)),
            WeightEntry(id = "2", kg = 88.0, timestamp = at(monday.plusDays(3))),
            WeightEntry(id = "3", kg = 86.0, timestamp = at(monday.plusDays(20))),
        ).shuffled()
        val series = weightSeries(entries, windowDays = 7)
        assertEquals(listOf(90.0, 88.0, 86.0), series.map { it.kg })
        assertEquals(90.0, series[0].movingAverage, 1e-9)
        assertEquals(89.0, series[1].movingAverage, 1e-9)
        assertEquals(86.0, series[2].movingAverage, 1e-9) // older points fell out of the window
        assertEquals(-4.0, series.change()!!, 1e-9)
        assertNull(series.take(1).change())
    }
}
