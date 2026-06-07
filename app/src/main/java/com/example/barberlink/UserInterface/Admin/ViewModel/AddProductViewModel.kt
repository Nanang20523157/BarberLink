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
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddProductViewModel(
    private val repository: ProductRepository,
    private val storage: FirebaseStorage,
    private val handle: SavedStateHandle
) : ViewModel() {

    val productListMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val allDataMutex = ReentrantCoroutineMutex()
    val listenerBarbershopMutex = ReentrantCoroutineMutex()
    val listenerOutletsMutex = ReentrantCoroutineMutex()
    val listenerProductsMutex = ReentrantCoroutineMutex()

    private val _originalProduct = MutableLiveData<Product>()
    val originalProduct: LiveData<Product> get() = _originalProduct

    private val _productParams = MutableLiveData<Product>()
    val productParams: LiveData<Product> get() = _productParams

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _isSaving = MutableLiveData<Boolean>()
    val isSaving: LiveData<Boolean> get() = _isSaving

    private val _saveResult = MutableLiveData<FirestoreResult<Unit>?>()
    val saveResult: LiveData<FirestoreResult<Unit>?> get() = _saveResult

    private val _allProducts = MutableLiveData<List<Product>>(emptyList())
    val allProducts: LiveData<List<Product>> get() = _allProducts

    private val _outletList = MutableLiveData<List<Outlet>>(emptyList())
    val outletList: LiveData<List<Outlet>> get() = _outletList

    private val _pendingImageUri = MutableLiveData<Uri?>()
    val pendingImageUri: LiveData<Uri?> get() = _pendingImageUri

    private val _categories = MutableLiveData<List<DataCategories>>()
    val categories: LiveData<List<DataCategories>> = _categories

    private val _generatedSku = MutableLiveData<String>()
    val generatedSku: LiveData<String> get() = _generatedSku

    fun setCategories(categoriesList: List<DataCategories>) {
        _categories.value = categoriesList
    }

    fun getProductCategories(adminUid: String) {
        viewModelScope.launch {
            val result = repository.getProductCategories(adminUid)
            if (result.isSuccessful) {
                _categories.value = result.data ?: emptyList()
            }
        }
    }

    fun addProductCategory(barbershopId: String, category: String) {
        viewModelScope.launch {
            val currentCategories = _categories.value?.toMutableList() ?: mutableListOf()
            if (!currentCategories.any { it.categoryName == category }) {
                currentCategories.add(com.example.barberlink.DataClass.DataCategories(categoryName = category))
                val result = repository.updateProductCategories(barbershopId, currentCategories.map { it.categoryName })
                if (result.isSuccessful) {
                    _categories.value = currentCategories
                }
            }
        }
    }

    fun deleteProductCategory(barbershopId: String, category: String) {
        viewModelScope.launch {
            val currentCategories = _categories.value?.toMutableList() ?: mutableListOf()
            val toRemove = currentCategories.find { it.categoryName == category }
            if (toRemove != null) {
                currentCategories.remove(toRemove)
                val result = repository.updateProductCategories(barbershopId, currentCategories.map { it.categoryName })
                if (result.isSuccessful) {
                    _categories.value = currentCategories
                }
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

    private fun generateInitials(text: String, targetLength: Int): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "X".repeat(targetLength)

        return if (words.size == 1) {
            val word = words[0].filter { it.isLetterOrDigit() }.uppercase()
            word.take(targetLength).padEnd(targetLength, 'X')
        } else {
            val charsPerWord = targetLength / words.size
            val remainder = targetLength % words.size
            var result = ""
            
            for (i in words.indices) {
                val takeCount = if (i == words.size - 1) charsPerWord + remainder else charsPerWord
                result += words[i].filter { it.isLetterOrDigit() }.uppercase().take(takeCount).padEnd(takeCount, 'X')
            }
            result.take(targetLength)
        }
    }

    fun onProductDataChanged(
        productName: String,
        category: String,
        productType: String,
        size: String,
        isHandmade: Boolean
    ) {
        val nameCode = if (productName.isNotBlank()) {
            productName.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(6)
        } else "PRD"
        
        val categoryCode = if (category.isNotBlank()) {
            category.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(3)
        } else ""

        val productTypeCode = productType.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(4)
        
        val digits = size.filter { it.isDigit() }
        val letters = size.filter { it.isLetter() }.uppercase()
        val sizeCode = (digits + letters).take(5)

        val sku = buildString {
            if (categoryCode.isNotBlank()) append(categoryCode).append("-")
            append(nameCode)
            if (productTypeCode.isNotBlank()) append("-").append(productTypeCode)
            if (sizeCode.isNotBlank()) append("-").append(sizeCode)
        }
        
        _generatedSku.value = sku
        
        _productParams.value?.let { product ->
            product.stockKeepingUnit = sku
            product.productCategory = category
            product.categoryCode = categoryCode
            if (isHandmade) {
                product.productBarcode = sku.replace("-", "")
            }
            _productParams.postValue(product)
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

    fun saveProduct(adminUid: String, barbershopId: String, isAddMode: Boolean) {
        viewModelScope.launch {
            val currentProduct = _productParams.value ?: return@launch

            _isSaving.value = true
            try {
                // Ensure we have a UID for storage path
                if (currentProduct.uid.isEmpty()) {
                    currentProduct.uid = repository.generateProductId(barbershopId)
                }

                // Fill additional fields
                currentProduct.rootRef = "barbershops/$barbershopId"

                // Handle New Category persistence
                val currentCategoryName = currentProduct.productCategory.trim()
                if (currentCategoryName.isNotEmpty()) {
                    val existingCategory = _categories.value?.find { 
                        it.categoryName.equals(currentCategoryName, ignoreCase = true) 
                    }
                    
                    if (existingCategory == null) {
                        val newCategory = DataCategories(
                            categoryName = currentCategoryName,
                            categoryCode = currentCategoryName.take(3).uppercase(),
                            barbershopRef = adminUid,
                            intendedFor = "Product" // Assuming default
                        )
                        repository.saveProductCategory(newCategory)
                        // Optional: Refresh local categories list
                        val updatedList = (_categories.value ?: emptyList()).toMutableList()
                        updatedList.add(newCategory)
                        _categories.postValue(updatedList)
                    }
                }

                // Handle Product Image Upload
                _pendingImageUri.value?.let { uri ->
                    val storageRef = storage.reference.child("products/images/${currentProduct.uid}.png")

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

                val result = if (isAddMode) {
                    repository.createProduct(barbershopId, currentProduct)
                } else {
                    repository.updateProduct(barbershopId, currentProduct)
                }

                if (result.isSuccessful) {
                    clearPendingImageUri()
                }
                _saveResult.postValue(result)
            } catch (e: Exception) {
                _saveResult.postValue(FirestoreResult(isSuccessful = false, errorMessage = e.message ?: "Unknown error"))
            } finally {
                _isSaving.postValue(false)
            }
        }
    }

    fun clearSaveResult() {
        viewModelScope.launch {
            _saveResult.value = null
        }
    }
    fun checkSkuUniqueness(barbershopId: String, sku: String): LiveData<Boolean?> {
        val resultLiveData = MutableLiveData<Boolean?>()
        viewModelScope.launch {
            // If SKU is unchanged from the original product, it's considered unique for this context
            if (_originalProduct.value?.stockKeepingUnit == sku) {
                resultLiveData.postValue(true)
                return@launch
            }
            
            val result = repository.checkSkuUniqueness(barbershopId, sku)
            if (result.isSuccessful) {
                resultLiveData.postValue(result.data)
            } else {
                resultLiveData.postValue(null) // Error case
            }
        }
        return resultLiveData
    }
}
