package com.example.barberlink.UserInterface.SignIn.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class SelectOutletViewModel : ViewModel() {

    val listenerOutletDataMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val outletsMutex = ReentrantCoroutineMutex()

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

    private val _outletSelected = MutableLiveData<Outlet>()
    val outletSelected: LiveData<Outlet> = _outletSelected

    private val _outletList = MutableLiveData<MutableList<Outlet>>().apply { emptyList<Outlet>() }
    val outletList: LiveData<MutableList<Outlet>> = _outletList

    private val _filteredOutletList = MutableLiveData<MutableList<Outlet>>().apply { emptyList<Outlet>() }
    val filteredOutletList: LiveData<MutableList<Outlet>> = _filteredOutletList

    private val _letsFilteringDataOutlet = MutableLiveData<Boolean?>()
    val letsFilteringDataOutlet: LiveData<Boolean?> = _letsFilteringDataOutlet

    private val _displayFilteredOutletResult = MutableLiveData<Boolean?>()
    val displayFilteredOutletResult: LiveData<Boolean?> = _displayFilteredOutletResult

    suspend fun setOutletList(outletList: MutableList<Outlet>) {
        withContext(Dispatchers.Main) {
            _outletList.postValue(outletList)
        }
    }

    suspend fun setOutletSelected(outlet: Outlet) {
        withContext(Dispatchers.Main) {
            _outletSelected.postValue(outlet)
        }
    }

    suspend fun triggerFilteringDataOutlet(withShimmer: Boolean) {
        withContext(Dispatchers.Main) {
            _letsFilteringDataOutlet.postValue(withShimmer)
        }
    }

    suspend fun setFilteredOutletList(outletList: MutableList<Outlet>) {
        withContext(Dispatchers.Main) {
            _filteredOutletList.postValue(outletList)
        }
    }

    suspend fun displayFilteredOutletResult(withShimmer: Boolean) {
        withContext(Dispatchers.Main) {
            _displayFilteredOutletResult.postValue(withShimmer)
        }
    }

    fun clearState() {
        viewModelScope.launch {
            _letsFilteringDataOutlet.postValue(null)
            _displayFilteredOutletResult.postValue(null)
        }
    }

}