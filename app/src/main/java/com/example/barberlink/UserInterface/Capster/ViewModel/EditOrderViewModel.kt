package com.example.barberlink.UserInterface.Capster.ViewModel

import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.DataClass.FirestoreResult
import com.example.barberlink.DataClass.ItemInfo
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.Service
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditOrderViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val message: String): ResultState()
        data class Failure(val message: String): ResultState()
    }

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    private var currentReservationData: ReservationData? = null
    private var duplicateReservationData: ReservationData? = null

    fun setCurrentReservationData(reservationData: ReservationData) {
        viewModelScope.launch {
            currentReservationData = reservationData
        }
    }

    fun setDuplicateReservationData(reservationData: ReservationData) {
        viewModelScope.launch {
            duplicateReservationData = reservationData
        }
    }

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun updatingDataReservation(serviceList: List<Service>, bundlingList: List<BundlingPackage>, useUidApplicantCapsterRef: Boolean, paymentMethod: String, userUID: String?) {
        viewModelScope.launch {
            _updateStateResult.value = ResultState.Loading

            val totalShareProfit = calculateTotalShareProfit(serviceList, bundlingList, userUID ?: "----------------")
            val accumulatedItemPrice = bundlingList.sumOf { it.bundlingQuantity * it.priceToDisplay }
                .let { result ->
                    serviceList.sumOf { it.serviceQuantity * it.priceToDisplay }
                        .plus(result)
                }

            val finalPrice = accumulatedItemPrice - (currentReservationData?.paymentDetail?.coinsUsed ?: 0) - (currentReservationData?.paymentDetail?.promoUsed ?: 0 )
            val orderInfo = createOrderInfoList(serviceList, bundlingList)

            duplicateReservationData?.apply {
                this.shareProfitCapsterRef = if (useUidApplicantCapsterRef) currentReservationData?.shareProfitCapsterRef ?: "" else currentReservationData?.capsterInfo?.capsterRef ?: ""
                this.capsterInfo?.shareProfit = totalShareProfit.toInt()
                this.paymentDetail.subtotalItems = accumulatedItemPrice
                this.paymentDetail.finalPrice = finalPrice
                this.paymentDetail.paymentMethod = paymentMethod

                this.itemInfo = orderInfo
            }

            duplicateReservationData?.let { reservation ->
                updateReservationToFirestore(reservation,
                    onSuccess = {
                        if (it.displayMessage) _updateStateResult.value = ResultState.Success(it.errorMessage.toString())
                        else _updateStateResult.value = ResultState.Success("Berhasil memperbarui data reservasi.")
                    },
                    onFailure = {
                        if (it.displayMessage) _updateStateResult.value = ResultState.Failure(it.errorMessage.toString())
                        else _updateStateResult.value = ResultState.Failure("Gagal memperbarui data reservasi!")
                    }
                )
            } ?: run {
                _updateStateResult.value = ResultState.Failure("Gagal memperbarui data reservasi!")
            }
        }
    }

    private suspend fun updateReservationToFirestore(
        reservationData: ReservationData,
        onSuccess: (task: FirestoreResult<Unit>) -> Unit,
        onFailure: (task: FirestoreResult<Unit>) -> Unit
    ) {
        // Jalankan di background thread agar tidak memblokir UI
        try {
            val reservationRef = db.document(reservationData.dataRef)

            // 🔹 Menjalankan operasi Firestore dengan dukungan offline
            val task = withContext(Dispatchers.IO) {
                reservationRef.set(reservationData).awaitWriteWithOfflineFallback(tag = "UpdateReservation")
            }

            if (task.isSuccessful) {
                // ✅ Sukses penuh atau sukses lokal (offline mode)
                onSuccess(task)
            } else {
                onFailure(task)
            }
        } catch (e: Exception) {
            // ❌ Jika benar-benar gagal (exception runtime, dataRef salah, dll)
            onFailure(
                FirestoreResult(
                    isSuccessful = false,
                    errorMessage = "Gagal memperbarui data reservasi!"
                )
            )
        }
    }

    private fun createOrderInfoList(serviceList: List<Service>, bundlingList: List<BundlingPackage>): List<ItemInfo> {
        val itemInfoList = mutableListOf<ItemInfo>()

        // Proses bundlingPackagesList dari ViewModel
        bundlingList.filter { it.bundlingQuantity > 0 }.forEach { bundling ->
            val itemInfo = ItemInfo(
                itemQuantity = bundling.bundlingQuantity,
                itemRef = bundling.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = false,  // Karena ini adalah bundling, nonPackage diatur menjadi false
                sumOfPrice = bundling.bundlingQuantity * bundling.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        // Proses servicesList dari ViewModel
        serviceList.filter { it.serviceQuantity > 0 }.forEach { service ->
            val itemInfo = ItemInfo(
                itemQuantity = service.serviceQuantity,
                itemRef = service.uid,  // Menggunakan atribut yang sesuai untuk referensi
                nonPackage = true,  // Karena ini adalah service, nonPackage diatur menjadi true
                sumOfPrice = service.serviceQuantity * service.priceToDisplay
            )
            itemInfoList.add(itemInfo)
        }

        return itemInfoList
    }

    private fun calculateTotalShareProfit(
        serviceList: List<Service>,
        bundlingList: List<BundlingPackage>,
        capsterUid: String
    ): Double {
        var totalShareProfit = 0.0

        if (capsterUid != "----------------") {
            // Hitung untuk setiap service
            for (service in serviceList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (service.applyToGeneral) {
                    service.resultsShareAmount?.get("all") ?: 0
                } else {
                    service.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val serviceShare = if (service.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * service.servicePrice * service.serviceQuantity
                } else { // fee
                    resultsShareAmount * service.serviceQuantity
                }
                totalShareProfit += serviceShare.toDouble()
            }

            // Hitung untuk setiap bundling package
            for (bundling in bundlingList) {
                // Ambil nilai share berdasarkan format dan apakah general atau specific capster
                val resultsShareAmount = if (bundling.applyToGeneral) {
                    bundling.resultsShareAmount?.get("all") ?: 0
                } else {
                    bundling.resultsShareAmount?.get(capsterUid) ?: 0
                }

                val bundlingShare = if (bundling.resultsShareFormat == "persen") {
                    (resultsShareAmount / 100.0) * bundling.packagePrice * bundling.bundlingQuantity
                } else { // fee
                    resultsShareAmount * bundling.bundlingQuantity
                }
                totalShareProfit += bundlingShare.toDouble()
            }
        }

        return totalShareProfit
    }

}