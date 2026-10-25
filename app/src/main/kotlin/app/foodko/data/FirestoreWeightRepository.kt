package app.foodko.data

import app.foodko.data.model.WeightEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** [WeightRepository] on Cloud Firestore. */
class FirestoreWeightRepository(db: FirebaseFirestore, uid: String) : WeightRepository {

    private val collection = db.collection("users").document(uid).collection("weights")

    override val all: Flow<List<WeightEntry>> =
        collection.orderBy("timestamp", Query.Direction.ASCENDING).snapshots<WeightEntry>()

    override suspend fun save(entry: WeightEntry): String {
        val doc = if (entry.id.isEmpty()) collection.document() else collection.document(entry.id)
        doc.set(entry.copy(id = doc.id)).await()
        return doc.id
    }

    override suspend fun delete(id: String) {
        collection.document(id).delete().await()
    }
}
