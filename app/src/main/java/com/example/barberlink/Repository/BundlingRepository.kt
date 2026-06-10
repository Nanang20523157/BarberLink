package com.example.barberlink.Repository

import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore

class BundlingRepository(private val db: FirebaseFirestore) {

    fun getBundlingCollection(barbershopId: String) = db.collection("barbershops").document(barbershopId).collection("bundling_packages")

    suspend fun getBundling(barbershopId: String): FirestoreResult<List<BundlingPackage>> {
        val result = getBundlingCollection(barbershopId).awaitGetWithOfflineFallback(tag = "GetBundling")
        return if (result.isSuccessful) {
            val bundlingList = result.data?.documents?.mapNotNull { doc ->
                doc.toObject(BundlingPackage::class.java)?.apply {
                    uid = doc.id
                    dataRef = doc.reference.path
                }
            } ?: emptyList()
            FirestoreResult(data = bundlingList, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = emptyList(), isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun createBundling(barbershopId: String, bundling: BundlingPackage): FirestoreResult<Unit> {
        val documentRef = getBundlingCollection(barbershopId).document(bundling.uid)
        bundling.rootRef = "barbershops/$barbershopId"

        return documentRef.set(bundling).awaitWriteWithOfflineFallback(tag = "CreateBundling")
    }

    suspend fun updateBundling(barbershopId: String, bundling: BundlingPackage): FirestoreResult<Unit> {
        val documentRef = getBundlingCollection(barbershopId).document(bundling.uid)
        return documentRef.set(bundling).awaitWriteWithOfflineFallback(tag = "UpdateBundling")
    }

    suspend fun deleteBundling(barbershopId: String, bundlingId: String): FirestoreResult<Unit> {
        return getBundlingCollection(barbershopId).document(bundlingId).delete().awaitWriteWithOfflineFallback(tag = "DeleteBundling")
    }
}
