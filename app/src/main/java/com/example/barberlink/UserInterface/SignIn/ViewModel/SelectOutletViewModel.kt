package com.example.barberlink.UserInterface.SignIn.ViewModel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.EmployeeRolesData
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.ReservationData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.Utils.Concurrency.ReentrantCoroutineMutex
import com.example.barberlink.Utils.Concurrency.withStateLock
import com.example.barberlink.Utils.DateComparisonUtils.isSameDay
import com.example.barberlink.Utils.Logger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import com.example.barberlink.Utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.collections.mapNotNull

class SelectOutletViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {
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

    private val _employeeList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val employeeList: LiveData<MutableList<UserEmployeeData>> = _employeeList

    private val _employeeRolesList = MutableLiveData<List<EmployeeRolesData>>().apply { value = mutableListOf() }
    val employeeRolesList: LiveData<List<EmployeeRolesData>> = _employeeRolesList

    private val _capsterList = MutableLiveData<MutableList<UserEmployeeData>>().apply { emptyList<UserEmployeeData>() }
    val capsterList: LiveData<MutableList<UserEmployeeData>> = _capsterList

    private val _reservationDataList = MutableLiveData<MutableList<ReservationData>>().apply { emptyList<ReservationData>() }
    val reservationDataList: LiveData<MutableList<ReservationData>> = _reservationDataList

    private val _letsFilteringDataOutlet = MutableLiveData<Boolean?>()
    val letsFilteringDataOutlet: LiveData<Boolean?> = _letsFilteringDataOutlet

    private val _displayFilteredOutletResult = MutableLiveData<Boolean?>()
    val displayFilteredOutletResult: LiveData<Boolean?> = _displayFilteredOutletResult

    sealed class ResultState {
        data object Loading: ResultState()
        data class Navigate(val loginType: String, val emptyData: Boolean = false): ResultState()
        data class Failure(val message: String): ResultState()
    }

    private val _gettingStateResult = MutableLiveData<ResultState?>()
    val gettingStateResult: LiveData<ResultState?> = _gettingStateResult

    sealed class TriggerToast {
        data object LocalToast: TriggerToast()
        data class CommonToast(val message: String): TriggerToast()
    }

    private val _toastDetection = MutableLiveData<TriggerToast?>()
    val toastDetection: LiveData<TriggerToast?> = _toastDetection

    private var loginType: String = ""

    fun getLoginType(): String {
        return runBlocking {
            loginType
        }
    }

    fun setLoginType(type: String) {
        viewModelScope.launch {
            loginType = type
        }
    }

    fun setGettingStateResult(value: ResultState?) {
        viewModelScope.launch {
            _gettingStateResult.value = value
        }
    }

    fun setOutletSelected(outlet: Outlet) {
        viewModelScope.launch {
            _outletSelected.postValue(outlet)
        }
    }

