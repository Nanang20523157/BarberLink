package com.example.barberlink.UserInterface.Capster.ViewModel

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.DailyCapital
import com.example.barberlink.DataClass.DataCreator
import com.example.barberlink.DataClass.LocationPoint
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Admin.ViewModel.RecordInstallmentViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class CapitalInputViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val message: String): ResultState()
        data class Failure(val message: String): ResultState()
    }

    private val _savingStateResult = MutableLiveData<ResultState?>()
    val savingStateResult: LiveData<ResultState?> = _savingStateResult

    private lateinit var userAdminData: UserAdminData
    private lateinit var userPegawaiData: UserEmployeeData

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


    fun setUserAdminData(userData: UserAdminData) {
        viewModelScope.launch {
            userAdminData = userData
        }
    }

    fun setUserPegawaiData(userData: UserEmployeeData) {
        viewModelScope.launch {
            userPegawaiData = userData
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun saveDailyCapital(capitalAmount: Int, outletSelected: Outlet?, uidDailyCapital: String, timeStampFilter: Timestamp) {
        viewModelScope.launch {
            outletSelected?.let { outletSelected ->
                if (outletSelected.rootRef.isEmpty()) {
                    _savingStateResult.value = ResultState.Failure("Gagal menyimpan data modal harian outlet!")
                    return@let
                }

                isInSaveProcess = true
                _savingStateResult.value = ResultState.Loading

                // val differenceCurrentCapital = capitalAmount - previousCapitalAmount
                var dailyCapital = DailyCapital()

                if (::userAdminData.isInitialized) {
                    val dataCreator = DataCreator<UserData>(
                        userFullname = userAdminData.ownerName,
                        userPhone = userAdminData.phone,
                        userPhoto = userAdminData.imageCompanyProfile,
                        userRef = userAdminData.userRef,
                        userRole = "Owner"
                    )
                    val locationPoint = LocationPoint(
                        placeName = outletSelected.outletName,
                        locationAddress = outletSelected.outletAddress,
                        latitude = outletSelected.latitudePoint,
                    )
                    dailyCapital = DailyCapital(
                        timestampCreated = timeStampFilter,
                        outletCapital = capitalAmount,
                        uid = uidDailyCapital,
                        rootRef = "barbershops/${userAdminData.uid}",
                        outletIdentifier = outletSelected.uid,
                        locationPoint = locationPoint,
                        dataCreator = dataCreator
                    )
                } else if (::userPegawaiData.isInitialized) {
                    val dataCreator = DataCreator<UserData>(
                        userFullname = userPegawaiData.fullname,
                        userPhone = userPegawaiData.phone,
                        userPhoto = userPegawaiData.photoProfile,
                        userRef = userPegawaiData.userRef,
                        userRole = "Employee"
                    )
                    val locationPoint = LocationPoint(
                        placeName = outletSelected.outletName,
                        locationAddress = outletSelected.outletAddress,
                        latitude = outletSelected.latitudePoint,
                    )
                    dailyCapital = DailyCapital(
                        timestampCreated = timeStampFilter,
                        outletCapital = capitalAmount,
                        uid = uidDailyCapital,
                        rootRef = userPegawaiData.rootRef,
                        outletIdentifier = outletSelected.uid,
                        locationPoint = locationPoint,
                        dataCreator = dataCreator
                    )
                }

                saveDailyCapitalToFirestore(outletSelected, dailyCapital, uidDailyCapital)
            } ?: run {
                _savingStateResult.value = ResultState.Failure("Gagal menyimpan data modal harian outlet!")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun saveDailyCapitalToFirestore(outletSelected: Outlet, dailyCapital: DailyCapital, uidDailyCapital: String) {
        try {
            if (uidDailyCapital.isNotEmpty()) {
                // 🔹 Mode update dokumen lama
                val capitalRef = db.document(outletSelected.rootRef)
                    .collection("daily_capital")
                    .document(uidDailyCapital)

                val task = withContext(Dispatchers.IO) {
                    capitalRef
                        .set(dailyCapital)
                        .awaitWriteWithOfflineFallback(tag = "UpdateDailyCapital")
                }

                if (task.isSuccessful) {
                    // end process with local toast checking
                    if (task.displayMessage) _savingStateResult.value = ResultState.Success(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Success("Berhasil memperbarui data modal harian outlet.")
                } else {
                    isInSaveProcess = false
                    if (task.displayMessage) _savingStateResult.value = ResultState.Failure(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Failure("Gagal memperbarui data modal harian outlet!")
                }
            } else {
                // 🔹 Mode dokumen baru (generate ID otomatis)
                val newDocRef = db.document(outletSelected.rootRef)
                    .collection("daily_capital")
                    .document()

                val capitalData = dailyCapital.copy(uid = newDocRef.id)

                val task = withContext(Dispatchers.IO) {
                    newDocRef.set(capitalData)
                        .awaitWriteWithOfflineFallback(tag = "CreateDailyCapital")
                }

                if (task.isSuccessful) {
                    // end process with local toast checking
                    if (task.displayMessage) _savingStateResult.value = ResultState.Success(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Success("Berhasil menyimpan data modal harian baru.")
                } else {
                    isInSaveProcess = false
                    if (task.displayMessage) _savingStateResult.value = ResultState.Failure(task.errorMessage.toString())
                    else _savingStateResult.value = ResultState.Failure("Gagal menyimpan data modal harian baru!")
                }
            }
        } catch (e: Exception) {
            // 🔹 Tangani error fatal
            isInSaveProcess = false
            _savingStateResult.value = ResultState.Failure("Gagal memproses data modal harian outlet!")
        }
    }

}