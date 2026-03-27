package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Repository.EmployeeRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddEmployeeViewModel(
    private val repository: EmployeeRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val EMPLOYEE_PARAMS_KEY = "employee_params"
        private const val PENDING_PHOTO_URI_KEY = "pending_photo_uri"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
    }

    // Backed by SavedStateHandle
    private val _employeeParams = handle.getLiveData<UserEmployeeData>(EMPLOYEE_PARAMS_KEY, UserEmployeeData())
    val employeeParams: LiveData<UserEmployeeData> get() = _employeeParams

    private val _pendingPhotoUri = handle.getLiveData<Uri?>(PENDING_PHOTO_URI_KEY)
    val pendingPhotoUri: LiveData<Uri?> get() = _pendingPhotoUri

    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0) // 0: VIEW, 1: EDIT, 2: ADD
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    fun updateEmployeeParams(employee: UserEmployeeData) {
        _employeeParams.value = employee
    }

    fun setPendingPhotoUri(uri: Uri?) {
        _pendingPhotoUri.value = uri
    }

    fun setBarbershopId(id: String) {
        _barbershopId.value = id
    }

    fun setCurrentMode(mode: Int) {
        _currentMode.value = mode
    }

    fun saveEmployee(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentEmployee = _employeeParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                // 1. Handle Photo Upload if needed
                _pendingPhotoUri.value?.let { uri ->
                    val storageRef = storage.reference.child("employees/${currentEmployee.uid}")
                    
                    // Upload new photo
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentEmployee.photoProfile = downloadUrl.toString()
                }

                val result = if (isAddMode) {
                    // repository.createEmployee(bId, currentEmployee)
                } else {
                    repository.updateEmployee(currentEmployee)
                }

//                if (result.isSuccessful) {
//                    _pendingPhotoUri.value = null
//                }
//                _saveResult.value = result
            } catch (e: Exception) {
                _saveResult.value = FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}
