package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData

class SelectAccountViewModel : ViewModel() {

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

    fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        _employeeList.postValue(employeeList)
    }

    fun setOutletSelected(outlet: Outlet) {
        _outletSelected.postValue(outlet)
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData?) {
        _userEmployeeData.postValue(userEmployeeData)
    }

    fun triggerFilteringDataEmployee(withShimmer: Boolean) {
        _letsFilteringDataEmployee.postValue(withShimmer)
    }

    fun setFilteredEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        _filteredEmployeeList.postValue(employeeList)
    }

    fun displayFilteredEmployeeResult(withShimmer: Boolean) {
        _displayFilteredEmployeeResult.postValue(withShimmer)
    }

    fun clearState() {
        _letsFilteringDataEmployee.postValue(null)
        _displayFilteredEmployeeResult.postValue(null)
    }

}