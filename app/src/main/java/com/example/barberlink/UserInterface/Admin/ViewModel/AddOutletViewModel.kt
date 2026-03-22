package com.example.barberlink.UserInterface.Admin.ViewModel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
    private val storage: FirebaseStorage
) : ViewModel() {

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

    private val _outletParams = MutableLiveData<Outlet>()
    val outletParams: LiveData<Outlet> get() = _outletParams

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _allServices = MutableLiveData<List<Service>>(emptyList())
    val allServices: LiveData<List<Service>> get() = _allServices

    private val _allBundling = MutableLiveData<List<BundlingPackage>>(emptyList())
    val allBundling: LiveData<List<BundlingPackage>> get() = _allBundling

    private val _allStaff = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val allStaff: LiveData<List<UserEmployeeData>> get() = _allStaff

    private val _allProducts = MutableLiveData<List<Product>>(emptyList())
    val allProducts: LiveData<List<Product>> get() = _allProducts

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

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
            _outletList.postValue(outlets)
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

    fun saveOutlet(barbershopId: String, isAddMode: Boolean) {
        viewModelScope.launch {
            val currentOutlet = _outletParams.value ?: return@launch

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
                    repository.createOutlet(barbershopId, currentOutlet)
                } else {
                    repository.updateOutlet(barbershopId, currentOutlet)
                }

                _isSaving.value = false
                if (result.isSuccessful) clearPendingImageUri()
                _saveResult.value = result
            } catch (e: Exception) {
                _isSaving.value = false
                _saveResult.value = FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    private fun getFileExtension(uri: Uri): String {
        return runBlocking {
            uri.path?.substringAfterLast('.', "jpg") ?: "jpg"
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }

}
