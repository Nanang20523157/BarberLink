package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Reservation
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.UserInterface.Capster.ViewModel.InputFragmentViewModel

class QueueTrackerViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    sealed class PendingCalculation {
        data object None : PendingCalculation()
        data class Recalculate(val isAllData: Boolean) : PendingCalculation()
    }

    private val _pendingCalculation = MutableLiveData<PendingCalculation>(PendingCalculation.None)
    val pendingCalculation: LiveData<PendingCalculation> = _pendingCalculation

    fun setPendingCalculation(isAllData: Boolean) {
        _pendingCalculation.postValue(PendingCalculation.Recalculate(isAllData))
    }

    fun clearPendingCalculation() {
        _pendingCalculation.postValue(PendingCalculation.None)
    }

    // LiveData for reservations and capsters
    private val _reservationList = MutableLiveData<List<Reservation>>(emptyList())
    val reservationList: LiveData<List<Reservation>> = _reservationList

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

    fun setCapsterWaitingQueues(data: Map<String, List<String>>) {
        _capsterWaitingQueues.postValue(data)
    }

//    private val _reSetupDropdownCapster = MutableLiveData<Boolean?>()
//    val reSetupDropdownCapster: LiveData<Boolean?> = _reSetupDropdownCapster

    fun setUpdateUIBoard(withShimmer: Boolean?) {
        _updateUIBoard.postValue(withShimmer)
    }

    fun setCalculateDataReservation(isAllData: Boolean?) {
        _calculateDataReservation.postValue(isAllData)
    }

//    fun setReSetupDropdownCapster(reSetup: Boolean) {
//        _reSetupDropdownCapster.postValue(reSetup)
//    }

    override fun setOutletSelected(outlet: Outlet?) {
        _outletSelected.postValue(outlet)
    }

    fun setCapsterList(
        capsterList: List<UserEmployeeData>,
        setupDropdown: Boolean?,
        isSavedInstanceStateNull: Boolean?
    ) {
        Log.d("CacheChecking", "setCapsterList --> capsterList size: ${capsterList.size}")
        _capsterList.postValue(capsterList)
        _setupDropdownFilter.postValue(setupDropdown)
        _setupDropdownFilterWithNullState.postValue(isSavedInstanceStateNull)
    }

    fun updateCapsterList(capsterList: List<UserEmployeeData>) {
        _capsterList.postValue(capsterList)
    }

    override fun setupDropdownFilterWithNullState() {
        _setupDropdownFilter.postValue(false)
        _setupDropdownFilterWithNullState.postValue(false)
        Log.d("ObjectReferences", "neptunes 5")
    }

//    fun addCapsterNames(capsterNames: List<String>) {
//        Log.d("CacheChecking", "addCapsterNames --> capsterNames size: ${capsterNames.size}")
//        _capsterNames.postValue(capsterNames)
//    }

    fun triggerFilteringDataCapster(withShimmer: Boolean?) {
        _letsFilteringDataCapster.postValue(withShimmer)
    }

    fun setFilteredCapsterList(filteredCapsterList: List<UserEmployeeData>) {
        Log.d("CacheChecking", "setFilteredCapsterList --> filteredCapsterList size: ${filteredCapsterList.size}")
        _filteredCapsterList.postValue(filteredCapsterList)
    }

    fun setCapsterToDisplay(withShimmer: Boolean?) {
        _displayFilteredCapsterResult.postValue(withShimmer)
    }

    fun clearState() {
        _letsFilteringDataCapster.value = null
        _displayFilteredCapsterResult.value = null
        _updateUIBoard.value = null
        _calculateDataReservation.value = null
//        _reSetupDropdownCapster.value = null
    }

    fun setCapsterWaitingCount(capsterWaitingCount: Map<String, Int>) {
        _capsterWaitingCount.postValue(capsterWaitingCount)
    }

    fun setCurrentQueue(currentQueue: Map<String, String>) {
        _currentQueue.postValue(currentQueue)
    }

    fun setReservationList(reservationList: List<Reservation>, isAllData: Boolean?) {
        Log.d("CacheChecking", "addReservationList --> reservationList size: ${reservationList.size}")
        _reservationList.postValue(reservationList)
        _calculateDataReservation.postValue(isAllData)
    }

    fun clearCapsterList() {
        _capsterList.postValue(emptyList())
    }

//    fun clearCapsterNames() {
//        _capsterNames.postValue(emptyList())
//    }

    fun clearCapsterWaitingCount() {
        _capsterWaitingCount.postValue(emptyMap())
    }

    fun clearCurrentQueue() {
        _currentQueue.postValue(emptyMap())
    }

    fun clearReservationList() {
        _reservationList.postValue(emptyList())
    }

    fun removeCapsterWaitingCountByKey(key: String) {
        val currentMap = capsterWaitingCount.value?.toMutableMap()
        currentMap?.remove(key)
        _capsterWaitingCount.postValue(currentMap)
    }


}