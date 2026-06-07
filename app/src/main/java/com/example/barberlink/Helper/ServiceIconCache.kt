package com.example.barberlink.Helper

import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.Utils.Logger
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object ServiceIconCache {
    var cachedIcons: List<ServiceIcon>? = null
        private set

    fun setCachedIcons(icons: List<ServiceIcon>) {
        cachedIcons = icons
    }

    suspend fun preloadIcons() {
        if (cachedIcons != null && cachedIcons!!.isNotEmpty()) return
        try {
            Logger.d("ServiceIconCache", "Preloading service icons from Firebase Storage...")
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference.child("services/icons")
            val result = storageRef.listAll().await()
            val iconList = result.items.map { item ->
                val url = item.downloadUrl.await().toString()
                ServiceIcon(iconUrl = url, isSelected = false)
            }
            cachedIcons = iconList
            Logger.d("ServiceIconCache", "Preload completed. Count: ${iconList.size}")
        } catch (e: Exception) {
            Logger.e("ServiceIconCache", "Error preloading service icons", e)
        }
    }
}
