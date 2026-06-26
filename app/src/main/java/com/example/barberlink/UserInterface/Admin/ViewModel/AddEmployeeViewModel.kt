package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Repository.EmployeeRepository
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.PermissionItem
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.collections.plus

class AddEmployeeViewModel(
    private val repository: EmployeeRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : InputFragmentViewModel(handle) {

    companion object {
        private const val EMPLOYEE_PARAMS_KEY = "employee_params"
        private const val PENDING_PHOTO_URI_KEY = "pending_photo_uri"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
        private const val EMPLOYEE_SELECTED_ID_KEY = "employee_selected_id"
    }

    val employeeListMutex = ReentrantCoroutineMutex()
    val rolesListMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val permissionListMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerEmployeeMutex = ReentrantCoroutineMutex()
    val listenerRolesMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerPermissionMutex = ReentrantCoroutineMutex()


    // Backed by SavedStateHandle
    private var userInputGender: String = "Rahasiakan"

    private val _originalEmployee = MutableLiveData<UserEmployeeData>()
    val originalEmployee: LiveData<UserEmployeeData> get() = _originalEmployee

    private val _employeeParams = handle.getLiveData<UserEmployeeData>(EMPLOYEE_PARAMS_KEY)
    val employeeParams: LiveData<UserEmployeeData> get() = _employeeParams

    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0) // 0: VIEW, 1: EDIT, 2: ADD
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _employeeSelectedId = handle.getLiveData<String>(EMPLOYEE_SELECTED_ID_KEY, "")
    val employeeSelectedId: LiveData<String> get() = _employeeSelectedId

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _employeeList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val employeeList : LiveData<List<UserEmployeeData>> get() = _employeeList

    private val _pendingPhotoUri = handle.getLiveData<Uri?>(PENDING_PHOTO_URI_KEY)
    val pendingPhotoUri: LiveData<Uri?> get() = _pendingPhotoUri

    private val _rolesList = MutableLiveData<List<EmployeeRolesData>>()

    val rolesList : LiveData<List<EmployeeRolesData>> get() = _rolesList

    private val _permissionList = MutableLiveData<List<PermissionItem>>()
    val permissionList : LiveData<List<PermissionItem>> get() = _permissionList

    fun setUserInputGender(gender: String) {
        viewModelScope.launch {
            userInputGender = gender
        }
    }

    fun setRolesList(rolesList: List<EmployeeRolesData>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            val employees = _employeeList.value ?: emptyList()
            _rolesList.value = rolesList

            setupDropdown?.let { isSetupDropdown ->
                isSavedInstanceStateNull?.let { isSavedInstanceStateNull ->
                    if (employees.isNotEmpty() && !isSetupDropdown && isSavedInstanceStateNull) {
                        employeeListMutex.withStateLock {
                            employees.forEach { employee ->
                                employee.roleDetail = rolesList.find { it.roleName == employee.role }
                            }
                            _employeeList.value = employees
                        }
                    }
                }
            }
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
        }
    }

    fun setOriginalEmployee(employee: UserEmployeeData) {
        viewModelScope.launch {
            _originalEmployee.value = employee
        }
    }

    fun updateEmployeeParams(employee: UserEmployeeData) {
        viewModelScope.launch {
            _employeeParams.value = employee
        }
    }

    fun setEmployeeList(employees: List<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.value = employees
        }
    }

    fun setPendingPhotoUri(uri: Uri?) {
        viewModelScope.launch {
            _pendingPhotoUri.value = uri
        }
    }

    fun clearPendingPhotoUri() {
        viewModelScope.launch {
            _pendingPhotoUri.value = null
        }
    }

    fun setBarbershopId(id: String) {
        viewModelScope.launch {
            _barbershopId.value = id
        }
    }

    fun setEmployeeSelectedId(id: String) {
        viewModelScope.launch {
            _employeeSelectedId.value = id
        }
    }

    fun setCurrentMode(mode: Int) {
        viewModelScope.launch {
            _currentMode.value = mode
        }
    }

    fun setOutletList(outlets: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = outlets
        }
    }

    fun setPermissionList(list: List<PermissionItem>) {
        viewModelScope.launch {
            _permissionList.value = list
        }
    }

    fun getUserInputGender(): String {
        return runBlocking {
            userInputGender
        }
    }

    fun saveEmployee(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentEmployee = _employeeParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                // 1. Handle Photo Upload if needed
                _pendingPhotoUri.value?.let { uri ->
                    val storageRef = storage.reference.child("profiles/${currentEmployee.uid}")

                    // Delete old image if it exists and path is different
                    if (currentEmployee.photoProfile.isNotEmpty()) {
                        try {
                            val oldReference = FirebaseStorage.getInstance().getReferenceFromUrl(currentEmployee.photoProfile)
                            if (oldReference.path != storageRef.path) {
                                oldReference.delete().await()
                            }
                        } catch (e: Exception) {
                            // Non-critical, ignore deletion errors
                        }
                    }

                    // Upload new image
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentEmployee.photoProfile = downloadUrl.toString()
                }

                val result = if (isAddMode) {
                    repository.createEmployee(bId, currentEmployee)
                } else {
                    repository.updateEmployee(bId, currentEmployee)
                }

                _isSaving.value = false
                if (result.isSuccessful) clearPendingPhotoUri()
                if (isAddMode && result.isSuccessful) _employeeList.value = _employeeList.value.orEmpty() + currentEmployee
                else if (result.isSuccessful) {
                    _employeeList.value = _employeeList.value.orEmpty().map { if (it.uid == currentEmployee.uid) currentEmployee else it }
                }
                _saveResult.postValue(result)
            } catch (e: Exception) {
                _isSaving.value = false
                _saveResult.value = FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }

}
