package com.example.barberlink.UserInterface.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Helper.Event
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BonEmployeeViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val listBonMutex = ReentrantCoroutineMutex()
    val employeeListMutex = ReentrantCoroutineMutex()
    val rolesListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerCurrentBonMutex = ReentrantCoroutineMutex()
    val listenerNextPrevMutex = ReentrantCoroutineMutex()
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

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private val _employeeRolesList = MutableLiveData<List<EmployeeRolesData>>().apply { value = mutableListOf() }
    val employeeRolesList: LiveData<List<EmployeeRolesData>> = _employeeRolesList

//    private val _capsterNames = MutableLiveData<List<String>>(emptyList())
//    val capsterNames: LiveData<List<String>> = _capsterNames

    private val _employeeListBon = MutableLiveData<MutableList<BonEmployeeData>>().apply { emptyList<BonEmployeeData>() }
    val employeeListBon: LiveData<MutableList<BonEmployeeData>> = _employeeListBon

    private val _filteredEmployeeListBon = MutableLiveData<MutableList<BonEmployeeData>>().apply { emptyList<BonEmployeeData>() }
    val filteredEmployeeListBon: LiveData<MutableList<BonEmployeeData>> = _filteredEmployeeListBon

    private val _dataBonDelete = MutableLiveData<BonEmployeeData?>()
    val dataBonDelete: LiveData<BonEmployeeData?> = _dataBonDelete

    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

//    private val _initializationPage = MutableLiveData<Boolean?>()
//    val initializationPage: LiveData<Boolean?> = _initializationPage

    // Fragment Requirement
    private val _bonEmployeeData = MutableLiveData<BonEmployeeData?>()
    val bonEmployeeData: LiveData<BonEmployeeData?> = _bonEmployeeData

    private val _userCurrentAccumulationBon = MutableLiveData<Int?>()
    val userCurrentAccumulationBon: LiveData<Int?> = _userCurrentAccumulationBon

    private val _userPreviousAccumulationBon = MutableLiveData<Int?>()
    val userPreviousAccumulationBon: LiveData<Int?> = _userPreviousAccumulationBon

    private val _employeeCurrentAccumulationBon = MutableLiveData<MutableMap<String, Int>>().apply { value = mutableMapOf() }
    val employeeCurrentAccumulationBon: LiveData<MutableMap<String, Int>> = _employeeCurrentAccumulationBon

    private val _employeePreviousAccumulationBon = MutableLiveData<MutableMap<String, Int>>().apply { value = mutableMapOf() }
    val employeePreviousAccumulationBon: LiveData<MutableMap<String, Int>> = _employeePreviousAccumulationBon

    // Pindahkan tag filtering category ke dalam ViewModel
    private val _tagFilteringCategory = MutableLiveData<ArrayList<UserFilterCategories>>(
        arrayListOf(
            UserFilterCategories(
                tagCategory = "Semua",
                textContained = "Semua",
                dataSelected = true
            ),
            UserFilterCategories(
                tagCategory = "Sistem Angsuran",
                textContained = "From Installment",
                dataSelected = false
            ),
            UserFilterCategories(
                tagCategory = "Potong Gaji",
                textContained = "From Salary",
                dataSelected = false
            ),
            UserFilterCategories(
                tagCategory = "Lunas",
                textContained = "Lunas",
                dataSelected = false
            ),
            UserFilterCategories(
                tagCategory = "Belum Bayar",
                textContained = "Belum Bayar",
                dataSelected = false
            ),
            UserFilterCategories(
                tagCategory = "Terangsur",
                textContained = "Terangsur",
                dataSelected = false
            )
        )
    )
    val tagFilteringCategory: LiveData<ArrayList<UserFilterCategories>> = _tagFilteringCategory

    fun setUserEmployeeData(
        data: UserEmployeeData?,
//        initPage: Boolean?,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        viewModelScope.launch {
            _userEmployeeData.postValue(data)
            if (setupDropdown != null && isSavedInstanceStateNull != null) {
                _setupDropdownFilter.postValue(setupDropdown)
                _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
            }
        }
    }

    fun setBonEmployeeData(data: BonEmployeeData?) {
        viewModelScope.launch {
            _bonEmployeeData.postValue(data)
        }
    }

    fun setUserCurrentAccumulationBon(data: Int?) {
        viewModelScope.launch {
            _userCurrentAccumulationBon.postValue(data)
        }
    }

    fun setUserPreviousAccumulationBon(data: Int?) {
        viewModelScope.launch {
            _userPreviousAccumulationBon.postValue(data)
        }
    }

    fun setEmployeeCurrentAccumulationBon(data: MutableMap<String, Int>) {
        viewModelScope.launch {
            _employeeCurrentAccumulationBon.postValue(data)
        }
    }

    fun setEmployeePreviousAccumulationBon(data: MutableMap<String, Int>) {
        viewModelScope.launch {
            _employeePreviousAccumulationBon.postValue(data)
        }
    }

    fun updateCurrentAccumulationBon(data: MutableMap<String, Int>) {
        viewModelScope.launch {
            _employeeCurrentAccumulationBon.postValue(data)
        }
    }

    fun updatePreviousAccumulationBon(data: MutableMap<String, Int>) {
        viewModelScope.launch {
            _employeePreviousAccumulationBon.postValue(data)
        }
    }

    fun setDataBonDeleted(data: BonEmployeeData?, message: String) {
        viewModelScope.launch {
            _dataBonDelete.postValue(data)
            if (message.isNotEmpty()) {
                _snackBarMessage.postValue(Event(message))
            }
        }
    }

    fun setCapsterList(
        capsterList: List<UserEmployeeData>,
        //capsterNames: List<String>,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        Log.d("CacheChecking", "setCapsterList --> capsterList size: ${capsterList.size}")
        viewModelScope.launch {
            _capsterList.postValue(capsterList)
            //_capsterNames.postValue(capsterNames)
            _setupDropdownFilter.postValue(setupDropdown)
            _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
        }
    }

    // ^^^^^^^ Bukan untuk capsterList (KasbonGatewayPage) ^^^^^^^
    fun setEmployeeRoles(list: List<EmployeeRolesData>) {
        viewModelScope.launch {
            val employeeData = _userEmployeeData.value
            _employeeRolesList.postValue(list)

            if (employeeData != null) {
                employeeData.let { data ->
                    data.roleDetail = list.find { it.roleName == data.role }
                }
                _userEmployeeData.postValue(employeeData)
            }
        }
    }

