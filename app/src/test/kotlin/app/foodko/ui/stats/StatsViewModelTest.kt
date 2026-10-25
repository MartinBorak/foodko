package app.foodko.ui.stats

import app.foodko.CLOCK
import app.foodko.MainDispatcherRule
import app.foodko.TODAY
import app.foodko.TestWorld
import app.foodko.data.model.UserSettings
import app.foodko.food
import app.foodko.meal
import app.foodko.ui.components.StatsRange
import app.foodko.weight
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val world = TestWorld(
        main.dispatcher,
        foods = listOf(food("bread", "Bread", kcal = 250.0, carbs = 50.0, fat = 2.0, protein = 8.0, fibre = 3.0)),
        meals = listOf(
            meal("m1", "bread", 100.0, TODAY),
            meal("m2", "bread", 100.0, TODAY.minusDays(2)),
            meal("m3", "bread", 200.0, TODAY.minusDays(2)),
            meal("m4", "bread", 100.0, TODAY.minusDays(20)),
        ),
        weights = listOf(
            weight("w1", 90.0, TODAY.minusDays(40)),
            weight("w2", 88.0, TODAY.minusDays(3)),
            weight("w3", 87.0, TODAY),
        ),
        settings = UserSettings(dailyKcalTarget = 1800.0),
    )
    private lateinit var vm: StatsViewModel

    // Built here, not in a field initialiser: viewModelScope must see the rule's Main dispatcher.
    @Before
    fun setUp() {
        vm = StatsViewModel(world.session, CLOCK)
    }

    @After
    fun tearDown() = world.session.close()

    private fun kotlinx.coroutines.test.TestScope.subscribe() = backgroundScope.launch(main.dispatcher) { vm.uiState.collect() }

    @Test
    fun `the week view covers the last seven days ending today`() = runTest {
        subscribe()
        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(StatsRange.WEEK, s.range)
        assertEquals(7, s.daily.size)
        assertEquals(TODAY.minusDays(6), s.daily.first().date)
        assertEquals(TODAY, s.daily.last().date)
        assertEquals(listOf(0, 0, 0, 0, 2, 0, 1), s.daily.map { it.mealCount })
        assertEquals(2, s.loggedDays)
        assertEquals("(750 + 250) / 2 logged days", 500.0, s.average!!.kcal, 1e-9)
        assertEquals(1800.0, s.target!!, 1e-9)
        assertEquals("weights in range only", listOf(88.0, 87.0), s.weights.map { it.kg })
        val split = s.split!!
        assertEquals(100.0, split.proteinPct + split.carbsPct + split.fatPct, 1e-9)
    }

    @Test
    fun `other ranges widen the window`() = runTest {
        subscribe()
        vm.setRange(StatsRange.MONTH)
        var s = vm.uiState.value
        assertEquals(30, s.daily.size)
        assertEquals(3, s.loggedDays)
        assertEquals(2, s.weights.size)

        vm.setRange(StatsRange.QUARTER)
        s = vm.uiState.value
        assertEquals(90, s.daily.size)
        assertEquals(3, s.weights.size)
    }

    @Test
    fun `all time starts at the earliest meal or weigh-in`() = runTest {
        subscribe()
        vm.setRange(StatsRange.ALL)
        val s = vm.uiState.value
        assertEquals(TODAY.minusDays(40), s.daily.first().date)
        assertEquals(41, s.daily.size)
        assertEquals(3, s.weights.size)
    }

    @Test
    fun `no data gives empty but not loading`() = runTest {
        val empty = TestWorld(main.dispatcher)
        val fresh = StatsViewModel(empty.session, CLOCK)
        backgroundScope.launch(main.dispatcher) { fresh.uiState.collect() }
        val s = fresh.uiState.value
        assertFalse(s.loading)
        assertNull(s.average)
        assertNull(s.split)
        assertNull(s.target)
        assertEquals(0, s.loggedDays)
        assertTrue(s.daily.all { it.mealCount == 0 })
        fresh.setRange(StatsRange.ALL)
        assertEquals("all-time with nothing logged is just today", listOf(TODAY), fresh.uiState.value.daily.map { it.date })
        empty.session.close()
    }
}
