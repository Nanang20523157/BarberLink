package com.example.barberlink.Helper

import android.content.Context
import com.example.barberlink.DataClass.ServiceIcon
import com.example.barberlink.Utils.Logger
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

object ServiceIconCache {
    private const val PREFS_NAME = "service_icon_cache_prefs"
    private const val KEY_ICON_URLS = "icon_urls"

    private var appContext: Context? = null
    private var memoryCachedUrls: List<String>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val cachedIcons: List<ServiceIcon>?
        get() {
            val urls = getSavedUrls() ?: return null
            return urls.map { ServiceIcon(iconUrl = it, isSelected = false) }
        }

    fun setCachedIcons(icons: List<ServiceIcon>) {
        val urls = icons.map { it.iconUrl }
        saveUrls(urls)
    }

    private fun getSavedUrls(): List<String>? {
        if (memoryCachedUrls != null) {
            return memoryCachedUrls
        }
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ICON_URLS, null) ?: return null
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val urls: List<String> = Gson().fromJson(json, type)
            memoryCachedUrls = urls
            urls
        } catch (e: Exception) {
            Logger.e("ServiceIconCache", "Error parsing cached URLs", e)
            null
        }
    }

    private fun saveUrls(urls: List<String>?) {
        memoryCachedUrls = urls
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (urls != null) {
            val json = Gson().toJson(urls)
            prefs.edit { putString(KEY_ICON_URLS, json) }
        } else {
            prefs.edit { remove(KEY_ICON_URLS) }
        }
    }

    suspend fun preloadIcons() {
        try {
            Logger.d("ServiceIconCache", "Preloading service icons from Firebase Storage...")
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference.child("services/icons")
            val result = storageRef.listAll().await()
            val urls = result.items.map { item ->
                item.downloadUrl.await().toString()
            }
            saveUrls(urls)
            Logger.d("ServiceIconCache", "Preload completed and saved to local. Count: ${urls.size}")
        } catch (e: Exception) {
            Logger.e("ServiceIconCache", "Error preloading service icons", e)
        }
    }
}
