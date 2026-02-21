package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Helper.Event
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SharedReserveViewModel : ViewModel() {

    val stateMutex = ReentrantCoroutineMutex() // sudah mewakili servicesMutex dan juga bundlingMutex
    val customerMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerCustomerDataMutex = ReentrantCoroutineMutex()
    val listenerCapsterDataMutex = ReentrantCoroutineMutex()
    val listenerCustomerListMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val listenerOutletDataMutex = ReentrantCoroutineMutex()
    val listenerBundlingsMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()

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
    private val _indexBundlingChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
    val indexBundlingChanged: LiveData<MutableList<Int>> = _indexBundlingChanged

    private val _indexServiceChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
    val indexServiceChanged: LiveData<MutableList<Int>> = _indexServiceChanged

    private val _snackBarMessage = MutableLiveData<Event<String>>()
    val snackBarMessage: LiveData<Event<String>> = _snackBarMessage

    private val _userFullname = MutableLiveData<Event<String>>()
    val userFullname: LiveData<Event<String>> = _userFullname

    private val _userGender = MutableLiveData<Event<String>>()
    val userGender: LiveData<Event<String>> = _userGender

    private val _isDataChanged = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(_bundlingPackagesList) {
            Log.d("TestDataChange", "isDataChanged 40: initial source by bundling")
            value = true
            val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            _itemSelectedCounting.value = currentCount
        }
        addSource(_servicesList) {
            Log.d("TestDataChange", "isDataChanged 47: initial source by service")
            value = true
            val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            _itemSelectedCounting.value = currentCount
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

    fun setOutletSelected(outlet: Outlet) {
        Log.d("ScanAll", "A2")
        viewModelScope.launch {
            _outletSelected.value = outlet
        }
    }

    fun setOutletList(outletList: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = outletList
        }
    }

    fun setCapsterSelected(capster: UserEmployeeData?) {
        Log.d("ScanAll", "A1")
        viewModelScope.launch {
            _capsterSelected.value = capster
        }
    }

    fun setCustomerSelected(customer: UserCustomerData?) {
        Log.d("ScanAll", "A3")
        viewModelScope.launch {
            _customerSelected.value = customer
        }
    }

    fun showSnackBarToAll(fullname: String, gender: String, message: String) {
        Log.d("ScanAll", "B2")
        viewModelScope.launch {
            _userFullname.value = Event(fullname)
            _userGender.value = Event(gender)
            _snackBarMessage.value = Event(message)
        }
    }

    fun showSnackBarToSynchronization(message: String) {
        Log.d("ScanAll", "C2")
        viewModelScope.launch {
            _snackBarMessage.value = Event(message)
        }
    }

    fun addCustomerData(customer: UserCustomerData) {
        Log.d("ScanAll", "D2")
        viewModelScope.launch {
            customerMutex.withStateLock {
                val currentList = _customerList.value.orEmpty().toMutableList()

                // Cek apakah customer dengan UID yang sama sudah ada
                val alreadyExists = currentList.any { it.uid == customer.uid }
                if (!alreadyExists) {
                    currentList.add(customer)
                    Log.d("CacheChecking", "addCustomerData --> customerList size: ${currentList.size}")
                    Log.d("BtnSaveChecking", "Button Save Clicked 6")
                    _customerList.value = currentList.sortedByDescending { it.lastReserve }.toMutableList()
                } else {
                    Log.d("CacheChecking", "Customer dengan UID ${customer.uid} sudah ada, tidak ditambahkan ulang.")
                }
            }
        }
    }

    suspend fun setCustomerList(
        lowerCaseQuery: String,
        newCustomerList: List<UserCustomerData>,
        isFromListener: Boolean = false,
    ) {
        withContext(Dispatchers.Default) {
            val updatedCustomerList = _customerList.value?.toMutableList() ?: mutableListOf()
            val updatedFilteredList = _filteredCustomerList.value?.toMutableList() ?: mutableListOf()
            Log.d("XYZChecking", "setCustomerList --> new size: ${newCustomerList.size} XXX isFromListener: $isFromListener")

            if (!isFromListener) {
                Log.d("XYZChecking", "updatedCustomerList.addAll(newCustomerList)")
                updatedCustomerList.clear()
                updatedCustomerList.addAll(newCustomerList)
            } else {
                newCustomerList.forEach { newCustomerData ->
                    val existingCustomer = updatedCustomerList.find { it.uid == newCustomerData.uid }
                    if (existingCustomer != null) {
                        Log.d("XYZChecking", "if (existingCustomer != null)")
                        existingCustomer.apply {
                            userReminder = newCustomerData.userReminder
                            email = newCustomerData.email
                            fullname = newCustomerData.fullname
                            gender = newCustomerData.gender
                            membership = newCustomerData.membership
                            password = newCustomerData.password
                            phone = newCustomerData.phone
                            photoProfile = newCustomerData.photoProfile
                            userNotification = newCustomerData.userNotification
                            uid = newCustomerData.uid
                            username = newCustomerData.username
                            userCoins = newCustomerData.userCoins
                            lastReserve = newCustomerData.lastReserve
                            // dataSelected = newCustomerData.dataSelected
                            // guestAccount = newCustomerData.guestAccount
                            userRef = newCustomerData.userRef
                        }
                    } else {
                        Log.d("XYZChecking", "<<<!!!>>> if (existingCustomer == null)")
                        updatedCustomerList.add(newCustomerData)

                        if (newCustomerData.phone.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                            Log.d("XYZChecking", "data baru dengan query yang cocok ditemukan: ${newCustomerData.phone}")
                            // Tambahkan ke filtered list hanya jika belum ada uid yang sama
                            val isAlreadyInFilteredList = updatedFilteredList.any { it.uid == newCustomerData.uid }
                            if (!isAlreadyInFilteredList) {
                                val insertAt = updatedFilteredList.indexOfFirst {
                                    (it.lastReserve ?: Timestamp(0, 0)) < (newCustomerData.lastReserve ?: Timestamp(0, 0))
                                }
                                val insertIndex = if (insertAt == -1) updatedFilteredList.size else insertAt
                                updatedFilteredList.add(insertIndex, newCustomerData)
                            }
                        } else {
                            Log.d("XYZChecking", "<<<___>>> data baru dengan query yang cocok tidak ditemukan: ${newCustomerData.phone}")
                        }
                    }
                }

                val customerToRemove = updatedCustomerList.filterNot { updated ->
                    newCustomerList.any { it.uid == updated.uid }
                }
                if (customerToRemove.isNotEmpty()) {
                    Log.d("XYZChecking", "customerToRemove size: ${customerToRemove.size}")
                    updatedCustomerList.removeAll(customerToRemove)
                    updatedFilteredList.removeAll(customerToRemove)
                }
            }

            _customerList.updateOnMain(updatedCustomerList)
            if (isFromListener) {
                // Ambil item yang selected
                val selectedItems = updatedFilteredList.find { it.dataSelected }
                selectedItems?.let { _customerSelected.updateOnMain(selectedItems) }
                _filteredCustomerList.updateOnMain(updatedFilteredList.take(10))
                // _letsFilteringDataCustomer.updateOnMain(false)
                _displayFilteredCustomerResult.updateOnMain(true)
            }
        }
    }

    fun updateCustomerData(customer: UserCustomerData) {
        viewModelScope.launch {
            Log.d("ScanAll", "G2")
            val updatedList = _customerList.value.orEmpty().toMutableList()
            val index = updatedList.indexOfFirst { it.uid == customer.uid }
            if (index != -1) {
                updatedList[index] = customer
                Log.d("CacheChecking", "updateCustomerData --> customerList size: ${updatedList.size}")
                _customerList.value = updatedList.sortedByDescending { it.lastReserve }.toMutableList()
            }
        }
    }

    fun triggerFilteringDataCustomer(displayAllData: Boolean) {
        Log.d("ScanAll", "H2")
        viewModelScope.launch {
            _letsFilteringDataCustomer.value = displayAllData
        }
    }

    fun setFilteredCustomerList(filteredResult: List<UserCustomerData>) {
        Log.d("ScanAll", "I2")
        Log.d("CacheChecking", "setFilteredCustomerList --> filteredCustomerList size: ${filteredResult.size}")
        viewModelScope.launch {
            _filteredCustomerList.value = filteredResult
        }
    }

    fun updateDataCustomerOnly() {
        Log.d("ScanAll", "J2")
        viewModelScope.launch {
            _displayFilteredCustomerResult.value = true
        }
    }

    fun displayAllDataToUI(value: Boolean) {
        Log.d("ScanAll", "L2")
        viewModelScope.launch {
            _displayAllDataToUI.value = value
        }
    }

    fun addItemSelectedCounting(name: String, category: String, includeMutex: Boolean) {
        viewModelScope.launch {
            addItemCounting(name, category, includeMutex)
        }
    }

    // Fungsi untuk menambah item yang dipilih
    private suspend fun addItemCounting(name: String, category: String, includeMutex: Boolean) {
        Log.d("ScanAll", "M2")
        val thisAction = suspend {
            // val newCount = (_itemSelectedCounting.value ?: 0) + 1
            val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()
            if (currentList.none { it.first == name }) {
                currentList.add(0, name to category)
            }

            // Memperbarui LiveData di main thread
            // _itemSelectedCounting.value = newCount
            _itemNameSelected.updateOnMain(currentList)
            _itemSelectedCounting.updateOnMain(currentCount)
            Log.d("TestAct", "addItemSelectedCounting 52: $currentCount || $category")
        }
        if (includeMutex) thisAction()
        else viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock { thisAction() }
        }
    }

    // Fungsi untuk menghapus item berdasarkan nama
    fun removeItemSelectedByName(name: String, removeName: Boolean) {
        Log.d("ScanAll", "N2")
        viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock {
                val currentList = _itemNameSelected.value?.toMutableList()
                if (!currentList.isNullOrEmpty() && removeName) {
                    val itemToRemove = currentList.find { it.first == name }
                    itemToRemove?.let {
                        currentList.remove(it)
                        _itemNameSelected.updateOnMain(currentList)
                    }
                }

                val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                        (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
                // var currentCount = _itemSelectedCounting.value ?: 0
                // if (currentCount > 0) {
                //    currentCount--
                //    _itemSelectedCounting.value = currentCount
                // }
                _itemSelectedCounting.updateOnMain(currentCount)
                Log.d("TestAct", "removeItemSelectedByName 70: $currentCount || $name || $removeName")
            }
        }
    }

    // Fungsi untuk mereset semua item yang dipilih
    fun resetAllItem() {
        viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock {
                // _itemSelectedCounting.value = 0
                _itemNameSelected.updateOnMain(emptyList())

                _servicesList.value?.forEach { service ->
                    service.apply {
                        serviceQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(serviceName, "service", true)
                    }
                }
                Log.d("CacheChecking", "resetAllItem --> serviceList size: ${_servicesList.value?.size.toString() ?: "null"}")
                // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())

                _bundlingPackagesList.value?.forEach { bundling ->
                    bundling.apply {
                        bundlingQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(packageName, "package", true)
                    }
                }
                Log.d("CacheChecking", "resetAllItem --> bundlingList size: ${_bundlingPackagesList.value?.size.toString() ?: "null"}")
                // _bundlingPackagesList.updateIfNeeded(_bundlingPackagesList.value ?: emptyList())
                _servicesList.updateOnMain(_servicesList.value ?: emptyList())
                _bundlingPackagesList.updateOnMain(_bundlingPackagesList.value ?: emptyList())
                Log.d("LifeAct", "observer 95: resetAllItem")
                _isDataChanged.updateOnMain(true)
                Log.d("TestDataChange", "isDataChanged 120: resetAllItem")
            }
        }
    }

    // Fungsi untuk mereset semua layanan (kategori "service")
    fun resetAllServices() {
        viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock {
                Log.d("ScanAll", "P2")
                val currentSelected = _itemNameSelected.value.orEmpty()
                    .filterNot { it.second == "service" }
                _itemNameSelected.updateOnMain(currentSelected)

                // val itemCount = _bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0
                // _itemSelectedCounting.value = itemCount

                _servicesList.value?.forEach { service ->
                    service.apply {
                        serviceQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(serviceName, "service", true)
                    }
                }

                // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())
                Log.d("CacheChecking", "resetAllServices --> serviceList size: ${_servicesList.value?.size.toString() ?: "null"}")
                _servicesList.updateOnMain(_servicesList.value ?: emptyList())
                Log.d("LifeAct", "observer 114: resetAllServices")
                _isDataChanged.updateOnMain(true)
                Log.d("TestDataChange", "isDataChanged 142: resetAllServices")
            }
        }
    }

    suspend fun setUpAndSortedBundling(
        currentBundlingList: MutableList<BundlingPackage>,
        capsterSelected: UserEmployeeData,
        isFromListener: Boolean
    ) {
        Log.d("ScanAll", "Q2")
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
                val updatedBundlingList = _bundlingPackagesList.value?.toMutableList() ?: mutableListOf()
                Log.d("CacheChecking", "setUpAndSortedBundling --> oldBundlinglist size: ${updatedBundlingList.size}")
                Log.d("CacheChecking", "setUpAndSortedBundling --> currentBundlinglist size: ${currentBundlingList.size}")

                if (updatedBundlingList.isEmpty()) {
                    // Jika oldBundlingList null, gunakan currentBundlingList
                    currentBundlingList.forEach { bundling ->
                        applyFieldBundling(bundling, capsterSelected, isFromListener)
                    }
                    updatedBundlingList.addAll(currentBundlingList) // Tambahkan semua item dari currentBundlingList
                } else {
                    currentBundlingList.forEach { currentBundling ->
                        // Cek apakah bundling dengan UID yang sama sudah ada
                        val existingBundling = updatedBundlingList.find { it.uid == currentBundling.uid }
                        if (existingBundling == null) {
                            // Jika tidak ada, tambahkan bundling baru
                            applyFieldBundling(currentBundling, capsterSelected, isFromListener)
                            updatedBundlingList.add(currentBundling)
                        } else {
                            // Jika ada, perbarui properti dari existing item
                            existingBundling.apply {
                                accumulatedPrice = currentBundling.accumulatedPrice
                                applyToGeneral = currentBundling.applyToGeneral
                                autoSelected = currentBundling.autoSelected
                                defaultItem = currentBundling.defaultItem
                                listItems = currentBundling.listItems
                                packageCounting = currentBundling.packageCounting
                                packageDesc = currentBundling.packageDesc
                                packageDiscount = currentBundling.packageDiscount
                                packageName = currentBundling.packageName
                                packagePrice = currentBundling.packagePrice
                                packageRating = currentBundling.packageRating
                                resultsShareAmount = currentBundling.resultsShareAmount
                                resultsShareFormat = currentBundling.resultsShareFormat
                                rootRef = currentBundling.rootRef
                                uid = currentBundling.uid

                                listItemDetails = _servicesList.value?.filter { service ->
                                    listItems.contains(service.uid)
                                } ?: emptyList()
                                Log.d("CacheChecking", "setUpAndSortedBundling --> listservice contain 1: ${listItemDetails?.size} || ${packageName}")

                                priceToDisplay = calculatePriceToDisplay(
                                    packagePrice,
                                    resultsShareFormat,
                                    resultsShareAmount,
                                    applyToGeneral,
                                    capsterSelected.uid
                                )
                            }
                        }
                    }
                    // Hapus item dari updatedBundlingList yang tidak ada di currentBundlingList
                    val bundlingsToRemove = updatedBundlingList.filterNot { existingBundling ->
                        currentBundlingList.any { it.uid == existingBundling.uid }
                    }
                    updatedBundlingList.removeAll(bundlingsToRemove) // Menghapus item yang tidak ada di currentBundlingList
                }

                // Urutkan bundlingPackagesList berdasarkan autoSelected atau defaultItem
                updatedBundlingList.sortByDescending { it.autoSelected || it.defaultItem }
                updatedBundlingList.forEachIndexed { index, bundlingPackage ->
                    bundlingPackage.itemIndex = index
                }

                Log.d("CacheChecking", "setUpAndSortedBundling --> updatedBundlingList size: ${updatedBundlingList.size}")
                // Update _bundlingPackagesList dengan referensi yang telah diperbarui
                _bundlingPackagesList.updateOnMain(updatedBundlingList)
                Log.d("LifeAct", "observer 158: setUpAndSortedBundling")
            }
        }
    }

    private suspend fun applyFieldBundling(bundling: BundlingPackage, capsterSelected: UserEmployeeData, isFromListener: Boolean) {
        bundling.apply {
            if (autoSelected || defaultItem) {
                bundlingQuantity = 1 // Set default quantity
                addItemCounting(packageName, "package", true)
            }
            if (isFromListener) {
                // Atur properti lainnya
                listItemDetails = _servicesList.value?.filter { service ->
                    listItems.contains(service.uid)
                } ?: emptyList()
                Log.d("CacheChecking", "setUpAndSortedBundling --> listservice contain 3: ${listItemDetails?.size} || ${bundling.packageName}")
            }

            priceToDisplay = calculatePriceToDisplay(
                packagePrice,
                resultsShareFormat,
                resultsShareAmount,
                applyToGeneral,
                capsterSelected.uid
            )
        }
    }

    suspend fun setServiceBundlingList() {
        Log.d("ScanAll", "R2")
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
                val listBundling = _bundlingPackagesList.value ?: emptyList()
                Log.d("CacheChecking", "setServiceBundlingList --> listbundling size: ${listBundling.size}")
                if (listBundling.isNotEmpty()) {
                    listBundling.onEach { bundling ->
                        val serviceBundlingList = _servicesList.value?.filter { service ->
                            bundling.listItems.contains(service.uid)
                        } ?: emptyList() // Jika null, gunakan list kosong

                        bundling.listItemDetails = serviceBundlingList
                        Log.d("CacheChecking", "setServiceBundlingList --> listservice contain 1: ${serviceBundlingList.size} || ${bundling.packageName}")
                    }

                    // _bundlingPackagesList.updateIfNeeded(listBundling)
                    _bundlingPackagesList.updateOnMain(listBundling)
                }

                _isSetItemBundling.updateOnMain(false)
            }
        }
    }

    suspend fun setUpAndSortedServices(
        currentServicesList: MutableList<Service>,
        capsterSelected: UserEmployeeData,
        isFromListener: Boolean
    ) {
        Log.d("ScanAll", "S2")
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
                val updatedServicesList = _servicesList.value?.toMutableList() ?: mutableListOf()
                Log.d("CacheChecking", "setUpAndSortedServices --> oldServiceList size: ${updatedServicesList.size}")
                Log.d("CacheChecking", "setUpAndSortedServices --> currentServicesList size: ${currentServicesList.size}")

                if (updatedServicesList.isEmpty()) {
                    // Jika oldServiceList null, gunakan currentServicesList
                    currentServicesList.forEach { service ->
                        applyFieldService(service, capsterSelected)
                    }
                    updatedServicesList.addAll(currentServicesList) // Tambahkan semua item dari currentServicesList
                } else {
                    currentServicesList.forEach { currentService ->
                        // Cek apakah service dengan UID yang sama sudah ada
                        val existingService = updatedServicesList.find { it.uid == currentService.uid }
                        if (existingService == null) {
                            // Jika tidak ada, tambahkan service baru
                            applyFieldService(currentService, capsterSelected)
                            updatedServicesList.add(currentService)
                        } else {
                            existingService.apply {
                                // Perbarui semua properti dari matching item tanpa mengganti referensi
                                applyToGeneral = currentService.applyToGeneral
                                autoSelected = currentService.autoSelected
                                categoryDetail = currentService.categoryDetail
                                defaultItem = currentService.defaultItem
                                freeOfCharge = currentService.freeOfCharge
                                resultsShareAmount = currentService.resultsShareAmount
                                resultsShareFormat = currentService.resultsShareFormat
                                rootRef = currentService.rootRef
                                serviceCategory = currentService.serviceCategory
                                serviceCounting = currentService.serviceCounting
                                serviceDesc = currentService.serviceDesc
                                serviceIcon = currentService.serviceIcon
                                serviceImg = currentService.serviceImg
                                serviceName = currentService.serviceName
                                servicePrice = currentService.servicePrice
                                serviceRating = currentService.serviceRating
                                uid = currentService.uid
                                // serviceQuantity = currentService.serviceQuantity

                                // Perhitungan priceToDisplay pada service
                                priceToDisplay = calculatePriceToDisplay(
                                    servicePrice,
                                    resultsShareFormat,
                                    resultsShareAmount,
                                    applyToGeneral,
                                    capsterSelected.uid
                                )
                            }
                        }
                    }
                    // Hapus item dari updatedServicesList yang tidak ada di currentServicesList
                    val servicesToRemove = updatedServicesList.filterNot { existingService ->
                        currentServicesList.any { it.uid == existingService.uid }
                    }
                    updatedServicesList.removeAll(servicesToRemove) // Menghapus item yang tidak ada di currentServicesList
                }

                // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
                updatedServicesList.sortByDescending { it.autoSelected || it.defaultItem }
                updatedServicesList.forEachIndexed { index, service ->
                    service.itemIndex = index
                }

                Log.d("CacheChecking", "setUpAndSortedServices --> updatedServicesList size: ${updatedServicesList.size}")
                // Update _servicesList dengan list yang sudah diubah
                _servicesList.updateOnMain(updatedServicesList)
                if (isFromListener) _isSetItemBundling.updateOnMain(true)
                Log.d("LifeAct", "observer 196: setUpAndSortedServices")
            }
        }
    }

    private suspend fun applyFieldService(service: Service, capsterSelected: UserEmployeeData) {
        service.apply {
            if (autoSelected || defaultItem) {
                serviceQuantity = 1 // Set default quantity
                addItemCounting(service.serviceName, "service", true)
            }

            // Perhitungan priceToDisplay pada service
            priceToDisplay = calculatePriceToDisplay(
                servicePrice,
                resultsShareFormat,
                resultsShareAmount,
                applyToGeneral,
                capsterSelected.uid
            )
        }
    }

    // Fungsi untuk memperbarui bundling quantity
    fun updateBundlingQuantity(index: Int, newQuantity: Int) {
        Log.d("ScanAll", "U2")
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        Log.d("TestAct", "updateServicesQuantity 217: old = ${_bundlingPackagesList.value?.get(index)?.bundlingQuantity} || new = $newQuantity")
//        val updatedList = _bundlingPackagesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(bundlingQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _bundlingPackagesList.value = updatedList
        viewModelScope.launch {
            stateMutex.withStateLock {
                val currentList = _indexBundlingChanged.value ?: mutableListOf()
                Log.d("TestDataChange", "isDataChanged 292: click btn bundling >> ${currentList.contains(index)}")
                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
                    currentList.add(index)
                    _indexBundlingChanged.value = currentList
                    Log.d("TestDataChange", "add index bundling 290: $index")
                }
                _isDataChanged.value = true
            }
        }
    }

    fun resetIndexBundlingChanged() {
        Log.d("ScanAll", "V2")
        viewModelScope.launch {
            stateMutex.withStateLock {
                _indexBundlingChanged.value = mutableListOf()
            }
        }
    }

    fun updateServicesQuantity(index: Int, newQuantity: Int) {
        Log.d("ScanAll", "W2")
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        Log.d("TestAct", "updateServicesQuantity 227: old = ${_servicesList.value?.get(index)?.serviceQuantity} || new = $newQuantity")
//        val updatedList = _servicesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(serviceQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _servicesList.value = updatedList
        viewModelScope.launch {
            stateMutex.withStateLock {
                val currentList = _indexServiceChanged.value ?: mutableListOf()
                Log.d("TestDataChange", "isDataChanged 315: click btn service >> ${currentList.contains(index)}")
                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
                    currentList.add(index)
                    _indexServiceChanged.value = currentList
                    Log.d("TestDataChange", "add index service 314: $index")
                }
                _isDataChanged.value = true
            }
        }
    }

    fun resetIndexServiceChanged() {
        Log.d("ScanAll", "X2")
        viewModelScope.launch {
            stateMutex.withStateLock {
                _indexServiceChanged.value = mutableListOf()
            }
        }
    }

    private fun calculatePriceToDisplay(
        basePrice: Int,
        resultsShareFormat: String,
        resultsShareAmount: Map<String, Any>?,
        applyToGeneral: Boolean,
        capsterUid: String
    ): Int {
        Log.d("ScanAll", "T2 || capsterUid: $capsterUid")
        if (resultsShareFormat == "fee" && capsterUid != "----------------") {
            val shareAmount = if (applyToGeneral) {
                (resultsShareAmount?.get("all") as? Number)?.toInt() ?: 0
            } else {
                (resultsShareAmount?.get(capsterUid) as? Number)?.toInt() ?: 0
            }
            return basePrice + shareAmount
        }
        return basePrice
    }

    fun clearState() {
        Log.d("ScanAll", "K2")
        viewModelScope.launch {
            _letsFilteringDataCustomer.value = null
//        _displayFilteredCustomerResult.value = true
            _displayAllDataToUI.value = null
        }
    }

    fun clearAllData() {
        Log.d("ScanAll", "Z2")
        Log.d("CacheChecking", "clearAllData")
        viewModelScope.launch {
            stateMutex.withStateLock {
                _isDataChanged.removeSource(_bundlingPackagesList)
                _isDataChanged.removeSource(_servicesList)

                // _itemSelectedCounting.value = 0
                _itemNameSelected.value = emptyList()
                _bundlingPackagesList.value = mutableListOf()
                _servicesList.value = mutableListOf()
                _indexBundlingChanged.value = mutableListOf()
                _indexServiceChanged.value = mutableListOf()

                _isDataChanged.value = false
                Log.d("TestDataChange", "isDataChanged 33:2 false by clearAllData")

                _isDataChanged.addSource(_bundlingPackagesList) {
                    _isDataChanged.value = true
                    Log.d("TestDataChange", "isDataChanged 336: re add source by bundling")
                    val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                            (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
                    _itemSelectedCounting.value = currentCount
                }
                _isDataChanged.addSource(_servicesList) {
                    _isDataChanged.value  = true
                    Log.d("TestDataChange", "isDataChanged 343: re add source by service")
                    val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                            (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
                    _itemSelectedCounting.value = currentCount
                }
            }
        }

        Log.d("TestDataChange", "observer 257: clearAllData")
    }


}


