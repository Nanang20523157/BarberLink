package com.example.barberlink.Repository

import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore

class EmployeeRepository(private val db: FirebaseFirestore) {

    private fun getEmployeeCollection() = db.collection("employees")

    suspend fun getEmployee(employeeId: String): FirestoreResult<UserEmployeeData?> {
        val result = getEmployeeCollection().document(employeeId)
            .awaitGetWithOfflineFallback(tag = "GetEmployee")
        
        return if (result.isSuccessful) {
            val employee = result.data?.toObject(UserEmployeeData::class.java)
            if (employee != null) {
                // Set reference or other transient fields if needed
                 employee.userRef = result.data.reference.path
            }
            FirestoreResult(data = employee, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = null, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun updateEmployee(employee: UserEmployeeData): FirestoreResult<Unit> {
        val documentRef = getEmployeeCollection().document(employee.uid)
        return documentRef.set(employee).awaitWriteWithOfflineFallback(tag = "UpdateEmployee")
    }

//    suspend fun deleteEmployee(employeeId: String): FirestoreResult<Unit> {
//        return getEmployeeCollection().document(employeeId).delete().awaitWriteWithOfflineFallback(tag = "DeleteEmployee")
//    }

}
