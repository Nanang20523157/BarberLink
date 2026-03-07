package com.example.barberlink.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookingRepository {

    // MutableLiveData untuk menghitung item yang dipilih
    private val _itemSelectedCounting = MutableLiveData<Int>().apply { value = 0 }
    val itemSelectedCounting: LiveData<Int> = _itemSelectedCounting

    // MutableLiveData untuk daftar pasangan nama dan kategori item yang dipilih
    private val _itemNameSelected = MutableLiveData<List<Pair<String, String>>>()
    val itemNameSelected: LiveData<List<Pair<String, String>>> = _itemNameSelected

    // Tambahan untuk bundlingPackagesList
    private val _bundlingPackagesList = MutableLiveData<List<BundlingPackage>>().apply { value = listOf() }
    val bundlingPackagesList: LiveData<List<BundlingPackage>> = _bundlingPackagesList

    // Tambahan untuk servicesList
    private val _servicesList = MutableLiveData<List<Service>>().apply { value = listOf() }
    val servicesList: LiveData<List<Service>> = _servicesList

    private val _outletList = MutableLiveData<List<Outlet>>().apply { value = emptyList() }
    val outletList: LiveData<List<Outlet>> = _outletList

    private val _customerList = MutableLiveData<List<UserCustomerData>>().apply { value = listOf() }
    val customerList: LiveData<List<UserCustomerData>> = _customerList

    private val _filteredCustomerList = MutableLiveData<List<UserCustomerData>>().apply { value = listOf() }
    val filteredCustomerList: LiveData<List<UserCustomerData>> = _filteredCustomerList

    private val _letsFilteringDataCustomer = MutableLiveData<Boolean?>()
    val letsFilteringDataCustomer: LiveData<Boolean?> = _letsFilteringDataCustomer

    private val _displayFilteredCustomerResult = MutableLiveData<Boolean?>().apply { value = true }
    val displayFilteredCustomerResult: LiveData<Boolean?> = _displayFilteredCustomerResult

    private val _displayAllDataToUI = MutableLiveData<Boolean?>()
    val displayAllDataToUI: LiveData<Boolean?> = _displayAllDataToUI

    // Properti LiveData untuk daftar Int dengan nilai default list kosong
    private val _indexBundlingChanged = MutableLiveData<List<Int>>().apply { value = mutableListOf() }
    val indexBundlingChanged: LiveData<List<Int>> = _indexBundlingChanged

    private val _indexServiceChanged = MutableLiveData<List<Int>>().apply { value = mutableListOf() }
    val indexServiceChanged: LiveData<List<Int>> = _indexServiceChanged

    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _userFullname = MutableLiveData<Event<String>>()
    val userFullname: LiveData<Event<String>> = _userFullname

    private val _userGender = MutableLiveData<Event<String>>()
    val userGender: LiveData<Event<String>> = _userGender

    private val _isDataChanged = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(_bundlingPackagesList) {
            Logger.d("DataSync", "isDataChanged 40: initial source by bundling")
            recalculate()
            value = true
        }
        addSource(_servicesList) {
            Logger.d("DataSync", "isDataChanged 47: initial source by service")
            recalculate()
            value = true
        }
    }
    val isDataChanged: LiveData<Boolean> = _isDataChanged

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

    private val _outletSelected = MutableLiveData<Outlet>()
    val outletSelected: LiveData<Outlet> = _outletSelected

    private val _capsterSelected = MutableLiveData<UserEmployeeData?>()
    val capsterSelected: LiveData<UserEmployeeData?> = _capsterSelected

    private val _customerSelected = MutableLiveData<UserCustomerData?>()
    val customerSelected: LiveData<UserCustomerData?> = _customerSelected

    private fun recalculate() {
        val count =
            (_servicesList.value?.sumOf { it.serviceQuantity } ?: 0) +
                    (_bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0)

        _itemSelectedCounting.value = count
    }

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

    suspend fun updateOutletSelected(outlet: Outlet) {
        _outletSelected.updateOnMain(outlet)
    }

    suspend fun updateOutletList(outletList: List<Outlet>) {
        _outletList.updateOnMain(outletList)
    }

    suspend fun updateCapsterSelected(capster: UserEmployeeData?) {
        _capsterSelected.updateOnMain(capster)
    }

    suspend fun updateCustomerSelected(customer: UserCustomerData?) {
        _customerSelected.updateOnMain(customer)
    }

    suspend fun updateCustomerList(customerList: List<UserCustomerData>) {
        _customerList.updateOnMain(customerList)
    }

    suspend fun updateFilteredCustomerList(filteredCustomerList: List<UserCustomerData>) {
        _filteredCustomerList.updateOnMain(filteredCustomerList)
    }

    suspend fun updateLetsFilteringDataCustomer(displayAllData: Boolean?) {
        _letsFilteringDataCustomer.updateOnMain(displayAllData)
    }

    suspend fun updateDisplayFilteredCustomerResult(displayAllData: Boolean?) {
        _displayFilteredCustomerResult.updateOnMain(displayAllData)
    }

    suspend fun updateDisplayAllDataToUI(value: Boolean?) {
        _displayAllDataToUI.updateOnMain(value)
    }

    suspend fun updateSnackbarToAll(fullname: String, gender: String, message: String) {
        _userFullname.updateOnMain(Event(fullname))
        _userGender.updateOnMain(Event(gender))
        _snackBarMessage.updateOnMain(Event(message))
    }

    suspend fun updateSnackbarMessage(message: String) {
        _snackBarMessage.updateOnMain(Event(message))
    }

    suspend fun updateIsDataChanged(value: Boolean) {
        _isDataChanged.updateOnMain(value)
    }

    suspend fun updateItemNameSelected(itemNameList: List<Pair<String, String>>) {
        _itemNameSelected.updateOnMain(itemNameList)
    }

    suspend fun updateSelectedCounting(count: Int) {
        _itemSelectedCounting.updateOnMain(count)
    }

    suspend fun updateServicesList(servicesList: List<Service>) {
        _servicesList.updateOnMain(servicesList)
    }

    suspend fun updateBundlingPackagesList(bundlingPackagesList: List<BundlingPackage>) {
        _bundlingPackagesList.updateOnMain(bundlingPackagesList)
    }

    suspend fun updateIsSetItemBundling(value: Boolean) {
        _isSetItemBundling.updateOnMain(value)
    }

    suspend fun updateIndexBundlingChanged(indexList: List<Int>) {
        _indexBundlingChanged.updateOnMain(indexList)
    }

    suspend fun updateIndexServiceChanged(indexList: List<Int>) {
        _indexServiceChanged.updateOnMain(indexList)
    }

}