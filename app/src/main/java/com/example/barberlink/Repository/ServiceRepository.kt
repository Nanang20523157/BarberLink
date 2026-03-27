package com.example.barberlink.Repository

import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore

class ServiceRepository(private val db: FirebaseFirestore) {

    fun getServiceCollection(barbershopId: String) = db.collection("barbershops").document(barbershopId).collection("services")

    suspend fun getService(barbershopId: String, serviceId: String): FirestoreResult<Service?> {
        val result = getServiceCollection(barbershopId).document(serviceId).awaitGetWithOfflineFallback(tag = "GetService")
        return if (result.isSuccessful) {
            val service = result.data?.toObject(Service::class.java)
            if (service != null) {
                service.dataRef = result.data.reference.path
            }
            FirestoreResult(data = service, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = null, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun createService(barbershopId: String, service: Service): FirestoreResult<Unit> {
        val documentRef = getServiceCollection(barbershopId).document(service.uid)
        service.rootRef = "barbershops/$barbershopId"

        return documentRef.set(service).awaitWriteWithOfflineFallback(tag = "CreateService")
    }

    suspend fun updateService(barbershopId: String, service: Service): FirestoreResult<Unit> {
        val documentRef = getServiceCollection(barbershopId).document(service.uid)
        return documentRef.set(service).awaitWriteWithOfflineFallback(tag = "UpdateService")
    }

    suspend fun deleteService(barbershopId: String, serviceId: String): FirestoreResult<Unit> {
        return getServiceCollection(barbershopId).document(serviceId).delete().awaitWriteWithOfflineFallback(tag = "DeleteService")
    }

    suspend fun getServiceCategories(adminUid: String): FirestoreResult<List<DataCategories>> {
        val result = db.collection("service_categories")
            .whereIn("barbershop_ref", listOf(adminUid, "All"))
            .awaitGetWithOfflineFallback(tag = "GetServiceCategories")

        return if (result.isSuccessful) {
            val categories = result.data?.documents?.mapNotNull { it.toObject(DataCategories::class.java) } ?: emptyList()
            FirestoreResult(data = categories, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = emptyList(), isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }
}
