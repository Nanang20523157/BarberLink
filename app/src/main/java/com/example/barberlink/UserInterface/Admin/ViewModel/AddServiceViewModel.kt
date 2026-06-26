package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Repository.ServiceRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Logger
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddServiceViewModel(
    private val repository: ServiceRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : InputFragmentViewModel(handle) {

    companion object {
        private const val SERVICE_PARAMS_KEY = "service_params"
        private const val PENDING_IMAGE_URI_KEY = "pending_image_uri"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
        private const val SERVICE_SELECTED_ID_KEY = "service_selected_id"
    }

    val serviceListMutex = ReentrantCoroutineMutex()
    val categoryListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerCategoriesMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()

    private val _originalService = MutableLiveData<Service>()
    val originalService: LiveData<Service> get() = _originalService

    private val _serviceParams = handle.getLiveData<Service>(SERVICE_PARAMS_KEY)
    val serviceParams: LiveData<Service> get() = _serviceParams

    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0)
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _serviceSelectedId = handle.getLiveData<String>(SERVICE_SELECTED_ID_KEY, "")
    val serviceSelectedId: LiveData<String> get() = _serviceSelectedId

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _serviceList = MutableLiveData<List<Service>>(emptyList())
    val serviceList: LiveData<List<Service>> get() = _serviceList

    private val _pendingImageUri = handle.getLiveData<Uri?>(PENDING_IMAGE_URI_KEY)
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

    private val _categoryList = MutableLiveData<List<DataCategories>>()
    val categoryList: LiveData<List<DataCategories>> = _categoryList

    private val _serviceIcons = MutableLiveData<List<ServiceIcon>>(
        com.example.barberlink.Helper.ServiceIconCache.cachedIcons ?: emptyList()
    )
    val serviceIcons: LiveData<List<ServiceIcon>> = _serviceIcons

    init {
        if (com.example.barberlink.Helper.ServiceIconCache.cachedIcons.isNullOrEmpty()) {
            fetchStorageIcons()
        }
    }

    fun setCategories(categoryListList: List<DataCategories>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            _categoryList.value = categoryListList
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
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

    fun setServiceList(services: List<Service>) {
        viewModelScope.launch {
            _serviceList.value = services
        }
    }

    fun setPendingImageUri(uri: Uri?) {
        viewModelScope.launch {
            _pendingImageUri.value = uri
        }
    }

    fun clearPendingImageUri() {
        viewModelScope.launch {
            _pendingImageUri.value = null
        }
    }

    fun setBarbershopId(id: String) {
        viewModelScope.launch {
            _barbershopId.value = id
        }
    }

    fun setServiceSelectedId(id: String) {
        viewModelScope.launch {
            _serviceSelectedId.value = id
        }
    }

    fun setCurrentMode(mode: Int) {
        viewModelScope.launch {
            _currentMode.value = mode
        }
    }

    fun saveService(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentService = _serviceParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                // 1. Handle Service Image Upload if needed
                _pendingImageUri.value?.let { uri ->
                    val storageRef = storage.reference.child("services/images/${currentService.uid}")

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

                val result = if (isAddMode) {
                    repository.createService(bId, currentService)
                } else {
                    repository.updateService(bId, currentService)
                }

                _isSaving.value = false
                if (result.isSuccessful) clearPendingImageUri()
                if (isAddMode && result.isSuccessful) _serviceList.value = _serviceList.value.orEmpty() + currentService
                else if (result.isSuccessful) {
                    _serviceList.value = _serviceList.value.orEmpty().map { if (it.uid == currentService.uid) currentService else it }
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

    fun getCachedIcons(): List<ServiceIcon>? {
        // 1. Immediately emit cached icons if available to avoid waiting
        return serviceIcons.value
    }

    fun fetchStorageIcons() {
        // 2. Fetch/update in the background to ensure it is always fresh
        viewModelScope.launch {
            try {
                // Use default bucket's reference
                val storageRef = storage.reference.child("services/icons")
                val result = storageRef.listAll().await()

                val iconList = result.items.map { item ->
                    val url = item.downloadUrl.await().toString()
                    ServiceIcon(iconUrl = url, isSelected = false)
                }

                com.example.barberlink.Helper.ServiceIconCache.setCachedIcons(iconList)
                _serviceIcons.value = iconList
            } catch (e: Exception) {
                Logger.e("AddServiceViewModel", "Error fetching icons from storage", e)
                if (_serviceIcons.value.isNullOrEmpty()) {
                    _serviceIcons.value = emptyList()
                }
            }
        }
    }

}
