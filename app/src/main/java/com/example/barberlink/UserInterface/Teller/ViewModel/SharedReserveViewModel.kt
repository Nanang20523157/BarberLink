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
//    private val _indexBundlingChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
//    val indexBundlingChanged: LiveData<MutableList<Int>> = _indexBundlingChanged

//    private val _indexServiceChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
//    val indexServiceChanged: LiveData<MutableList<Int>> = _indexServiceChanged

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

    suspend fun setOutletSelected(outlet: Outlet) {
        Log.d("ScanAll", "A2")
        withContext(Dispatchers.Main) {
            _outletSelected.value = outlet
        }
    }

    suspend fun setOutletList(outletList: List<Outlet>) {
        withContext(Dispatchers.Main) {
            _outletList.value = outletList
        }
    }

    suspend fun setCapsterSelected(capster: UserEmployeeData?) {
        Log.d("ScanAll", "A1")
        withContext(Dispatchers.Main) {
            _capsterSelected.value = capster
        }
    }

    suspend fun setCustomerSelected(customer: UserCustomerData?) {
        Log.d("ScanAll", "A3")
        withContext(Dispatchers.Main) {
            _customerSelected.value = customer
        }
    }

    suspend fun showSnackBarToAll(fullname: String, gender: String, message: String) {
        Log.d("ScanAll", "B2")
        withContext(Dispatchers.Main) {
            _userFullname.value = Event(fullname)
            _userGender.value = Event(gender)
            _snackBarMessage.value = Event(message)
        }
    }

    suspend fun showSnackBarToSynchronization(message: String) {
        Log.d("ScanAll", "C2")
        withContext(Dispatchers.Main) {
            _snackBarMessage.value = Event(message)
        }
    }

    suspend fun addCustomerData(customer: UserCustomerData) {
        withContext(Dispatchers.Main) {
            customerMutex.withStateLock {
                val currentList = _customerList.value.orEmpty()

                if (currentList.any { it.uid == customer.uid }) {
                    Log.d("CacheChecking", "Customer ${customer.uid} already exists, skip add.")
                    return@withStateLock
                }

                val updatedList = currentList
                    .toMutableList()
                    .apply { add(customer) }
                    .sortedByDescending { it.lastReserve }

                _customerList.value = updatedList

                Log.d("CacheChecking", "addCustomerData --> customerList size: ${updatedList.size}")
            }
        }
    }

    suspend fun setCustomerList(
        lowerCaseQuery: String,
        newCustomerList: List<UserCustomerData>,
        isFromListener: Boolean = false,
    ) {
        withContext(Dispatchers.Default) {
            customerMutex.withStateLock {
                val currentList = _customerList.value.orEmpty()
                val currentFiltered = _filteredCustomerList.value.orEmpty()

                val (updatedCustomerList, updatedFilteredList) = cloneSelectionKeepingSharedReference(currentList, currentFiltered, false)
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
                    _displayFilteredCustomerResult.value = true
                }
            }
        }
    }

    suspend fun updateCustomerData(customer: UserCustomerData) {
        withContext(Dispatchers.Main) {
            customerMutex.withStateLock {
                val current = _customerList.value.orEmpty()
                val index = current.indexOfFirst { it.uid == customer.uid }

                if (index != -1) {
                    val updatedList = current.toMutableList()
                    updatedList[index] = customer

                    // cukup sorted list tanpa perlu toMutableList lagi
                    _customerList.value = updatedList.sortedByDescending { it.lastReserve }

                    Log.d("CacheChecking", "updateCustomerData --> size: ${updatedList.size}")
                }
            }
        }
    }

    suspend fun triggerFilteringDataCustomer(displayAllData: Boolean) {
        Log.d("ScanAll", "H2")
        withContext(Dispatchers.Main) {
            _letsFilteringDataCustomer.value = displayAllData
        }
    }

    suspend fun setFilteredCustomerList(filteredResult: List<UserCustomerData>) {
        Log.d("ScanAll", "I2")
        Log.d("CacheChecking", "setFilteredCustomerList --> filteredCustomerList size: ${filteredResult.size}")
        withContext(Dispatchers.Main) {
            customerMutex.withStateLock {
                _filteredCustomerList.value = filteredResult
            }
        }
    }

    suspend fun updateDataCustomerOnly() {
        Log.d("ScanAll", "J2")
        withContext(Dispatchers.Main) {
            _displayFilteredCustomerResult.value = true
        }
    }

    suspend fun displayAllDataToUI(value: Boolean) {
        Log.d("ScanAll", "L2")
        withContext(Dispatchers.Main) {
            _displayAllDataToUI.value = value
        }
    }

    // Fungsi untuk menambah item yang dipilih
    suspend fun addItemSelectedCounting(name: String, category: String) {
        Log.d("ScanAll", "M2")
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
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
        }
    }

    // Fungsi untuk menghapus item berdasarkan nama
    suspend fun removeItemSelectedByName(name: String, removeName: Boolean) {
        Log.d("ScanAll", "N2")
        withContext(Dispatchers.Default) {
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
    suspend fun resetAllItem() {
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
                _itemNameSelected.updateOnMain(emptyList())
                // 🔹 SAFE CLONE service list
                val updatedServices = _servicesList.value.orEmpty().map { service ->
                    service.copy(
                        serviceQuantity = if (service.defaultItem) 1 else 0
                    ).also {
                        if (service.defaultItem) addItemSelectedCounting(service.serviceName, "service")
                    }
                }

                // 🔹 SAFE CLONE bundling list
                val updatedBundling = _bundlingPackagesList.value.orEmpty().map { bundling ->
                    bundling.copy(
                        bundlingQuantity = if (bundling.defaultItem) 1 else 0
                    ).also {
                        if (bundling.defaultItem) addItemSelectedCounting(bundling.packageName, "package")
                    }
                }

                // 🔁 Assign list baru ke LiveData → Adapter akan update clean
                _servicesList.updateOnMain(updatedServices)
                _bundlingPackagesList.updateOnMain(updatedBundling)

                _isDataChanged.updateOnMain(true)
            }
        }
    }

    // Fungsi untuk mereset semua layanan (kategori "service")
    suspend fun resetAllServices() {
        withContext(Dispatchers.Default) {
            stateMutex.withStateLock {
                val currentSelected = _itemNameSelected.value.orEmpty()
                    .filterNot { it.second == "service" }
                _itemNameSelected.updateOnMain(currentSelected)

                val updatedServices = _servicesList.value.orEmpty().map { service ->
                    service.copy(
                        serviceQuantity = if (service.defaultItem) 1 else 0
                    ).also {
                        if (service.defaultItem) addItemSelectedCounting(service.serviceName, "service")
                    }
                }

                _servicesList.updateOnMain(updatedServices)

                _isDataChanged.updateOnMain(true)
            }
        }
    }

    suspend fun setUpAndSortedBundling(
        currentBundlingList: MutableList<BundlingPackage>,
        capsterSelected: UserEmployeeData
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
                        applyFieldBundling(bundling, capsterSelected)
                    }
                    updatedBundlingList.addAll(currentBundlingList) // Tambahkan semua item dari currentBundlingList
                } else {
                    currentBundlingList.forEach { currentBundling ->
                        // Cek apakah bundling dengan UID yang sama sudah ada
                        val index = updatedBundlingList.indexOfFirst { it.uid == currentBundling.uid }
                        if (index != -1) {
                            val oldItem = updatedBundlingList[index]
                            // Jika ada, perbarui properti dari existing item
                            updatedBundlingList[index] = oldItem.copy(
                                accumulatedPrice = currentBundling.accumulatedPrice,
                                applyToGeneral = currentBundling.applyToGeneral,
                                autoSelected = currentBundling.autoSelected,
                                defaultItem = currentBundling.defaultItem,
                                listItems = currentBundling.listItems,
                                packageCounting = currentBundling.packageCounting,
                                packageDesc = currentBundling.packageDesc,
                                packageDiscount = currentBundling.packageDiscount,
                                packageName = currentBundling.packageName,
                                packagePrice = currentBundling.packagePrice,
                                packageRating = currentBundling.packageRating,
                                resultsShareAmount = currentBundling.resultsShareAmount,
                                resultsShareFormat = currentBundling.resultsShareFormat,
                                rootRef = currentBundling.rootRef,
                                uid = currentBundling.uid,

                                listItemDetails = _servicesList.value?.filter { service ->
                                    currentBundling.listItems.contains(service.uid)
                                } ?: emptyList(),

                                priceToDisplay = calculatePriceToDisplay(
                                    currentBundling.packagePrice,
                                    currentBundling.resultsShareFormat,
                                    currentBundling.resultsShareAmount,
                                    currentBundling.applyToGeneral,
                                    capsterSelected.uid
                                )
                            )
                        } else {
                            // Jika tidak ada, tambahkan bundling baru
                            applyFieldBundling(currentBundling, capsterSelected)
                            updatedBundlingList.add(currentBundling)
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
                val finalListData = updatedBundlingList.mapIndexed { index, bundlingPackage ->
                    if (bundlingPackage.itemIndex != index) bundlingPackage.copy(itemIndex = index)
                    else bundlingPackage
                }

                Log.d("CacheChecking", "setUpAndSortedBundling --> updatedBundlingList size: ${updatedBundlingList.size}")
                // Update _bundlingPackagesList dengan referensi yang telah diperbarui
                _bundlingPackagesList.updateOnMain(finalListData)
            }
        }
        Log.d("LifeAct", "observer 158: setUpAndSortedBundling")
    }

    private suspend fun applyFieldBundling(bundling: BundlingPackage, capsterSelected: UserEmployeeData) {
        // Pastikan data yang di apply adalah data yang tidak memili referensi yang sama dengan yang ada di adapter
        bundling.apply {
            if (autoSelected || defaultItem) {
                bundlingQuantity = 1 // Set default quantity
                addItemSelectedCounting(packageName, "package")
            }
            // Atur properti lainnya
            listItemDetails = _servicesList.value?.filter { service ->
                listItems.contains(service.uid)
            } ?: emptyList()
            Log.d("CacheChecking", "setUpAndSortedBundling --> listservice contain 3: ${listItemDetails?.size} || ${bundling.packageName}")

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
                    val updatedList = listBundling.map { bundling ->
                        bundling.copy(
                            listItemDetails = _servicesList.value
                                ?.filter { bundling.listItems.contains(it.uid) }
                                ?: emptyList()
                        )
                    }

                    // _bundlingPackagesList.updateIfNeeded(listBundling)
                    _bundlingPackagesList.updateOnMain(updatedList)
                }

                _isSetItemBundling.updateOnMain(false)
            }
        }
    }

    suspend fun setUpAndSortedServices(
        currentServicesList: MutableList<Service>,
        capsterSelected: UserEmployeeData
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
                        val index = updatedServicesList.indexOfFirst { it.uid == currentService.uid }
                        if (index != -1) {
                            val oldItem = updatedServicesList[index]
                            // ⛔ do NOT mutate oldItem → instead create safe updated copy
                            updatedServicesList[index] = oldItem.copy(
                                applyToGeneral = currentService.applyToGeneral,
                                autoSelected = currentService.autoSelected,
                                categoryDetail = currentService.categoryDetail,
                                defaultItem = currentService.defaultItem,
                                freeOfCharge = currentService.freeOfCharge,
                                resultsShareAmount = currentService.resultsShareAmount,
                                resultsShareFormat = currentService.resultsShareFormat,
                                rootRef = currentService.rootRef,
                                serviceCategory = currentService.serviceCategory,
                                serviceCounting = currentService.serviceCounting,
                                serviceDesc = currentService.serviceDesc,
                                serviceIcon = currentService.serviceIcon,
                                serviceImg = currentService.serviceImg,
                                serviceName = currentService.serviceName,
                                servicePrice = currentService.servicePrice,
                                serviceRating = currentService.serviceRating,
                                uid = currentService.uid,

                                // 🔹 keep UI state
                                serviceQuantity = oldItem.serviceQuantity,

                                // 🔹 recalc dynamic field
                                priceToDisplay = calculatePriceToDisplay(
                                    currentService.servicePrice,
                                    currentService.resultsShareFormat,
                                    currentService.resultsShareAmount,
                                    currentService.applyToGeneral,
                                    capsterSelected.uid
                                )
                            )
                        } else {
                            // Jika tidak ada, tambahkan service baru
                            applyFieldService(currentService, capsterSelected)
                            updatedServicesList.add(currentService)
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
                val finalListData = updatedServicesList.mapIndexed { index, service ->
                    if (service.itemIndex != index) service.copy(itemIndex = index)
                    else service
                }

                Log.d("CacheChecking", "setUpAndSortedServices --> updatedServicesList size: ${updatedServicesList.size}")
                // Update _servicesList dengan list yang sudah diubah
                _servicesList.updateOnMain(finalListData)

                _isSetItemBundling.updateOnMain(true)
            }
        }
        Log.d("LifeAct", "observer 196: setUpAndSortedServices")
    }

    private suspend fun applyFieldService(service: Service, capsterSelected: UserEmployeeData) {
        // Pastikan data yang di apply adalah data yang tidak memili referensi yang sama dengan yang ada di adapter
        service.apply {
            if (autoSelected || defaultItem) {
                serviceQuantity = 1 // Set default quantity
                addItemSelectedCounting(service.serviceName, "service")
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
    suspend fun updateBundlingQuantity(bundling: BundlingPackage) {
        Log.d("ScanAll", "U2")
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        // Memperbarui LiveData di main thread
//        _bundlingPackagesList.value = updatedList
        withContext(Dispatchers.Main) {
            stateMutex.withStateLock {
                val updatedList = _bundlingPackagesList.value?.toMutableList()?.apply {
                    val index = this.indexOfFirst { it.uid == bundling.uid }
                    if (index != -1) {
                        this[index] = bundling
                    }
                }
                _bundlingPackagesList.value = updatedList
//                val currentList = _indexBundlingChanged.value ?: mutableListOf()
//                Log.d("TestDataChange", "isDataChanged 292: click btn bundling >> ${currentList.contains(index)}")
//                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
//                    currentList.add(index)
//                    _indexBundlingChanged.value = currentList
//                    Log.d("TestDataChange", "add index bundling 290: $index")
//                }
                _isDataChanged.value = true
            }
        }
    }

//    suspend fun resetIndexBundlingChanged() {
//        Log.d("ScanAll", "V2")
//        withContext(Dispatchers.Main) {
//            stateMutex.withStateLock {
//                _indexBundlingChanged.value = mutableListOf()
//            }
//        }
//    }

    suspend fun updateServicesQuantity(service: Service) {
        Log.d("ScanAll", "W2")
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        // Memperbarui LiveData di main thread
//        _servicesList.value = updatedList
        withContext(Dispatchers.Main) {
            stateMutex.withStateLock {
                val updatedList = _servicesList.value?.toMutableList()?.apply {
                    val index = this.indexOfFirst { it.uid == service.uid }
                    if (index != -1) {
                        this[index] = service
                    }
                }
                _servicesList.value = updatedList
//                val currentList = _indexServiceChanged.value ?: mutableListOf()
//                Log.d("TestDataChange", "isDataChanged 315: click btn service >> ${currentList.contains(index)}")
//                if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
//                    currentList.add(index)
//                    _indexServiceChanged.value = currentList
//                    Log.d("TestDataChange", "add index service 314: $index")
//                }
                _isDataChanged.value = true
            }
        }
    }

//    suspend fun resetIndexServiceChanged() {
//        Log.d("ScanAll", "X2")
//        withContext(Dispatchers.Main) {
//            stateMutex.withStateLock {
//                _indexServiceChanged.value = mutableListOf()
//            }
//        }
//    }

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

    fun cloneSelectionKeepingSharedReference(
        customerList: List<UserCustomerData>,
        filteredList: List<UserCustomerData>,
        isReset: Boolean
    ): Pair<MutableList<UserCustomerData>, MutableList<UserCustomerData>> {
        // Cache clone per UID untuk memastikan referensi tetap sama di semua list
        val clonedMap = mutableMapOf<String, UserCustomerData>()

        fun cloneIfData(item: UserCustomerData): UserCustomerData {
            return if (isReset) {
                clonedMap.getOrPut(item.uid) { item.copy(dataSelected = false) }
            } else {
                clonedMap.getOrPut(item.uid) { item.copy() }
            }
        }

        // Process List (clone only where needed)
        val newCustomerList = customerList.map(::cloneIfData)
        val newFilteredList = filteredList.map(::cloneIfData)

        return Pair(newCustomerList.toMutableList(), newFilteredList.toMutableList())
    }

    fun clearState() {
        viewModelScope.launch {
            Log.d("ScanAll", "K2")
            _letsFilteringDataCustomer.value = null
//        _displayFilteredCustomerResult.value = true
            _displayAllDataToUI.value = null
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            Log.d("ScanAll", "Z2")
            Log.d("CacheChecking", "clearAllData")
            _isDataChanged.removeSource(_bundlingPackagesList)
            _isDataChanged.removeSource(_servicesList)

            // _itemSelectedCounting.value = 0
            _itemNameSelected.value = emptyList()
            _bundlingPackagesList.value = mutableListOf()
            _servicesList.value = mutableListOf()
//            _indexBundlingChanged.value = mutableListOf()
//            _indexServiceChanged.value = mutableListOf()

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

            Log.d("TestDataChange", "observer 257: clearAllData")
        }
    }

    private fun <T> MutableLiveData<T>.updateIfNeeded(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

}


