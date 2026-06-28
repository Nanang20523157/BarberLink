package com.example.barberlink.UserInterface.Admin.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.Service
import com.example.barberlink.DataClass.Product
import com.example.barberlink.DataClass.BundlingPackage
import com.example.barberlink.Repository.EmployeeRepository
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.Logger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ManageEmployeeViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ConfirmDeleteViewModel() {

    val employeeMutex = ReentrantCoroutineMutex()
    val outletListMutex = ReentrantCoroutineMutex()
    val serviceListMutex = ReentrantCoroutineMutex()
    val productListMutex = ReentrantCoroutineMutex()
    val bundlingListMutex = ReentrantCoroutineMutex()
    val listenerEmployeeListMutex = ReentrantCoroutineMutex()
    val listenerOutletListMutex = ReentrantCoroutineMutex()
    val listenerServiceListMutex = ReentrantCoroutineMutex()
    val listenerProductListMutex = ReentrantCoroutineMutex()
    val listenerBundlingListMutex = ReentrantCoroutineMutex()

    private var targetDeleteData: UserEmployeeData? = null

    private suspend fun <T> MutableLiveData<T>.updateOnMain(newValue: T) =
        withContext(Dispatchers.Main) { value = newValue }

    private suspend fun <T> MutableLiveData<MutableList<T>>.clearList() =
        updateOnMain(mutableListOf())

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val message: String): ResultState()
        data class Failure(val type: String, val message: String, val index: Int): ResultState()
    }

    private val _employeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { value = mutableListOf() }
    val employeeList: LiveData<MutableList<UserEmployeeData>> = _employeeList

    private val _userAdminData = MutableLiveData<UserAdminData>()
    val userAdminData: LiveData<UserAdminData> = _userAdminData

    private val _employeeRoles = MutableLiveData<List<EmployeeRolesData>>().apply { value = emptyList() }
    val employeeRoles: LiveData<List<EmployeeRolesData>> = _employeeRoles

    private val _outletList = MutableLiveData<List<Outlet>>().apply { value = emptyList() }
    val outletList: LiveData<List<Outlet>> = _outletList

//    private val _serviceList = MutableLiveData<List<Service>>(emptyList())
//    val serviceList: LiveData<List<Service>> get() = _serviceList
//
//    private val _productList = MutableLiveData<List<Product>>(emptyList())
//    val productList: LiveData<List<Product>> get() = _productList
//
//    private val _bundlingList = MutableLiveData<List<BundlingPackage>>(emptyList())
//    val bundlingList: LiveData<List<BundlingPackage>> get() = _bundlingList

    private val _updateStateResult = MutableLiveData<ResultState?>()
    val updateStateResult: LiveData<ResultState?> = _updateStateResult

    fun getTargetDeleteData(): UserEmployeeData? {
        return runBlocking {
            targetDeleteData
        }
    }

    fun setTargetDeleteData(data: UserEmployeeData?) {
        viewModelScope.launch {
            targetDeleteData = data
        }
    }

    fun setUpdateStateResult(value: ResultState?) {
        viewModelScope.launch {
            _updateStateResult.value = value
        }
    }

    fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch {
            _employeeList.value = employeeList
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            _userAdminData.value = data
        }
    }

    fun setEmployeeRolesList(roles: List<EmployeeRolesData>) {
        viewModelScope.launch {
            _employeeRoles.value = roles
            val employees = _employeeList.value ?: mutableListOf()
            if (employees.isNotEmpty()) {
                employeeMutex.withStateLock {
                    employees.forEach { employee ->
                        employee.roleDetail = roles.find { it.roleName == employee.role }
                    }
                    _employeeList.value = employees
                }
            }
        }
    }

    fun setOutletList(outlets: List<Outlet>) {
        viewModelScope.launch {
            _outletList.value = outlets
        }
    }

