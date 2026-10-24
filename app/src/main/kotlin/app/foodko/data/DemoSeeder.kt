package app.foodko.data

import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.WeightEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Mock build only: fills an empty emulator database with three weeks of plausible data so the
 * UI can be exercised without typing. Deterministic (fixed seed) so screenshots are comparable.
 */
object DemoSeeder {

    private data class Template(val label: String, val kcal: Double, val carbs: Double?, val fat: Double?, val protein: Double?, val fibre: Double?, val grams: IntRange)

    private val templates = listOf(
        Template("Ovsené vločky", 370.0, 59.0, 7.0, 13.0, 10.0, 50..80),
        Template("Skyr vanilkový", 63.0, 4.1, 0.2, 10.0, 0.0, 140..200),
        Template("Banán", 89.0, 23.0, 0.3, 1.1, 2.6, 100..140),
        Template("Kuracie prsia", 165.0, 0.0, 3.6, 31.0, 0.0, 120..200),
        Template("Ryža varená", 130.0, 28.0, 0.3, 2.7, 0.4, 150..250),
        Template("Brokolica", 34.0, 7.0, 0.4, 2.8, 2.6, 100..200),
        Template("Celozrnný chlieb", 247.0, 41.0, 3.4, 13.0, 7.0, 60..120),
        Template("Vajce", 155.0, 1.1, 11.0, 13.0, 0.0, 55..120),
        Template("Olivový olej", 884.0, 0.0, 100.0, 0.0, 0.0, 5..15),
        Template("Jablko", 52.0, 14.0, 0.2, 0.3, 2.4, 150..200),
        Template("Losos", 208.0, 0.0, 13.0, 20.0, 0.0, 120..180),
        Template("Tvaroh polotučný", 98.0, 3.4, 4.3, 12.0, 0.0, 150..250),
        Template("Čokoláda horká 70 %", 598.0, 46.0, 43.0, 7.8, 11.0, 15..40),
        Template("Káva s mliekom", 25.0, 2.0, 1.2, 1.4, null, 200..250),
        Template("Proteínová tyčinka", 380.0, 35.0, 12.0, 30.0, null, 50..60),
    )

    suspend fun seed(session: app.foodko.UserSession, startWeightKg: Double) {
        val random = Random(42)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val ids = templates.map { t ->
            session.foods.save(
                Food(
                    label = t.label, kcalPer100g = t.kcal, carbsPer100g = t.carbs, fatPer100g = t.fat,
                    proteinPer100g = t.protein, fibrePer100g = t.fibre,
                ),
                session.uid,
            )
        }

        val slots = listOf(LocalTime.of(7, 30), LocalTime.of(12, 15), LocalTime.of(16, 0), LocalTime.of(19, 30))
        for (daysAgo in 20 downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            if (daysAgo in listOf(13, 6)) continue // a couple of unlogged days
            val mealsToday = if (daysAgo == 0) 2 else 3 + random.nextInt(2)
            repeat(mealsToday) { i ->
                val slot = slots[i].plusMinutes(random.nextLong(-20, 20))
                val picks = 2 + random.nextInt(2)
                repeat(picks) {
                    val index = random.nextInt(templates.size)
                    val t = templates[index]
                    session.meals.save(
                        MealEntry(
                            foodId = ids[index],
                            grams = t.grams.random(random).toDouble(),
                            timestamp = day.atTime(slot).atZone(zone).toInstant().toTimestamp(),
                        ),
                    )
                }
            }
        }

        var kg = startWeightKg
        for (daysAgo in 20 downTo 0) {
            if (random.nextInt(3) == 0) continue // not every day
            kg += -0.12 + random.nextDouble(-0.4, 0.4)
            val time = today.minusDays(daysAgo.toLong()).atTime(7, 5).atZone(zone).toInstant()
            session.weights.save(WeightEntry(kg = Math.round(kg * 10) / 10.0, timestamp = time.toTimestamp()))
        }

        session.settings.setDailyKcalTarget(2000.0)
        session.settings.setLastUsed(ids.take(5))
    }
}
