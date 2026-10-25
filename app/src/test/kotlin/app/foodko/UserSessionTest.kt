package app.foodko

import app.foodko.data.AccessDeniedException
import app.foodko.data.FakeFoodRepository
import app.foodko.data.FakeMealRepository
import app.foodko.data.FakeSettingsRepository
import app.foodko.data.FakeWeightRepository
import app.foodko.data.MealRepository
import app.foodko.data.RecordingMerger
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionTest {

    @Test
    fun `exposes loaded data and performs merges with the current recent list`() = runTest {
        val world = TestWorld(
            UnconfinedTestDispatcher(testScheduler),
            foods = listOf(food("a", "Apple")),
            settings = UserSettings(lastUsedFoodIds = listOf("a", "b")),
        )
        val session = world.session

        assertEquals(listOf("a"), session.foodIndex.value!!.visible.map { it.id })
        assertEquals(emptyList<MealEntry>(), session.allMeals.value)
        assertFalse(session.accessDenied.value)

        session.mergeFoods(food("a", "Apple"), listOf("b"))
        val call = world.merger.calls.single()
        assertEquals(listOf("b"), call.mergedIds)
        assertEquals(listOf("a", "b"), call.lastUsedFoodIds)
        session.close()
    }

    @Test
    fun `a permission denied read marks the account as not allowlisted`() = runTest {
        val denied = object : MealRepository {
            override val all: Flow<List<MealEntry>> = flow {
                throw AccessDeniedException()
            }
            override suspend fun save(meal: MealEntry) = error("unused")
            override suspend fun delete(id: String) = error("unused")
            override suspend fun repoint(meals: List<MealEntry>, mapping: Map<String, String>) = error("unused")
        }
        val session = UserSession(
            uid = "uid-1",
            foods = FakeFoodRepository(),
            meals = denied,
            weights = FakeWeightRepository(),
            settings = FakeSettingsRepository(),
            merger = RecordingMerger(),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )

        assertTrue(session.accessDenied.value)
        assertNull("the failed stream stays unloaded", session.allMeals.value)
        session.close()
    }
}
