package com.example.barberlink.UserInterface.Capster.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SwitchCapsterViewModel(state: SavedStateHandle) : InputFragmentViewModel(state) {

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

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private var capsterData: UserEmployeeData? = null

    fun getCapsterData(): UserEmployeeData? {
        return runBlocking {
            capsterData
        }
    }

    suspend fun setCapsterData(data: UserEmployeeData?) {
        Logger.d("DisplayCapsterData", "setCapsterData: ${data?.fullname}")
        withContext(Dispatchers.Main) {
            capsterData = data
        }
    }

    suspend fun setCapsterList(listCapster: List<UserEmployeeData>, setupDropdown: Boolean?, isSavedInstanceStateNull: Boolean?) {
        withContext(Dispatchers.Main) {
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

    override suspend fun setupDropdownFilterWithNullState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.value = false
            _setupDropdownFilterWithNullState.value = false
        }
        Log.d("ObjectReferences", "neptunes 5")
    }

    override suspend fun setupDropdownWithInitialState() {
        withContext(Dispatchers.Main) {
            _setupDropdownFilter.value = true
            _setupDropdownFilterWithNullState.value = true
        }
    }

    fun clearCapsterData() {
        viewModelScope.launch {
            capsterData = null
        }
    }

}