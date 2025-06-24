package com.example.barberlink.UserInterface.SignIn.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.Outlet

class SelectOutletViewModel : ViewModel() {

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

    fun setOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        _outletList.postValue(outletList)
    }

    fun setOutletSelected(outlet: Outlet) {
        _outletSelected.postValue(outlet)
    }

    fun triggerFilteringDataOutlet(withShimmer: Boolean) {
        _letsFilteringDataOutlet.postValue(withShimmer)
    }

    fun setFilteredOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        _filteredOutletList.postValue(outletList)
    }

    fun displayFilteredOutletResult(withShimmer: Boolean) {
        _displayFilteredOutletResult.postValue(withShimmer)
    }

    fun clearState() {
        _letsFilteringDataOutlet.postValue(null)
        _displayFilteredOutletResult.postValue(null)
    }

}