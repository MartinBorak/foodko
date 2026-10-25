package app.foodko.ui.weight

import app.foodko.CLOCK
import app.foodko.MainDispatcherRule
import app.foodko.TODAY
import app.foodko.TestWorld
import app.foodko.data.model.WeightEntry
import app.foodko.at
import app.foodko.ui.components.StatsRange
import app.foodko.weight
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeightViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val world = TestWorld(
        main.dispatcher,
        weights = listOf(
            weight("w2", 88.0, TODAY.minusDays(3)),
            weight("w1", 90.0, TODAY.minusDays(40)),
            weight("w3", 87.0, TODAY),
        ),
    )
    private lateinit var vm: WeightViewModel

    // Built here, not in a field initialiser: viewModelScope must see the rule's Main dispatcher.
    @Before
    fun setUp() {
        vm = WeightViewModel(world.session, CLOCK)
    }

    @After
    fun tearDown() = world.session.close()

    private fun kotlinx.coroutines.test.TestScope.subscribe() = backgroundScope.launch(main.dispatcher) { vm.uiState.collect() }

    @Test
    fun `history is newest first, the chart covers the selected range`() = runTest {
        subscribe()
        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(StatsRange.MONTH, s.range)
        assertEquals(listOf("w3", "w2", "w1"), s.entries.map { it.id })
        assertEquals(listOf(88.0, 87.0), s.series.map { it.kg })
        assertEquals(87.5, s.series.last().movingAverage, 1e-9)

        vm.setRange(StatsRange.ALL)
        assertEquals(listOf(90.0, 88.0, 87.0), vm.uiState.value.series.map { it.kg })
        vm.setRange(StatsRange.WEEK)
        assertEquals(listOf(88.0, 87.0), vm.uiState.value.series.map { it.kg })
    }

    @Test
    fun `save and delete go straight to the repository and the state follows`() = runTest {
        subscribe()
        vm.save(WeightEntry(kg = 86.5, timestamp = at(TODAY.minusDays(1))))
        assertEquals(4, vm.uiState.value.entries.size)
        assertEquals(listOf(88.0, 86.5, 87.0), vm.uiState.value.series.map { it.kg })

        vm.delete("w3")
        assertEquals(listOf("w2", "w1"), vm.uiState.value.entries.filter { it.kg != 86.5 }.map { it.id })
        assertEquals(listOf(88.0, 86.5), vm.uiState.value.series.map { it.kg })
    }
}
