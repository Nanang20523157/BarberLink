package com.example.barberlink.UserInterface.Admin.ViewModel

import android.app.Application
import android.provider.Settings.Global.getString
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Teller.ViewModel.ExitTrackerViewModel
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils
import com.example.barberlink.Utils.Logger
import com.example.barberlink.databinding.ItemListManageOutletAdapterBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ManageOutletViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ViewModel() {

    val outletListMutex = ReentrantCoroutineMutex()
    val employeeListMutex = ReentrantCoroutineMutex()
    val rolesListMutex = ReentrantCoroutineMutex()
    val listenerEmployeeDataMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val listenerRolesMutex = ReentrantCoroutineMutex()

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

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String,  val index: Int, val oldCode: String = ""): ResultState()
    }

    private val _outletList = MutableLiveData<MutableList<Outlet>>().apply { value = mutableListOf() }
    val outletList: LiveData<MutableList<Outlet>> = _outletList

    private val _userAdminData = MutableLiveData<com.example.barberlink.DataClass.UserAdminData>()
    val userAdminData: LiveData<com.example.barberlink.DataClass.UserAdminData> = _userAdminData

    private val _outletSelected = MutableLiveData<Outlet?>()
    val outletSelected: LiveData<Outlet?> = _outletSelected

    private val _employeeList = MutableLiveData<List<UserEmployeeData>>().apply { value = mutableListOf() }
    val employeeList: LiveData<List<UserEmployeeData>> = _employeeList

    private val _employeeRolesList = MutableLiveData<List<EmployeeRolesData>>().apply { value = mutableListOf() }
    val employeeRolesList: LiveData<List<EmployeeRolesData>> = _employeeRolesList

    private val _extendedStateMap = MutableLiveData<MutableMap<String, Boolean>>(mutableMapOf())
    val extendedStateMap: LiveData<MutableMap<String, Boolean>> = _extendedStateMap

    private val _capsterList = MutableLiveData<List<UserEmployeeData>>(emptyList())
    val capsterList: LiveData<List<UserEmployeeData>> = _capsterList

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    private var defaultCode: String = ""

    fun setDefaultCode(code: String) {
        defaultCode = code
    }

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setOutletList(outletList: MutableList<Outlet>) {
        viewModelScope.launch {
            _outletList.value = outletList
        }
    }

    fun setUserAdminData(data: com.example.barberlink.DataClass.UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun updateOutletStatus(
        outlet: Outlet,
        isOpen: Boolean,
        index: Int,
    ) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val docsRef = db.document(outlet.rootRef)
                    .collection("outlets")
                    .document(outlet.uid)

                val isSameDay = DateComparisonUtils.isSameDay(
                    Timestamp.now().toDate(),
                    outlet.timestampModify.toDate()
                )

                // Tentukan nilai current_queue baru
                val updatedCurrentQueue = if (isOpen && isSameDay)
                    outlet.currentQueue ?: emptyMap()
                else
                    outlet.currentQueue?.keys?.associateWith { "00" } ?: emptyMap()

                Log.d("IsOpen", "outlet: ${outlet.openStatus} || isOpen: $isOpen || updatedCurrentQueue: $updatedCurrentQueue")

                val task = withContext(Dispatchers.IO) {
                    docsRef.update(
                        mapOf(
                            "open_status" to isOpen,
                            "current_queue" to updatedCurrentQueue,
                            "timestamp_modify" to Timestamp.now()
                        )
                    ).awaitWriteWithOfflineFallback(tag = "UpdateOutletStatus")
                }

                if (task.isSuccessful) {
                    // KENAPA SUCCESSNYA KETIKA isOpen == outlet.openStatus PADAHAL TIDAK ADA SET DATA outlet.openStatus SECARA EKSPLISIT TAPI KENAPA TETAP DIANGGAP BERHASIL PADAHAL SAYA PIKIR MALAH HARUSNYA isOpen != outlet.openStatus,
                    // ITU MUNGKIN KARENA SET DATA TERJADI DI UPDATE LISTENER PADA ITEM REFERENCE YANG SAMA YANG TERJADI LEBIH CEPAT DARI PADA CUSTOM SNAPSHOT YANG DIBUAT KECUALI SAAT UPDATE LISTENER KITA PAKEK COPY()
                    if (isOpen == outlet.openStatus) {
                        if (task.displayMessage) _updateStateResult.value = ResultState.Success("Status Open", task.errorMessage.toString())
                        else _updateStateResult.value = ResultState.Success("Status Open", "Status buka outlet berhasil diperbarui.")
                    } else {
//                        restoreSwitchStatus(outlet, -999)
                        _updateStateResult.value = ResultState.Failure("Status Open", "Gagal memperbarui status buka outlet!", index)
                        Log.d("IsOpen", "No Toast")
                    }
                } else {
//                    restoreSwitchStatus(outlet, -999)
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Status Open", task.errorMessage.toString(), index)
                    else _updateStateResult.value = ResultState.Failure("Status Open", "Gagal memperbarui status buka outlet!", index)
                }
            } catch (e: Exception) {
                Logger.e("UpdateOutletStatus", "❌ Error: ${e.message}")
//                restoreSwitchStatus(outlet, -999)
                _updateStateResult.value = ResultState.Failure("Status Open", "Gagal memperbarui status buka outlet!", index)
            }
        }
    }

    fun updateOutletAccessCode(
        outlet: Outlet,
        newCode: String,
        oldCode: String,
        index: Int
    ) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val docsRef = db.document(outlet.rootRef)
                    .collection("outlets")
                    .document(outlet.uid)

                val task = withContext(Dispatchers.IO) {
                    docsRef.update(
                        mapOf(
                            "outlet_access_code" to newCode,
                            "last_updated" to Timestamp.now()
                        )
                    ).awaitWriteWithOfflineFallback(tag = "UpdateOutletAccessCode")
                }

                if (task.isSuccessful) {
                    if (oldCode == defaultCode) {
                        // Generate code
                        if (task.displayMessage) _updateStateResult.value = ResultState.Success("Access Code", task.errorMessage.toString())
                        else _updateStateResult.value = ResultState.Success("Access Code", "Berhasil mengenerate kode akses.")
                    } else {
                        // Revoke code
                        if (task.displayMessage) _updateStateResult.value = ResultState.Success("Access Code", task.errorMessage.toString())
                        else _updateStateResult.value = ResultState.Success("Access Code", "Berhasil memperbarui kode akses.")
                    }
                } else {
                    //setButtonAccessCode(oldCode, outlet.lastUpdated, binding)
                    if (oldCode == defaultCode) {
                        // Generate code
                        if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Access Code", task.errorMessage.toString(), index, oldCode)
                        else _updateStateResult.value = ResultState.Failure("Access Code", "Gagal mengenerate kode akses!", index, oldCode)
                    } else {
                        // Revoke code
                        if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Access Code", task.errorMessage.toString(), index, oldCode)
                        else _updateStateResult.value = ResultState.Failure("Access Code", "Gagal memperbarui kode akses!", index, oldCode)
                    }
                }
            } catch (e: Exception) {
                Logger.e("UpdateOutletAccessCode", "❌ Error: ${e.message}")
                //setButtonAccessCode(oldCode, outlet.lastUpdated, binding)
                if (oldCode == defaultCode) {
                    // Generate code
                    _updateStateResult.value = ResultState.Failure("Access Code", "Gagal mengenerate kode akses!", index, oldCode)
                } else {
                    // Revoke code
                    _updateStateResult.value = ResultState.Failure("Access Code", "Gagal memperbarui kode akses!", index, oldCode)
                }
            }
        }
    }

    fun deleteOutlet(outlet: Outlet) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val docsRef = db.document(outlet.rootRef)
                    .collection("outlets")
                    .document(outlet.uid)

                // 1. Delete image from Storage if it exists
                if (outlet.imgOutlet.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(outlet.imgOutlet)
                        imageRef.delete().await()
                        Logger.d("DeleteOutlet", "Image deleted successfully: ${outlet.imgOutlet}")
                    } catch (e: Exception) {
                        Logger.e("DeleteOutlet", "Failed to delete image: ${e.message}")
                        // We continue deleting the document even if image deletion fails
                    }
                }

                val task = withContext(Dispatchers.IO) {
                    docsRef.delete().awaitWriteWithOfflineFallback(tag = "DeleteOutlet")
                }

                if (task.isSuccessful) {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Success("Delete", task.errorMessage.toString())
                    else _updateStateResult.value = ResultState.Success("Delete", "Outlet \"${outlet.outletName}\" berhasil dihapus.")
                } else {
                    if (task.displayMessage) _updateStateResult.value = ResultState.Failure("Delete", task.errorMessage.toString(), -1)
                    else _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus outlet!", -1)
                }
            } catch (e: Exception) {
                Logger.e("DeleteOutlet", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus outlet!", -1)
            }
        }
    }

    fun updateOutletList(newOutletList: MutableList<Outlet>) {
        viewModelScope.launch(Dispatchers.Default) {
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
            _outletList.updateOnMain(currentList)
        }
    }

    fun setOutletSelected(outlet: Outlet?) {
        viewModelScope.launch {
            _outletSelected.value = outlet
        }
    }

    fun clearAllDataOutlet() {
        viewModelScope.launch {
            _outletList.clearList()
        }
    }

    fun setEmployeeList(employeeList: List<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.value = employeeList
        }
    }

    fun clearAllDataEmployee() {
        viewModelScope.launch {
            _employeeList.value = mutableListOf()
        }
    }

    fun setEmployeeRoles(list: List<EmployeeRolesData>) {
        viewModelScope.launch {
            val employees = _employeeList.value ?: emptyList()
            _employeeRolesList.value = list

            if (employees.isNotEmpty()) {
                employeeListMutex.withStateLock {
                    employees.forEach { employee ->
                        employee.roleDetail = list.find { it.roleName == employee.role }
                    }
                    _employeeList.value = employees
                }
            }
        }
    }

    fun setCapsterList(capsterList: List<UserEmployeeData>) {
        viewModelScope.launch {
            _capsterList.value = capsterList
        }
    }

    fun clearAllDataCapster() {
        viewModelScope.launch {
            _capsterList.value = emptyList()
        }
    }

    fun setExtendedStateMap(map: MutableMap<String, Boolean>) {
        viewModelScope.launch {
            _extendedStateMap.value = map
        }
    }

    fun updateState(key: String, value: Boolean) {
        viewModelScope.launch {
            val currentMap = _extendedStateMap.value ?: mutableMapOf()
            currentMap[key] = value
            _extendedStateMap.value = currentMap // Memicu observer LiveData
        }
    }

    fun removeState(key: String) {
        viewModelScope.launch {
            val currentMap = _extendedStateMap.value ?: mutableMapOf()
            if (currentMap.containsKey(key)) {
                currentMap.remove(key)
                _extendedStateMap.value = currentMap // Memicu observer LiveData
            }
        }
    }

    fun clearAllExtendedStates() {
        viewModelScope.launch {
            _extendedStateMap.value = mutableMapOf() // Mengosongkan map
        }
    }

    fun clearFragmentData() {
        viewModelScope.launch {
            _capsterList.value = emptyList()
            _outletSelected.value = null
        }
    }

}
