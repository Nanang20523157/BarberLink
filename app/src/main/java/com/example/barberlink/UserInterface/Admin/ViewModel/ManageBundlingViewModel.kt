package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ManageBundlingViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ConfirmDeleteViewModel() {

    val bundlingMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val listenerBundlingListMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int): ResultState()
    }

    private var targetDeleteData: BundlingPackage? = null

    private val _bundlingList = MutableLiveData<MutableList<BundlingPackage>>().apply { value = mutableListOf() }
    val bundlingList: LiveData<MutableList<BundlingPackage>> = _bundlingList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _allServices = MutableLiveData<List<Service>>(emptyList())
    val allServices: LiveData<List<Service>> get() = _allServices

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun getTargetDeleteData(): BundlingPackage? {
        return runBlocking {
            targetDeleteData
        }
    }

    fun setTargetDeleteData(data: BundlingPackage?) {
        viewModelScope.launch {
            targetDeleteData = data
        }
    }

    fun setAllServices(services: List<Service>) {
        viewModelScope.launch {
            _allServices.value = services
        }
    }

    fun setOutletList(list: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = list
        }
    }

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setBundlingList(bundlingList: MutableList<BundlingPackage>) {
        viewModelScope.launch {
            _bundlingList.value = bundlingList
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun deleteBundling(bundling: BundlingPackage) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val bundlingRef = db.document(bundling.rootRef)
                    .collection("bundling_packages")
                    .document(bundling.uid)

                val outlets = _outletList.value ?: emptyList()
                val outletsToUpdate = outlets.filter { outlet ->
                    outlet.listBundling.contains(bundling.uid)
                }

                withContext(Dispatchers.IO) {
                    db.runTransaction { transaction ->
                        transaction.delete(bundlingRef)

                        // Update outlets
                        for (outlet in outletsToUpdate) {
                            val ref = db.document(outlet.outletReference)
                            val newListBundling = outlet.listBundling.filter { it != bundling.uid }
                            transaction.update(ref, "list_bundling", newListBundling)
                        }
                    }.await()
                }

                _updateStateResult.value = ResultState.Success("Delete", "Paket bundling \"${bundling.packageName}\" berhasil dihapus.")
            } catch (e: Exception) {
                Logger.e("DeleteBundling", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus bundling!", -1)
            }
        }
    }

    fun updateBundlingList(newBundlingList: MutableList<BundlingPackage>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentList = _bundlingList.value ?: mutableListOf()
            val services = _allServices.value ?: emptyList()

            // Map listItems to listItemDetails for new entries
            newBundlingList.forEach { newItem ->
                newItem.listItemDetails = newItem.listItems.mapNotNull { serviceId ->
                    services.find { it.uid == serviceId }
                }
            }

            // Build list of items to remove
            val itemsToRemove = currentList.filter { current ->
                newBundlingList.none { new -> new.uid == current.uid }
            }

            // Update or add new items
            newBundlingList.forEach { newItem ->
                val existingItem = currentList.find { it.uid == newItem.uid }
                if (existingItem != null) {
                    existingItem.apply {
                        accumulatedPrice = newItem.accumulatedPrice
                        applyToGeneral = newItem.applyToGeneral
                        autoSelected = newItem.autoSelected
                        defaultItem = newItem.defaultItem
                        listItems = newItem.listItems
                        packageCounting = newItem.packageCounting
                        packageDesc = newItem.packageDesc
                        packageDiscount = newItem.packageDiscount
                        packageName = newItem.packageName
                        packagePrice = newItem.packagePrice
                        packageRating = newItem.packageRating
                        resultsShareAmount = newItem.resultsShareAmount
                        resultsShareFormat = newItem.resultsShareFormat
                        rootRef = newItem.rootRef
                        uid = newItem.uid

                        listItemDetails = newItem.listItemDetails
                    }
                } else {
                    currentList.add(newItem)
                }
            }

            itemsToRemove.forEach { item ->
                currentList.remove(item)
            }

            _bundlingList.updateOnMain(currentList)
        }
    }

    fun clearAllDataBundling() {
        viewModelScope.launch {
            _bundlingList.clearList()
        }
    }
}
