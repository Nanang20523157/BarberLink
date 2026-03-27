package com.example.barberlink.Repository

import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BundlingRepository(private val db: FirebaseFirestore) {

    suspend fun getBundling(barbershopId: String): FirestoreResult<List<BundlingPackage>> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("bundling")
                    .awaitGetWithOfflineFallback(tag = "GetBundling")

                val bundlingList = snapshot.data?.mapNotNull { doc ->
                    doc.toObject(BundlingPackage::class.java).apply {
                        uid = doc.id
                    }
                }
                FirestoreResult.Success(bundlingList)
            } catch (e: Exception) {
                FirestoreResult.Failure(e.message ?: "Failed to fetch bundling")
            } as FirestoreResult<List<BundlingPackage>>
        }
    }

    suspend fun createBundling(barbershopId: String, bundling: BundlingPackage): FirestoreResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("bundling")
                    .document()
                
                bundling.uid = ref.id
                val task = ref.set(bundling).awaitWriteWithOfflineFallback(tag = "CreateBundling")
                
                if (task.isSuccessful) FirestoreResult.Success(Unit)
                else FirestoreResult.Failure(task.errorMessage ?: "Failed to create bundling")
            } catch (e: Exception) {
                FirestoreResult.Failure(e.message ?: "Error creating bundling")
            }
        }
    }

    suspend fun updateBundling(barbershopId: String, bundling: BundlingPackage): FirestoreResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("bundling")
                    .document(bundling.uid)
                
                val task = ref.set(bundling).awaitWriteWithOfflineFallback(tag = "UpdateBundling")
                
                if (task.isSuccessful) FirestoreResult.Success(Unit)
                else FirestoreResult.Failure(task.errorMessage ?: "Failed to update bundling")
            } catch (e: Exception) {
                FirestoreResult.Failure(e.message ?: "Error updating bundling")
            }
        }
    }

    suspend fun deleteBundling(barbershopId: String, bundlingId: String): FirestoreResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("barbershops")
                    .document(barbershopId)
                    .collection("bundling")
                    .document(bundlingId)
                
                val task = ref.delete().awaitWriteWithOfflineFallback(tag = "DeleteBundling")
                
                if (task.isSuccessful) FirestoreResult.Success(Unit)
                else FirestoreResult.Failure(task.errorMessage ?: "Failed to delete bundling")
            } catch (e: Exception) {
                FirestoreResult.Failure(e.message ?: "Error deleting bundling")
            }
        }
    }
}
