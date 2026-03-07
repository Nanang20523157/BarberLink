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
import com.example.barberlink.Repository.BookingRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SharedReserveViewModel(
    private val repository: BookingRepository
) : ViewModel() {

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

    val itemSelectedCounting: LiveData<Int> = repository.itemSelectedCounting
    val itemNameSelected: LiveData<List<Pair<String, String>>> = repository.itemNameSelected
    val bundlingPackagesList: LiveData<List<BundlingPackage>> = repository.bundlingPackagesList
    val servicesList: LiveData<List<Service>> = repository.servicesList
    val outletList: LiveData<List<Outlet>> = repository.outletList
    val customerList: LiveData<List<UserCustomerData>> = repository.customerList
    val filteredCustomerList: LiveData<List<UserCustomerData>> = repository.filteredCustomerList
    val letsFilteringDataCustomer: LiveData<Boolean?> = repository.letsFilteringDataCustomer
    val displayFilteredCustomerResult: LiveData<Boolean?> = repository.displayFilteredCustomerResult
    val displayAllDataToUI: LiveData<Boolean?> = repository.displayAllDataToUI
    val indexBundlingChanged: LiveData<List<Int>> = repository.indexBundlingChanged
    val indexServiceChanged: LiveData<List<Int>> = repository.indexServiceChanged
    val snackBarMessage: LiveData<Event<String>> = repository.snackBarMessage
    val userFullname: LiveData<Event<String>> = repository.userFullname
    val userGender: LiveData<Event<String>> = repository.userGender
    val isDataChanged: LiveData<Boolean> = repository.isDataChanged
    val isSetItemBundling: LiveData<Boolean> = repository.isSetItemBundling
    val outletSelected: LiveData<Outlet> = repository.outletSelected
    val capsterSelected: LiveData<UserEmployeeData?> = repository.capsterSelected
    val customerSelected: LiveData<UserCustomerData?> = repository.customerSelected

    fun setOutletSelected(outlet: Outlet) {
        viewModelScope.launch {
            Logger.d("DataSync", "outlet selected: ${outlet.outletName}")
            repository.updateOutletSelected(outlet)
        }
    }

    fun setOutletList(outletList: List<Outlet>) {
        viewModelScope.launch {
            Logger.d("DataSync", "setOutletList: ${outletList.size}")
            repository.updateOutletList(outletList)
        }
    }

    fun setCapsterSelected(capster: UserEmployeeData?) {
        viewModelScope.launch {
            Logger.d("DataSync", "A1 || capster: ${capster?.uid}")
            repository.updateCapsterSelected(capster)
        }
    }

    fun setCustomerSelected(customer: UserCustomerData?) {
        viewModelScope.launch {
            Logger.d("DataSync", "A3 || customer: ${customer?.fullname}")
            repository.updateCustomerSelected(customer)
        }
    }

    fun showSnackBarToAll(fullname: String, gender: String, message: String) {
        viewModelScope.launch {
            Logger.d("DataSync", "AddNewCustomerSnackbar fullname: $fullname || gender: $gender || message: $message")
            repository.updateSnackbarToAll(fullname, gender, message)
        }
    }

    fun showSnackBarToSynchronization(message: String) {
        viewModelScope.launch {
            Logger.d("DataSync", "showSnackBarToSynchronization")
            repository.updateSnackbarMessage(message)
        }
    }

    fun addCustomerData(customer: UserCustomerData) {
        Log.d("ScanAll", "D2")
        viewModelScope.launch {
            customerMutex.withStateLock {
                val currentList = customerList.value.orEmpty().toMutableList()

                // Cek apakah customer dengan UID yang sama sudah ada
                val alreadyExists = currentList.any { it.uid == customer.uid }
                if (!alreadyExists) {
                    currentList.add(customer)
                    Logger.d("DataSync", "addCustomerData --> customerList size: ${currentList.size}")
                    Log.d("BtnSaveChecking", "Button Save Clicked 6")
                    repository.updateCustomerList(currentList.sortedByDescending { it.lastReserve }.toMutableList())
                } else {
                    Logger.d("DataSync", "Customer dengan UID ${customer.uid} sudah ada, tidak ditambahkan ulang.")
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
            val updatedCustomerList = customerList.value?.toMutableList() ?: mutableListOf()
            val updatedFilteredList = filteredCustomerList.value?.toMutableList() ?: mutableListOf()
            Logger.d("DataSync", "setCustomerList --> new size: ${newCustomerList.size} XXX isFromListener: $isFromListener")

            if (!isFromListener) {
                Logger.d("DataSync", "updatedCustomerList.addAll(newCustomerList)")
                updatedCustomerList.clear()
                updatedCustomerList.addAll(newCustomerList)
            } else {
                newCustomerList.forEach { newCustomerData ->
                    val existingCustomer = updatedCustomerList.find { it.uid == newCustomerData.uid }
                    if (existingCustomer != null) {
                        Logger.d("DataSync", "if (existingCustomer != null) ${newCustomerData.phone}")
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
                        updatedCustomerList.add(newCustomerData)

                        if (newCustomerData.phone.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                            Logger.d("DataSync", "<<<!!!>>> if (existingCustomer == null) data baru dengan query yang cocok ditemukan: ${newCustomerData.phone}")
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
                            Logger.d("DataSync", "<<<___>>> data baru dengan query yang cocok tidak ditemukan: ${newCustomerData.phone}")
                        }
                    }
                }

                val customerToRemove = updatedCustomerList.filterNot { updated ->
                    newCustomerList.any { it.uid == updated.uid }
                }
                if (customerToRemove.isNotEmpty()) {
                    updatedCustomerList.removeAll(customerToRemove)
                    updatedFilteredList.removeAll(customerToRemove)
                }
            }

            repository.updateCustomerList(updatedCustomerList)
            if (isFromListener) {
                // Ambil item yang selected
                val selectedItems = updatedFilteredList.find { it.dataSelected }
                selectedItems?.let { repository.updateCustomerSelected(it) }
                repository.updateFilteredCustomerList(updatedFilteredList.take(10))
                // _letsFilteringDataCustomer.updateOnMain(false)
                Logger.d("DataSync", "_displayFilteredCustomerResult.updateOnMain(true)")
                repository.updateDisplayFilteredCustomerResult(true)
            }
        }
    }

    fun updateCustomerData(customer: UserCustomerData) {
        viewModelScope.launch {
            Log.d("ScanAll", "G2")
            val updatedList = customerList.value.orEmpty().toMutableList()
            val index = updatedList.indexOfFirst { it.uid == customer.uid }
            if (index != -1) {
                updatedList[index] = customer
                Logger.d("DataSync", "fun updateCustomerData --> customerList size: ${updatedList.size}")
                repository.updateCustomerList(updatedList.sortedByDescending { it.lastReserve }.toMutableList())
            }
        }
    }

    fun triggerFilteringDataCustomer(displayAllData: Boolean) {
        Logger.d("DataSync", "fun triggerFilteringDataCustomer")
        viewModelScope.launch {
            Logger.d("DataSync", "H2 == ${letsFilteringDataCustomer.value}")
            repository.updateLetsFilteringDataCustomer(displayAllData)
        }
    }

    fun setFilteredCustomerList(filteredResult: List<UserCustomerData>) {
        Log.d("ScanAll", "I2")
        viewModelScope.launch {
            Logger.d("DataSync", "setFilteredCustomerList --> filteredCustomerList size: ${filteredResult.size}")
            repository.updateFilteredCustomerList(filteredResult)
        }
    }

    fun updateDataCustomerOnly() {
        viewModelScope.launch {
            Logger.d("DataSync", "fun updateDataCustomerOnly()")
            repository.updateDisplayFilteredCustomerResult(true)
        }
    }

    fun displayAllDataToUI(value: Boolean) {
        viewModelScope.launch {
            Logger.d("DataSync", "fun displayAllDataToUI()")
            repository.updateDisplayAllDataToUI(value)
        }
    }

    fun addItemSelectedCounting(name: String, category: String, includeMutex: Boolean) {
        viewModelScope.launch {
            Logger.d("DataSync", "fun addItemSelectedCounting()")
            addItemCounting(name, category, includeMutex)
        }
    }

    // Fungsi untuk menambah item yang dipilih
    private suspend fun addItemCounting(name: String, category: String, includeMutex: Boolean) {
        Log.d("ScanAll", "M2")
        val thisAction = suspend {
            // val newCount = (_itemSelectedCounting.value ?: 0) + 1
            val count = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            val currentList = itemNameSelected.value?.toMutableList() ?: mutableListOf()
            if (currentList.none { it.first == name }) {
                currentList.add(0, name to category)
            }

            Logger.d("DataSync", "addItemSelectedCounting 52: $count || $category")
            // Memperbarui LiveData di main thread
            // _itemSelectedCounting.value = newCount
            repository.updateItemNameSelected(currentList)
            repository.updateSelectedCounting(count)
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
                val currentList = itemNameSelected.value?.toMutableList()
                if (!currentList.isNullOrEmpty() && removeName) {
                    val itemToRemove = currentList.find { it.first == name }
                    itemToRemove?.let {
                        currentList.remove(it)
                        repository.updateItemNameSelected(currentList)
                    }
                }

                val count = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                        (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
                // var currentCount = _itemSelectedCounting.value ?: 0
                // if (currentCount > 0) {
                //    currentCount--
                //    _itemSelectedCounting.value = currentCount
                // }
                Logger.d("DataSync", "removeItemSelectedByName 70: $count || $name || $removeName")
                repository.updateSelectedCounting(count)
            }
        }
    }

    // Fungsi untuk mereset semua item yang dipilih
    fun resetAllItem() {
        viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock {
                // _itemSelectedCounting.value = 0
                repository.updateItemNameSelected(emptyList())

                servicesList.value?.forEach { service ->
                    service.apply {
                        serviceQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(serviceName, "service", true)
                    }
                }
                Log.d("CacheChecking", "resetAllItem --> serviceList size: ${servicesList.value?.size.toString()}")
                // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())

                bundlingPackagesList.value?.forEach { bundling ->
                    bundling.apply {
                        bundlingQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(packageName, "package", true)
                    }
                }
                Log.d("CacheChecking", "resetAllItem --> bundlingList size: ${bundlingPackagesList.value?.size.toString()}")
                // _bundlingPackagesList.updateIfNeeded(_bundlingPackagesList.value ?: emptyList())
                repository.updateServicesList(servicesList.value ?: emptyList())
                repository.updateBundlingPackagesList(bundlingPackagesList.value ?: emptyList())
                Logger.d("DataSync", "observer 95: resetAllItem")
                repository.updateIsDataChanged(true)
                Log.d("TestDataChange", "isDataChanged 120: resetAllItem")
            }
        }
    }

    // Fungsi untuk mereset semua layanan (kategori "service")
    fun resetAllServices() {
        viewModelScope.launch(Dispatchers.Default) {
            stateMutex.withStateLock {
                Log.d("ScanAll", "P2")
                val currentSelected = itemNameSelected.value.orEmpty()
                    .filterNot { it.second == "service" }
                repository.updateItemNameSelected(currentSelected)

                // val itemCount = _bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0
                // _itemSelectedCounting.value = itemCount

                servicesList.value?.forEach { service ->
                    service.apply {
                        serviceQuantity = if (defaultItem) 1 else 0
                        if (defaultItem) addItemCounting(serviceName, "service", true)
                    }
                }

                // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())
                Log.d("CacheChecking", "resetAllServices --> serviceList size: ${servicesList.value?.size.toString()}")
                repository.updateServicesList(servicesList.value ?: emptyList())
                Logger.d("DataSync", "observer 114: resetAllServices")
                repository.updateIsDataChanged(true)
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
                val updatedBundlingList = bundlingPackagesList.value?.toMutableList() ?: mutableListOf()
                Logger.d("DataSync", "setUpAndSortedBundling --> oldBundlinglist size: ${updatedBundlingList.size}")
                Logger.d("DataSync", "setUpAndSortedBundling --> currentBundlinglist size: ${currentBundlingList.size}")

                if (updatedBundlingList.isEmpty()) {
                    // Jika oldBundlingList null, gunakan currentBundlingList
                    currentBundlingList.forEach { bundling ->
                        applyFieldBundling(bundling, capsterSelected, isFromListener)
                    }
                    Logger.d("DataSync", "fun setUpAndSortedBundling >> updatedBundlingList.isEmpty()")
                    updatedBundlingList.addAll(currentBundlingList) // Tambahkan semua item dari currentBundlingList
                } else {
                    currentBundlingList.forEach { currentBundling ->
                        // Cek apakah bundling dengan UID yang sama sudah ada
                        val existingBundling = updatedBundlingList.find { it.uid == currentBundling.uid }
                        if (existingBundling == null) {
                            Logger.d("DataSync", "if (existingBundling == null) data baru ditemukan: ${currentBundling.packageName}")
                            // Jika tidak ada, tambahkan bundling baru
                            applyFieldBundling(currentBundling, capsterSelected, isFromListener)
                            updatedBundlingList.add(currentBundling)
                        } else {
                            Logger.d("DataSync", "if (existingBundling != null) data sudah ada: ${currentBundling.packageName}")
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

                                listItemDetails = servicesList.value?.filter { service ->
                                    listItems.contains(service.uid)
                                } ?: emptyList()
                                Logger.d("DataSync", "setUpAndSortedBundling --> listservice contain 1: ${listItemDetails?.size} || $packageName")

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
                repository.updateBundlingPackagesList(updatedBundlingList)
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
                listItemDetails = servicesList.value?.filter { service ->
                    listItems.contains(service.uid)
                } ?: emptyList()
                Logger.d("DataSync", "setUpAndSortedBundling --> listservice contain 3: ${listItemDetails?.size} || ${bundling.packageName}")
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
                val listBundling = bundlingPackagesList.value ?: emptyList()
                Logger.d("DataSync", "setServiceBundlingList --> listbundling size: ${listBundling.size}")
                if (listBundling.isNotEmpty()) {
                    listBundling.onEach { bundling ->
                        val serviceBundlingList = servicesList.value?.filter { service ->
                            bundling.listItems.contains(service.uid)
                        } ?: emptyList() // Jika null, gunakan list kosong

                        bundling.listItemDetails = serviceBundlingList
                        Logger.d("DataSync", "setServiceBundlingList --> listservice contain 2: ${serviceBundlingList.size} || ${bundling.packageName}")
                    }

                    // _bundlingPackagesList.updateIfNeeded(listBundling)
                    repository.updateBundlingPackagesList(listBundling)
                }

                repository.updateIsSetItemBundling(false)
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
                val updatedServicesList = servicesList.value?.toMutableList() ?: mutableListOf()
                Logger.d("DataSync", "setUpAndSortedServices --> oldServiceList size: ${updatedServicesList.size}")
                Logger.d("DataSync", "setUpAndSortedServices --> currentServicesList size: ${currentServicesList.size}")

                if (updatedServicesList.isEmpty()) {
                    Logger.d("DataSync", "if (updatedServicesList.isEmpty())")
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
                            Logger.d("DataSync", "if (existingService == null) data baru ditemukan: ${currentService.serviceName}")
                            // Jika tidak ada, tambahkan service baru
                            applyFieldService(currentService, capsterSelected)
                            updatedServicesList.add(currentService)
                        } else {
                            Logger.d("DataSync", "if (existingService != null) data sudah ada: ${currentService.serviceName}")
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
                repository.updateServicesList(updatedServicesList)
                if (isFromListener) repository.updateIsSetItemBundling(true)
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
        Log.d("TestAct", "updateServicesQuantity 217: old = ${bundlingPackagesList.value?.get(index)?.bundlingQuantity} || new = $newQuantity")
//        val updatedList = _bundlingPackagesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(bundlingQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _bundlingPackagesList.value = updatedList
        viewModelScope.launch {
            stateMutex.withStateLock {
                val currentList = indexBundlingChanged.value?.toMutableList() ?: mutableListOf()
                Log.d("TestDataChange", "isDataChanged 292: click btn bundling >> ${currentList.contains(index)}")
                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
                    Logger.d("DataSync", "add index bundling 290: $index")
                    currentList.add(index)
                    repository.updateIndexBundlingChanged(currentList)
                }
                repository.updateIsDataChanged(true)
            }
        }
    }

    fun resetIndexBundlingChanged() {
        viewModelScope.launch {
            stateMutex.withStateLock {
                Logger.d("DataSync", "resetIndexBundlingChanged()")
                repository.updateIndexBundlingChanged(mutableListOf())
            }
        }
    }

    fun updateServicesQuantity(index: Int, newQuantity: Int) {
        Log.d("ScanAll", "W2")
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        Log.d("TestAct", "updateServicesQuantity 227: old = ${servicesList.value?.get(index)?.serviceQuantity} || new = $newQuantity")
//        val updatedList = _servicesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(serviceQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _servicesList.value = updatedList
        viewModelScope.launch {
            stateMutex.withStateLock {
                val currentList = indexServiceChanged.value?.toMutableList() ?: mutableListOf()
                Log.d("TestDataChange", "isDataChanged 315: click btn service >> ${currentList.contains(index)}")
                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
                    Logger.d("DataSync", "add index service 314: $index")
                    currentList.add(index)
                    repository.updateIndexServiceChanged(currentList)
                }
                repository.updateIsDataChanged(true)
            }
        }
    }

    fun resetIndexServiceChanged() {
        viewModelScope.launch {
            stateMutex.withStateLock {
                Logger.d("DataSync", "resetIndexServiceChanged()")
                repository.updateIndexServiceChanged(mutableListOf())
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
        Logger.d("DataSync", "T2 || capsterUid: $capsterUid")
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
        viewModelScope.launch {
            Logger.d("DataSync", "clearState")
            repository.updateLetsFilteringDataCustomer(null)
//        _displayFilteredCustomerResult.value = true
            repository.updateDisplayAllDataToUI(null)
        }
    }

//    fun clearAllData() {
//        Log.d("ScanAll", "Z2")
//        viewModelScope.launch {
//            stateMutex.withStateLock {
//                Logger.d("DataSync", "clearAllData")
//                _isDataChanged.removeSource(_bundlingPackagesList)
//                _isDataChanged.removeSource(_servicesList)
//
//                // _itemSelectedCounting.value = 0
//                _itemNameSelected.value = emptyList()
//                _bundlingPackagesList.value = mutableListOf()
//                _servicesList.value = mutableListOf()
//                _indexBundlingChanged.value = mutableListOf()
//                _indexServiceChanged.value = mutableListOf()
//
//                _isDataChanged.value = false
//                Log.d("TestDataChange", "isDataChanged 33:2 false by clearAllData")
//
//                _isDataChanged.addSource(_bundlingPackagesList) {
//                    _isDataChanged.value = true
//                    Log.d("TestDataChange", "isDataChanged 336: re add source by bundling")
//                    val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
//                            (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
//                    _itemSelectedCounting.value = currentCount
//                }
//                _isDataChanged.addSource(_servicesList) {
//                    _isDataChanged.value  = true
//                    Log.d("TestDataChange", "isDataChanged 343: re add source by service")
//                    val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
//                            (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
//                    _itemSelectedCounting.value = currentCount
//                }
//            }
//        }
//
//        Log.d("TestDataChange", "observer 257: clearAllData")
//    }


}


