package com.example.barberlink.UserInterface.Teller.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExitTrackerViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data object Success: ResultState()
        data class Failure(val message: String): ResultState()
    }

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun updateActiveDevices(dataTellerRef: String) {
        Logger.d("CheckShimmer", "updateActiveDevices start")
        viewModelScope.launch {
//            if (withTransaction) {
//                // Mode TRANSACTION: hanya online, tidak offline-aware
//                db.runTransaction { transaction ->
//                    val currentActiveDevices = outletSelected.activeDevices
//                    outletSelected.activeDevices = currentActiveDevices + change
//                    transaction.update(outletDocRef, "active_devices", outletSelected.activeDevices)
//                }.await()
//
//                Logger.d("CheckShimmer", "✅ Firestore transaction success")
//            }

            try {
                _updateStateResult.postValue(ResultState.Loading)
                val outletDocRef = db.document(dataTellerRef)

                val task = withContext(Dispatchers.IO) {
                    outletDocRef
                        .update("active_devices", FieldValue.increment((-1).toLong()))
                        .awaitWriteWithOfflineFallback(tag = "UpdateActiveDevices")
                }

                if (task.isSuccessful) {
                    Logger.d("CheckShimmer", "✅ Firestore updateActiveDevices success")
                    _updateStateResult.postValue(ResultState.Success)
                } else {
                    Logger.d("CheckShimmer", "❌ Firestore updateActiveDevices failed")
                    if (task.displayMessage) _updateStateResult.postValue(ResultState.Failure(task.errorMessage.toString()))
                    else _updateStateResult.postValue(ResultState.Failure("Terjadi kesalahan saat memperbarui status aktif dari device!."))
                }
            } catch (e: Exception) {
                Logger.d("CheckShimmer", "❌ Firestore updateActiveDevices failed with exception: ${e.message}")
                _updateStateResult.postValue(ResultState.Failure("Terjadi kesalahan saat memperbarui status aktif dari device!."))
            }
        }
    }

}