//    fun setServiceList(list: List<Service>) {
//        viewModelScope.launch {
//            _serviceList.value = list
//        }
//    }
//
//    fun setProductList(list: List<Product>) {
//        viewModelScope.launch {
//            _productList.value = list
//        }
//    }
//
//    fun setBundlingList(list: List<BundlingPackage>) {
//        viewModelScope.launch {
//            _bundlingList.value = list
//        }
//    }

    fun deleteEmployee(employee: UserEmployeeData) {
        viewModelScope.launch {
            try {
                _updateStateResult.value = ResultState.Loading

                val employeeRef = if (employee.userRef.isNotEmpty()) db.document(employee.userRef) else db.collection("employees").document(employee.uid)

                val outlets = _outletList.value ?: emptyList()
                val outletsToUpdate = outlets.filter { outlet ->
                    outlet.listEmployees.contains(employee.uid) ||
                    (outlet.currentQueue != null && outlet.currentQueue!!.containsKey(employee.uid))
                }

//=========================  HARUSNYA GAK PERLU GETTING DATA PAKEK DATA YANG DARI BERANDA_ADMIN_FRAGMENT SAJA  =========================
//                val services = _serviceList.value ?: emptyList()
//                val serviceRefsToUpdate = services.filter { service ->
//                    service.resultsShareAmount != null && service.resultsShareAmount!!.containsKey(employee.uid)
//                }.map { db.document(it.dataRef) }

//                val products = _productList.value ?: emptyList()
//                val productRefsToUpdate = products.filter { product ->
//                    product.resultsShareAmount != null && product.resultsShareAmount!!.containsKey(employee.uid)
//                }.map { db.document(it.dataRef) }

//                val bundlings = _bundlingList.value ?: emptyList()
//                val bundlingRefsToUpdate = bundlings.filter { bundling ->
//                    bundling.resultsShareAmount != null && bundling.resultsShareAmount!!.containsKey(employee.uid)
//                }.map { db.document(it.dataRef) }

                withContext(Dispatchers.IO) {
                    db.runTransaction { transaction ->
                        // 1. Update employee doc (soft-unlink)
                        val updatesEmployee = mutableMapOf<String, Any>()
                        updatesEmployee["attendance_status"] = false
                        updatesEmployee["black_list"] = false
                        updatesEmployee["root_ref"] = ""
                        updatesEmployee["talent_availability"] = true
                        updatesEmployee["uid_list_placement"] = emptyList<String>()
                        transaction.update(employeeRef, updatesEmployee)

                        // 2. Update related outlets
                        for (outlet in outletsToUpdate) {
                            val ref = db.document(outlet.outletReference)
                            val updates = mutableMapOf<String, Any>()
                            var changed = false

                             if (outlet.listEmployees.contains(employee.uid)) {
                                 updates["list_employees"] = outlet.listEmployees.filter { it != employee.uid }
                                 changed = true
                             }
                             if (outlet.currentQueue != null && outlet.currentQueue!!.containsKey(employee.uid)) {
                                 val newQueue = outlet.currentQueue!!.toMutableMap()
                                 newQueue.remove(employee.uid)
                                 updates["current_queue"] = newQueue
                                 changed = true
                             }
                             if (changed) {
                                 transaction.update(ref, updates)
                             }
                        }

                        // 3. Update related services
//                        for (ref in serviceRefsToUpdate) {
//                            val service = transaction.get(ref).toObject(Service::class.java)
//                            if (service != null && service.resultsShareAmount != null && service.resultsShareAmount!!.containsKey(employee.uid)) {
//                                val newShare = service.resultsShareAmount!!.toMutableMap()
//                                newShare.remove(employee.uid)
//                                transaction.update(ref, "results_share_amount", newShare)
//                            }
//                        }

                        // 4. Update related products
//                        for (ref in productRefsToUpdate) {
//                            val product = transaction.get(ref).toObject(Product::class.java)
//                            if (product != null && product.resultsShareAmount != null && product.resultsShareAmount!!.containsKey(employee.uid)) {
//                                val newShare = product.resultsShareAmount!!.toMutableMap()
//                                newShare.remove(employee.uid)
//                                transaction.update(ref, "results_share_amount", newShare)
//                            }
//                        }

                        // 5. Update related bundlings
//                        for (ref in bundlingRefsToUpdate) {
//                            val bundling = transaction.get(ref).toObject(BundlingPackage::class.java)
//                            if (bundling != null && bundling.resultsShareAmount != null && bundling.resultsShareAmount!!.containsKey(employee.uid)) {
//                                val newShare = bundling.resultsShareAmount!!.toMutableMap()
//                                newShare.remove(employee.uid)
//                                transaction.update(ref, "results_share_amount", newShare)
//                            }
//                        }
                    }.await()
                }

                _updateStateResult.value = ResultState.Success("Delete", "Karyawan \"${employee.fullname}\" berhasil dihapus.")
            } catch (e: Exception) {
                Logger.e("DeleteEmployee", "❌ Error: ${e.message}")
                _updateStateResult.value = ResultState.Failure("Delete", "Gagal menghapus karyawan!", -1)
            }
        }
    }
    fun updateEmployeeList(newEmployeeList: MutableList<UserEmployeeData>) {
        viewModelScope.launch(Dispatchers.Default) {
            val updatedEmployeeList = _employeeList.value ?: mutableListOf()
            val rolesList = _employeeRoles.value ?: emptyList()
 
            // Update or add new employees
            updatedEmployeeList.forEach { existing ->
                val matchingCapsterData =
                    newEmployeeList.find { it.uid == existing.uid }
                if (matchingCapsterData != null) {
                    // Update relevant fields
                    existing.apply {
                        accountVerification = matchingCapsterData.accountVerification
                        accumulatedLateness =
                            matchingCapsterData.accumulatedLateness
                        attendanceStatus = matchingCapsterData.attendanceStatus
                        availabilityStatus =
                            matchingCapsterData.availabilityStatus
                        blackList = matchingCapsterData.blackList
                        customerCounting =
                            matchingCapsterData.customerCounting
                        debutDate = matchingCapsterData.debutDate
                        email = matchingCapsterData.email
                        employeeRating =
                            matchingCapsterData.employeeRating
                        fullname = matchingCapsterData.fullname
                        gender = matchingCapsterData.gender
                        historyWorkplace = matchingCapsterData.historyWorkplace
                        password = matchingCapsterData.password
                        phone = matchingCapsterData.phone
                        photoProfile = matchingCapsterData.photoProfile
                        pin = matchingCapsterData.pin
                        point = matchingCapsterData.point
                        //positions = matchingCapsterData.positions
                        role = matchingCapsterData.role
                        rootRef = matchingCapsterData.rootRef
                        salary = matchingCapsterData.salary
                        superAdmin = matchingCapsterData.superAdmin
                        uid = matchingCapsterData.uid
                        uidListPlacement =
                            matchingCapsterData.uidListPlacement
                        userNotification =
                            matchingCapsterData.userNotification
                        userReminder = matchingCapsterData.userReminder
                        username = matchingCapsterData.username
                        userRef = matchingCapsterData.userRef
                        outletRef = matchingCapsterData.outletRef
                        roleDetail = rolesList.find { it.roleName == matchingCapsterData.role }
                    }
                }
            }
 
            // Tambah yang baru
            val toAdd = newEmployeeList.filter { fetched ->
                updatedEmployeeList.none { it.uid == fetched.uid }
            }
            toAdd.forEach { fetched ->
                fetched.roleDetail = rolesList.find { it.roleName == fetched.role }
            }
            updatedEmployeeList.addAll(toAdd)

            // Hapus yang sudah tidak ada
            val toRemove = updatedEmployeeList.filterNot { current ->
                newEmployeeList.any { it.uid == current.uid }
            }
            updatedEmployeeList.removeAll(toRemove)

            _employeeList.updateOnMain(updatedEmployeeList)
        }
    }

    fun clearAllDataEmployee() {
        viewModelScope.launch {
            _employeeList.clearList()
        }
    }
}
