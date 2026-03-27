package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Repository.ServiceRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.Utils.Logger
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddServiceViewModel(
    private val repository: ServiceRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : ViewModel() {

    val serviceListMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()

    private val _originalService = MutableLiveData<Service>()
    val originalService: LiveData<Service> get() = _originalService

    private val _serviceParams = MutableLiveData<Service>()
    val serviceParams: LiveData<Service> get() = _serviceParams

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _allServices = MutableLiveData<List<Service>>(emptyList())
    val allServices: LiveData<List<Service>> get() = _allServices

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

    private val _pendingIconUri = MutableLiveData<Uri?>()
    val pendingIconUri: LiveData<Uri?> get() = _pendingIconUri

    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories

    private val _serviceIcons = MutableLiveData<List<ServiceIcon>>(emptyList())
    val serviceIcons: LiveData<List<ServiceIcon>> = _serviceIcons

    fun getServiceCategories(adminUid: String) {
        viewModelScope.launch {
            val result = repository.getServiceCategories(adminUid)
            if (result.isSuccessful) {
                _categories.value = result.data?.map { it.categoryName } ?: emptyList()
            }
        }
    }

    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
        }
    }

    fun setOriginalService(service: Service) {
        viewModelScope.launch {
            _originalService.value = service
        }
    }

    fun updateServiceParams(service: Service) {
        viewModelScope.launch {
            _serviceParams.value = service
        }
    }

    fun setAllServices(services: List<Service>) {
        viewModelScope.launch {
            _allServices.value = services
        }
    }

    fun setOutletList(outlets: List<Outlet>) {
        viewModelScope.launch {
            _outletList.postValue(outlets)
        }
    }

    fun setPendingImageUri(uri: Uri?) {
        viewModelScope.launch {
            _pendingImageUri.value = uri
        }
    }

    fun setPendingIconUri(uri: Uri?) {
        viewModelScope.launch {
            _pendingIconUri.value = uri
        }
    }

    fun clearPendingImageUri() {
        viewModelScope.launch {
            _pendingImageUri.value = null
        }
    }

    fun clearPendingIconUri() {
        viewModelScope.launch {
            _pendingIconUri.value = null
        }
    }

    fun saveService(barbershopId: String, isAddMode: Boolean) {
        viewModelScope.launch {
            val currentService = _serviceParams.value ?: return@launch

            _isSaving.value = true
            try {
                // 1. Handle Service Image Upload if needed
                _pendingImageUri.value?.let { uri ->
                    val storageRef = storage.reference.child("services/images/${currentService.uid}.png")

                    // Delete old image if it exists
                    if (currentService.serviceImg.isNotEmpty()) {
                        try {
                            val oldReference = FirebaseStorage.getInstance().getReferenceFromUrl(currentService.serviceImg)
                            if (oldReference.path != storageRef.path) {
                                oldReference.delete().await()
                            }
                        } catch (e: Exception) {
                            // Ignore deletion errors
                        }
                    }

                    // Upload new image
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentService.serviceImg = downloadUrl.toString()
                }

                // 2. Handle Service Icon Upload if needed
                _pendingIconUri.value?.let { uri ->
                    val storageRef = storage.reference.child("services/icons/${currentService.uid}.png")

                    // Delete old icon if it exists
                    if (currentService.serviceIcon.isNotEmpty()) {
                        try {
                            val oldReference = FirebaseStorage.getInstance().getReferenceFromUrl(currentService.serviceIcon)
                            if (oldReference.path != storageRef.path) {
                                oldReference.delete().await()
                            }
                        } catch (e: Exception) {
                            // Ignore deletion errors
                        }
                    }

                    // Upload new icon
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentService.serviceIcon = downloadUrl.toString()
                }

                val result = if (isAddMode) {
                    repository.createService(barbershopId, currentService)
                } else {
                    repository.updateService(barbershopId, currentService)
                }

                _isSaving.value = false
                if (result.isSuccessful) {
                    clearPendingImageUri()
                    clearPendingIconUri()
                }
                _saveResult.value = result
            } catch (e: Exception) {
                _isSaving.value = false
                _saveResult.value = FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }

    fun fetchStorageIcons() {
        viewModelScope.launch {
            try {
                // Use default bucket's reference
                val storageRef = storage.reference.child("services/icons")
                val result = storageRef.listAll().await()
                
                val iconList = result.items.map { item ->
                    val url = item.downloadUrl.await().toString()
                    ServiceIcon(iconUrl = url, isSelected = false)
                }
                
                _serviceIcons.value = iconList
            } catch (e: Exception) {
                Logger.e("AddServiceViewModel", "Error fetching icons from storage", e)
                _serviceIcons.value = emptyList()
            }
        }
    }

}
