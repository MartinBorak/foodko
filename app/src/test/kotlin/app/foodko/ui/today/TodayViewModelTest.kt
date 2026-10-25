package app.foodko.ui.today

import app.foodko.CLOCK
import app.foodko.MainDispatcherRule
import app.foodko.TODAY
import app.foodko.TestWorld
import app.foodko.ZONE
import app.foodko.data.model.UserSettings
import app.foodko.food
import app.foodko.meal
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class TodayViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val yesterday = TODAY.minusDays(1)

    private val world = TestWorld(
        main.dispatcher,
        foods = listOf(
            food("bread", "Bread", kcal = 250.0, carbs = 50.0, fat = 2.0, protein = 8.0, fibre = 3.0),
            food("oldbread", mergedInto = "bread"),
            food("mystery", "Mystery", kcal = 400.0, carbs = null, fat = null, protein = null, fibre = null),
        ),
        meals = listOf(
            meal("late", "bread", 100.0, yesterday, LocalTime.of(23, 50)),
            meal("early", "oldbread", 200.0, TODAY, LocalTime.of(0, 10)),
            meal("lunch", "bread", 100.0, TODAY, LocalTime.of(12, 0)),
            meal("snack", "mystery", 50.0, TODAY, LocalTime.of(10, 0)),
        ),
        settings = UserSettings(dailyKcalTarget = 2000.0),
    )
    private lateinit var vm: TodayViewModel

    // Built here, not in a field initialiser: viewModelScope must see the rule's Main dispatcher.
    @Before
    fun setUp() {
        vm = TodayViewModel(world.session, CLOCK)
    }

    @After
    fun tearDown() = world.session.close()

    private fun kotlinx.coroutines.test.TestScope.subscribe() = backgroundScope.launch(main.dispatcher) { vm.uiState.collect() }

    @Test
    fun `shows today's meals only, in time order, with totals and target`() = runTest {
        subscribe()
        val state = vm.uiState.value

        assertFalse(state.loading)
        assertEquals(TODAY, state.date)
        assertEquals(listOf("early", "snack", "lunch"), state.meals.map { it.meal.id })
        assertEquals("redirect resolved", "Bread", state.meals[0].food!!.label)
        assertEquals(500.0 + 250.0 + 200.0, state.totals.kcal, 1e-9)
        assertEquals(150.0, state.totals.carbs, 1e-9)
        assertTrue("mystery food has no macros", state.totals.incomplete)
        assertEquals(2000.0, state.target!!, 1e-9)
    }

    @Test
    fun `day boundaries follow the local zone, not UTC`() = runTest {
        subscribe()
        vm.shiftDay(-1)
        val state = vm.uiState.value
        assertEquals(yesterday, state.date)
        assertEquals(listOf("late"), state.meals.map { it.meal.id })
        assertEquals(250.0, state.totals.kcal, 1e-9)

        vm.setDate(TODAY.minusDays(7))
        assertTrue(vm.uiState.value.meals.isEmpty())
        assertEquals(0.0, vm.uiState.value.totals.kcal, 1e-9)
    }

    @Test
    fun `log again copies food and grams, stamps now, records the recent food and returns to today`() = runTest {
        subscribe()
        vm.shiftDay(-1)
        val item = vm.uiState.value.meals.single()

        vm.logAgain(item)

        val added = world.meals.state.value.single { it.id !in setOf("late", "early", "lunch", "snack") }
        assertEquals("bread", added.foodId)
        assertEquals(100.0, added.grams, 1e-9)
        assertEquals(CLOCK.instant(), added.timestamp.toInstant())
        assertEquals(listOf("bread"), world.settings.state.value.lastUsedFoodIds)
        assertEquals(TODAY, vm.uiState.value.date)
        assertEquals(4, vm.uiState.value.meals.size)
    }

    @Test
    fun `log again of a redirected meal uses the surviving food`() = runTest {
        subscribe()
        val item = vm.uiState.value.meals.first { it.meal.id == "early" }
        vm.logAgain(item)
        val added = world.meals.state.value.single { it.timestamp.toInstant() == CLOCK.instant() }
        assertEquals("bread", added.foodId)
    }

    @Test
    fun `is loading until the streams deliver`() = runTest {
        val empty = TestWorld(main.dispatcher)
        val fresh = TodayViewModel(empty.session, CLOCK)
        backgroundScope.launch(main.dispatcher) { fresh.uiState.collect() }
        assertFalse("fakes deliver immediately", fresh.uiState.value.loading)
        assertTrue(fresh.uiState.value.meals.isEmpty())
        assertEquals(TODAY.atStartOfDay(ZONE).toLocalDate(), fresh.uiState.value.date)
        empty.session.close()
    }
}
