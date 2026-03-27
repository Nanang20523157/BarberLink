package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SwitchCapsterViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

    val capsterListMutex = ReentrantCoroutineMutex()

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>()
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private var capsterData: UserEmployeeData? = null

    fun getCapsterData(): UserEmployeeData? {
        return runBlocking {
            capsterData
        }
    }

    fun setCapsterData(data: UserEmployeeData?) {
        Logger.d("DisplayCapsterData", "setCapsterData: ${data?.fullname}")
        viewModelScope.launch {
            capsterData = data
        }
    }

    fun setCapsterList(listCapster: List<UserEmployeeData>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        viewModelScope.launch {
            if (listCapster.isNotEmpty()) {
                capsterData?.let { data ->
                    if (setupDropdown == false && isSavedInstanceStateNull == true) {
                        capsterData = listCapster.find { it.uid == data.uid }
                    }
                    Logger.d("DisplayCapsterData", "setCapsterList: ${capsterData?.fullname}")
                } ?: run {
                    Logger.d("DisplayCapsterData", "setCapsterList: capsterData is null")
                }
            }
            _capsterList.value = listCapster
            _setupDropdownFilter.value = setupDropdown
            _setupDropdownFilterWithNullState.value = isSavedInstanceStateNull
        }
    }

    fun setCapsterRoles(list: List<EmployeeRolesData>) {
        viewModelScope.launch {
            val capsters = _capsterList.value ?: emptyList()

            if (capsters.isNotEmpty()) {
                capsterListMutex.withStateLock {
                    capsters.forEach { capster ->
                        capster.roleDetail = list.find { it.roleName == capster.role }
                    }
                    _capsterList.value = capsters
                }
            }
        }
    }

    override fun setupDropdownFilterWithNullState() {
        Log.d("ObjectReferences", "neptunes 5")
        viewModelScope.launch {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
    }

    override fun setupDropdownWithInitialState() {
        viewModelScope.launch {
            _setupDropdownFilter.value = true
            _setupDropdownFilterWithNullState.value = true
        }
    }

    override fun clearDropdownStateValue() {
        viewModelScope.launch {
            _setupDropdownFilter.value = null
            _setupDropdownFilterWithNullState.value = null
        }
    }

    fun clearCapsterData() {
        viewModelScope.launch {
            capsterData = null
        }
    }

}
