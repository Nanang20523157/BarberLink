package com.example.barberlink.UserInterface.Capster.ViewModel

import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BonDetails
import com.example.barberlink.DataClass.BonEmployeeData
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.example.barberlink.Utils.Logger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.text.ifEmpty

class FormInputBonViewModel(
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

    fun saveEmployeeBon(bonAmount: Int, returnType: String, userReasonNotes: String, timeStampFilter: Timestamp) {
        viewModelScope.launch {
            userEmployeeData?.let {
                val rootRef = it.rootRef.ifEmpty { null } ?: run {
                    _savingStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pegawai tidak valid!")
                    return@let
                }

                isInSaveProcess = true
                _savingStateResult.value = ResultState.Loading

                // HARUSNYA VARIABEL userPhoto DIHILANGKAN KARENA NILAINYA TIDAK AKAN DIPERBARUI KETIKA USER MENGUBAH PHOTO PROFILNYA - HARUSNYA GET USER DATA DULU
                val dataCreator = DataCreator<UserData>(
                    userFullname = userEmployeeData?.fullname ?: "",
                    userRole = userEmployeeData?.role ?: "",
                    userPhoto = userEmployeeData?.photoProfile ?: "",
                    userPhone = userEmployeeData?.phone ?: "",
                    userRef = userEmployeeData?.userRef ?: ""
                )

                val bonDetails = BonDetails(
                    nominalBon = bonAmount,
                    remainingBon = bonAmount,
                    installmentsBon = 0
                )

                bonEmployeeData.apply {
                    this.bonStatus = if (this.uid.isEmpty()) "waiting" else this.bonStatus
                    this.returnStatus = if (this.uid.isEmpty()) "" else this.returnStatus
                    this.returnType = returnType
                    this.reasonNoted = userReasonNotes
                    this.timestampCreated = timeStampFilter
                    this.rootRef = userEmployeeData?.rootRef ?: ""
                    this.dataCreator = dataCreator
                    this.bonDetails = bonDetails
                }

                saveEmployeeBonToFirestore(rootRef)
            } ?: run {
                _savingStateResult.value = ResultState.Failure("Tidak dapat melanjutkan proses karena data pegawai tidak valid!")
            }
        }
    }

    private suspend fun saveEmployeeBonToFirestore(rootRef: String) {
        try {
            val bonRef = db.document(rootRef)
                .collection("employee_bon")

            if (bonEmployeeData.uid.isNotEmpty()) {
                // 🔹 Update dokumen yang sudah ada
                val task = withContext(Dispatchers.IO) {
                    bonRef.document(bonEmployeeData.uid).set(bonEmployeeData)
                        .awaitWriteWithOfflineFallback(tag = "SaveEmployeeBon")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _savingStateResult.value = ResultState.Success(task.errorMessage.toString())
                    _savingStateResult.value = ResultState.Success("Berhasil memperbarui data pinjaman Anda.")
                } else {
                    isInSaveProcess = false
                    if (task.displayMessage) _savingStateResult.value = ResultState.Failure(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Failure("Gagal memperbarui data pinjaman Anda!")
                }
            } else {
                // 🔹 Simpan dokumen baru (ID otomatis)
                val newDocRef = bonRef.document()
                val bonData = bonEmployeeData.copy(uid = newDocRef.id)

                val task = withContext(Dispatchers.IO) {
                    newDocRef.set(bonData)
                        .awaitWriteWithOfflineFallback(tag = "SaveNewEmployeeBon")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _savingStateResult.value = ResultState.Success(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Success("Berhasil menyimpan data pinjaman baru.")
                } else {
                    isInSaveProcess = false
                    if (task.displayMessage) _savingStateResult.value = ResultState.Failure(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Failure("Gagal menyimpan data pinjaman baru!")
                }
            }
        } catch (e: Exception) {
            isInSaveProcess = false
            _savingStateResult.value = ResultState.Failure("Gagal memproses data pinjaman Anda!")
        }
    }

}