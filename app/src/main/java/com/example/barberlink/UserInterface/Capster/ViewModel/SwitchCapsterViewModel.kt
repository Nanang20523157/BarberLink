package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.barberlink.DataClass.UserEmployeeData

class SwitchCapsterViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {
    private val _capsterList = MutableLiveData<List<UserEmployeeData>>()
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    fun setCapsterList(listCapster: List<UserEmployeeData>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        _capsterList.value = listCapster
        _setupDropdownFilter.value = setupDropdown
        _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
    }

    override fun setupDropdownFilterWithNullState() {
        _setupDropdownFilter.value = false
        _setupDropdownFilterWithNullState.value = false
        Log.d("ObjectReferences", "neptunes 5")
    }

    override fun setupDropdownWithInitialState() {
        _setupDropdownFilter.value = true
        _setupDropdownFilterWithNullState.value = true
    }

}