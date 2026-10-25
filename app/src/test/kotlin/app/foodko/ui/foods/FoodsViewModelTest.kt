package app.foodko.ui.foods

import app.foodko.MainDispatcherRule
import app.foodko.TestWorld
import app.foodko.food
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FoodsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val world = TestWorld(main.dispatcher, foods = listOf(food("a"), food("b")))
    private lateinit var vm: FoodsViewModel

    // Built here, not in a field initialiser: viewModelScope must see the rule's Main dispatcher.
    @Before
    fun setUp() {
        vm = FoodsViewModel(world.session)
    }

    @After
    fun tearDown() = world.session.close()

    @Test
    fun `long-press toggles selection and enters selection mode`() {
        assertFalse(vm.selecting)
        vm.toggle("a")
        assertTrue(vm.selecting)
        assertEquals(setOf("a"), vm.selected)
        vm.toggle("b")
        vm.toggle("a")
        assertEquals(setOf("b"), vm.selected)
    }

    @Test
    fun `the menu enters selection mode with nothing selected, the X leaves it`() {
        vm.startSelecting()
        assertTrue(vm.selecting)
        assertTrue(vm.selected.isEmpty())
        vm.toggle("a")
        vm.clearSelection()
        assertFalse(vm.selecting)
        assertTrue(vm.selected.isEmpty())
    }

    @Test
    fun `exposes the shared food index`() {
        assertEquals(listOf("a", "b"), vm.foodIndex.value!!.visible.map { it.id })
    }
}
