package com.example.barberlink.UserInterface.Admin.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData

class ManageOutletViewModel : ViewModel() {

    private val _outletList = MutableLiveData<MutableList<Outlet>>().apply { emptyList<Outlet>() }
    val outletList: LiveData<MutableList<Outlet>> = _outletList

    private val _outletSelected = MutableLiveData<Outlet?>()
    val outletSelected: LiveData<Outlet?> = _outletSelected

    private val _userEmployeeDataList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val userEmployeeDataList: LiveData<MutableList<UserEmployeeData>> = _userEmployeeDataList

    private val _extendedStateMap = MutableLiveData<MutableMap<String, Boolean>>(mutableMapOf())
    val extendedStateMap: LiveData<MutableMap<String, Boolean>> = _extendedStateMap

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    fun setOutletList(outletList: MutableList<Outlet>) {
        _outletList.value = outletList
    }

    fun updateOutletList(newOutletList: MutableList<Outlet>) {
        val currentList = _outletList.value ?: mutableListOf()

        // Buat list untuk menambahkan dan menghapus item
        val outletsToRemove = currentList.filter { current ->
            newOutletList.none { new -> new.uid == current.uid }
        }

        // Update atau tambahkan outlet baru ke currentList
        newOutletList.forEach { newOutlet ->
            val existingOutlet = currentList.find { it.uid == newOutlet.uid }
            if (existingOutlet != null) {
                // Update data outlet yang sudah ada
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
                // Tambahkan outlet baru jika tidak ada
                currentList.add(newOutlet)
            }
        }

        // Hapus outlet yang tidak ada di newList
        outletsToRemove.forEach { outlet ->
            currentList.remove(outlet)
        }

        // Update live data dengan currentList yang diperbarui
        _outletList.value = currentList
    }

    fun setOutletSelected(outlet: Outlet?) {
        _outletSelected.value = outlet
    }

    fun setEmployeeList(userEmployeeDataList: MutableList<UserEmployeeData>) {
        _userEmployeeDataList.value = userEmployeeDataList
    }

    fun setCapsterList(capsterList: List<UserEmployeeData>) {
        _capsterList.value = capsterList
    }

    fun clearAllDataCapster() {
        _capsterList.value = emptyList()
    }

    fun clearAllDataOutlet() {
        _outletList.value = mutableListOf()
    }

    fun clearAllDataEmployee() {
        _userEmployeeDataList.value = mutableListOf()
    }

    fun setExtendedStateMap(map: MutableMap<String, Boolean>) {
        _extendedStateMap.value = map
    }

    fun updateState(key: String, value: Boolean) {
        val currentMap = _extendedStateMap.value ?: mutableMapOf()
        currentMap[key] = value
        _extendedStateMap.value = currentMap // Memicu observer LiveData
    }

    fun removeState(key: String) {
        val currentMap = _extendedStateMap.value ?: mutableMapOf()
        if (currentMap.containsKey(key)) {
            currentMap.remove(key)
            _extendedStateMap.value = currentMap // Memicu observer LiveData
        }
    }

    fun clearAllExtendedStates() {
        _extendedStateMap.value = mutableMapOf() // Mengosongkan map
    }


}