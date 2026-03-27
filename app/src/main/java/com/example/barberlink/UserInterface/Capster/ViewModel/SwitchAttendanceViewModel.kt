package com.example.barberlink.UserInterface.Capster.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwitchAttendanceViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val message: String): ResultState()
        data class Failure(val message: String, val isAttendance: Boolean): ResultState()
    }

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun updateAttendanceStatus(isAttendance: Boolean, userEmployeeData: UserEmployeeData) {
        // Jalankan update di coroutine agar aman & non-blocking
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val userRefPath = userEmployeeData.userRef
                if (userRefPath.isEmpty()) {
                    Logger.d("AttendanceCapster", "❌ Failed Process: userRef is empty")
                    _updateStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pengguna tidak valid!", isAttendance)
                    return@launch
                }

                val docsRef = db.document(userRefPath)
                Logger.d("AttendanceCapster", "updateAttendanceStatus >>> userRef: $userRefPath")

                val task = withContext(Dispatchers.IO) {
                    docsRef.update("attendance_status", isAttendance)
                        .awaitWriteWithOfflineFallback(tag = "UpdateAttendanceStatus")
                }

                if (task.isSuccessful) {
                    Logger.d("AttendanceCapster", "✅ Successfully updated (local/server)")
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success(task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Berhasil memperbarui status kehadiran pegawai.")
                } else {
                    Logger.d("AttendanceCapster", "❌ Update gagal, revert switch")
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure(task.errorMessage.toString(), isAttendance)
                    else _updateStateResult.value = ResultState.Failure("Gagal memperbarui status kehadiran pegawai!", isAttendance)
                }
            } catch (e: Exception) {
                Logger.e("AttendanceCapster", "❌ Exception saat update: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Gagal memperbarui status kehadiran pegawai!", isAttendance)
            }
        }
    }

}
