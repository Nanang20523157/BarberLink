package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageBundlingViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    val bundlingMutex = ReentrantCoroutineMutex()
    val listenerBundlingListMutex = ReentrantCoroutineMutex()

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int): ResultState()
    }

    private val _bundlingList = MutableLiveData<MutableList<BundlingPackage>>().apply { value = mutableListOf() }
    val bundlingList: LiveData<MutableList<BundlingPackage>> = _bundlingList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    private val _allServices = MutableLiveData<List<Service>>(emptyList())
    val allServices: LiveData<List<Service>> get() = _allServices

    fun setAllServices(services: List<Service>) {
        viewModelScope.launch {
            _allServices.value = services
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

                // Delete from Firestore
                val result = db.document(bundling.rootRef)
                    .collection("bundling_packages")
                    .document(bundling.uid)
                    .delete()
                    .awaitWriteWithOfflineFallback(tag = "DeleteBundling")

                if (result.isSuccessful) {
                    _updateStateResult.value = ResultState.Success("Delete", "Paket bundling \"${bundling.packageName}\" berhasil dihapus.")
                } else {
                    _updateStateResult.value = ResultState.Failure("Delete", result.errorMessage ?: "Gagal menghapus bundling!", -1)
                }
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
