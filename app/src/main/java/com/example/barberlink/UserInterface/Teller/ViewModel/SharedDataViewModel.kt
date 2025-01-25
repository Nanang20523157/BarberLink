package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Employee
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserCustomerData

class SharedDataViewModel : ViewModel() {

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

    private val _customerList = MutableLiveData<List<UserCustomerData>>().apply { value = listOf() }
    val customerList: LiveData<List<UserCustomerData>> = _customerList

    private val _filteredCustomerList = MutableLiveData<List<UserCustomerData>>().apply { value = listOf() }
    val filteredCustomerList: LiveData<List<UserCustomerData>> = _filteredCustomerList

    private val _letsFilteringDataCustomer = MutableLiveData<Boolean?>()
    val letsFilteringDataCustomer: LiveData<Boolean?> = _letsFilteringDataCustomer

    private val _displayFilteredCustomerResult = MutableLiveData<Boolean?>()
    val displayFilteredCustomerResult: LiveData<Boolean?> = _displayFilteredCustomerResult

    private val _displayAllDataToUI = MutableLiveData<Boolean?>()
    val displayAllDataToUI: LiveData<Boolean?> = _displayAllDataToUI

    // Properti LiveData untuk daftar Int dengan nilai default list kosong
    private val _indexBundlingChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
    val indexBundlingChanged: LiveData<MutableList<Int>> = _indexBundlingChanged

    private val _indexServiceChanged = MutableLiveData<MutableList<Int>>().apply { value = mutableListOf() }
    val indexServiceChanged: LiveData<MutableList<Int>> = _indexServiceChanged

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

    val listLock = Any()

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

    fun addCustomerData(customer: UserCustomerData) {
        synchronized(listLock) {
            val updatedList = _customerList.value.orEmpty().toMutableList()
            updatedList.add(customer)
            _customerList.value = updatedList.sortedByDescending { it.lastReserve }.toMutableList()
        }
    }

    fun setCustomerList(customerList: List<UserCustomerData>) {
        synchronized(listLock) {
            _customerList.value = customerList
        }
    }

    fun updateCustomerData(customer: UserCustomerData) {
        val updatedList = _customerList.value.orEmpty().toMutableList()
        val index = updatedList.indexOfFirst { it.uid == customer.uid }
        if (index != -1) {
            updatedList[index] = customer
            _customerList.value = updatedList.sortedByDescending { it.lastReserve }.toMutableList()
        }
    }

    fun triggerFilteringDataCustomer(withShimmer: Boolean) {
        _letsFilteringDataCustomer.value = withShimmer
    }

    fun setFilteredCustomerList(filteredResult: List<UserCustomerData>) {
        _filteredCustomerList.value = filteredResult
    }

    fun displayFilteredCustomerResult(withShimmer: Boolean) {
        _displayFilteredCustomerResult.value = withShimmer
    }

    fun clearState() {
        _letsFilteringDataCustomer.value = null
        _displayFilteredCustomerResult.value = null
        _displayAllDataToUI.value = null
    }

    fun displayAllDataToUI(value: Boolean) {
        _displayAllDataToUI.value = value
    }

    // Fungsi untuk menambah item yang dipilih
    fun addItemSelectedCounting(name: String, category: String) = synchronized(this) {
        // val newCount = (_itemSelectedCounting.value ?: 0) + 1
        val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()
        if (currentList.none { it.first == name }) {
            currentList.add(0, name to category)
        }

        // Memperbarui LiveData di main thread
        // _itemSelectedCounting.value = newCount
        _itemNameSelected.value = currentList
        _itemSelectedCounting.value = currentCount
        Log.d("TestAct", "addItemSelectedCounting 52: $currentCount || $category")
    }

