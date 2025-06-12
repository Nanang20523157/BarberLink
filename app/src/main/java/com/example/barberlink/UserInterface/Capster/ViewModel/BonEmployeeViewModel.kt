package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.barberlink.Adapter.ItemListTagFilteringAdapter
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserFilterCategories
import com.example.barberlink.Helper.Event

class BonEmployeeViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private val _capsterNames = MutableLiveData<List<String>>(emptyList())
    val capsterNames: LiveData<List<String>> = _capsterNames

    private val _employeeListBon = MutableLiveData<MutableList<BonEmployeeData>>().apply { emptyList<BonEmployeeData>() }
    val employeeListBon: LiveData<MutableList<BonEmployeeData>> = _employeeListBon

    private val _filteredEmployeeListBon = MutableLiveData<MutableList<BonEmployeeData>>().apply { emptyList<BonEmployeeData>() }
    val filteredEmployeeListBon: LiveData<MutableList<BonEmployeeData>> = _filteredEmployeeListBon

    private val _reSetupDropdownCapster = MutableLiveData<Boolean?>()
    val reSetupDropdownCapster: LiveData<Boolean?> = _reSetupDropdownCapster

    private val _dataBonDelete = MutableLiveData<BonEmployeeData?>()
    val dataBonDelete: LiveData<BonEmployeeData?> = _dataBonDelete

    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _userEmployeeData = MutableLiveData<UserEmployeeData?>()
    override val userEmployeeData: LiveData<UserEmployeeData?> = _userEmployeeData

    private val _initializationPage = MutableLiveData<Boolean?>()
    val initializationPage: LiveData<Boolean?> = _initializationPage

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
            UserFilterCategories(tagCategory = "Semua", textContained = "", dataSelected = true),
            UserFilterCategories(tagCategory = "Sistem Angsuran", textContained = "From Installment", dataSelected = false),
            UserFilterCategories(tagCategory = "Potong Gaji", textContained = "From Salary", dataSelected = false),
            UserFilterCategories(tagCategory = "Lunas", textContained = "Lunas", dataSelected = false),
            UserFilterCategories(tagCategory = "Belum Bayar", textContained = "Belum Bayar", dataSelected = false),
            UserFilterCategories(tagCategory = "Terangsur", textContained = "Terangsur", dataSelected = false)
        )
    )
    val tagFilteringCategory: LiveData<ArrayList<UserFilterCategories>> = _tagFilteringCategory

    fun setUserEmployeeData(data: UserEmployeeData?, initPage: Boolean?) {
        _userEmployeeData.postValue(data)
        if (initPage == true) {
            _initializationPage.postValue(initPage)
        }
    }

    fun setBonEmployeeData(data: BonEmployeeData?) {
        _bonEmployeeData.postValue(data)
    }

    fun setUserCurrentAccumulationBon(data: Int?) {
        _userCurrentAccumulationBon.postValue(data)
    }

    fun setUserPreviousAccumulationBon(data: Int?) {
        _userPreviousAccumulationBon.postValue(data)
    }

    fun setEmployeeCurrentAccumulationBon(data: MutableMap<String, Int>) {
        _employeeCurrentAccumulationBon.postValue(data)
    }

    fun setEmployeePreviousAccumulationBon(data: MutableMap<String, Int>) {
        _employeePreviousAccumulationBon.postValue(data)
    }

    fun setDataBonDeleted(data: BonEmployeeData?, message: String) {
        _dataBonDelete.postValue(data)
        if (message.isNotEmpty()) {
            _snackBarMessage.postValue(Event(message))
        }
    }
    fun addCapsterList(capsterList: List<UserEmployeeData>) {
        Log.d("CacheChecking", "addCapsterList --> capsterList size: ${capsterList.size}")
        _capsterList.postValue(capsterList)
    }

    fun addCapsterNames(capsterNames: List<String>) {
        Log.d("CacheChecking", "addCapsterNames --> capsterNames size: ${capsterNames.size}")
        _capsterNames.postValue(capsterNames)
    }

    fun setEmployeeListBon(employeeListBon: MutableList<BonEmployeeData>) {
        _employeeListBon.postValue(employeeListBon)
    }

    fun setFilteredEmployeeListBon(filteredEmployeeListBon: MutableList<BonEmployeeData>) {
        _filteredEmployeeListBon.postValue(filteredEmployeeListBon)
    }

    fun setReSetupDropdownCapster(reSetup: Boolean) {
        _reSetupDropdownCapster.postValue(reSetup)
    }

    fun setActiveTagFilterCategory(position: Int, adapter: ItemListTagFilteringAdapter) {
        _tagFilteringCategory.value?.apply {
            forEachIndexed { index, userFilterCategories ->
                userFilterCategories.dataSelected = index == position
            }
        }

        adapter.notifyDataSetChanged() // Paksa adapter untuk refresh UI
    }

}