package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.barberlink.DataClass.DataCategories

class ManageProductViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    val productsMutex = ReentrantCoroutineMutex()
    val listenerProductListMutex = ReentrantCoroutineMutex()

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int, val oldCode: String = ""): ResultState()
    }

    private val _productList = MutableLiveData<MutableList<Product>>().apply { value = mutableListOf() }
    val productList: LiveData<MutableList<Product>> = _productList

    private val _categoryList = MutableLiveData<MutableList<DataCategories>>().apply { value = mutableListOf() }
    val categoryList: LiveData<MutableList<DataCategories>> = _categoryList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setProductList(productList: MutableList<Product>) {
        viewModelScope.launch {
            _productList.value = productList
        }
    }

    fun setCategoryList(categoryList: MutableList<DataCategories>) {
        viewModelScope.launch {
            _categoryList.value = categoryList
        }
    }

    fun clearAllDataProduct() {
        viewModelScope.launch {
            _productList.clearList()
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val productRef = db.document(product.rootRef)
                    .collection("products")
                    .document(product.uid)

                // Delete product image from Storage if it exists
                if (product.imgProduct.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(product.imgProduct)
                        imageRef.delete().await()
                        Logger.d("DeleteProduct", "Image deleted successfully: ${product.imgProduct}")
                    } catch (e: Exception) {
                        Logger.e("DeleteProduct", "Failed to delete image: ${e.message}")
                    }
                }

                val task = withContext(Dispatchers.IO) {
                    productRef.delete().awaitWriteWithOfflineFallback(tag = "DeleteProduct")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Delete", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Delete", "Produk \"${product.productName}\" berhasil dihapus.")
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Delete", task.errorMessage.toString(), -1)
                    else _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus produk!", -1)
                }
            } catch (e: Exception) {
                Logger.e("DeleteProduct", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus produk!", -1)
            }
        }
    }

    fun updateProductList(newProductList: MutableList<Product>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentList = _productList.value ?: mutableListOf()

            // Build list of items to remove
            val productsToRemove = currentList.filter { current ->
                newProductList.none { new -> new.uid == current.uid }
            }

            // Update or add new products
            newProductList.forEach { newProduct ->
                val existingProduct = currentList.find { it.uid == newProduct.uid }
                if (existingProduct != null) {
                    // Update existing product data
                    existingProduct.apply {
                        applyToGeneral = newProduct.applyToGeneral
                        categoryCode = newProduct.categoryCode
                        imgProduct = newProduct.imgProduct
                        minimumQuantity = newProduct.minimumQuantity
                        productBarcode = newProduct.productBarcode
                        productCategory = newProduct.productCategory
                        productCounting = newProduct.productCounting
                        productDescription = newProduct.productDescription
                        productName = newProduct.productName
                        productPrice = newProduct.productPrice
                        productRating = newProduct.productRating
                        productSize = newProduct.productSize
                        productType = newProduct.productType
                        purchasePrice = newProduct.purchasePrice
                        resultsShareAmount = newProduct.resultsShareAmount
                        resultsShareFormat = newProduct.resultsShareFormat
                        rootRef = newProduct.rootRef
                        stockKeepingUnit = newProduct.stockKeepingUnit
                        stockQuantity = newProduct.stockQuantity
                        tag = newProduct.tag
                        dataRef = newProduct.dataRef
                        uid = newProduct.uid
                    }
                } else {
                    currentList.add(newProduct)
                }
            }

            // Remove products not in newList
            productsToRemove.forEach { product ->
                currentList.remove(product)
            }

            _productList.updateOnMain(currentList)
        }
    }
}
