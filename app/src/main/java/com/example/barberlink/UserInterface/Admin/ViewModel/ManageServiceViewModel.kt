package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ManageServiceViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ConfirmDeleteViewModel() {

    val servicesMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val listenerServiceListMutex = ReentrantCoroutineMutex()
    val listenerBundlingListMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()

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

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int, val oldCode: String = ""): ResultState()
    }

    private var targetDeleteData: Service? = null

    private val _serviceList = MutableLiveData<MutableList<Service>>().apply { value = mutableListOf() }
    val serviceList: LiveData<MutableList<Service>> = _serviceList

    private val _categoryList = MutableLiveData<MutableList<DataCategories>>().apply { value = mutableListOf() }
    val categoryList: LiveData<MutableList<DataCategories>> = _categoryList

    private val _bundlingList = MutableLiveData<List<com.example.barberlink.DataClass.BundlingPackage>>(emptyList())
    val bundlingList: LiveData<List<com.example.barberlink.DataClass.BundlingPackage>> get() = _bundlingList

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun getTargetDeleteData(): Service? {
        return runBlocking {
            targetDeleteData
        }
    }

    fun setTargetDeleteData(data: Service?) {
        viewModelScope.launch {
            targetDeleteData = data
        }
    }


    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setServiceList(serviceList: MutableList<Service>) {
        viewModelScope.launch {
            _serviceList.value = serviceList
        }
    }

    fun setBundlingList(list: List<com.example.barberlink.DataClass.BundlingPackage>) {
        viewModelScope.launch {
            _bundlingList.value = list
        }
    }

    fun setOutletList(list: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = list
        }
    }

    fun setCategoryList(categoryList: MutableList<DataCategories>) {
        viewModelScope.launch {
            _categoryList.value = categoryList
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun deleteService(service: Service) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val serviceRef = db.document(service.rootRef)
                    .collection("services")
                    .document(service.uid)

                if (service.serviceImg.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(service.serviceImg)
                        imageRef.delete().await()
                        Logger.d("DeleteService", "Image deleted successfully: ${service.serviceImg}")
                    } catch (e: Exception) {
                        Logger.e("DeleteService", "Failed to delete image: ${e.message}")
                    }
                }

                val bundlings = _bundlingList.value ?: emptyList()
                val bundlingsToUpdate = bundlings.filter { bundling ->
                    bundling.listItems.contains(service.uid)
                }

                val outlets = _outletList.value ?: emptyList()
                val outletsToUpdate = outlets.filter { outlet ->
                    outlet.listServices.contains(service.uid)
                }

                withContext(Dispatchers.IO) {
                    db.runTransaction { transaction ->
                        transaction.delete(serviceRef)

                        // Update bundling packages
                        for (bundling in bundlingsToUpdate) {
                            val ref = db.document(bundling.dataRef)
                            val newListItems = bundling.listItems.filter { it != service.uid }
                            val newAccumulatedPrice = if (bundling.accumulatedPrice > service.servicePrice) bundling.accumulatedPrice - service.servicePrice else 0
                            val newPackageDiscount = if (newAccumulatedPrice - bundling.packageDiscount <= 0) {
                                newAccumulatedPrice
                            } else {
                                bundling.packageDiscount
                            }
                            val newPackagePrice = maxOf(0, newAccumulatedPrice - newPackageDiscount)
                            val updates = mutableMapOf<String, Any>()
                            updates["list_items"] = newListItems
                            updates["accumulated_price"] = newAccumulatedPrice
                            updates["package_discount"] = newPackageDiscount
                            updates["package_price"] = newPackagePrice
                            transaction.update(ref, updates)
                        }

                        // Update outlets
                        for (outlet in outletsToUpdate) {
                            val ref = db.document(outlet.outletReference)
                            val newListServices = outlet.listServices.filter { it != service.uid }
                            transaction.update(ref, "list_services", newListServices)
                        }
                    }.await()
                }

                _updateStateResult.value = ResultState.Success("Delete", "Layanan \"${service.serviceName}\" berhasil dihapus.")
            } catch (e: Exception) {
                Logger.e("DeleteService", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus layanan!", -1)
            }
        }
    }

    fun updateServiceList(newServiceList: MutableList<Service>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentList = _serviceList.value ?: mutableListOf()

            // Build list of items to remove
            val servicesToRemove = currentList.filter { current ->
                newServiceList.none { new -> new.uid == current.uid }
            }

            // Update or add new services
            newServiceList.forEach { newService ->
                val existingService = currentList.find { it.uid == newService.uid }
                if (existingService != null) {
                    // Update existing service data
                    existingService.apply {
                        applyToGeneral = newService.applyToGeneral
                        autoSelected = newService.autoSelected
                        defaultItem = newService.defaultItem
                        freeOfCharge = newService.freeOfCharge
                        resultsShareAmount = newService.resultsShareAmount
                        resultsShareFormat = newService.resultsShareFormat
                        rootRef = newService.rootRef
                        serviceCategory = newService.serviceCategory
                        serviceCounting = newService.serviceCounting
                        serviceDesc = newService.serviceDesc
                        serviceIcon = newService.serviceIcon
                        serviceImg = newService.serviceImg
                        serviceName = newService.serviceName
                        servicePrice = newService.servicePrice
                        serviceRating = newService.serviceRating
                        dataRef = newService.dataRef
                        uid = newService.uid
                    }
                } else {
                    // Add new service
                    currentList.add(newService)
                }
            }

            // Remove services not in newList
            servicesToRemove.forEach { service ->
                currentList.remove(service)
            }

            // Update live data
            _serviceList.updateOnMain(currentList)
        }
    }

    fun clearAllDataService() {
        viewModelScope.launch {
            _serviceList.clearList()
        }
    }
}
