package app.foodko

import android.app.Application
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class FoodkoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MOCK) {
            // Local Firebase emulators on the host machine (10.0.2.2 from the Android emulator).
            Firebase.auth.useEmulator(Mock.HOST, Mock.AUTH_PORT)
            Firebase.firestore.useEmulator(Mock.HOST, Mock.FIRESTORE_PORT)
        }
        // Offline logging relies on the persistent cache; make it explicit rather than
        // trusting the SDK default.
        Firebase.firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        container = AppContainer()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FoodkoApp).container

/** Settings for the mock build (`-PfoodkoMock=true`); unused otherwise. */
object Mock {
    const val HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080

    data class User(val name: String, val email: String)

    /**
     * Fake accounts for the emulator. `scripts/emulators.sh` puts the first two on the emulator's
     * allowlist; the third must be refused by the rules.
     */
    val users = listOf(
        User("Alice", "alice@example.com"),
        User("Bob", "bob@example.com"),
        User("Stranger", "stranger@example.com"),
    )
}
