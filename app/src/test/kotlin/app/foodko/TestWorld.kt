package app.foodko

import app.foodko.data.FakeFoodRepository
import app.foodko.data.FakeMealRepository
import app.foodko.data.FakeSettingsRepository
import app.foodko.data.FakeWeightRepository
import app.foodko.data.RecordingMerger
import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.data.model.WeightEntry
import app.foodko.data.toTimestamp
import app.foodko.domain.normalizeForSearch
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Fixed point in time for the tests: Sunday 2026-10-25 14:00 in Bratislava. */
val ZONE: ZoneId = ZoneId.of("Europe/Bratislava")
val TODAY: LocalDate = LocalDate.of(2026, 10, 25)
val CLOCK: Clock = Clock.fixed(TODAY.atTime(14, 0).atZone(ZONE).toInstant(), ZONE)

fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Timestamp =
    LocalDateTime.of(date, time).atZone(ZONE).toInstant().toTimestamp()

fun food(
    id: String,
    label: String = id,
    kcal: Double = 100.0,
    carbs: Double? = 10.0,
    fat: Double? = 5.0,
    protein: Double? = 8.0,
    fibre: Double? = 2.0,
    mergedInto: String? = null,
    archived: Boolean = false,
    createdAtSeconds: Long? = null,
) = Food(
    id = id, label = label, labelLower = normalizeForSearch(label),
    kcalPer100g = kcal, carbsPer100g = carbs, fatPer100g = fat, proteinPer100g = protein, fibrePer100g = fibre,
    mergedInto = mergedInto, archived = archived,
    createdAt = createdAtSeconds?.let { Timestamp(it, 0) },
)

fun meal(id: String, foodId: String, grams: Double, date: LocalDate = TODAY, time: LocalTime = LocalTime.NOON) =
    MealEntry(id = id, foodId = foodId, grams = grams, timestamp = at(date, time))

fun weight(id: String, kg: Double, date: LocalDate, time: LocalTime = LocalTime.of(7, 0)) =
    WeightEntry(id = id, kg = kg, timestamp = at(date, time))

/** A [UserSession] over fakes, with its coroutines on the test dispatcher. */
class TestWorld(
    dispatcher: CoroutineDispatcher,
    foods: List<Food> = emptyList(),
    meals: List<MealEntry> = emptyList(),
    weights: List<WeightEntry> = emptyList(),
    settings: UserSettings = UserSettings(),
) {
    val uid = "uid-1"
    val foods = FakeFoodRepository(foods)
    val meals = FakeMealRepository(meals)
    val weights = FakeWeightRepository(weights)
    val settings = FakeSettingsRepository(settings)
    val merger = RecordingMerger()
    val session = UserSession(
        uid = uid,
        foods = this.foods,
        meals = this.meals,
        weights = this.weights,
        settings = this.settings,
        merger = merger,
        scope = CoroutineScope(SupervisorJob() + dispatcher),
    )
}
