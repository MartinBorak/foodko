package app.foodko.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.util.concurrent.Executor

// Timestamp.toInstant() is a member of the SDK class; only the reverse direction is needed here.
fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

/**
 * The rules refused a read: this account is not on the allowlist (spec Flow 1). Raised instead
 * of the SDK's own exception so that nothing outside this file depends on Firestore types.
 */
class AccessDeniedException(cause: Throwable? = null) : RuntimeException("Access denied by Firestore rules", cause)

@PublishedApi
internal fun Throwable.asRepositoryError(): Throwable =
    if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED) AccessDeniedException(this) else this

/** Snapshot callbacks (and the reflection-based document mapping) run off the main thread. */
val snapshotExecutor: Executor = Dispatchers.Default.asExecutor()

/** Live list of documents mapped to [T]. Fails the flow on a listener error (e.g. permission denied). */
inline fun <reified T : Any> Query.snapshots(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener(snapshotExecutor) { snapshot, error ->
        if (error != null) {
            close(error.asRepositoryError())
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot.toObjects(T::class.java))
    }
    awaitClose { registration.remove() }
}

/** Live single document; emits null while the document doesn't exist. */
fun DocumentReference.snapshots(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener(snapshotExecutor) { snapshot, error ->
        if (error != null) {
            close(error.asRepositoryError())
            return@addSnapshotListener
        }
        trySend(snapshot?.takeIf { it.exists() })
    }
    awaitClose { registration.remove() }
}

/*
 * Writes (set, update, delete, batch commit) are never awaited. The local cache and every
 * listener have a write at once and the SDK sends it when online; awaiting would wait for the
 * server, so offline the UI would hang until reconnected (spec: offline saving behaves the same).
 * A write the rules reject is rolled back in the cache. Reads before a write may still await.
 */

/** Firestore limits for batching. */
const val WHERE_IN_LIMIT = 30
const val BATCH_LIMIT = 500
