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
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Repository.ProductRepository
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.collections.plus

class AddProductViewModel(
    private val repository: ProductRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : InputFragmentViewModel(handle) {

    companion object {
        private const val PRODUCT_PARAMS_KEY = "product_params"
        private const val PENDING_IMAGE_URI_KEY = "pending_image_uri"
        private const val CURRENT_MODE_KEY = "current_mode"
        private const val BARBERSHOP_ID_KEY = "barbershop_id"
        private const val PRODUCT_SELECTED_ID_KEY = "product_selected_id"
    }

    val productListMutex = ReentrantCoroutineMutex()
    val categoryListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerCategoriesMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()

    private val _originalProduct = MutableLiveData<Product>()
    val originalProduct: LiveData<Product> get() = _originalProduct

    private val _productParams = handle.getLiveData<Product>(PRODUCT_PARAMS_KEY)
    val productParams: LiveData<Product> get() = _productParams

    private val _currentMode = handle.getLiveData<Int>(CURRENT_MODE_KEY, 0)
    val currentMode: LiveData<Int> get() = _currentMode

    private val _barbershopId = handle.getLiveData<String>(BARBERSHOP_ID_KEY, "")
    val barbershopId: LiveData<String> get() = _barbershopId

    private val _productSelectedId = handle.getLiveData<String>(PRODUCT_SELECTED_ID_KEY, "")
    val productSelectedId: LiveData<String> get() = _productSelectedId

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _productList = MutableLiveData<List<Product>>(emptyList())
    val productList: LiveData<List<Product>> get() = _productList

    private val _pendingImageUri = handle.getLiveData<Uri?>(PENDING_IMAGE_URI_KEY)
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

    private val _categoryList = MutableLiveData<List<DataCategories>>()
    val categoryList: LiveData<List<DataCategories>> = _categoryList

    private val _generatedSku = MutableLiveData<String>()
    val generatedSku: LiveData<String> get() = _generatedSku

    fun setCategories(categoryList: List<DataCategories>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            _categoryList.value = categoryList
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

    fun addProductCategory(barbershopId: String, category: String) {
        viewModelScope.launch {
            val currentCategories = _categoryList.value?.toMutableList() ?: mutableListOf()
            if (!currentCategories.any { it.categoryName == category }) {
                currentCategories.add(com.example.barberlink.DataClass.DataCategories(categoryName = category))
//                val result = repository.updateProductCategories(barbershopId, currentCategories.map { it.categoryName })
//                if (result.isSuccessful) {
//                    _categoryList.value = currentCategories
//                }
            }
        }
    }

    fun deleteProductCategory(barbershopId: String, category: String) {
        viewModelScope.launch {
            val currentCategories = _categoryList.value?.toMutableList() ?: mutableListOf()
            val toRemove = currentCategories.find { it.categoryName == category }
            if (toRemove != null) {
                currentCategories.remove(toRemove)
//                val result = repository.updateProductCategories(barbershopId, currentCategories.map { it.categoryName })
//                if (result.isSuccessful) {
//                    _categoryList.value = currentCategories
//                }
            }
        }
    }

    fun setUserAdminData(userAdminData: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = userAdminData
        }
    }

    fun setOriginalProduct(product: Product) {
        viewModelScope.launch {
            _originalProduct.value = product
        }
    }

    fun updateProductParams(product: Product) {
        viewModelScope.launch {
            _productParams.value = product
        }
    }

    fun onProductDataChanged(
        productName: String,
        categoryCode: String,
        productType: String,
        size: String
    ) {
        val nameCode = if (productName.isNotBlank()) {
            productName.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(6)
        } else "XXXXXX"

//        val categoryCode = if (category.isNotBlank()) {
//            category.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(3)
//        } else "XXX"

        val productTypeCode = if (productType.isNotBlank()) {
            productType.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(4)
        } else ""

        val digits = size.filter { it.isDigit() }
        val letters = size.filter { it.isLetter() }.uppercase()
        val sizeCode = if (size.isNotBlank()) {
            (digits + letters).take(5)
        } else "XXXXX"

        val sku = buildString {
            append(nameCode).append("-")
            if (categoryCode.isNotBlank()) append(categoryCode).append("-")
            if (productTypeCode.isNotBlank()) append(productTypeCode).append("-")
            if (sizeCode.isNotBlank()) append(sizeCode)
        }
        
        _generatedSku.value = sku
    }

    fun setProductList(products: List<Product>) {
        viewModelScope.launch {
            _productList.value = products
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

    fun setProductSelectedId(id: String) {
        viewModelScope.launch {
            _productSelectedId.value = id
        }
    }

    fun setCurrentMode(mode: Int) {
        viewModelScope.launch {
            _currentMode.value = mode
        }
    }

    fun saveProduct(isAddMode: Boolean) {
        viewModelScope.launch {
            val currentProduct = _productParams.value ?: return@launch
            val bId = _barbershopId.value ?: return@launch

            _isSaving.value = true
            try {
                // Handle Product Image Upload
                _pendingImageUri.value?.let { uri ->
                    val storageRef = storage.reference.child("products/${currentProduct.uid}")

                    // Delete old image if it exists and path is different
                    if (currentProduct.imgProduct.isNotEmpty()) {
                        try {
                            val oldReference = FirebaseStorage.getInstance().getReferenceFromUrl(currentProduct.imgProduct)
                            if (oldReference.path != storageRef.path) {
                                oldReference.delete().await()
                            }
                        } catch (e: Exception) {
                            // Non-critical, ignore deletion errors
                        }
                    }

                    // Upload new image
                    storageRef.putFile(uri).await()
                    val downloadUrl = storageRef.downloadUrl.await()
                    currentProduct.imgProduct = downloadUrl.toString()
                }

                val db = FirebaseFirestore.getInstance()
                val productRef = db.collection("barbershops").document(bId).collection("products").document(currentProduct.uid)

                val result = if (isAddMode) {
                    repository.createProduct(bId, currentProduct)
                } else {
                    repository.updateProduct(bId, currentProduct)
                }

                _isSaving.value = false
                if (result.isSuccessful) {
                    clearPendingImageUri()
                    currentProduct.dataRef = productRef.path
                }
                if (isAddMode && result.isSuccessful) _productList.value = _productList.value.orEmpty() + currentProduct
                else if (result.isSuccessful) {
                    _productList.value = _productList.value.orEmpty().map { if (it.uid == currentProduct.uid) currentProduct else it }
                }
                _saveResult.postValue(result)
            } catch (e: Exception) {
                _isSaving.value = false
                _saveResult.postValue(FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error"))
            }
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }

}
