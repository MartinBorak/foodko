package app.foodko

import app.foodko.data.AccessDeniedException
import app.foodko.data.AuthRepository
import app.foodko.data.DemoSeeder
import app.foodko.data.FirestoreFoodRepository
import app.foodko.data.FirestoreMealRepository
import app.foodko.data.FirestoreSettingsRepository
import app.foodko.data.FirestoreWeightRepository
import app.foodko.data.FoodMerger
import app.foodko.data.FoodRepository
import app.foodko.data.MealRepository
import app.foodko.data.RedirectHealer
import app.foodko.data.SettingsRepository
import app.foodko.data.WeightRepository
import app.foodko.data.model.Food
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.data.model.WeightEntry
import app.foodko.domain.FoodIndex
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Manual dependency wiring. One per process, owned by [FoodkoApp]. */
class AppContainer {
    val auth = AuthRepository()
    val db: FirebaseFirestore = Firebase.firestore
    val foods = FirestoreFoodRepository(db)

    private var session: UserSession? = null

    /** The session for [uid], reusing it across activity recreations; closes any other user's. */
    @Synchronized
    fun sessionFor(uid: String): UserSession {
        session?.let { if (it.uid == uid) return it else it.close() }
        return firestoreSession(uid).also { session = it }
    }

    @Synchronized
    fun closeSession() {
        session?.close()
        session = null
    }

    private fun firestoreSession(uid: String): UserSession {
        val meals = FirestoreMealRepository(db, uid)
        val settings = FirestoreSettingsRepository(db, uid)
        return UserSession(
            uid = uid,
            foods = foods,
            meals = meals,
            weights = FirestoreWeightRepository(db, uid),
            settings = settings,
            merger = { survivor, mergedIds, recent -> foods.merge(uid, survivor, mergedIds, meals, settings, recent) },
            seedDemoData = BuildConfig.MOCK,
        )
    }
}

/**
 * Everything scoped to a signed-in user: repositories plus one shared live stream per
 * collection. `null` in a StateFlow means "not loaded yet".
 *
 * Built by [AppContainer] on Firestore; the unit tests build it on in-memory fakes with a test
 * dispatcher in [scope].
 */
class UserSession(
    val uid: String,
    val foods: FoodRepository,
    val meals: MealRepository,
    val weights: WeightRepository,
    val settings: SettingsRepository,
    private val merger: FoodMerger,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    seedDemoData: Boolean = false,
) {

    private val _accessDenied = MutableStateFlow(false)

    /** True once Firestore rejected a read: this Google account is not on the allowlist. */
    val accessDenied: StateFlow<Boolean> = _accessDenied.asStateFlow()

    val foodIndex: StateFlow<FoodIndex?> =
        foods.foods.map { FoodIndex(it) }.guarded().stateIn(scope, SharingStarted.Eagerly, null)

    val allMeals: StateFlow<List<MealEntry>?> =
        meals.all.guarded().stateIn(scope, SharingStarted.Eagerly, null)

    val allWeights: StateFlow<List<WeightEntry>?> =
        weights.all.guarded().stateIn(scope, SharingStarted.Eagerly, null)

    val userSettings: StateFlow<UserSettings?> =
        settings.settings.guarded().stateIn(scope, SharingStarted.Eagerly, null)

    init {
        RedirectHealer(meals, settings).start(
            scope = scope,
            foodIndex = foodIndex.filterLoaded(),
            allMeals = allMeals.filterLoaded(),
            userSettings = userSettings.filterLoaded(),
        )
        if (seedDemoData) {
            // Empty emulator database → fill it so there is something to look at.
            scope.launch {
                val index = foodIndex.filterLoaded().first()
                val mine = allMeals.filterLoaded().first()
                if (index.isEmpty && mine.isEmpty()) {
                    runCatching { DemoSeeder.seed(this@UserSession, startWeightKg = 92.0) }
                }
            }
        }
    }

    /** Merge (spec Flow 7) with this user's current recent list; see [FoodMerger]. */
    suspend fun mergeFoods(survivor: Food, mergedIds: List<String>) =
        merger.merge(survivor, mergedIds, userSettings.value?.lastUsedFoodIds.orEmpty())

    fun close() = scope.cancel()

    private fun <T> Flow<T>.guarded(): Flow<T> = catch { e ->
        if (e is AccessDeniedException) {
            _accessDenied.value = true
        } else {
            throw e
        }
    }
}

private fun <T : Any> StateFlow<T?>.filterLoaded(): Flow<T> = filterNotNull()