    // Fungsi untuk menghapus item berdasarkan nama
    fun removeItemSelectedByName(name: String, removeName: Boolean) = synchronized(this) {
        val currentList = _itemNameSelected.value?.toMutableList()
        if (!currentList.isNullOrEmpty() && removeName) {
            val itemToRemove = currentList.find { it.first == name }
            itemToRemove?.let {
                currentList.remove(it)
                _itemNameSelected.value = currentList
            }
        }

        val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
        // var currentCount = _itemSelectedCounting.value ?: 0
        // if (currentCount > 0) {
        //    currentCount--
        //    _itemSelectedCounting.value = currentCount
        // }
        _itemSelectedCounting.value = currentCount
        Log.d("TestAct", "removeItemSelectedByName 70: $currentCount || $name || $removeName")
    }

    // Fungsi untuk mereset semua item yang dipilih
    fun resetAllItem() = synchronized(this) {
        // _itemSelectedCounting.value = 0
        _itemNameSelected.value = emptyList()

        _servicesList.value?.forEach { service ->
            service.apply {
                serviceQuantity = if (defaultItem) 1 else 0
                if (defaultItem) addItemSelectedCounting(serviceName, "service")
            }
        }
        // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())
        _servicesList.value = _servicesList.value

        _bundlingPackagesList.value?.forEach { bundling ->
            bundling.apply {
                bundlingQuantity = if (defaultItem) 1 else 0
                if (defaultItem) addItemSelectedCounting(packageName, "package")
            }
        }
        // _bundlingPackagesList.updateIfNeeded(_bundlingPackagesList.value ?: emptyList())
        _bundlingPackagesList.value = _bundlingPackagesList.value
        Log.d("LifeAct", "observer 95: resetAllItem")
        _isDataChanged.value = true
        Log.d("TestDataChange", "isDataChanged 120: resetAllItem")
    }

    // Fungsi untuk mereset semua layanan (kategori "service")
    fun resetAllServices() = synchronized(this) {
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()
        currentList.removeAll { it.second == "service" }
        _itemNameSelected.value = currentList

        // val itemCount = _bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0
        // _itemSelectedCounting.value = itemCount

        _servicesList.value?.forEach { service ->
            service.apply {
                serviceQuantity = if (defaultItem) 1 else 0
                if (defaultItem) addItemSelectedCounting(serviceName, "service")
            }
        }

        // _servicesList.updateIfNeeded(_servicesList.value ?: emptyList())
        _servicesList.value = _servicesList.value
        Log.d("LifeAct", "observer 114: resetAllServices")
        _isDataChanged.value = true
        Log.d("TestDataChange", "isDataChanged 142: resetAllServices")
    }

