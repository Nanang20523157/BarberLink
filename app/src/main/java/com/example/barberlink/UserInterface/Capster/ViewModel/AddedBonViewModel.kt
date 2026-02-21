package com.example.barberlink.UserInterface.Capster.ViewModel

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddedBonViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data class Loading(val isRestore: Boolean = false): ResultState()
        data class Success(val type: String, val message: String, val bonData: BonEmployeeData): ResultState()
        data class Failure(val type: String, val message: String, val isRestore: Boolean = false): ResultState()
    }

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun updateBonStatus(
        bonData: BonEmployeeData,
        newStatus: String
    ) {
        viewModelScope.launch {
            _updateStateResult.value = ResultState.Loading() // Tampilkan loading
            // Jalankan di coroutine agar non-blocking
            try {
                val bonRef = db.collection("${bonData.rootRef}/employee_bon")
                    .document(bonData.uid)

                // 🔥 Offline-aware Firestore update
                val task = withContext(Dispatchers.IO) {
                    bonRef.update("bon_status", newStatus).awaitWriteWithOfflineFallback(tag = "UpdateBonStatus")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Update Status", task.errorMessage.toString(), bonData)
                    else _updateStateResult.value = ResultState.Success("Update Status", "Berhasil memperbarui status pinjaman Anda.", bonData)
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Update Status", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Failure("Update Status", "Gagal memperbarui status pinjaman Anda!")
                }
            } catch (e: Exception) {
                Logger.e("UpdateBonStatus", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Update Status", "Gagal memperbarui status pinjaman Anda!")
            }
        }
    }

    fun deleteBonItem(
        bonData: BonEmployeeData,
        isLastPosition: Boolean
    ) {
        viewModelScope.launch {
            bonData.isDeleteLastPosition = isLastPosition
            _updateStateResult.value = ResultState.Loading() // Tampilkan loading

            try {
                val bonRef = db.collection("${bonData.rootRef}/employee_bon")
                    .document(bonData.uid)

                // 🔥 Offline-aware Firestore delete
                val task = withContext(Dispatchers.IO) {
                    bonRef.delete().awaitWriteWithOfflineFallback(tag = "DeleteBonItem")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Delete Item", task.errorMessage.toString(), bonData)
                    else _updateStateResult.value = ResultState.Success("Delete Item", "", bonData)
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Delete Item", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Failure("Delete Item", "Gagal menghapus data pinjaman Anda!")
                }
            } catch (e: Exception) {
                Logger.e("DeleteBonItem", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete Item", "Gagal menghapus data pinjaman Anda!")
            }
        }
    }

    fun restoreDeletedData(bonData: BonEmployeeData, rootRef: String) {
        // Referensi dokumen bon pegawai
        viewModelScope.launch {
            var isRestoreDeletedData = false
            if (bonData.isDeleteLastPosition) isRestoreDeletedData = true
            _updateStateResult.value = ResultState.Loading(isRestoreDeletedData)

            try {
                val bonRef = db.document(rootRef)
                    .collection("employee_bon")
                    .document(bonData.uid)

                // 🔹 Gunakan offline-aware Firestore update
                val task = withContext(Dispatchers.IO) {
                    bonRef.set(bonData)
                        .awaitWriteWithOfflineFallback(tag = "RestoreDeletedBon")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Restore Item", task.errorMessage.toString(), bonData)
                    else _updateStateResult.value = ResultState.Success("Restore Item", "Berhasil mengembalikan data pinjaman Anda!", bonData)
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Restore Item", task.errorMessage.toString(), isRestoreDeletedData)
                    else _updateStateResult.value = ResultState.Failure("Restore Item", "Gagal mengembalikan data pinjaman Anda!", isRestoreDeletedData)
                }
            } catch (e: Exception) {
                _updateStateResult.value = ResultState.Failure("Restore Item", "Gagal mengembalikan data pinjaman Anda!", isRestoreDeletedData)
            }
        }
    }

}