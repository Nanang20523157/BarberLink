package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ManageEmployeeViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    val employeeMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int): ResultState()
    }

    private val _employeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { value = mutableListOf() }
    val employeeList: LiveData<MutableList<UserEmployeeData>> = _employeeList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.value = employeeList
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun deleteEmployee(employee: UserEmployeeData) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                // 1. Delete employee profile photo from Storage if it exists
                if (employee.photoProfile.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(employee.photoProfile)
                        imageRef.delete().await()
                    } catch (e: Exception) {
                        Logger.e("DeleteEmployee", "Failed to delete image: ${e.message}")
                    }
                }

                // 2. Delete from Firestore
//                val barbershopId = employee.rootRef.split("/").last()
//                val result = db.collection("employees")
//                    .document(employee.uid)
//                    .delete()
//                    .awaitWriteWithOfflineFallback(tag = "DeleteEmployee")
//
//                if (result.isSuccessful) {
//                    _updateStateResult.value = ResultState.Success("Delete", "Karyawan \"${employee.fullname}\" berhasil dihapus.")
//                } else {
//                    _updateStateResult.value = ResultState.Failure("Delete", result.errorMessage ?: "Gagal menghapus karyawan!", -1)
//                }
            } catch (e: Exception) {
                Logger.e("DeleteEmployee", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus karyawan!", -1)
            }
        }
    }

    fun updateEmployeeList(newEmployeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch(Dispatchers.Default) {
            val updatedEmployeeList = _employeeList.value ?: mutableListOf()

            // Update or add new employees
            updatedEmployeeList.forEach { existing ->
                val matchingCapsterData =
                    newEmployeeList.find { it.uid == existing.uid }
                if (matchingCapsterData != null) {
                    // Update relevant fields
                    existing.apply {
                        accountVerification = matchingCapsterData.accountVerification
                        accumulatedLateness =
                            matchingCapsterData.accumulatedLateness
                        attendanceStatus = matchingCapsterData.attendanceStatus
                        availabilityStatus =
                            matchingCapsterData.availabilityStatus
                        blackList = matchingCapsterData.blackList
                        customerCounting =
                            matchingCapsterData.customerCounting
                        debutDate = matchingCapsterData.debutDate
                        email = matchingCapsterData.email
                        employeeRating =
                            matchingCapsterData.employeeRating
                        fullname = matchingCapsterData.fullname
                        gender = matchingCapsterData.gender
                        historyWorkplace = matchingCapsterData.historyWorkplace
                        password = matchingCapsterData.password
                        phone = matchingCapsterData.phone
                        photoProfile = matchingCapsterData.photoProfile
                        pin = matchingCapsterData.pin
                        point = matchingCapsterData.point
                        //positions = matchingCapsterData.positions
                        role = matchingCapsterData.role
                        rootRef = matchingCapsterData.rootRef
                        salary = matchingCapsterData.salary
                        superAdmin = matchingCapsterData.superAdmin
                        uid = matchingCapsterData.uid
                        uidListPlacement =
                            matchingCapsterData.uidListPlacement
                        userNotification =
                            matchingCapsterData.userNotification
                        userReminder = matchingCapsterData.userReminder
                        username = matchingCapsterData.username
                        userRef = matchingCapsterData.userRef
                        outletRef = matchingCapsterData.outletRef
                        roleDetail = matchingCapsterData.roleDetail
                    }
                }
            }

            // Tambah yang baru
            val toAdd = newEmployeeList.filter { fetched ->
                updatedEmployeeList.none { it.uid == fetched.uid }
            }
            updatedEmployeeList.addAll(toAdd)

            // Hapus yang sudah tidak ada
            val toRemove = updatedEmployeeList.filterNot { current ->
                newEmployeeList.any { it.uid == current.uid }
            }
            updatedEmployeeList.removeAll(toRemove)

            _employeeList.updateOnMain(updatedEmployeeList)
        }
    }

    fun clearAllDataEmployee() {
        viewModelScope.launch {
            _employeeList.clearList()
        }
    }
}
