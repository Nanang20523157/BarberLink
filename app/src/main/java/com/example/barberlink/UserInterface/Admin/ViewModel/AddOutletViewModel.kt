package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Repository.OutletRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class AddOutletViewModel(
    private val repository: OutletRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val OUTLET_PARAMS_KEY = "outlet_params"
        private const val PENDING_IMAGE_URI_KEY = "pending_image_uri"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
        private const val OUTLET_SELECTED_ID_KEY = "outlet_selected_id"
    }

    val outletListMutex = ReentrantCoroutineMutex()
    val servicesListMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val employeesListMutex = ReentrantCoroutineMutex()
    val productsListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerServicesMutex = ReentrantCoroutineMutex()
    val listenerBundlingsMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()

    private val _originalOutlet = MutableLiveData<Outlet>()
    val originalOutlet: LiveData<Outlet> get() = _originalOutlet

    // Backed by SavedStateHandle to survive process death
    private val _outletParams = handle.getLiveData<Outlet>(OUTLET_PARAMS_KEY)
    val outletParams: LiveData<Outlet> get() = _outletParams

    // 0: VIEW, 1: EDIT, 2: ADD
    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0)
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _outletSelectedId = handle.getLiveData<String>(OUTLET_SELECTED_ID_KEY, "")
    val outletSelectedId: LiveData<String> get() = _outletSelectedId

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _pendingImageUri = handle.getLiveData<Uri?>(PENDING_IMAGE_URI_KEY)
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

    private val _allServices = MutableLiveData<List<Service>>()
    val allServices: LiveData<List<Service>> get() = _allServices

    private val _allBundling = MutableLiveData<List<BundlingPackage>>()
    val allBundling: LiveData<List<BundlingPackage>> get() = _allBundling

    private val _allStaff = MutableLiveData<List<UserEmployeeData>>()
    val allStaff: LiveData<List<UserEmployeeData>> get() = _allStaff

    private val _allProducts = MutableLiveData<List<Product>>()
    val allProducts: LiveData<List<Product>> get() = _allProducts


    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
        }
    }

    fun setOriginalOutlet(outlet: Outlet) {
        viewModelScope.launch {
            _originalOutlet.value = outlet
        }
    }

    fun updateOutletParams(outlet: Outlet) {
        viewModelScope.launch {
            _outletParams.value = outlet
        }
    }

    fun setAllServices(services: List<Service>) {
        viewModelScope.launch {
            _allServices.value = services
        }
    }

    fun setAllBundling(bundling: List<BundlingPackage>) {
        viewModelScope.launch {
            _allBundling.value = bundling
        }
    }

    fun setAllStaff(staff: List<UserEmployeeData>) {
        viewModelScope.launch {
            _allStaff.value = staff
        }
    }

    fun setAllProducts(products: List<Product>) {
        viewModelScope.launch {
            _allProducts.value = products
        }
    }

    fun setOutletList(outlets: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = outlets
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

    fun setOutletSelectedId(id: String) {
        viewModelScope.launch {
            _outletSelectedId.value = id
        }
    }

    fun setCurrentMode(mode: Int) {
        viewModelScope.launch {
            _currentMode.value = mode
        }
    }

    fun saveOutlet(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentOutlet = _outletParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                // 1. Handle Image Upload if needed
                _pendingImageUri.value?.let { uri ->
                    val storageRef = storage.reference.child("outlets/${currentOutlet.uid}")

                    // Delete old image if it exists and filename is different (extension change)
                    if (currentOutlet.imgOutlet.isNotEmpty()) {
                        try {
                            val oldReference = FirebaseStorage.getInstance().getReferenceFromUrl(currentOutlet.imgOutlet)
                            if (oldReference.path != storageRef.path) {
                                oldReference.delete().await()
                            }
                        } catch (e: Exception) {
                            // Ignore deletion errors (e.g. file doesn't exist)
                        }
                    }

                    // Upload new image
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentOutlet.imgOutlet = downloadUrl.toString()
                }

                val result = if (isAddMode) {
                    repository.createOutlet(bId, currentOutlet)
                } else {
                    repository.updateOutlet(bId, currentOutlet)
                }

                _isSaving.value = false
                if (result.isSuccessful) clearPendingImageUri()
                if (isAddMode && result.isSuccessful) _outletList.value = _outletList.value.orEmpty() + currentOutlet
                else if (result.isSuccessful) {
                    _outletList.value = _outletList.value.orEmpty().map { if (it.uid == currentOutlet.uid) currentOutlet else it }
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

}
