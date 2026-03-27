package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.UserInterface.Admin.ViewModel.ManageOutletViewModel.ResultState
import com.example.barberlink.UserInterface.Capster.ViewModel.EditOrderViewModel
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApproveBonViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int, val isCheck: Boolean = false, val oldStatus: String = ""): ResultState()
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
        newStatus: String,
        isBtnApprove: Boolean,
        index: Int
    ) {
        viewModelScope.launch {
            _updateStateResult.value = ResultState.Loading // Tampilkan loading
            // Gunakan coroutine agar tetap aman (non-blocking di UI)
            try {
                val bonRef = db.collection("${bonData.rootRef}/employee_bon")
                    .document(bonData.uid)

                val updateData = mutableMapOf<String, Any>(
                    "bon_status" to newStatus
                )

                // Jika tombol Approve ditekan, tambahkan perubahan return_status
                if (isBtnApprove) {
                    updateData["return_status"] = "Belum Bayar"
                }

                val task = withContext(Dispatchers.IO) {
                    bonRef.update(updateData).awaitWriteWithOfflineFallback(tag = "UpdateBonStatus")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Status Bon", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Status Bon", "Berhasil memeperbarui status pinjaman pegawai.")
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Status Bon", task.errorMessage.toString(), index)
                    else _updateStateResult.value = ResultState.Failure("Status Bon", "Gagal memeperbarui status pinjaman pegawai!", index)
                }
            } catch (e: Exception) {
                Logger.e("UpdateBonStatus", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Status Bon", "Gagal memeperbarui status pinjaman pegawai!", index)
            }
        }
    }

    fun updateReturnStatus(
        data: BonEmployeeData,
        bonData: BonEmployeeData,
        isChecked: Boolean,
        oldStatus: String,
        index: Int
    ) {
        viewModelScope.launch {
            bonData.returnStatus = data.returnStatus
            _updateStateResult.value = ResultState.Loading // Tampilkan loadin

            try {
                val bonRef = db.collection("${data.rootRef}/employee_bon")
                    .document(data.uid)

                val task = withContext(Dispatchers.IO) {
                    bonRef.set(data).awaitWriteWithOfflineFallback(tag = "UpdateReturnStatus")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Return Status", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Return Status", "Berhasil memperbarui status pengembalian pegawai.")
                } else {
//                    binding.switch2.isChecked = !isCheck
//                    binding.switch2.jumpDrawablesToCurrentState() // Kembalikan status switch ke semula
//                    bonData.returnStatus = oldStatus // Kembalikan status bonData ke semula
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Return Status", task.errorMessage.toString(), index, !isChecked, oldStatus)
                    else _updateStateResult.value = ResultState.Failure("Return Status", "Gagal memperbarui status pengembalian pegawai!", index, !isChecked, oldStatus)
                }
            } catch (e: Exception) {
                Logger.e("UpdateReturnStatus", "❌ Error: ${e.message}")
//                binding.switch2.isChecked = !isCheck
//                binding.switch2.jumpDrawablesToCurrentState() // Kembalikan status switch ke semula
//                bonData.returnStatus = oldStatus // Kembalikan status bonData ke semula
                _updateStateResult.value = ResultState.Failure("Return Status", "Gagal memperbarui status pengembalian pegawai!", index, !isChecked, oldStatus)
            }
        }
    }

}
