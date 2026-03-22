package com.example.barberlink.Repository

import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.tasks.await

class OutletRepository(private val db: FirebaseFirestore) {

    fun getOutletCollection(barbershopId: String) = db.collection("barbershops").document(barbershopId).collection("outlets")

    suspend fun getOutlet(barbershopId: String, outletId: String): FirestoreResult<Outlet?> {
        val result = getOutletCollection(barbershopId).document(outletId).awaitGetWithOfflineFallback(tag = "GetOutlet")
        return if (result.isSuccessful) {
            val outlet = result.data?.toObject(Outlet::class.java)
            if (outlet != null) {
                outlet.outletReference = result.data.reference.path
            }
            FirestoreResult(data = outlet, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = null, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun createOutlet(barbershopId: String, outlet: Outlet): FirestoreResult<Unit> {
        val documentRef = getOutletCollection(barbershopId).document(outlet.uid)
        outlet.rootRef = "barbershops/$barbershopId"
        
        return documentRef.set(outlet).awaitWriteWithOfflineFallback(tag = "CreateOutlet")
    }

    suspend fun updateOutlet(barbershopId: String, outlet: Outlet): FirestoreResult<Unit> {
        val documentRef = getOutletCollection(barbershopId).document(outlet.uid)
        return documentRef.set(outlet).awaitWriteWithOfflineFallback(tag = "UpdateOutlet")
    }

    suspend fun deleteOutlet(barbershopId: String, outletId: String): FirestoreResult<Unit> {
        return getOutletCollection(barbershopId).document(outletId).delete().awaitWriteWithOfflineFallback(tag = "DeleteOutlet")
    }
}
