package app.foodko.data

import app.foodko.data.model.WeightEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** `users/{uid}/weights`. */
class WeightRepository(db: FirebaseFirestore, uid: String) {

    private val collection = db.collection("users").document(uid).collection("weights")

    val all: Flow<List<WeightEntry>> =
        collection.orderBy("timestamp", Query.Direction.ASCENDING).snapshots<WeightEntry>()

    suspend fun save(entry: WeightEntry): String {
        val doc = if (entry.id.isEmpty()) collection.document() else collection.document(entry.id)
        doc.set(entry.copy(id = doc.id)).await()
        return doc.id
    }

    suspend fun delete(id: String) {
        collection.document(id).delete().await()
    }
}
