package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BonEmployeeViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    var letScrollToLastPosition: Boolean? = null
    val listBonMutex = ReentrantCoroutineMutex()
    val employeeListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerCurrentBonMutex = ReentrantCoroutineMutex()
    val listenerNextPrevMutex = ReentrantCoroutineMutex()

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
    private val _tagFilteringCategory = MutableLiveData(
        arrayListOf(
            UserFilterCategories(tagCategory = "Semua", textContained = "Semua", dataSelected = true),
            UserFilterCategories(tagCategory = "Sistem Angsuran", textContained = "From Installment", dataSelected = false),
            UserFilterCategories(tagCategory = "Potong Gaji", textContained = "From Salary", dataSelected = false),
            UserFilterCategories(tagCategory = "Lunas", textContained = "Lunas", dataSelected = false),
            UserFilterCategories(tagCategory = "Belum Bayar", textContained = "Belum Bayar", dataSelected = false),
            UserFilterCategories(tagCategory = "Terangsur", textContained = "Terangsur", dataSelected = false)
        )
    )
    val tagFilteringCategory: LiveData<ArrayList<UserFilterCategories>> = _tagFilteringCategory

    suspend fun setUserEmployeeData(
        data: UserEmployeeData?,
//        initPage: Boolean?,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        withContext(Dispatchers.Main) {
            _userEmployeeData.postValue(data)
            if (setupDropdown != null && isSavedInstanceStateNull != null) {
                _setupDropdownFilter.postValue(setupDropdown)
                _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
            }
        }
    }

    suspend fun setBonEmployeeData(data: BonEmployeeData?) {
        withContext(Dispatchers.Main) {
            _bonEmployeeData.postValue(data)
        }
    }


    suspend fun setUserCurrentAccumulationBon(data: Int?) {
        withContext(Dispatchers.Main) {
            _userCurrentAccumulationBon.postValue(data)
        }
    }

    suspend fun setUserPreviousAccumulationBon(data: Int?) {
        withContext(Dispatchers.Main) {
            _userPreviousAccumulationBon.postValue(data)
        }
    }

    suspend fun setEmployeeCurrentAccumulationBon(data: MutableMap<String, Int>) {
        withContext(Dispatchers.Main) {
            _employeeCurrentAccumulationBon.postValue(data)
        }
    }

    suspend fun setEmployeePreviousAccumulationBon(data: MutableMap<String, Int>) {
        withContext(Dispatchers.Main) {
            _employeePreviousAccumulationBon.postValue(data)
        }
    }

    suspend fun setDataBonDeleted(data: BonEmployeeData?, message: String) {
        withContext(Dispatchers.Main) {
            _dataBonDelete.postValue(data)
            if (message.isNotEmpty()) {
                _snackBarMessage.postValue(Event(message))
            }
        }
    }

    suspend fun setCapsterList(
        capsterList: List<UserEmployeeData>,
        //capsterNames: List<String>,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        // txzList
        Log.d("CacheChecking", "setCapsterList --> capsterList size: ${capsterList.size}")
        withContext(Dispatchers.Main) {
            _capsterList.postValue(capsterList)
            //_capsterNames.postValue(capsterNames)
            _setupDropdownFilter.postValue(setupDropdown)
            _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
        }
    }

    suspend fun setScrollToLastPositionState(value: Boolean, employeeListBon: MutableList<BonEmployeeData>?) {
        // txzList
        withContext(Dispatchers.Main) {
            letScrollToLastPosition = value
            employeeListBon.let { _filteredEmployeeListBon.postValue(employeeListBon) }
        }
    }

    suspend fun setEmployeeListBon(employeeListBon: MutableList<BonEmployeeData>) {
        // txzList
        withContext(Dispatchers.Main) {
            _employeeListBon.postValue(employeeListBon)
        }
    }

    suspend fun setFilteredEmployeeListBon(filteredEmployeeListBon: MutableList<BonEmployeeData>) {
        // txzList pasti aman
        withContext(Dispatchers.Main) {
            _filteredEmployeeListBon.postValue(filteredEmployeeListBon)
        }
    }

    override suspend fun setupDropdownFilterWithNullState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.postValue(false)
            _setupDropdownFilterWithNullState.postValue(false)
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    fun setActiveTagFilterCategory(position: Int) {
        // txzList
        val oldList = _tagFilteringCategory.value ?: return

        val newList = oldList.mapIndexed { index, item ->
            item.copy(dataSelected = index == position)
        }

        _tagFilteringCategory.value = newList.toCollection(ArrayList())
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

    fun clearState() {
        viewModelScope.launch {
            letScrollToLastPosition = null
        }
    }

}