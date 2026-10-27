package app.foodko.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetsTest {

    @Test
    fun `2000 kcal gives the textbook grams`() {
        val t = macroTargetsFor(2000.0)
        assertEquals(100.0, t.protein, 1e-9)   // 20 % of 2000 = 400 kcal / 4
        assertEquals(250.0, t.carbs, 1e-9)     // 50 % = 1000 kcal / 4
        assertEquals(66.666666667, t.fat, 1e-6) // 30 % = 600 kcal / 9
        assertEquals(28.0, t.fibre, 1e-9)      // 14 g per 1000 kcal
    }

    @Test
    fun `macro energy adds up to the kcal target`() {
        val t = macroTargetsFor(1850.0)
        assertEquals(1850.0, t.protein * 4 + t.carbs * 4 + t.fat * 9, 1e-9)
    }

    @Test
    fun `a limit is crossed only above it, a goal already at it`() {
        assertFalse(TargetKind.Limit.crossed(2000.0, 2000.0))
        assertTrue(TargetKind.Limit.crossed(2000.1, 2000.0))
        assertFalse(TargetKind.Goal.crossed(99.9, 100.0))
        assertTrue(TargetKind.Goal.crossed(100.0, 100.0))
        assertTrue(TargetKind.Goal.crossed(300.0, 100.0))
    }
}
