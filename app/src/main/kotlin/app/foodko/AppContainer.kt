package app.foodko

import app.foodko.data.AuthRepository
import app.foodko.data.FoodRepository
import app.foodko.data.MealRepository
import app.foodko.data.RedirectHealer
import app.foodko.data.SettingsRepository
import app.foodko.data.WeightRepository
import app.foodko.data.model.MealEntry
import app.foodko.data.model.UserSettings
import app.foodko.data.model.WeightEntry
import app.foodko.domain.FoodIndex
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
    val foods = FoodRepository(db)

    private var session: UserSession? = null

    /** The session for [uid], reusing it across activity recreations; closes any other user's. */
    @Synchronized
    fun sessionFor(uid: String): UserSession {
        session?.let { if (it.uid == uid) return it else it.close() }
        return UserSession(uid, db, foods).also { session = it }
    }

    @Synchronized
    fun closeSession() {
        session?.close()
        session = null
    }
}

/**
 * Everything scoped to a signed-in user: repositories plus one shared live stream per
 * collection. `null` in a StateFlow means "not loaded yet".
 */
class UserSession(val uid: String, db: FirebaseFirestore, val foods: FoodRepository) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val meals = MealRepository(db, uid)
    val weights = WeightRepository(db, uid)
    val settings = SettingsRepository(db, uid)

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
        if (BuildConfig.MOCK) {
            // Empty emulator database → fill it so there is something to look at.
            scope.launch {
                val index = foodIndex.filterLoaded().first()
                val mine = allMeals.filterLoaded().first()
                if (index.isEmpty && mine.isEmpty()) {
                    runCatching { app.foodko.data.DemoSeeder.seed(this@UserSession, startWeightKg = 92.0) }
                }
            }
        }
    }

    fun close() = scope.cancel()

    private fun <T> Flow<T>.guarded(): Flow<T> = catch { e ->
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            _accessDenied.value = true
        } else {
            throw e
        }
    }
}

private fun <T : Any> StateFlow<T?>.filterLoaded(): Flow<T> = filterNotNull()
