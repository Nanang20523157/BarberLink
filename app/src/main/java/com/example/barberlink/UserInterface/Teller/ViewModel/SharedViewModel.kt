package com.example.barberlink.UserInterface.Teller.ViewModel

import BundlingPackage
import Employee
import Service
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

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

    private val _isDataChanged = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(_bundlingPackagesList) {
            Log.d("TestAct", "isDataChanged 33: true by bundling")
            value = true
            val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            _itemSelectedCounting.value = currentCount
        }
        addSource(_servicesList) {
            Log.d("TestAct", "isDataChanged 37: true by service")
            value = true
            val currentCount = (bundlingPackagesList.value?.sumOf { it.bundlingQuantity } ?: 0) +
                    (servicesList.value?.sumOf { it.serviceQuantity } ?: 0)
            _itemSelectedCounting.value = currentCount
        }
    }
    val isDataChanged: LiveData<Boolean> = _isDataChanged

    private val _isSetItemBundling = MutableLiveData<Boolean>().apply { value = false }
    val isSetItemBundling: LiveData<Boolean> = _isSetItemBundling

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
        _servicesList.value = _servicesList.value

        _bundlingPackagesList.value?.forEach { bundling ->
            bundling.apply {
                bundlingQuantity = if (defaultItem) 1 else 0
                if (defaultItem) addItemSelectedCounting(packageName, "package")
            }
        }
        _bundlingPackagesList.value = _bundlingPackagesList.value
        Log.d("LifeAct", "observer 95: resetAllItem")
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

        _servicesList.value = _servicesList.value
        Log.d("LifeAct", "observer 114: resetAllServices")
    }

    fun setUpAndSortedBundling(
        currentBundlingList: MutableList<BundlingPackage>,
        capsterSelected: Employee,
        oldBundlingList: List<BundlingPackage>?
    ) = synchronized(this) {
        currentBundlingList.apply {
            forEach { bundling ->
                if (oldBundlingList.isNullOrEmpty()) {
                    if (bundling.autoSelected || bundling.defaultItem) {
                        bundling.bundlingQuantity = 1
                        addItemSelectedCounting(bundling.packageName, "package")
                    }
                } else {
                    val existingBundling = oldBundlingList.find { it.uid == bundling.uid }
                    if (existingBundling != null) {
                        bundling.bundlingQuantity = existingBundling.bundlingQuantity
                    }
                }

                // Set serviceBundlingList
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong
                bundling.listItemDetails = serviceBundlingList
                Log.d("ItemsAll", "itemCount: ${serviceBundlingList.size} || ${bundling.packageName}")

                // Perhitungan results_share_format dan applyToGeneral pada bundling
                bundling.priceToDisplay = calculatePriceToDisplay(
                    bundling.packagePrice,
                    bundling.resultsShareFormat,
                    bundling.resultsShareAmount,
                    bundling.applyToGeneral,
                    capsterSelected.uid
                )
            }

            // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        // Update _bundlingPackagesList dengan list yang sudah diubah
        _bundlingPackagesList.value = currentBundlingList
        Log.d("LifeAct", "observer 158: setUpAndSortedBundling")
    }

    fun setServiceBundlingList() = synchronized(this) {
        val listBundling = _bundlingPackagesList.value ?: emptyList()
        Log.d("TestAct", "setServiceBundlingList 183 set: ${listBundling.isNotEmpty()}")
        if (listBundling.isNotEmpty()) {
            listBundling.onEach { bundling ->
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong
                bundling.listItemDetails = serviceBundlingList
                Log.d("ItemsAll", "itemCount: ${serviceBundlingList.size} || ${bundling.packageName}")
            }

            _bundlingPackagesList.value = listBundling
        }

        _isSetItemBundling.value = false
    }

    fun setUpAndSortedServices(
        currentServicesList: MutableList<Service>,
        capsterSelected: Employee,
        oldServiceList: List<Service>?
    ) = synchronized(this) {
        currentServicesList.apply {
            forEach { service ->
                if (oldServiceList.isNullOrEmpty()) {
                    if (service.autoSelected || service.defaultItem) {
                        service.serviceQuantity = 1
                        addItemSelectedCounting(service.serviceName, "service")
                    }
                } else {
                    val existingService = oldServiceList.find { it.uid == service.uid }
                    if (existingService != null) {
                        service.serviceQuantity = existingService.serviceQuantity
                    }
                }

                // Perhitungan results_share_format dan applyToGeneral pada service
                service.priceToDisplay = calculatePriceToDisplay(
                    service.servicePrice,
                    service.resultsShareFormat,
                    service.resultsShareAmount,
                    service.applyToGeneral,
                    capsterSelected.uid
                )
            }

            // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        // Update _servicesList dengan list yang sudah diubah
        _servicesList.value = currentServicesList
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
        _isDataChanged.value = true
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
        _isDataChanged.value = true
    }

    fun clearAllData() = synchronized(this) {
        _isDataChanged.removeSource(_bundlingPackagesList)
        _isDataChanged.removeSource(_servicesList)

        // _itemSelectedCounting.value = 0
        _itemNameSelected.value = emptyList()
        _bundlingPackagesList.value = mutableListOf()
        _servicesList.value = mutableListOf()

        _isDataChanged.value = false

        _isDataChanged.addSource(_bundlingPackagesList) {
            _isDataChanged.value = true
            Log.d("TestAct", "isDataChanged 33: true by bundling")
        }
        _isDataChanged.addSource(_servicesList) {
            _isDataChanged.value  = true
            Log.d("TestAct", "isDataChanged 37: true by service")
        }

        Log.d("LifeAct", "observer 257: clearAllData")
    }

}


