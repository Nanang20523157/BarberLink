package com.example.barberlink.Repository

import com.example.barberlink.DataClass.DataCategories
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.Product
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import com.google.firebase.firestore.FirebaseFirestore

class ProductRepository(private val db: FirebaseFirestore) {

    fun getProductCollection(barbershopId: String) = db.collection("barbershops").document(barbershopId).collection("products")

    fun generateProductId(barbershopId: String): String {
        return getProductCollection(barbershopId).document().id
    }

    suspend fun getProduct(barbershopId: String, productId: String): FirestoreResult<Product?> {
        val result = getProductCollection(barbershopId).document(productId).awaitGetWithOfflineFallback(tag = "GetProduct")
        return if (result.isSuccessful) {
            val product = result.data?.toObject(Product::class.java)
            if (product != null) {
                product.dataRef = result.data.reference.path
            }
            FirestoreResult(data = product, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = null, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun createProduct(barbershopId: String, product: Product): FirestoreResult<Unit> {
        val documentRef = getProductCollection(barbershopId).document(product.uid)
        product.rootRef = "barbershops/$barbershopId"

        return documentRef.set(product).awaitWriteWithOfflineFallback(tag = "CreateProduct")
    }

    suspend fun updateProduct(barbershopId: String, product: Product): FirestoreResult<Unit> {
        val documentRef = getProductCollection(barbershopId).document(product.uid)
        return documentRef.set(product).awaitWriteWithOfflineFallback(tag = "UpdateProduct")
    }

    suspend fun deleteProduct(barbershopId: String, productId: String): FirestoreResult<Unit> {
        return getProductCollection(barbershopId).document(productId).delete().awaitWriteWithOfflineFallback(tag = "DeleteProduct")
    }

    suspend fun getProductCategories(adminUid: String): FirestoreResult<List<DataCategories>> {
        val result = db.collection("product_categories")
            .whereIn("barbershop_ref", listOf(adminUid, "All"))
            .awaitGetWithOfflineFallback(tag = "GetProductCategories")

        return if (result.isSuccessful) {
            val categories = result.data?.documents?.mapNotNull { it.toObject(DataCategories::class.java) } ?: emptyList()
            FirestoreResult(data = categories, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = emptyList(), isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun updateProductCategories(barbershopId: String, categories: List<String>): FirestoreResult<Unit> {
        val documentRef = db.collection("barbershops")
            .document(barbershopId)
            .collection("meta")
            .document("categories")

        return documentRef.set(mapOf("products" to categories))
            .awaitWriteWithOfflineFallback(tag = "UpdateProductCategories")
    }

    suspend fun getProductCategoriesList(barbershopId: String): FirestoreResult<List<String>> {
        val result = db.collection("barbershops")
            .document(barbershopId)
            .collection("meta")
            .document("categories")
            .awaitGetWithOfflineFallback(tag = "GetProductCategoriesList")

        return if (result.isSuccessful) {
            val categories = result.data?.get("products") as? List<String> ?: emptyList()
            FirestoreResult(data = categories, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = emptyList(), isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun checkSkuUniqueness(barbershopId: String, sku: String): FirestoreResult<Boolean> {
        val result = getProductCollection(barbershopId)
            .whereEqualTo("stock_keeping_unit", sku)
            .limit(1)
            .awaitGetWithOfflineFallback(tag = "CheckSkuUniqueness")

        return if (result.isSuccessful) {
            val isUnique = result.data?.isEmpty ?: true
            FirestoreResult(data = isUnique, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            // If error, we return false as a safety measure (treat as not unique or unknown)
            FirestoreResult(data = false, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun getProductCountByCategory(barbershopId: String, category: String): FirestoreResult<Int> {
        val result = getProductCollection(barbershopId)
            .whereEqualTo("product_category", category)
            .awaitGetWithOfflineFallback(tag = "GetProductCountByCategory")

        return if (result.isSuccessful) {
            val count = result.data?.size() ?: 0
            FirestoreResult(data = count, isSuccessful = true, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        } else {
            FirestoreResult(data = 0, isSuccessful = false, displayMessage = result.displayMessage, errorMessage = result.errorMessage)
        }
    }

    suspend fun saveProductCategory(category: DataCategories): FirestoreResult<Unit> {
        val documentRef = if (category.uid.isEmpty()) {
            db.collection("product_categories").document().apply { category.uid = id }
        } else {
            db.collection("product_categories").document(category.uid)
        }
        return documentRef.set(category).awaitWriteWithOfflineFallback(tag = "SaveProductCategory")
    }
}
