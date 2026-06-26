package com.example.barberlink.Repository

import com.example.barberlink.DataClass.EmployeeRolesData
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

    suspend fun createEmployee(barbershopId: String, employee: UserEmployeeData): FirestoreResult<Unit> {
        val documentRef = getEmployeeCollection().document(employee.uid)
        employee.rootRef = "barbershops/$barbershopId"

        return documentRef.set(employee).awaitWriteWithOfflineFallback(tag = "CreateEmployee")
    }

    suspend fun updateEmployee(barbershopId: String, employee: UserEmployeeData): FirestoreResult<Unit> {
        val documentRef = getEmployeeCollection().document(employee.uid)
        return documentRef.set(employee).awaitWriteWithOfflineFallback(tag = "UpdateEmployee")
    }

    suspend fun searchEmployeeByUsername(username: String, employeeRoles: List<EmployeeRolesData>): FirestoreResult<UserEmployeeData?> {
        val result = getEmployeeCollection()
            .whereEqualTo("username", username)
            .limit(1)
            .awaitGetWithOfflineFallback(tag = "SearchEmployeeByUsername")

        return if (result.isSuccessful) {
            val document = result.data?.documents?.firstOrNull()
            val employee = document?.toObject(UserEmployeeData::class.java)
            if (employee != null) {
                employee.userRef = document.reference.path
                employee.outletRef = ""
                employee.roleDetail = employeeRoles.find {
                    it.roleName.equals(employee.role, ignoreCase = true)
                } ?: EmployeeRolesData()
            }
            FirestoreResult(data = employee, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = null, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun deleteEmployee(employeeId: String): FirestoreResult<Unit> {
        return getEmployeeCollection().document(employeeId).delete().awaitWriteWithOfflineFallback(tag = "DeleteEmployee")
    }

}
