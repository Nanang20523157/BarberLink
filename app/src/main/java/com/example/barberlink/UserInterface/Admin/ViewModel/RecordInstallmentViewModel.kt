package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BonDetails
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class RecordInstallmentViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val message: String): ResultState()
        data class Failure(val message: String): ResultState()
    }

    private val _savingStateResult = MutableLiveData<ResultState?>()
    val savingStateResult: LiveData<ResultState?> = _savingStateResult
    private var userEmployeeData: UserEmployeeData? = null
    private lateinit var bonEmployeeData: BonEmployeeData
    private var isInSaveProcess: Boolean = false

    fun getIsSaveProcess(): Boolean {
        return runBlocking {
            isInSaveProcess
        }
    }

    fun setIsSaveProcess(value: Boolean) {
        viewModelScope.launch {
            isInSaveProcess = value
        }
    }

    fun setSavingStateResult(value: ResultState?) {
        viewModelScope.launch {
            _savingStateResult.value = value
        }
    }

    fun setUserEmployeeData(userData: UserEmployeeData) {
        viewModelScope.launch {
            userEmployeeData = userData
        }
    }

    fun setBonEmployeeData(bonData: BonEmployeeData) {
        viewModelScope.launch {
            bonEmployeeData = bonData
        }
    }

     fun saveEmployeeBon(userInstallment: Int, userRemainingBon: Int) {
        viewModelScope.launch {
            userEmployeeData?.let {
                val rootRef = it.rootRef.ifEmpty { null } ?: run {
                    _savingStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pegawai tidak valid!")
                    return@let
                }

                isInSaveProcess = true
                _savingStateResult.value = ResultState.Loading

                val returnStatus = when (userInstallment) {
                    bonEmployeeData.bonDetails.nominalBon -> {
                        "Lunas"
                    }
                    0 -> {
                        "Belum Bayar"
                    }
                    else -> {
                        "Terangsur"
                    }
                }

                val bonDetails = BonDetails(
                    nominalBon = bonEmployeeData.bonDetails.nominalBon,
                    remainingBon = userRemainingBon,
                    installmentsBon = userInstallment
                )

                bonEmployeeData.apply {
                    this.bonDetails = bonDetails
                    this.returnStatus = returnStatus
                }

                saveEmployeeBonToFirestore(rootRef)
            } ?: run {
                _savingStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pegawai tidak valid!")
            }
        }
    }

    private suspend fun saveEmployeeBonToFirestore(rootRef: String) {
        // Jalankan coroutine agar tidak blocking UI
        try {
            val bonRef = db.document(rootRef)
                .collection("employee_bon")
                .document(bonEmployeeData.uid)

            // 🔹 Gunakan awaitWriteWithOfflineFallback agar tetap lanjut walau offline
            val task = withContext(Dispatchers.IO) {
                bonRef.set(bonEmployeeData).awaitWriteWithOfflineFallback(tag = "SaveEmployeeBon")
            }

            if (task.isSuccessful) {
                if (task.displayMessage) _savingStateResult.value = ResultState.Success(task.errorMessage.toString())
                else _savingStateResult.value = ResultState.Success("Berhasil memperbarui data angsuran pegawai.")
            } else {
                isInSaveProcess = false
                if (task.displayMessage) _savingStateResult.value = ResultState.Failure(task.errorMessage.toString())
                else _savingStateResult.value = ResultState.Failure("Gagal memperbarui data angsuran pegawai!")
            }
        } catch (e: Exception) {
            Logger.e("SaveEmployeeBon", "❌ Gagal menyimpan data: ${e.message}")
            isInSaveProcess = false
            _savingStateResult.value = ResultState.Failure("Gagal memperbarui data angsuran pegawai!")
        }
    }

}
