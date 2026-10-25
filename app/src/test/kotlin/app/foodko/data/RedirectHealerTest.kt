package app.foodko.data

import app.foodko.data.model.UserSettings
import app.foodko.domain.FoodIndex
import app.foodko.food
import app.foodko.meal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RedirectHealerTest {

    private val foods = FakeFoodRepository(
        listOf(
            food("new", "Skyr"),
            food("old", mergedInto = "new"),
            food("older", mergedInto = "old"),
            food("other", "Bread"),
        ),
    )
    private val meals = FakeMealRepository(
        listOf(meal("m1", "old", 100.0), meal("m2", "other", 50.0), meal("m3", "older", 10.0)),
    )
    private val settings = FakeSettingsRepository(UserSettings(lastUsedFoodIds = listOf("old", "other", "new")))

    private fun startHealer(scope: kotlinx.coroutines.CoroutineScope) =
        RedirectHealer(meals, settings).start(
            scope = scope,
            foodIndex = foods.state.map { FoodIndex(it) },
            allMeals = meals.state,
            userSettings = settings.state,
        )

    @Test
    fun `rewrites meals and the recent list to the final food after the debounce`() = runTest {
        startHealer(backgroundScope)

        advanceTimeBy(999)
        assertTrue("must wait out the debounce", meals.repointCalls.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("new", "other", "new"), meals.state.value.sortedBy { it.id }.map { it.foodId })
        assertEquals(mapOf("old" to "new", "older" to "new"), meals.repointCalls.single())
        // "old" became "new", which was already in the list, so it is deduplicated.
        assertEquals(listOf("new", "other"), settings.state.value.lastUsedFoodIds)
    }

    @Test
    fun `does nothing when nothing is redirected`() = runTest {
        meals.state.value = listOf(meal("m1", "new", 100.0))
        settings.state.value = UserSettings(lastUsedFoodIds = listOf("new"))
        startHealer(backgroundScope)

        advanceTimeBy(5_000)
        assertTrue(meals.repointCalls.isEmpty())
        assertTrue(settings.lastUsedWrites.isEmpty())
    }

    @Test
    fun `ignores an empty food index rather than treating every meal as dangling`() = runTest {
        foods.state.value = emptyList()
        startHealer(backgroundScope)

        advanceTimeBy(5_000)
        assertTrue(meals.repointCalls.isEmpty())
        assertEquals("old", meals.state.value.first { it.id == "m1" }.foodId)
    }

    @Test
    fun `settles after one pass and reacts to a later merge`() = runTest {
        startHealer(backgroundScope)
        advanceTimeBy(1_500)
        assertEquals(1, meals.repointCalls.size)

        advanceTimeBy(10_000)
        assertEquals("no further writes once healed", 1, meals.repointCalls.size)

        // Another user merges Bread into Skyr.
        foods.state.value = foods.state.value.map { if (it.id == "other") it.copy(mergedInto = "new") else it }
        advanceTimeBy(1_500)
        assertEquals(2, meals.repointCalls.size)
        assertTrue(meals.state.value.all { it.foodId == "new" })
        assertEquals(listOf("new"), settings.state.value.lastUsedFoodIds)
    }
}
