package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QueueTrackerViewModel(
    private val db: FirebaseFirestore,
    state: SavedStateHandle
) : InputFragmentViewModel(state) {

    val listenerOutletListMutex =  ReentrantCoroutineMutex()
    val listenerCapsterListMutex = ReentrantCoroutineMutex()
    val listenerReservationsMutex = ReentrantCoroutineMutex()
    val capsterListMutex = ReentrantCoroutineMutex()
    val reservationMutex = ReentrantCoroutineMutex()
    val animationMutex = ReentrantCoroutineMutex()

    // =========================================================
    // === UTILITAS DASAR
    // =========================================================

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.addItem(item: T) {
        val updated = (value ?: mutableListOf()).apply { add(item) }
        updateOnMain(updated)
    }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    // =======================================================================

    sealed class PendingCalculation {
        data object None : PendingCalculation()
        data class Recalculate(val isAllData: Boolean) : PendingCalculation()
    }

    private val _pendingCalculation = MutableLiveData<PendingCalculation>(PendingCalculation.None)
    val pendingCalculation: LiveData<PendingCalculation> = _pendingCalculation

    sealed class TriggerToast {
        data object LocalToast: TriggerToast()
        data class CommonToast(val message: String): TriggerToast()
    }

    private val _toastDetection = MutableLiveData<TriggerToast?>()
    val toastDetection: LiveData<TriggerToast?> = _toastDetection

    // LiveData for reservations and capsters
    private val _reservationDataList = MutableLiveData<List<ReservationData>>(emptyList())
    val reservationDataList: LiveData<List<ReservationData>> = _reservationDataList

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

//    private val _capsterNames = MutableLiveData<List<String>>(emptyList())
//    val capsterNames: LiveData<List<String>> = _capsterNames

    private val _capsterWaitingCount = MutableLiveData<Map<String, Int>>(emptyMap())
    val capsterWaitingCount: LiveData<Map<String, Int>> = _capsterWaitingCount

    private val _currentQueue = MutableLiveData<Map<String, String>>(emptyMap())
    val currentQueue: LiveData<Map<String, String>> = _currentQueue

    private val _updateUIBoard = MutableLiveData<Boolean?>()
    val updateUIBoard: LiveData<Boolean?> = _updateUIBoard

    private val _letsFilteringDataCapster = MutableLiveData<Boolean?>()
    val letsFilteringDataCapster: LiveData<Boolean?> = _letsFilteringDataCapster

    private val _displayFilteredCapsterResult = MutableLiveData<Boolean?>()
    val displayFilteredCapsterResult: LiveData<Boolean?> = _displayFilteredCapsterResult

    private val _filteredCapsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val filteredCapsterList: LiveData<List<UserEmployeeData>> = _filteredCapsterList

    private val _calculateDataReservation = MutableLiveData<Boolean?>()
    val calculateDataReservation: LiveData<Boolean?> = _calculateDataReservation

    private val _capsterWaitingQueues = MutableLiveData<Map<String, List<String>>>(emptyMap())
    val capsterWaitingQueues: LiveData<Map<String, List<String>>> = _capsterWaitingQueues

    fun setPendingCalculation(isAllData: Boolean) {
        viewModelScope.launch {
            _pendingCalculation.postValue(PendingCalculation.Recalculate(isAllData))
        }
    }

    fun clearPendingCalculation() {
        viewModelScope.launch {
            _pendingCalculation.value = PendingCalculation.None
        }
    }

    fun setCapsterWaitingQueues(data: Map<String, List<String>>) {
        viewModelScope.launch {
            _capsterWaitingQueues.postValue(data)
        }
    }

//    private val _reSetupDropdownCapster = MutableLiveData<Boolean?>()
//    val reSetupDropdownCapster: LiveData<Boolean?> = _reSetupDropdownCapster

    fun setUpdateUIBoard(withShimmer: Boolean?) {
        viewModelScope.launch {
            _updateUIBoard.postValue(withShimmer)
        }
    }

    fun setCalculateDataReservation(isAllData: Boolean?) {
        viewModelScope.launch {
            _calculateDataReservation.postValue(isAllData)
        }
    }

//    fun setReSetupDropdownCapster(reSetup: Boolean) {
//        _reSetupDropdownCapster.postValue(reSetup)
//    }

    override fun setOutletSelected(outlet: Outlet?) {
        viewModelScope.launch {
            _outletSelected.postValue(outlet)
        }
    }

    fun setCapsterList(
        capsterList: List<UserEmployeeData>,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        Log.d("CacheChecking", "setCapsterList --> capsterList size: ${capsterList.size}")
        viewModelScope.launch {
            _capsterList.postValue(capsterList)
            _setupDropdownFilter.postValue(setupDropdown)
            _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
        }
    }

    fun updateCapsterList(capsterList: List<UserEmployeeData>) {
        viewModelScope.launch {
            _capsterList.postValue(capsterList)
        }
    }

    override fun setupDropdownFilterWithNullState() {
        viewModelScope.launch {
            _setupDropdownFilter.postValue(false)
            _setupDropdownFilterWithNullState.postValue(false)
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

//    fun addCapsterNames(capsterNames: List<String>) {
//        Log.d("CacheChecking", "addCapsterNames --> capsterNames size: ${capsterNames.size}")
//        _capsterNames.postValue(capsterNames)
//    }

    fun updateActiveDevices(dataTellerRef: String) {
        viewModelScope.launch {
            Logger.d("CheckShimmer", "updateActiveDevices start")
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
                val outletDocRef = db.document(dataTellerRef)

                val task = withContext(Dispatchers.IO) {
                    outletDocRef
                        .update("active_devices", FieldValue.increment(1.toLong()))
                        .awaitWriteWithOfflineFallback(tag = "UpdateActiveDevices")
                }

                if (task.isSuccessful) {
                    Logger.d("CheckShimmer", "✅ Firestore updateActiveDevices success")
                    // toastViewModel.showToast("Layanan QueueTracker ${outletData.outletName}", false)
                } else {
                    Logger.d("CheckShimmer", "❌ Firestore updateActiveDevices failed")
                    _toastDetection.value = TriggerToast.CommonToast("Terjadi kesalahan saat memperbarui status aktif dari device!.")
                }
            } catch (e: Exception) {
                Logger.d("CheckShimmer", "❌ Firestore updateActiveDevices failed with exception: ${e.message}")
                _toastDetection.value = TriggerToast.CommonToast("Terjadi kesalahan saat memperbarui status aktif dari device!.")
            }
        }
    }

    suspend fun updateOutletCurrentQueue(outletSelected: Outlet) {
        try {
            val startTime = System.currentTimeMillis()

            val outletRef = db.document(outletSelected.outletReference)
            Logger.d("CheckShimmer", "🚀 Mulai update current_queue untuk outletRef: $outletRef")

            val task = withContext(Dispatchers.IO) {
                outletRef.update(
                    mapOf(
                        "current_queue" to outletSelected.currentQueue,
                        "timestamp_modify" to outletSelected.timestampModify
                    )
                ).awaitWriteWithOfflineFallback(tag = "UpdateOutletQueue")
            }

            val duration = System.currentTimeMillis() - startTime
            if (task.isSuccessful) {
                Logger.d("CheckShimmer", "✅ Update current_queue sukses (${duration} ms)")
            } else {
                Logger.e("CheckShimmer", "❌ Update current_queue gagal (${duration} ms)")
                throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            }
        } catch (e: Exception) {
            Logger.e("CheckShimmer", "❌ Exception update_current_queue: ${e.message}")
            throw e
        }
    }

    fun triggerFilteringDataCapster(withShimmer: Boolean?) {
        viewModelScope.launch {
            _letsFilteringDataCapster.postValue(withShimmer)
        }
    }

    fun setFilteredCapsterList(filteredCapsterList: List<UserEmployeeData>) {
        Log.d("CacheChecking", "setFilteredCapsterList --> filteredCapsterList size: ${filteredCapsterList.size}")
        viewModelScope.launch {
            _filteredCapsterList.postValue(filteredCapsterList)
        }
    }

    fun setCapsterToDisplay(withShimmer: Boolean?) {
        viewModelScope.launch {
            _displayFilteredCapsterResult.postValue(withShimmer)
        }
    }

    fun setCapsterWaitingCount(capsterWaitingCount: Map<String, Int>) {
        viewModelScope.launch {
            _capsterWaitingCount.postValue(capsterWaitingCount)
        }
    }

    fun setCurrentQueue(currentQueue: Map<String, String>) {
        viewModelScope.launch {
            _currentQueue.postValue(currentQueue)
        }
    }

    fun setReservationList(reservationDataList: List<ReservationData>, isAllData: Boolean?) {
        Log.d("CacheChecking", "addReservationList --> reservationList size: ${reservationDataList.size}")
        viewModelScope.launch {
            _reservationDataList.postValue(reservationDataList)
            _calculateDataReservation.postValue(isAllData)
        }
    }

    fun clearCapsterList() {
        viewModelScope.launch {
            _capsterList.value = emptyList()
        }
    }

//    fun clearCapsterNames() {
//        _capsterNames.value = emptyList()
//    }

    fun clearCapsterWaitingCount() {
        viewModelScope.launch {
            _capsterWaitingCount.value = emptyMap()
        }
    }

    fun clearCurrentQueue() {
        viewModelScope.launch {
            _currentQueue.value = emptyMap()
        }
    }

    fun clearReservationList() {
        viewModelScope.launch {
            _reservationDataList.value = emptyList()
        }
    }

    fun removeCapsterWaitingCountByKey(key: String) {
        viewModelScope.launch {
            val currentMap = capsterWaitingCount.value?.toMutableMap()
            if (currentMap != null) {
                currentMap.remove(key)
                _capsterWaitingCount.postValue(currentMap)
            }
        }
    }

    fun clearState() {
        viewModelScope.launch {
            _letsFilteringDataCapster.postValue(null)
            _displayFilteredCapsterResult.postValue(null)
            _updateUIBoard.postValue(null)
            _calculateDataReservation.postValue(null)
        }
    }

    fun clearToastDetection() {
        viewModelScope.launch {
            _toastDetection.postValue(null)
        }
    }

}