package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ManageOutletViewModel : ViewModel() {

    val outletsMutex = ReentrantCoroutineMutex()
    val employeesMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()

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

    private val _outletList = MutableLiveData<MutableList<Outlet>>(mutableListOf())
    val outletList: LiveData<MutableList<Outlet>> = _outletList

    private val _outletSelected = MutableLiveData<Outlet?>()
    val outletSelected: LiveData<Outlet?> = _outletSelected

    private val _employeeList = MutableLiveData<MutableList<UserEmployeeData>>(mutableListOf())
    val employeeList: LiveData<MutableList<UserEmployeeData>> = _employeeList

    private val _extendedStateMap = MutableLiveData<MutableMap<String, Boolean>>(mutableMapOf())
    val extendedStateMap: LiveData<MutableMap<String, Boolean>> = _extendedStateMap

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    // ========================= OUTLET ==========================

    suspend fun setOutletList(outletList: MutableList<Outlet>) {
        withContext(Dispatchers.Main) {
            _outletList.value = outletList
        }
    }

    suspend fun updateOutletList(newOutletList: MutableList<Outlet>) {
        withContext(Dispatchers.Default) {
            val currentList = _outletList.value ?: mutableListOf()

            // Outlet yang sudah tidak ada di data baru akan dihapus
            val outletsToRemove = currentList.filter { current ->
                newOutletList.none { new -> new.uid == current.uid }
            }

            // Update outlet yang ada atau tambahkan outlet baru
            newOutletList.forEach { newOutlet ->
                val existingOutlet = currentList.find { it.uid == newOutlet.uid }
                if (existingOutlet != null) {
                    existingOutlet.apply {
                        activeDevices = newOutlet.activeDevices
                        currentQueue = newOutlet.currentQueue
                        imgOutlet = newOutlet.imgOutlet
                        lastUpdated = newOutlet.lastUpdated
                        listBestDeals = newOutlet.listBestDeals
                        listBundling = newOutlet.listBundling
                        listCustomers = newOutlet.listCustomers
                        listEmployees = newOutlet.listEmployees
                        listProducts = newOutlet.listProducts
                        listServices = newOutlet.listServices
                        openStatus = newOutlet.openStatus
                        outletAccessCode = newOutlet.outletAccessCode
                        outletName = newOutlet.outletName
                        outletPhoneNumber = newOutlet.outletPhoneNumber
                        outletRating = newOutlet.outletRating
                        rootRef = newOutlet.rootRef
                        taglineOrDesc = newOutlet.taglineOrDesc
                        timestampModify = newOutlet.timestampModify
                        isCollapseCard = newOutlet.isCollapseCard
                        outletReference = newOutlet.outletReference
                    }
                } else {
                    currentList.add(newOutlet)
                }
            }

            // Hapus outlet yang sudah tidak ada
            outletsToRemove.forEach { currentList.remove(it) }

            _outletList.updateOnMain(currentList)
        }
    }

    suspend fun clearAllDataOutlet() {
        withContext(Dispatchers.Main) {
            _outletList.value = mutableListOf()
        }
    }

    suspend fun setOutletSelected(outlet: Outlet?) {
        withContext(Dispatchers.Main) {
            _outletSelected.value = outlet
        }
    }

    // ========================= EMPLOYEE ==========================

    suspend fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _employeeList.value = employeeList
        }
    }

    suspend fun clearAllDataEmployee() {
        withContext(Dispatchers.Main) {
            _employeeList.value = mutableListOf()
        }
    }

    // ========================= CAPSTER ==========================
    suspend fun setCapsterList(capsterList: List<UserEmployeeData>) {
        withContext(Dispatchers.Main) {
            _capsterList.value = capsterList
        }
    }

    suspend fun clearAllDataCapster() {
        withContext(Dispatchers.Main) {
            _capsterList.value = emptyList()
        }
    }

    // ========================= EXTENDED STATE MAP ==========================
    suspend fun setExtendedStateMap(map: MutableMap<String, Boolean>) {
        withContext(Dispatchers.Main) {
            _extendedStateMap.value = map
        }
    }

    suspend fun updateState(key: String, value: Boolean) {
        withContext(Dispatchers.Main) {
            val currentMap = _extendedStateMap.value ?: mutableMapOf()
            currentMap[key] = value
            _extendedStateMap.value = currentMap
        }
    }

    suspend fun removeState(key: String) {
        withContext(Dispatchers.Main) {
            val currentMap = _extendedStateMap.value ?: mutableMapOf()
            if (currentMap.containsKey(key)) {
                currentMap.remove(key)
                _extendedStateMap.value = currentMap
            }
        }
    }

    suspend fun clearAllExtendedStates() {
        withContext(Dispatchers.Main) {
            _extendedStateMap.value = mutableMapOf()
        }
    }

}
