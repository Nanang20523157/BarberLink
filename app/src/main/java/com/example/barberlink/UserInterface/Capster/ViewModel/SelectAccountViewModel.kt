package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectAccountViewModel : ViewModel() {

    val employeeListMutex = ReentrantCoroutineMutex()
    val rolesListMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()
    val listenerOutletDataMutex = ReentrantCoroutineMutex()
    val listenerRolesMutex = ReentrantCoroutineMutex()

    // =========================================================
    // === UTILITAS DASAR
    // =========================================================

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.addItem(item: T) {
        val updated = (value ?: mutableListOf()).apply { add(item) }
        updateOnMain(updated)
    }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    // =======================================================================

    private val _outletSelected = MutableLiveData<Outlet>()
    val outletSelected: LiveData<Outlet> = _outletSelected

    private val _employeeList = MutableLiveData<List<UserEmployeeData>>().apply { value = mutableListOf() }
    val employeeList: LiveData<List<UserEmployeeData>> = _employeeList

    private val _employeeRolesList = MutableLiveData<List<EmployeeRolesData>>().apply { value = mutableListOf() }
    val employeeRolesList: LiveData<List<EmployeeRolesData>> = _employeeRolesList

    private val _filteredEmployeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val filteredEmployeeList: LiveData<MutableList<UserEmployeeData>> = _filteredEmployeeList

    private val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    private val _letsFilteringDataEmployee = MutableLiveData<Boolean?>()
    val letsFilteringDataEmployee: LiveData<Boolean?> = _letsFilteringDataEmployee

    private val _displayFilteredEmployeeResult = MutableLiveData<Boolean?>()
    val displayFilteredEmployeeResult: LiveData<Boolean?> = _displayFilteredEmployeeResult

    fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.postValue(employeeList)
        }
    }

    fun setEmployeeRoles(list: List<EmployeeRolesData>) {
        viewModelScope.launch {
            val employees = _employeeList.value ?: emptyList()
            _employeeRolesList.postValue(list)

            if (employees.isNotEmpty()) {
                employeeListMutex.withStateLock {
                    employees.forEach { employee ->
                        employee.roleDetail = list.find { it.roleName == employee.role }
                    }
                    _employeeList.postValue(employees)
                }
            }
        }
    }

    fun setOutletSelected(outlet: Outlet) {
        viewModelScope.launch {
            _outletSelected.postValue(outlet)
        }
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData?) {
        viewModelScope.launch {
            _userEmployeeData.postValue(userEmployeeData)
        }
    }

    fun triggerFilteringDataEmployee(withShimmer: Boolean) {
        viewModelScope.launch {
            _letsFilteringDataEmployee.postValue(withShimmer)
        }
    }

    fun setFilteredEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch {
            _filteredEmployeeList.postValue(employeeList)
        }
    }

    fun displayFilteredEmployeeResult(withShimmer: Boolean) {
        viewModelScope.launch {
            _displayFilteredEmployeeResult.postValue(withShimmer)
        }
    }

    fun clearState() {
        viewModelScope.launch {
            _letsFilteringDataEmployee.postValue(null)
            _displayFilteredEmployeeResult.postValue(null)
        }
    }

}
