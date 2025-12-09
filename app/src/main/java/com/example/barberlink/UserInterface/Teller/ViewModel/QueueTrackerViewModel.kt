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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class QueueTrackerViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

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

    suspend fun setPendingCalculation(isAllData: Boolean) {
        withContext(Dispatchers.Main) {
            _pendingCalculation.postValue(PendingCalculation.Recalculate(isAllData))
        }
    }

    suspend fun clearPendingCalculation() {
        withContext(Dispatchers.Main) {
            _pendingCalculation.postValue(PendingCalculation.None)
        }
    }

    suspend fun setCapsterWaitingQueues(data: Map<String, List<String>>) {
        withContext(Dispatchers.Main) {
            _capsterWaitingQueues.postValue(data)
        }
    }

    suspend fun setUpdateUIBoard(withShimmer: Boolean?) {
        withContext(Dispatchers.Main) {
            _updateUIBoard.postValue(withShimmer)
        }
    }

    suspend fun setCalculateDataReservation(isAllData: Boolean?) {
        withContext(Dispatchers.Main) {
            _calculateDataReservation.postValue(isAllData)
        }
    }

    override suspend fun setOutletSelected(outlet: Outlet?) {
        withContext(Dispatchers.Main) {
            _outletSelected.postValue(outlet)
        }
    }

    suspend fun setCapsterList(
        capsterList: List<UserEmployeeData>,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        Log.d("CacheChecking", "setCapsterList --> capsterList size: ${capsterList.size}")
        withContext(Dispatchers.Main) {
            _capsterList.postValue(capsterList)
            _setupDropdownFilter.postValue(setupDropdown)
            _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
        }
    }

    suspend fun updateCapsterList(capsterList: List<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _capsterList.postValue(capsterList)
        }
    }

    override suspend fun setupDropdownFilterWithNullState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.postValue(false)
            _setupDropdownFilterWithNullState.postValue(false)
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    suspend fun triggerFilteringDataCapster(withShimmer: Boolean?) {
        withContext(Dispatchers.Main) {
            _letsFilteringDataCapster.postValue(withShimmer)
        }
    }

    suspend fun setFilteredCapsterList(filteredCapsterList: List<UserEmployeeData>) {
        Log.d("CacheChecking", "setFilteredCapsterList --> filteredCapsterList size: ${filteredCapsterList.size}")
        withContext(Dispatchers.Main) {
            _filteredCapsterList.postValue(filteredCapsterList)
        }
    }

    suspend fun setCapsterToDisplay(withShimmer: Boolean?) {
        withContext(Dispatchers.Main) {
            _displayFilteredCapsterResult.postValue(withShimmer)
        }
    }

    suspend fun setCapsterWaitingCount(capsterWaitingCount: Map<String, Int>) {
        withContext(Dispatchers.Main) {
            _capsterWaitingCount.postValue(capsterWaitingCount)
        }
    }

    suspend fun setCurrentQueue(currentQueue: Map<String, String>) {
        withContext(Dispatchers.Main) {
            _currentQueue.postValue(currentQueue)
        }
    }

    suspend fun setReservationList(reservationDataList: List<ReservationData>, isAllData: Boolean?) {
        Log.d("CacheChecking", "addReservationList --> reservationList size: ${reservationDataList.size}")
        withContext(Dispatchers.Main) {
            _reservationDataList.postValue(reservationDataList)
            _calculateDataReservation.postValue(isAllData)
        }
    }

    suspend fun clearCapsterList() {
        withContext(Dispatchers.Main) {
            _capsterList.postValue(emptyList())
        }
    }

    suspend fun clearCapsterWaitingCount() {
        withContext(Dispatchers.Main) {
            _capsterWaitingCount.postValue(emptyMap())
        }
    }

    suspend fun clearCurrentQueue() {
        withContext(Dispatchers.Main) {
            _currentQueue.postValue(emptyMap())
        }
    }

    suspend fun clearReservationList() {
        withContext(Dispatchers.Main) {
            _reservationDataList.postValue(emptyList())
        }
    }

    suspend fun removeCapsterWaitingCountByKey(key: String) {
        withContext(Dispatchers.Main) {
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


}