    fun setUpAndSortedBundling(
        currentBundlingList: MutableList<BundlingPackage>,
        capsterSelected: Employee,
        oldBundlingList: List<BundlingPackage>?
    ) = synchronized(this) {
        val updatedBundlingList = oldBundlingList?.toMutableList() ?: mutableListOf()

        if (oldBundlingList.isNullOrEmpty()) {
            // Jika oldBundlingList null, gunakan currentBundlingList
            currentBundlingList.forEach { bundling ->
                bundling.apply {
                    if (autoSelected || defaultItem) {
                        bundlingQuantity = 1 // Set default quantity
                        addItemSelectedCounting(packageName, "package")
                    }

                    // Atur properti lainnya
                    listItemDetails = _servicesList.value?.filter { service ->
                        listItems.contains(service.uid)
                    } ?: emptyList()

                    priceToDisplay = calculatePriceToDisplay(
                        packagePrice,
                        resultsShareFormat,
                        resultsShareAmount,
                        applyToGeneral,
                        capsterSelected.uid
                    )
                }
            }
            updatedBundlingList.addAll(currentBundlingList) // Tambahkan semua item dari currentBundlingList
        } else {
            // Jika oldBundlingList tidak null, perbarui properti dari existing item
            updatedBundlingList.forEach { existingBundling ->
                val matchingCurrentBundling = currentBundlingList.find { it.uid == existingBundling.uid }
                if (matchingCurrentBundling != null) {
                    existingBundling.apply {
                        // Perbarui semua properti dari matching item tanpa mengganti referensi
                        accumulatedPrice = matchingCurrentBundling.accumulatedPrice
                        applyToGeneral = matchingCurrentBundling.applyToGeneral
                        autoSelected = matchingCurrentBundling.autoSelected
                        defaultItem = matchingCurrentBundling.defaultItem
                        listItems = matchingCurrentBundling.listItems
                        packageCounting = matchingCurrentBundling.packageCounting
                        packageDesc = matchingCurrentBundling.packageDesc
                        packageDiscount = matchingCurrentBundling.packageDiscount
                        packageName = matchingCurrentBundling.packageName
                        packagePrice = matchingCurrentBundling.packagePrice
                        packageRating = matchingCurrentBundling.packageRating
                        resultsShareAmount = matchingCurrentBundling.resultsShareAmount
                        resultsShareFormat = matchingCurrentBundling.resultsShareFormat
                        rootRef = matchingCurrentBundling.rootRef
                        uid = matchingCurrentBundling.uid
                        //bundlingQuantity = matchingCurrentBundling.bundlingQuantity

                        listItemDetails = _servicesList.value?.filter { service ->
                            listItems.contains(service.uid)
                        } ?: emptyList()

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

            // Tambahkan item dari currentBundlingList yang tidak ada di oldBundlingList
            val newBundlings = currentBundlingList.filter { current ->
                updatedBundlingList.none { it.uid == current.uid }
            }
            updatedBundlingList.addAll(newBundlings)

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

        // Update _bundlingPackagesList dengan referensi yang telah diperbarui
        _bundlingPackagesList.value = updatedBundlingList
        Log.d("LifeAct", "observer 158: setUpAndSortedBundling")
    }

    fun setServiceBundlingList() = synchronized(this) {
        val listBundling = _bundlingPackagesList.value ?: emptyList()
        Log.d("TestDataChange", "setServiceBundlingList 183 set: ${listBundling.isNotEmpty()}")
        if (listBundling.isNotEmpty()) {
            listBundling.onEach { bundling ->
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong
                bundling.listItemDetails = serviceBundlingList
                Log.d("ItemsAll", "itemCount: ${serviceBundlingList.size} || ${bundling.packageName}")
            }

            // _bundlingPackagesList.updateIfNeeded(listBundling)
            _bundlingPackagesList.value = listBundling
        }

        _isSetItemBundling.value = false
    }

    fun setUpAndSortedServices(
        currentServicesList: MutableList<Service>,
        capsterSelected: Employee,
        oldServiceList: List<Service>?
    ) = synchronized(this) {
        val updatedServicesList = oldServiceList?.toMutableList() ?: mutableListOf()

        if (oldServiceList.isNullOrEmpty()) {
            // Jika oldServiceList null, gunakan currentServicesList
            currentServicesList.forEach { service ->
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
            updatedServicesList.addAll(currentServicesList) // Tambahkan semua item dari currentServicesList
        } else {
            // Jika oldServiceList tidak null, perbarui properti dari existing item
            updatedServicesList.forEach { existingService ->
                val matchingCurrentService = currentServicesList.find { it.uid == existingService.uid }
                if (matchingCurrentService != null) {
                    existingService.apply {
                        // Perbarui semua properti dari matching item tanpa mengganti referensi
                        applyToGeneral = matchingCurrentService.applyToGeneral
                        autoSelected = matchingCurrentService.autoSelected
                        categoryDetail = matchingCurrentService.categoryDetail
                        defaultItem = matchingCurrentService.defaultItem
                        freeOfCharge = matchingCurrentService.freeOfCharge
                        resultsShareAmount = matchingCurrentService.resultsShareAmount
                        resultsShareFormat = matchingCurrentService.resultsShareFormat
                        rootRef = matchingCurrentService.rootRef
                        serviceCategory = matchingCurrentService.serviceCategory
                        serviceCounting = matchingCurrentService.serviceCounting
                        serviceDesc = matchingCurrentService.serviceDesc
                        serviceIcon = matchingCurrentService.serviceIcon
                        serviceImg = matchingCurrentService.serviceImg
                        serviceName = matchingCurrentService.serviceName
                        servicePrice = matchingCurrentService.servicePrice
                        serviceRating = matchingCurrentService.serviceRating
                        accountVerification = matchingCurrentService.accountVerification
                        uid = matchingCurrentService.uid
                        // serviceQuantity = matchingCurrentService.serviceQuantity

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

            // Tambahkan item dari currentServicesList yang tidak ada di oldServiceList
            val newServices = currentServicesList.filter { current ->
                updatedServicesList.none { it.uid == current.uid }
            }
            updatedServicesList.addAll(newServices)

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

        // Update _servicesList dengan list yang sudah diubah
        _servicesList.value = updatedServicesList
        _isSetItemBundling.value = true
        Log.d("LifeAct", "observer 196: setUpAndSortedServices")
    }

    private fun calculatePriceToDisplay(
        basePrice: Int,
        resultsShareFormat: String,
        resultsShareAmount: Map<String, Any>?,
        applyToGeneral: Boolean,
        capsterUid: String
    ): Int = synchronized(this) {
        if (resultsShareFormat == "fee") {
            val shareAmount = if (applyToGeneral) {
                (resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
            } else {
                (resultsShareAmount?.get(capsterUid) as? Number)?.toInt() ?: 0
            }
            return basePrice + shareAmount
        }
        return basePrice
    }

    // Fungsi untuk memperbarui bundling quantity
    fun updateBundlingQuantity(index: Int, newQuantity: Int) = synchronized(this) {
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        Log.d("TestAct", "updateServicesQuantity 217: old = ${_bundlingPackagesList.value?.get(index)?.bundlingQuantity} || new = $newQuantity")
//        val updatedList = _bundlingPackagesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(bundlingQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _bundlingPackagesList.value = updatedList
        val currentList = _indexBundlingChanged.value ?: mutableListOf()
        Log.d("TestDataChange", "isDataChanged 292: click btn bundling >> ${currentList.contains(index)}")
        if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
            currentList.add(index)
            _indexBundlingChanged.value = currentList
            Log.d("TestDataChange", "add index bundling 290: $index")
        }
        _isDataChanged.value = true
    }

    fun resetIndexBundlingChanged() = synchronized(this) {
        _indexBundlingChanged.value = mutableListOf()
    }

    fun updateServicesQuantity(index: Int, newQuantity: Int) = synchronized(this) {
        // sebenarnya tidak perlu memperbarui data pada viewModel karena perubahan sudah otomatis tercermin dari adapter
        // bahkan ketika snapshot listener tidak perlu submitList lagi cukup perbarui data pada viewModel saja (hanya perlu sekali submitList)
        // dan data pada viewModel dengan adapter akan saling terhubung
        Log.d("TestAct", "updateServicesQuantity 227: old = ${_servicesList.value?.get(index)?.serviceQuantity} || new = $newQuantity")
//        val updatedList = _servicesList.value?.toMutableList()?.apply {
//            this[index] = this[index].copy(serviceQuantity = newQuantity)
//        }
        // Memperbarui LiveData di main thread
//        _servicesList.value = updatedList
        val currentList = _indexServiceChanged.value ?: mutableListOf()
        Log.d("TestDataChange", "isDataChanged 315: click btn service >> ${currentList.contains(index)}")
        if (!currentList.contains(index)) { // Periksa apakah nilai sudah ada
            currentList.add(index)
            _indexServiceChanged.value = currentList
            Log.d("TestDataChange", "add index service 314: $index")
        }
        _isDataChanged.value = true
    }

    fun resetIndexServiceChanged() = synchronized(this) {
        _indexServiceChanged.value = mutableListOf()
    }

    fun clearAllData() = synchronized(this) {
        _isDataChanged.removeSource(_bundlingPackagesList)
        _isDataChanged.removeSource(_servicesList)

        // _itemSelectedCounting.value = 0
        _itemNameSelected.value = emptyList()
        _bundlingPackagesList.value = mutableListOf()
        _servicesList.value = mutableListOf()

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

    private fun <T> MutableLiveData<T>.updateIfNeeded(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

}


