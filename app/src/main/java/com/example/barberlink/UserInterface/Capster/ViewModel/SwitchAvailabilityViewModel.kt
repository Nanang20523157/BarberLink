package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwitchAvailabilityViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val message: String): ResultState()
        data class Failure(val message: String, val isAvailable: Boolean): ResultState()
    }

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun updateAvailabilityStatus(isAvailable: Boolean, userEmployeeData: UserEmployeeData) {
        // Jalankan update di coroutine agar aman & non-blocking
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val userRefPath = userEmployeeData.userRef
                if (userRefPath.isEmpty()) {
                    Logger.d("AvailableCapster", "❌ Failed Process: userRef is empty")
                    _updateStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pengguna tidak valid!", isAvailable)
                    return@launch
                }

                val userRef = db.document(userRefPath)
                Logger.d("AvailableCapster", "updateAvailabilityStatus >>> userRef: $userRefPath")

                val task = withContext(Dispatchers.IO) {
                    userRef.update("availability_status", isAvailable)
                        .awaitWriteWithOfflineFallback(tag = "UpdateAvailabilityStatus")
                }

                if (task.isSuccessful) {
                    Logger.d("AvailableCapster", "✅ Successfully updated (local/server)")
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success(task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Berhasil memperbarui status ketersediaan pegawai.")
                } else {
                    Logger.d("AvailableCapster", "❌ Update gagal, revert switch")
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure(task.errorMessage.toString(), isAvailable)
                    else _updateStateResult.value = ResultState.Failure("Gagal memperbarui status ketersediaan pegawai!", isAvailable)
                }
            } catch (e: Exception) {
                Logger.e("AvailableCapster", "❌ Exception saat update: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Gagal memperbarui status ketersediaan pegawai!", isAvailable)
            }
        }
    }

}