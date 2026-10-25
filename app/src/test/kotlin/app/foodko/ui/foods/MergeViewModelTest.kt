package app.foodko.ui.foods

import app.foodko.MainDispatcherRule
import app.foodko.TestWorld
import app.foodko.data.model.UserSettings
import app.foodko.food
import app.foodko.meal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MergeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val world = TestWorld(
        main.dispatcher,
        foods = listOf(
            food("a", "Skyr", kcal = 60.0, createdAtSeconds = 300),
            food("b", "Skyr vanilla", kcal = 63.0, createdAtSeconds = 100),
            food("c", "Skyr Vanilkový", kcal = 65.0, createdAtSeconds = 200),
            food("old-b", mergedInto = "b"),
        ),
        meals = listOf(meal("m1", "a", 100.0), meal("m2", "b", 100.0), meal("m3", "old-b", 100.0)),
        settings = UserSettings(lastUsedFoodIds = listOf("c", "a")),
    )

    @After
    fun tearDown() = world.session.close()

    @Test
    fun `resolves and deduplicates the ids, counts usage through redirects and proposes the most used survivor`() = runTest {
        val vm = MergeViewModel(world.session, listOf("a", "b", "old-b", "c", "ghost"))
        assertFalse(vm.loading)
        assertEquals(listOf("a", "b", "c"), vm.foods.map { it.id })
        assertEquals(mapOf("a" to 1, "b" to 2), vm.usage)
        assertEquals("b", vm.survivorId)
        assertEquals("Skyr vanilla", vm.fields.label)
        assertEquals("63", vm.fields.kcal)
        assertTrue(vm.canSave)
    }

    @Test
    fun `ties on usage go to the oldest food`() = runTest {
        world.meals.state.value = emptyList()
        val vm = MergeViewModel(world.session, listOf("a", "b", "c"))
        assertEquals("b", vm.survivorId)
    }

    @Test
    fun `choosing another survivor reloads the editable values`() = runTest {
        val vm = MergeViewModel(world.session, listOf("a", "b", "c"))
        vm.choose("c")
        assertEquals("c", vm.survivorId)
        assertEquals("Skyr Vanilkový", vm.fields.label)
        assertEquals("65", vm.fields.kcal)
    }

    @Test
    fun `save hands the edited survivor and the losers to the merger`() = runTest {
        val vm = MergeViewModel(world.session, listOf("a", "b", "c"))
        vm.fields.label = "Skyr vanilkový "
        vm.fields.kcal = "64"
        var done = false
        vm.save { done = true }

        assertTrue(done)
        val call = world.merger.calls.single()
        assertEquals("b", call.survivor.id)
        assertEquals("Skyr vanilkový", call.survivor.label)
        assertEquals(64.0, call.survivor.kcalPer100g, 1e-9)
        assertEquals(listOf("a", "c"), call.mergedIds)
        assertEquals("the user's recent list goes along for rewriting", listOf("c", "a"), call.lastUsedFoodIds)
    }

    @Test
    fun `fewer than two real foods cannot be merged`() = runTest {
        val vm = MergeViewModel(world.session, listOf("b", "old-b"))
        assertEquals(1, vm.foods.size)
        assertFalse(vm.canSave)
        vm.save { throw AssertionError("must not merge") }
        assertTrue(world.merger.calls.isEmpty())
    }

    @Test
    fun `invalid edits block saving`() = runTest {
        val vm = MergeViewModel(world.session, listOf("a", "b"))
        vm.fields.kcal = "x"
        assertFalse(vm.canSave)
    }
}
