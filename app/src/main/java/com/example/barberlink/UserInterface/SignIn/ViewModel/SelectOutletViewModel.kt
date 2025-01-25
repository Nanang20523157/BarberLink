package com.example.barberlink.UserInterface.SignIn.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.Outlet

class SelectOutletViewModel : ViewModel() {

    private val _outletList = MutableLiveData<MutableList<Outlet>>().apply { emptyList<Outlet>() }
    val outletList: LiveData<MutableList<Outlet>> = _outletList

    private val _filteredOutletList = MutableLiveData<MutableList<Outlet>>().apply { emptyList<Outlet>() }
    val filteredOutletList: LiveData<MutableList<Outlet>> = _filteredOutletList

    // private val _isDisableShimmer = MutableLiveData<Boolean>()
    // val isDisableShimmer: LiveData<Boolean> = _isDisableShimmer

    private val _letsFilteringDataOutlet = MutableLiveData<Boolean?>()
    val letsFilteringDataOutlet: LiveData<Boolean?> = _letsFilteringDataOutlet

    private val _displayFilteredOutletResult = MutableLiveData<Boolean?>()
    val displayFilteredOutletResult: LiveData<Boolean?> = _displayFilteredOutletResult

    fun setOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        _outletList.value = outletList
    }

    fun triggerFilteringDataOutlet(withShimmer: Boolean) {
        _letsFilteringDataOutlet.value = withShimmer
    }

    fun setFilteredOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        _filteredOutletList.value = outletList
    }

    fun displayFilteredOutletResult(withShimmer: Boolean) {
        _displayFilteredOutletResult.value = withShimmer
    }

    fun clearState() {
        _letsFilteringDataOutlet.value = null
        _displayFilteredOutletResult.value = null
    }

}