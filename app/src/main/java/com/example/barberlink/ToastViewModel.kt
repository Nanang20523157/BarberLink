package com.example.barberlink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.Manager.ToastQueueManager
import com.example.barberlink.Utils.Logger

class ToastViewModel(application: Application) : AndroidViewModel(application) {

    val toastManager = ToastQueueManager(
        appContext = application.applicationContext,
        scope = viewModelScope
    )

    fun showToast(message: String, important: Boolean) {
        toastManager.addToast(message, important)
    }

    override fun onCleared() {
        Logger.d("CheckShimmer", "onCleared")
        toastManager.clear()
        super.onCleared()
    }

}
