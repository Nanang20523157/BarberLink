package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class SelectAccountViewModel : ViewModel() {

    val employeeMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()
    val listenerOutletDataMutex = ReentrantCoroutineMutex()

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

    private val _employeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val employeeList: LiveData<MutableList<UserEmployeeData>> = _employeeList

    private val _filteredEmployeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val filteredEmployeeList: LiveData<MutableList<UserEmployeeData>> = _filteredEmployeeList

    private val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    private val _letsFilteringDataEmployee = MutableLiveData<Boolean?>()
    val letsFilteringDataEmployee: LiveData<Boolean?> = _letsFilteringDataEmployee

    private val _displayFilteredEmployeeResult = MutableLiveData<Boolean?>()
    val displayFilteredEmployeeResult: LiveData<Boolean?> = _displayFilteredEmployeeResult

    suspend fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _employeeList.postValue(employeeList)
        }
    }

    suspend fun setOutletSelected(outlet: Outlet) {
        withContext(Dispatchers.Main) {
            _outletSelected.postValue(outlet)
        }
    }

    suspend fun setUserEmployeeData(userEmployeeData: UserEmployeeData?) {
        withContext(Dispatchers.Main) {
            _userEmployeeData.postValue(userEmployeeData)
        }
    }

    suspend fun triggerFilteringDataEmployee(withShimmer: Boolean) {
        withContext(Dispatchers.Main) {
            _letsFilteringDataEmployee.postValue(withShimmer)
        }
    }

    suspend fun setFilteredEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _filteredEmployeeList.postValue(employeeList)
        }
    }

    suspend fun displayFilteredEmployeeResult(withShimmer: Boolean) {
        withContext(Dispatchers.Main) {
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