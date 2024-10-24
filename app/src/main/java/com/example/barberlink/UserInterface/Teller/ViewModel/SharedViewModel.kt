package com.example.barberlink.UserInterface.Teller.ViewModel

import BundlingPackage
import Employee
import Service
import android.util.Log
import androidx.lifecycle.LiveData
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
    private val _bundlingPackagesList = MutableLiveData<MutableList<BundlingPackage>>().apply { value = mutableListOf() }
    val bundlingPackagesList: LiveData<MutableList<BundlingPackage>> = _bundlingPackagesList

    // Tambahan untuk servicesList
    private val _servicesList = MutableLiveData<MutableList<Service>>().apply { value = mutableListOf() }
    val servicesList: LiveData<MutableList<Service>> = _servicesList

    // Fungsi untuk menambah item yang dipilih
    // Fungsi untuk menambah item yang dipilih
    fun addItemSelectedCounting(name: String, category: String) {
        val newCount = (_itemSelectedCounting.value ?: 0) + 1
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()

        if (currentList.none { it.first == name }) {
            currentList.add(0, name to category)
        }

        // Memperbarui LiveData di main thread
        _itemSelectedCounting.value = newCount
        _itemNameSelected.value = currentList
    }

    // Fungsi untuk menghapus item berdasarkan nama
    fun removeItemSelectedByName(name: String, removeName: Boolean) {
        val currentList = _itemNameSelected.value?.toMutableList()
        if (!currentList.isNullOrEmpty() && removeName) {
            val itemToRemove = currentList.find { it.first == name }
            itemToRemove?.let {
                currentList.remove(it)
                _itemNameSelected.value = currentList
            }
        }

        val currentCount = _itemSelectedCounting.value ?: 0
        if (currentCount > 0) {
            _itemSelectedCounting.value = currentCount - 1
        }
    }

    // Fungsi untuk mereset semua item yang dipilih
    fun resetAllItem() {
        _itemSelectedCounting.value = 0
        _itemNameSelected.value = emptyList()

        val updatedServicesList = _servicesList.value?.map { service ->
            if (!service.defaultItem) {
                service.serviceQuantity = 0
            } else {
                addItemSelectedCounting(service.serviceName, "service")
            }
            service
        }

        _servicesList.value = updatedServicesList?.toMutableList() ?: mutableListOf()

        val updatedBundlingPackagesList = _bundlingPackagesList.value?.map { bundling ->
            if (!bundling.defaultItem) {
                bundling.bundlingQuantity = 0
            } else {
                addItemSelectedCounting(bundling.packageName, "package")
            }
            bundling
        }

        _bundlingPackagesList.value = updatedBundlingPackagesList?.toMutableList() ?: mutableListOf()
    }

    // Fungsi untuk mereset semua layanan (kategori "service")
    fun resetAllServices() {
        val currentList = _itemNameSelected.value?.toMutableList() ?: mutableListOf()
        currentList.removeAll { it.second == "service" }
        // val initialSize = currentList.size

        // val itemCount = ((_itemSelectedCounting.value ?: 0) - (initialSize - currentList.size))
        // Log.d("LifeAct", "itemCount: $itemCount")
        // _itemSelectedCounting.value = itemCount
        _itemNameSelected.value = currentList

        var itemCount = 0
        _bundlingPackagesList.value?.forEach { bundling ->
            itemCount += bundling.bundlingQuantity
        }
        _itemSelectedCounting.value = itemCount

        val updatedServicesList = _servicesList.value?.map { service ->
            if (!service.defaultItem) {
                service.serviceQuantity = 0
            } else {
                addItemSelectedCounting(service.serviceName, "service")
            }
            service
        }

        _servicesList.value = updatedServicesList?.toMutableList() ?: mutableListOf()
    }

    fun replaceBundlingPackagesList(newPackages: MutableList<BundlingPackage>, capsterSelected: Employee, existingList: List<BundlingPackage>?) {
        val namePackage = newPackages.map { it.packageName }
        Log.d("LifeAct", "list packages1: $namePackage")

        if (existingList.isNullOrEmpty()) {
            setUpAndSortedBundling(newPackages, capsterSelected)
        } else {
            sortBundlingWithExistingList(newPackages, capsterSelected, existingList)
        }
    }

    // Fungsi untuk memperbarui daftar services
    fun replaceServicesList(newServices: MutableList<Service>, capsterSelected: Employee, existingList: List<Service>?) {
        val nameService = newServices.map { it.serviceName }
        Log.d("LifeAct", "list services1: $nameService")

        if (existingList.isNullOrEmpty()) {
            setUpAndSortedServices(newServices, capsterSelected)
        } else {
            sortServicesWithExistingList(newServices, capsterSelected, existingList)
        }
    }

    private fun setUpAndSortedBundling(currentBundlingList: MutableList<BundlingPackage>, capsterSelected: Employee) {
        currentBundlingList.apply {
            forEach { bundling ->
                if (bundling.autoSelected || bundling.defaultItem) {
                    bundling.bundlingQuantity = 1
                    addItemSelectedCounting(bundling.packageName, "package")
                }

                // Set serviceBundlingList
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong
                bundling.listItemDetails = serviceBundlingList

                // Perhitungan results_share_format dan applyToGeneral pada bundling
                bundling.priceToDisplay = if (bundling.resultsShareFormat == "fee") {
                    val resultsShareAmount: Int = if (bundling.applyToGeneral) {
                        (bundling.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                    } else {
                        (bundling.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                    }
                    bundling.packagePrice + resultsShareAmount
                } else {
                    bundling.packagePrice
                }
            }

            // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        val namePackage = currentBundlingList.map { it.packageName }
        Log.d("LifeAct", "list packages2: $namePackage")

        // Update _bundlingPackagesList dengan list yang sudah diubah
        _bundlingPackagesList.value = currentBundlingList
    }

    private fun setUpAndSortedServices(currentServicesList: MutableList<Service>, capsterSelected: Employee) {
        currentServicesList.apply {
            forEach { service ->
                if (service.autoSelected || service.defaultItem) {
                    service.serviceQuantity = 1
                    addItemSelectedCounting(service.serviceName, "service")
                }

                // Perhitungan results_share_format dan applyToGeneral pada service
                service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                    val resultsShareAmount: Int = if (service.applyToGeneral) {
                        (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                    } else {
                        (service.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                    }
                    service.servicePrice + resultsShareAmount
                } else {
                    service.servicePrice
                }
            }

            // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        val nameService = currentServicesList.map { it.serviceName }
        Log.d("LifeAct", "list services2: $nameService")

        // Update _servicesList dengan list yang sudah diubah
        _servicesList.value = currentServicesList
    }

    // Fungsi untuk memperbarui bundling quantity
    fun updateBundlingQuantity(index: Int, newQuantity: Int) {
        val updatedList = _bundlingPackagesList.value?.toMutableList()?.apply {
            this[index] = this[index].copy(bundlingQuantity = newQuantity)
        }
        // Memperbarui LiveData di main thread
        _bundlingPackagesList.value = updatedList
    }

    // Fungsi untuk memperbarui service quantity
    fun updateServicesQuantity(index: Int, newQuantity: Int) {
        val updatedList = _servicesList.value?.toMutableList()?.apply {
            this[index] = this[index].copy(serviceQuantity = newQuantity)
        }
        // Memperbarui LiveData di main thread
        _servicesList.value = updatedList
    }

    private fun sortBundlingWithExistingList(currentBundlingList: MutableList<BundlingPackage>, capsterSelected: Employee, oldBundlingList: List<BundlingPackage>) {
        currentBundlingList.apply {
            // Update setiap bundling berdasarkan oldBundlingList
            forEach { bundling ->
                // Mempertahankan nilai bundlingQuantity dari daftar sebelumnya
                val existingBundling = oldBundlingList.find { it.uid == bundling.uid }
                if (existingBundling != null) {
                    bundling.bundlingQuantity = existingBundling.bundlingQuantity
                }

                // Set serviceBundlingList dari servicesList di ViewModel
                val serviceBundlingList = _servicesList.value?.filter { service ->
                    bundling.listItems.contains(service.uid)
                } ?: emptyList() // Jika null, gunakan list kosong

                // Update listItemDetails dari bundling
                bundling.listItemDetails = serviceBundlingList

                // Perhitungan results_share_format dan applyToGeneral pada bundling
                bundling.priceToDisplay = if (bundling.resultsShareFormat == "fee") {
                    val resultsShareAmount: Int = if (bundling.applyToGeneral) {
                        (bundling.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                    } else {
                        (bundling.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                    }
                    bundling.packagePrice + resultsShareAmount
                } else {
                    bundling.packagePrice
                }
            }

            // Urutkan bundlingPackagesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        // Setelah update, post hasilnya ke LiveData
        _bundlingPackagesList.value = currentBundlingList
    }

    private fun sortServicesWithExistingList(currentServicesList: MutableList<Service>, capsterSelected: Employee, oldServiceList: List<Service>) {
        currentServicesList.apply {
            // Update setiap service berdasarkan oldServiceList
            forEach { service ->
                val existingService = oldServiceList.find { it.uid == service.uid }
                if (existingService != null) {
                    service.serviceQuantity = existingService.serviceQuantity
                }

                // Perhitungan results_share_format dan applyToGeneral pada service
                service.priceToDisplay = if (service.resultsShareFormat == "fee") {
                    val resultsShareAmount: Int = if (service.applyToGeneral) {
                        (service.resultsShareAmount?.get("All") as? Number)?.toInt() ?: 0
                    } else {
                        (service.resultsShareAmount?.get(capsterSelected.uid) as? Number)?.toInt() ?: 0
                    }
                    service.servicePrice + resultsShareAmount
                } else {
                    service.servicePrice
                }
            }

            // Urutkan servicesList: yang autoSelected atau defaultItem di indeks awal
            sortByDescending { it.autoSelected || it.defaultItem }
        }

        // Setelah update, post hasilnya ke LiveData
        _servicesList.value = currentServicesList
    }

    fun clearAllData() {
        // Reset _itemSelectedCounting ke 0
        _itemSelectedCounting.value = 0

        // Reset _itemNameSelected ke list kosong
        _itemNameSelected.value = emptyList()

        // Reset _bundlingPackagesList ke list kosong
        _bundlingPackagesList.value = mutableListOf()

        // Reset _servicesList ke list kosong
        _servicesList.value = mutableListOf()
    }


}


