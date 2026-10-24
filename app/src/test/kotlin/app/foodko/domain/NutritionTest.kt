package app.foodko.domain

import app.foodko.data.model.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {

    private val skyr = Food(
        id = "skyr", label = "Skyr", kcalPer100g = 63.0,
        carbsPer100g = 4.0, fatPer100g = 0.2, proteinPer100g = 11.0, fibrePer100g = 0.0,
    )
    private val mystery = Food(id = "m", label = "Mystery bar", kcalPer100g = 400.0, proteinPer100g = 10.0)

    @Test
    fun `nutrition scales per 100 g`() {
        val n = skyr.nutritionFor(150.0)
        assertEquals(94.5, n.kcal, 1e-9)
        assertEquals(6.0, n.carbs!!, 1e-9)
        assertEquals(0.3, n.fat!!, 1e-9)
        assertEquals(16.5, n.protein!!, 1e-9)
        assertTrue(n.isComplete)
    }

    @Test
    fun `missing macros stay null on the meal but count as zero in totals`() {
        val n = mystery.nutritionFor(50.0)
        assertNull(n.carbs)
        assertFalse(n.isComplete)

        val t = listOf(skyr.nutritionFor(100.0), n).totals()
        assertEquals(263.0, t.kcal, 1e-9)
        assertEquals(4.0, t.carbs, 1e-9)
        assertEquals(16.0, t.protein, 1e-9)
        assertTrue(t.incomplete)
    }

    @Test
    fun `complete totals are not flagged`() {
        assertFalse(listOf(skyr.nutritionFor(100.0)).totals().incomplete)
    }

    @Test
    fun `macro split uses 4-4-9 and sums to 100`() {
        val split = Totals(kcal = 0.0, carbs = 10.0, fat = 10.0, protein = 10.0).macroSplit()!!
        assertEquals(100.0, split.proteinPct + split.carbsPct + split.fatPct, 1e-9)
        assertEquals(90.0 / 170.0 * 100, split.fatPct, 1e-9)
        assertNull(Totals().macroSplit())
    }

    @Test
    fun `dividedBy averages and keeps the incomplete flag`() {
        val avg = Totals(kcal = 300.0, incomplete = true).dividedBy(3)
        assertEquals(100.0, avg.kcal, 1e-9)
        assertTrue(avg.incomplete)
    }
}
