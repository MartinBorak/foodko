package app.foodko.domain

import app.foodko.data.model.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositeTest {

    private val rice = Food(
        id = "rice", label = "Rice, dry", kcalPer100g = 350.0,
        carbsPer100g = 78.0, fatPer100g = 1.0, proteinPer100g = 7.0, fibrePer100g = 1.5,
    )
    private val butter = Food(
        id = "butter", label = "Butter", kcalPer100g = 740.0,
        carbsPer100g = 0.5, fatPer100g = 82.0, proteinPer100g = 0.5, fibrePer100g = 0.0,
    )
    private val stock = Food(id = "stock", label = "Stock cube", kcalPer100g = 200.0, proteinPer100g = 10.0)

    @Test
    fun `scales the summed ingredients to the finished weight`() {
        // 200 g dry rice + 20 g butter, cooked with water to 600 g.
        val r = composePer100g(listOf(Ingredient(rice, 200.0), Ingredient(butter, 20.0)), 600.0)!!
        assertEquals((700.0 + 148.0) / 6, r.kcal, 1e-9)
        assertEquals((156.0 + 0.1) / 6, r.carbs!!, 1e-9)
        assertEquals((2.0 + 16.4) / 6, r.fat!!, 1e-9)
        assertEquals((14.0 + 0.1) / 6, r.protein!!, 1e-9)
        assertEquals(3.0 / 6, r.fibre!!, 1e-9)
    }

    @Test
    fun `evaporation concentrates the values`() {
        val raw = composePer100g(listOf(Ingredient(rice, 100.0)), 100.0)!!
        val reduced = composePer100g(listOf(Ingredient(rice, 100.0)), 50.0)!!
        assertEquals(raw.kcal * 2, reduced.kcal, 1e-9)
        assertEquals(raw.protein!! * 2, reduced.protein!!, 1e-9)
    }

    @Test
    fun `a macro missing on any ingredient is missing on the result`() {
        val r = composePer100g(listOf(Ingredient(rice, 100.0), Ingredient(stock, 10.0)), 300.0)!!
        assertEquals((350.0 + 20.0) / 3, r.kcal, 1e-9)
        assertEquals((7.0 + 1.0) / 3, r.protein!!, 1e-9)
        assertNull(r.carbs)
        assertNull(r.fat)
        assertNull(r.fibre)
    }

    @Test
    fun `nothing to compute from gives null`() {
        assertNull(composePer100g(emptyList(), 100.0))
        assertNull(composePer100g(listOf(Ingredient(rice, 100.0)), 0.0))
        assertNull(composePer100g(listOf(Ingredient(rice, 100.0)), -5.0))
    }
}
