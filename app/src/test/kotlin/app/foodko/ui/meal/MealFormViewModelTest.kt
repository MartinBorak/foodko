package app.foodko.ui.meal

import app.foodko.CLOCK
import app.foodko.MainDispatcherRule
import app.foodko.TODAY
import app.foodko.TestWorld
import app.foodko.ZONE
import app.foodko.data.model.UserSettings
import app.foodko.food
import app.foodko.meal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class MealFormViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val world = TestWorld(
        main.dispatcher,
        foods = listOf(food("bread", "Bread"), food("skyr", "Skyr")),
        meals = listOf(meal("m1", "bread", 120.0, TODAY, LocalTime.of(8, 0))),
        settings = UserSettings(lastUsedFoodIds = listOf("bread")),
    )

    @After
    fun tearDown() = world.session.close()

    @Test
    fun `a new meal starts in the picker and saves with the chosen food, parsed grams and now`() = runTest {
        val vm = MealFormViewModel(world.session, mealId = null, initialFoodId = null, day = null, clock = CLOCK)
        assertTrue(vm.picking)
        assertFalse(vm.loading)
        assertFalse(vm.isEditing)
        assertFalse(vm.canSave)
        assertFalse("nothing to go back to without a food", vm.cancelPicking())

        vm.pickFood("skyr")
        assertFalse(vm.picking)
        assertFalse("grams still missing", vm.canSave)

        vm.grams = "0"
        assertFalse(vm.canSave)
        vm.grams = "150,5"
        assertTrue(vm.canSave)

        var done = false
        vm.save { done = true }

        assertTrue(done)
        val saved = world.meals.state.value.single { it.id != "m1" }
        assertEquals("skyr", saved.foodId)
        assertEquals(150.5, saved.grams, 1e-9)
        assertEquals(CLOCK.instant(), saved.timestamp.toInstant())
        assertEquals(listOf("skyr", "bread"), world.settings.state.value.lastUsedFoodIds)
    }

    @Test
    fun `a preselected food skips the picker`() = runTest {
        val vm = MealFormViewModel(world.session, mealId = null, initialFoodId = "bread", day = null, clock = CLOCK)
        assertFalse(vm.picking)
        assertEquals("bread", vm.foodId)
        vm.startPicking()
        assertTrue(vm.picking)
        assertTrue(vm.cancelPicking())
        assertFalse(vm.picking)
    }

    @Test
    fun `a meal for a past day is stamped on that day at the current time of day`() = runTest {
        val day = TODAY.minusDays(3)
        val vm = MealFormViewModel(world.session, mealId = null, initialFoodId = "bread", day = day, clock = CLOCK)
        val expected = day.atTime(LocalTime.of(14, 0)).atZone(ZONE).toInstant()
        assertEquals(expected, vm.timestamp)

        val sameDay = MealFormViewModel(world.session, mealId = null, initialFoodId = "bread", day = TODAY, clock = CLOCK)
        assertEquals(CLOCK.instant(), sameDay.timestamp)
    }

    @Test
    fun `editing loads the meal and saving keeps its id`() = runTest {
        val vm = MealFormViewModel(world.session, mealId = "m1", initialFoodId = null, day = null, clock = CLOCK)
        assertTrue(vm.isEditing)
        assertFalse(vm.loading)
        assertFalse(vm.picking)
        assertEquals("bread", vm.foodId)
        assertEquals("120", vm.grams)
        assertEquals(TODAY.atTime(8, 0).atZone(ZONE).toInstant(), vm.timestamp)

        vm.grams = "90"
        vm.pickFood("skyr")
        vm.save {}

        val meals = world.meals.state.value
        assertEquals(1, meals.size)
        assertEquals("m1", meals[0].id)
        assertEquals("skyr", meals[0].foodId)
        assertEquals(90.0, meals[0].grams, 1e-9)
        assertEquals("edited timestamp is preserved", TODAY.atTime(8, 0).atZone(ZONE).toInstant(), meals[0].timestamp.toInstant())
    }

    @Test
    fun `deleting removes the meal`() = runTest {
        val vm = MealFormViewModel(world.session, mealId = "m1", initialFoodId = null, day = null, clock = CLOCK)
        var done = false
        vm.delete { done = true }
        assertTrue(done)
        assertTrue(world.meals.state.value.isEmpty())
    }

    @Test
    fun `an unknown meal id finishes loading with an empty form`() = runTest {
        val vm = MealFormViewModel(world.session, mealId = "ghost", initialFoodId = null, day = null, clock = CLOCK)
        assertFalse(vm.loading)
        assertNull(vm.foodId)
        assertFalse(vm.canSave)
    }
}