//    fun setCapsterNames(capsterNames: List<String>) {
//        Log.d("CacheChecking", "setCapsterNames --> capsterNames size: ${capsterNames.size}")
//        _capsterNames.postValue(capsterNames)
//    }

    fun setEmployeeListBon(employeeListBon: MutableList<BonEmployeeData>) {
        viewModelScope.launch {
            _employeeListBon.postValue(employeeListBon)
        }
    }

    fun setFilteredEmployeeListBon(filteredEmployeeListBon: MutableList<BonEmployeeData>) {
        viewModelScope.launch {
            _filteredEmployeeListBon.postValue(filteredEmployeeListBon)
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.postValue(false)
            _setupDropdownFilterWithNullState.postValue(false)
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
        Log.d("CheckShimmer", "clearDropdownStateValue")
    }

    fun setActiveTagFilterCategory(position: Int, adapter: ItemListTagFilteringAdapter) {
        viewModelScope.launch {
            _tagFilteringCategory.value?.apply {
                forEachIndexed { index, userFilterCategories ->
                    userFilterCategories.dataSelected = index == position
                }
            }

            adapter.notifyDataSetChanged() // Paksa adapter untuk refresh UI
        }
    }

    fun clearAttacmentData() {
        viewModelScope.launch {
            _userPreviousAccumulationBon.postValue(null)
            _userCurrentAccumulationBon.postValue(null)
            _userEmployeeData.postValue(null)
        }
    }

    fun clearBonEmployeeData() {
        viewModelScope.launch {
            _bonEmployeeData.postValue(null)
        }
    }

}