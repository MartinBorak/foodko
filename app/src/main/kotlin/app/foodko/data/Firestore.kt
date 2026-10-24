package app.foodko.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
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

/** Snapshot callbacks (and the reflection-based document mapping) run off the main thread. */
val snapshotExecutor: Executor = Dispatchers.Default.asExecutor()

/** Live list of documents mapped to [T]. Fails the flow on a listener error (e.g. permission denied). */
inline fun <reified T : Any> Query.snapshots(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener(snapshotExecutor) { snapshot, error ->
        if (error != null) {
            close(error)
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
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.takeIf { it.exists() })
    }
    awaitClose { registration.remove() }
}

/** Firestore limits for batching. */
const val WHERE_IN_LIMIT = 30
const val BATCH_LIMIT = 500