    fun setOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        viewModelScope.launch {
            _outletList.postValue(outletList)
        }
    }

    fun setFilteredOutletList(outletList: MutableList<Outlet>) {
        // _isDisableShimmer.value = isDisableShimmer
        viewModelScope.launch {
            _filteredOutletList.postValue(outletList)
        }
    }

    fun setEmployeeList(employeeList: MutableList<UserEmployeeData>) {
        // _isDisableShimmer.value = isDisableShimmer
        viewModelScope.launch {
            _employeeList.postValue(employeeList)
        }
    }

    fun setCapsterList(capsterList: MutableList<UserEmployeeData>) {
        // _isDisableShimmer.value = isDisableShimmer
        viewModelScope.launch {
            _capsterList.postValue(capsterList)
        }
    }

    fun setReservationList(reservationDataList: MutableList<ReservationData>) {
        // _isDisableShimmer.value
        viewModelScope.launch {
            _reservationDataList.postValue(reservationDataList)
        }
    }

    fun handleTellerFlow() {
        viewModelScope.launch {
            outletSelected.value?.let { outletSelected ->
                try {
                    _gettingStateResult.postValue(ResultState.Loading)
                    Logger.d("CheckShimmer", "handleTellerLogin start")
                    if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty()) throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                    if (outletSelected.listEmployees.isEmpty()) throw IllegalStateException("Anda belum menambahkan daftar capster untuk outlet ini!")

                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("roles")
                            .whereIn("barbershop_ref", listOf("All", outletSelected.rootRef))
                            .awaitGetWithOfflineFallback(tag = "GetEmployeeRolesData")
                    }

                    if (snapshot.isSuccessful) {
                        val documents = snapshot.data
                        if (documents != null) {
                            withContext(Dispatchers.Default) {
                                val employeeRoles = documents.mapNotNull { document ->
                                    document.toObject(EmployeeRolesData::class.java)
                                }
                                _employeeRolesList.postValue(employeeRoles)
                                getCapstersData()
                            }
                        } else {
                            Logger.d("FormAccess", "handleTellerFlow: snapshot data is null")
                            if (snapshot.displayMessage) _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                            else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                            setupIntialDataWhenError()
                        }
                    }  else {
                        Logger.d("FormAccess", "handleTellerFlow: snapshot unsuccessful")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                _gettingStateResult.postValue(ResultState.Failure(""))
                            } else _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                        } else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                        setupIntialDataWhenError()
                    }
                } catch (e: Exception) {
                    Logger.d("FormAccess", "handleTellerFlow: exception ${e.message}")
                    _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                    setupIntialDataWhenError()
                }
            } ?: run {
                Logger.d("FormAccess", "handleTellerFlow: outletSelected is null")
                _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                setupIntialDataWhenError()
            }
        }
    }

    private fun getCapstersData() {
        viewModelScope.launch {
            outletSelected.value?.let { outletSelected ->
                try {
                    //_gettingStateResult.value = ResultState.Loading
                    Logger.d("CheckShimmer", "handleTellerLogin start")
                    //if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty()) throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                    //if (outletSelected.listEmployees.isEmpty()) throw IllegalStateException("Anda belum menambahkan daftar capster untuk outlet ini!")

                    coroutineScope {
                        val isSameDay = isSameDay(Timestamp.now().toDate(), outletSelected.timestampModify.toDate())
                        // Parallel Phase 1️⃣
                        awaitAll(
                            async { if (!isSameDay) {
                                outletSelected.apply {
                                    currentQueue = currentQueue?.keys?.associateWith { "00" } ?: emptyMap()
                                    timestampModify = Timestamp.now()
                                }
                                updateOutletCurrentQueue(outletSelected)
                            } },
                            async { getCapsterDataTask(outletSelected) },
                        )
                        // Parallel Phase 2️⃣
                        async { getAllReservationData(outletSelected) }.await()
                    }
                    // JIKA INGIN PARTIAL SCOPE DENGAN CHILD THROW EXCEPTIPN MAKA PAKAI SUPER_VISOR_SCOPE + RUN_CATCHING
                    // KODE AWAIT_ALL DIBAWAH INI TIDAK MENGIMPLEMENTASIKAN THROW APAPAUN PADA CHILDNYA (DI KODE INI IA RETURN FALSE KETIKA GAGAL) MAKA TIDAK PERLU SUPER_VISOR_SCOPE
                    // DITAMBAH SEBELUM MENGAKSES SERVER DENGAN GET, UPDATE, SET, ATAUPUN DELETE SUDAH DILAKUKAN PENGCHECKAN PATH SEPERTI NILAI ROOTREF YANG TIDAK BOLEH KOSONG
                    _gettingStateResult.postValue(ResultState.Navigate("Login as Teller", false))
                } catch (e: Exception) {
                    Logger.d("FormAccess", "getSpecificOutletData: exception ${e.message}")
                    Logger.e("CheckShimmer", "❌ getSpecificOutletData gagal: ${e.message}")
                    val messageText = if (e.message.toString() == "Anda belum menambahkan daftar capster untuk outlet ini!") {
                        e.message.toString()
                    } else {
                        "Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"
                    }
                    _gettingStateResult.postValue(ResultState.Failure(messageText))
                    setupIntialDataWhenError()
                }
            } ?: run {
                Logger.d("FormAccess", "getSpecificOutletData: outletSelected is null")
                _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                setupIntialDataWhenError()
            }
        }
    }

    private fun setupIntialDataWhenError() {
        setReservationList(mutableListOf())
        setCapsterList(mutableListOf())
    }

    fun handleEmployeeFlow() {
        viewModelScope.launch {
            outletSelected.value?.let { outletSelected ->
                _gettingStateResult.postValue(ResultState.Loading)
                Logger.d("CheckShimmer", "getEmployeesData start")

                try {
                    if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty()) throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                    if (outletSelected.listEmployees.isEmpty()) throw IllegalStateException("Anda belum menambahkan daftar capster untuk outlet ini!")

                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("roles")
                            .whereIn("barbershop_ref", listOf("All", outletSelected.rootRef))
                            .awaitGetWithOfflineFallback(tag = "GetEmployeeRolesData")
                    }

                    if (snapshot.isSuccessful) {
                        val documents = snapshot.data
                        if (documents != null) {
                            withContext(Dispatchers.Default) {
                                val employeeRoles = documents.mapNotNull { document ->
                                    document.toObject(EmployeeRolesData::class.java)
                                }
                                _employeeRolesList.postValue(employeeRoles)
                                getEmployeesData()
                            }
                        } else {
                            Logger.d("FormAccess", "handleEmployeeFlow: snapshot data is null")
                            if (snapshot.displayMessage) _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                            else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                            setEmployeeList(mutableListOf())
                        }
                    } else {
                        Logger.d("FormAccess", "handleEmployeeFlow: snapshot unsuccessful")
                        if (snapshot.displayMessage) {
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                _gettingStateResult.postValue(ResultState.Failure(""))
                            } else _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                        } else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                        setEmployeeList(mutableListOf())
                    }
                } catch (e: Exception) {
                    Logger.d("FormAccess", "handleEmployeeFlow: exception ${e.message}")
                    _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                    setEmployeeList(mutableListOf())
                }
            } ?: run {
                Logger.d("FormAccess", "handleEmployeeFlow: outletSelected is null")
                _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                setEmployeeList(mutableListOf())
            }
        }
    }

    private fun getEmployeesData() {
        viewModelScope.launch {
            outletSelected.value?.let { outletSelected ->
                //_gettingStateResult.value = ResultState.Loading
                Logger.d("CheckShimmer", "getEmployeesData start")

                try {
                    //if (outletSelected.rootRef.isEmpty() || outletSelected.uid.isEmpty()) throw IllegalStateException("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                    //if (outletSelected.listEmployees.isEmpty()) throw IllegalStateException("Anda belum menambahkan daftar capster untuk outlet ini!")
                    val snapshot = withContext(Dispatchers.IO) {
                        db.collection("employees")
                            .whereEqualTo("root_ref", outletSelected.rootRef)
                            .awaitGetWithOfflineFallback(tag = "GetEmployeesData")
                    }

                    if (snapshot.isSuccessful) {
                        val documents = snapshot.data
                        if (documents != null) {
                            withContext(Dispatchers.Default) {
                                this@SelectOutletViewModel.outletSelected.value?.let { outletData ->
                                    val employeeUidList = outletData.listEmployees

                                    val newEmployeesList = documents.mapNotNull { document ->
                                        document.toObject(UserEmployeeData::class.java).apply {
                                            userRef = document.reference.path
                                            outletRef = outletData.outletReference
                                            roleDetail = employeeRolesList.value?.find {
                                                it.roleName == this.role
                                            }
                                        }.takeIf { it.uid in employeeUidList }
                                    }

                                    setEmployeeList(newEmployeesList.toMutableList())
                                    _gettingStateResult.postValue(ResultState.Navigate("Login as Employee", newEmployeesList.isEmpty()))
                                    Logger.d("CheckShimmer", "✅ getEmployeesData found ${newEmployeesList.size} data")
                                } ?: run {
                                    throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                                }
                            }
                        } else {
                            Logger.d("FormAccess", "getEmployeesData: snapshot data is null")
                            if (snapshot.displayMessage) _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                            else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                            setEmployeeList(mutableListOf())
                        }
                    } else {
                        if (snapshot.displayMessage) {
                            Logger.d("FormAccess", "getEmployeesData: snapshot unsuccessful with message ${snapshot.errorMessage}")
                            if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                                NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                                _gettingStateResult.postValue(ResultState.Failure(""))
                            } else _gettingStateResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                        } else _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                        setEmployeeList(mutableListOf())
                    }
                } catch (e: Exception) {
                    Logger.d("FormAccess", "getEmployeesData: exception ${e.message}")
                    _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                    setEmployeeList(mutableListOf())
                }
            } ?: run {
                Logger.d("FormAccess", "getEmployeesData: outletSelected is null")
                _gettingStateResult.postValue(ResultState.Failure("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!"))
                setEmployeeList(mutableListOf())
            }
        }
    }

    private suspend fun updateOutletCurrentQueue(outletSelected: Outlet) {
        try {
            val startTime = System.currentTimeMillis()

            val docsRef = db.document(outletSelected.outletReference)
            Logger.d("CheckShimmer", "🚀 Mulai update current_queue untuk outletRef: $docsRef")

            val task = withContext(Dispatchers.IO) {
                docsRef.update(
                    mapOf(
                        "current_queue" to outletSelected.currentQueue,
                        "timestamp_modify" to outletSelected.timestampModify
                    )
                ).awaitWriteWithOfflineFallback(tag = "UpdateOutletQueue")
            }

            val duration = System.currentTimeMillis() - startTime
            if (task.isSuccessful) {
                Logger.d("CheckShimmer", "✅ Update current_queue sukses (${duration} ms)")
            } else {
                Logger.e("CheckShimmer", "❌ Update current_queue gagal (${duration} ms)")
                throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            }
        } catch (e: Exception) {
            Logger.e("CheckShimmer", "❌ Exception update_current_queue: ${e.message}")
            throw e
        }
    }

    private suspend fun getCapsterDataTask(outletSelected: Outlet) {
        Logger.d("CheckShimmer", "getCapsterDataTask start")
        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.collection("employees")
                    .whereEqualTo("root_ref", outletSelected.rootRef)
                    .awaitGetWithOfflineFallback(tag = "GetCapsterDataTask")
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        this@SelectOutletViewModel.outletSelected.value?.let { outletData ->
                            val employeeUidList = outletData.listEmployees

                            val (newCapsterList, _) = documents.mapNotNull { document ->
                                document.toObject(UserEmployeeData::class.java).apply {
                                    userRef = document.reference.path
                                    outletRef = outletData.outletReference
                                    roleDetail = employeeRolesList.value?.find {
                                        it.roleName == this.role
                                    }
                                }.takeIf { it.uid in employeeUidList && it.attendanceStatus && (it.roleDetail?.permissions?.get("manage_queue") == true) } // Filter untuk attendanceStatus == true
                                    ?.let { employee ->
                                        employee to employee.fullname
                                    }
                            }.unzip()

                            if (newCapsterList.isEmpty()) {
                                _toastDetection.postValue(TriggerToast.CommonToast("Tidak ditemukan data capster yang sesuai!"))
                                //Toast.makeText(context, "Tidak ditemukan data capster yang sesuai!", Toast.LENGTH_SHORT).show()
                            }
                            setCapsterList(newCapsterList.toMutableList())
                        } ?: run {
                            throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                        }
                    }
                } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun getAllReservationData(outletSelected: Outlet) {
        Logger.d("CheckShimmer", "getAllReservationData start")
        try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = Timestamp(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val startOfNextDay = Timestamp(calendar.time)

            val snapshot = withContext(Dispatchers.IO) {
                db.collection("${outletSelected.rootRef}/reservations")
                    .where(
                        Filter.and(
                            Filter.equalTo("outlet_identifier", outletSelected.uid),
                            Filter.greaterThanOrEqualTo("timestamp_to_booking", startOfDay),
                            Filter.lessThan("timestamp_to_booking", startOfNextDay)
                        )
                    )
                    .awaitGetWithOfflineFallback(tag = "GetAllReservationData")
            }

            if (snapshot.isSuccessful) {
                val documents = snapshot.data
                if (documents != null) {
                    withContext(Dispatchers.Default) {
                        this@SelectOutletViewModel.outletSelected.value?.let { outletData ->
                            val employeeUidList = outletData.listEmployees

                            val newReservationList = documents.mapNotNull { document ->
                                val reservationData = document.toObject(ReservationData::class.java).apply {
                                    dataRef = document.reference.path
                                }

                                val capsterUid = reservationData.capsterInfo?.capsterRef?.split("/")?.lastOrNull() // Ambil UID dari path terakhir
                                // Filter berdasarkan queueStatus dan juga employeeUidList
                                reservationData.takeIf {
                                    it.queueStatus !in listOf("pending", "expired") &&
                                            capsterUid == "" ||
                                            capsterUid in employeeUidList
                                }
                            }

                            if (newReservationList.isEmpty()) {
                                _toastDetection.postValue(TriggerToast.CommonToast("Tidak ditemukan data reservasi yang sesuai"))
                                //Toast.makeText(context, "Tidak ditemukan data reservasi yang sesuai", Toast.LENGTH_SHORT).show()
                            }
                            setReservationList(newReservationList.toMutableList())
                        } ?: run {
                            throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
                        }
                    }
                } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
            } else throw Exception("Terjadi kesalahan: Gagal memuat data yang dibutuhkan!!!")
        } catch (e: Exception) {
            throw e
        }
    }

    fun triggerFilteringDataOutlet(withShimmer: Boolean) {
        viewModelScope.launch {
            _letsFilteringDataOutlet.postValue(withShimmer)
        }
    }

    fun displayFilteredOutletResult(withShimmer: Boolean) {
        viewModelScope.launch {
            _displayFilteredOutletResult.postValue(withShimmer)
        }
    }

    fun clearState() {
        viewModelScope.launch {
            _letsFilteringDataOutlet.postValue(null)
            _displayFilteredOutletResult.postValue(null)
        }
    }

    fun clearToastDetection() {
        viewModelScope.launch {
            _toastDetection.postValue(null)
        }
    }

}
