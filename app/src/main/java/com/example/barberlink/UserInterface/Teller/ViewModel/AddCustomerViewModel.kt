package com.example.barberlink.UserInterface.Teller.ViewModel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.Customer
import com.example.barberlink.DataClass.Outlet
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserRolesData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitGetWithOfflineFallback
import com.yourapp.utils.awaitWriteWithOfflineFallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AddCustomerViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

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

    private var buttonStatus: String = "Add"
    private var isUserManualInput: Boolean = true
    private var isSaveData: Boolean = false
    private var userPhoneNumber: String = ""
    private var userInputName: String = ""
    private var userInputGender: String = "Rahasiakan"
    private lateinit var userEmployeeData: UserEmployeeData
    private lateinit var userAdminData: UserAdminData
    private lateinit var userCustomerData: UserCustomerData
    private lateinit var userRolesData: UserRolesData
    private lateinit var outletSelected: Outlet

    private val _addCustomerResult = MutableLiveData<ResultState?>()
    val addCustomerResult: LiveData<ResultState?> = _addCustomerResult

    // Lambda callback yang bisa di-set dari luar (fragment)
    var onCustomerAddResult: ((Boolean) -> Unit)? = null

    sealed class ResultState {
        data object Loading : ResultState()
        data object ResetingInput : ResultState()
        data object DisplayError : ResultState()
        data class DisplayData(val userRole: String) : ResultState()
        data class RetryProcess(val whichProcess: String, val userRef: String) : ResultState()
        data class Failure(val message: String) : ResultState()
        data class Success(val data: Customer) : ResultState()
    }

    suspend fun setAddCustomerResult(result: ResultState?) {
        withContext(Dispatchers.Main) {
            _addCustomerResult.value = result
        }
    }

    suspend fun setButtonStatus(status: String) {
        withContext(Dispatchers.Main) {
            buttonStatus = status
        }
    }

    suspend fun setUserManualInput(isManualInput: Boolean) {
        withContext(Dispatchers.Main) {
            isUserManualInput = isManualInput
        }
    }

    suspend fun setIsSaveData(isSaveData: Boolean) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.isSaveData = isSaveData
        }
    }

    suspend fun setUserPhoneNumber(phoneNumber: String) {
        withContext(Dispatchers.Main) {
            userPhoneNumber = phoneNumber
        }
    }

    suspend fun setUserInputName(name: String) {
        withContext(Dispatchers.Main) {
            userInputName = name
        }
    }

    suspend fun setUserInputGander(gander: String) {
        withContext(Dispatchers.Main) {
            userInputGender = gander
        }
    }

    suspend fun setUserEmployeeData(userEmployeeData: UserEmployeeData) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.userEmployeeData = userEmployeeData
        }
    }

    suspend fun setUserAdminData(userAdminData: UserAdminData) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.userAdminData = userAdminData
        }
    }

    suspend fun setUserCustomerData(userCustomerData: UserCustomerData) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.userCustomerData = userCustomerData
        }
    }

    suspend fun setOutletSelected(outlet: Outlet) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.outletSelected = outlet
        }
    }

    fun getButtonStatus(): String {
        return runBlocking {
            buttonStatus
        }
    }

    fun getIsUserManualInput(): Boolean {
        return runBlocking {
            isUserManualInput
        }
    }

    fun getIsSaveData(): Boolean {
        return runBlocking {
            isSaveData
        }
    }

    fun getUserPhoneNumber(): String {
        return runBlocking {
            userPhoneNumber
        }
    }

    fun getUserInputName(): String {
        return runBlocking {
            userInputName
        }
    }

    fun getUserInputGander(): String {
        return runBlocking {
            userInputGender
        }
    }

    suspend fun setUserRolesData(userRolesData: UserRolesData) {
        withContext(Dispatchers.Main) {
            this@AddCustomerViewModel.userRolesData = userRolesData
        }
    }

    fun getUserEmployeeData(): UserEmployeeData {
        return runBlocking {
            userEmployeeData
        }
    }

    fun getUserAdminData(): UserAdminData {
        return runBlocking {
            userAdminData
        }
    }

    fun getUserCustomerData(): UserCustomerData {
        return runBlocking {
            userCustomerData
        }
    }

    fun getUserRolesData(): UserRolesData {
        return runBlocking {
            userRolesData
        }
    }

    fun getOutletSelected(): Outlet {
        return runBlocking {
            outletSelected
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun checkAndAddCustomer() {
        withContext(Dispatchers.IO) {
            if (!isSaveData) {
                Log.d("TriggerUU", "====**********====")
                Log.d("TriggerUU", "X0X")
                isSaveData = true
                _addCustomerResult.postValue(ResultState.Loading)

                try {
                    // Gunakan utilitas offline-aware
                    val document = db.collection("users")
                        .document(userPhoneNumber)
                        .get()
                        .awaitGetWithOfflineFallback(tag = "CheckAndAddCustomer")

                    if (document != null && document.exists()) {
                        when {
                            document.exists() -> handleExistingUser(document)
                            isSaveData -> checkCustomerExistenceAndAdd(userPhoneNumber)
                            else -> _addCustomerResult.postValue(ResultState.ResetingInput)
                        }
                    } else {
                        // Tidak ada data & gagal ambil cache
                        _addCustomerResult.postValue(ResultState.Failure("Gagal mengambil data pengguna."))
                        // isSaveData = false lewat setter viewModel yang di panggil di fragment
                    }

                } catch (e: Exception) {
                    _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                    // isSaveData = false lewat setter viewModel yang di panggil di fragment
                }
            }
        }
    }

    // Function to handle existing user and update their data if manual input is detected
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun handleExistingUser(document: DocumentSnapshot) {
        Log.d("TriggerUU", "X1X")
        withContext(Dispatchers.Main) {
            try {
                document.toObject(UserRolesData::class.java)?.let {
                    userRolesData = it
                }

                if (buttonStatus == "Add") {
                    Log.d("TriggerUU", "X1.1X")
                    setupUserCard(gettingData = true)
                } else if (buttonStatus == "Sync") {
                    when (userRolesData.role) {
                        "admin", "employee", "pairAE" -> {
                            Log.d("TriggerUU", "X1.2X")
                            addNewCustomer()
                        }
                        "customer", "pairEC(-)", "pairEC(+)", "pairAC(-)", "pairAC(+)", "hybrid(-)", "hybrid(+)" -> {
                            if (!isUserManualInput) {
                                Log.d("TriggerUU", "X1.3X")
                                addCustomerToOutlet()
                            } else {
                                Log.d("TriggerUU", "X1.4X")
                                // Update fullname and gender using data from userCustomerData
                                //updateCustomerData(userRolesData?.customerRef)
                                syncCustomerRelatedData(customerRef = "")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun syncCustomerRelatedData(customerRef: String) {
        withContext(Dispatchers.IO) {
            try {
                val updateJobs = mutableListOf<Deferred<Boolean>>()

                when (userRolesData.role) {
                    "customer" -> {
                        updateJobs += async { updateCustomerData(userRolesData.customerRef) }
                    }
                    "pairEC(-)", "pairEC(+)" -> {
                        updateJobs += async { updateCustomerData(userRolesData.customerRef) }
                        if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                            updateJobs += async { updateEmployeeData(userRolesData.employeeRef) }
                        }
                    }
                    "pairAC(-)", "pairAC(+)" -> {
                        updateJobs += async { updateCustomerData(userRolesData.customerRef) }
                        if (userAdminData.ownerName != userInputName) {
                            updateJobs += async { updateAdminData(userRolesData.adminRef) }
                        }
                    }
                    "hybrid(-)", "hybrid(+)" -> {
                        updateJobs += async { updateCustomerData(userRolesData.customerRef) }
                        if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                            updateJobs += async { updateEmployeeData(userRolesData.employeeRef) }
                        }
                        if (userAdminData.ownerName != userInputName) {
                            updateJobs += async { updateAdminData(userRolesData.adminRef) }
                        }
                    }
                    else -> {
                        if (customerRef.isNotEmpty()) {
                            updateJobs += async { updateCustomerData(customerRef) }
                        }
                    }
                }

                val results = updateJobs.awaitAll()
                val allSuccess = results.all { !it } // remember: your update*Data returns true = fail, false = success

                if (allSuccess) {
                    addCustomerToOutlet()
                } else {
                    _addCustomerResult.postValue(ResultState.RetryProcess("CustomerRelatedData", customerRef))
                    isSaveData = false
                }

            } catch (e: Exception) {
                _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        Log.d("TriggerUU", "X2X")
        withContext(Dispatchers.IO) {
            try {
                val document = db.collection("customers")
                    .document(phoneNumber)
                    .get()
                    .awaitGetWithOfflineFallback(tag = "CheckCustomerExistence")

                if (document != null && document.exists()) {
                    if (!document.exists()) {
                        Log.d("TriggerUU", "X2.1X")
                        addNewCustomer()
                    } else {
                        Log.d("TriggerUU", "X2.2X")

                        if (buttonStatus == "Add") {
                            document.toObject(UserCustomerData::class.java)?.apply {
                                userRef = document.reference.path
                                userCustomerData = this
                            }
                            Log.d("TriggerUU", "X2.2.1X")
                            setupUserCard(gettingData = false)
                        } else if (buttonStatus == "Sync") {
                            if (!isUserManualInput) {
                                Log.d("TriggerUU", "X2.2.2X")
                                addCustomerToOutlet()
                            } else {
                                Log.d("TriggerUU", "X2.2.3X")
                                // Update fullname dan gender menggunakan data userCustomerData
                                syncCustomerRelatedData(customerRef = "customers/$phoneNumber")
                            }
                        }
                    }
                } else {
                    _addCustomerResult.postValue(ResultState.Failure("Gagal mengambil data customer."))
                    // isSaveData = false lewat setter viewModel yang di panggil di fragment
                }

            } catch (e: Exception) {
                _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun setupUserCard(gettingData: Boolean) {
        Log.d("TriggerUU", "X4X")
        withContext(Dispatchers.Main) {
            // setValidInput(binding.wrapperPhone, binding.etPhone)
            if (gettingData) {
                Log.d("TriggerUU", "X4.1X")
                when (userRolesData.role) {
                    "admin" -> {
                        Log.d("TriggerUU", "X4.1.1X")
                        getDataReference(userRolesData.adminRef, "admin")
                    }
                    "employee", "pairAE" -> {
                        Log.d("TriggerUU", "X4.1.2X")
                        getDataReference(userRolesData.employeeRef, "employee")
                    }
                    else -> {
                        Log.d("TriggerUU", "X4.1.3X")
                        getDataReference(userRolesData.customerRef, "customer")
                    }
                }
            } else {
                Log.d("TriggerUU", "X4.2X")
                // sebenarnya di setupUserCard(false) dia juga udah punya akun tapi kan proses pengambilan data sudah dilakuin sama checkCustomerExistenceAndAdd(_)
                setupCustomerData()
            }
        }

    }

    suspend fun resetObtainedData(savetyData: String) {
        withContext(Dispatchers.Main) {
            userAdminData = UserAdminData()
            userEmployeeData = UserEmployeeData()
            userRolesData = UserRolesData()
            userCustomerData = UserCustomerData(
                userReminder = null,
                email = "",
                fullname = userInputName,
                gender = userInputGender.ifEmpty { savetyData },
                membership = false,
                password = "",
                phone = userPhoneNumber,
                photoProfile = "",
                userNotification = null,
                uid = userPhoneNumber,
                username = "",
                userCoins = 0
            )
        }
    }

    // Function getDataReference berdasarkan contoh kode getDataCustomerReference
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getDataReference(reference: String, role: String) {
        Log.d("TriggerUU", "X5X")
        withContext(Dispatchers.IO) {
            try {
                val document = db.document(reference)
                    .get()
                    .awaitGetWithOfflineFallback(tag = "GetDataReference")

                if (document != null && document.exists()) {
                    when (role) {
                        "admin" -> {
                            Log.d("TriggerUU", "X5.1X")
                            document.toObject(UserAdminData::class.java)?.apply {
                                userRef = document.reference.path
                                userAdminData = this
                            }
                        }

                        "employee" -> {
                            Log.d("TriggerUU", "X5.2X")
                            document.toObject(UserEmployeeData::class.java)?.apply {
                                userRef = document.reference.path
                                userEmployeeData = this
                            }
                        }

                        else -> {
                            Log.d("TriggerUU", "X5.3X")
                            document.toObject(UserCustomerData::class.java)?.apply {
                                userRef = document.reference.path
                                userCustomerData = this
                            }
                        }
                    }

                    setupCustomerData()
                } else {
                    _addCustomerResult.postValue(ResultState.Failure("Data tidak ditemukan."))
                    // isSaveData = false lewat setter viewModel yang di panggil di fragment
                }

            } catch (e: Exception) {
                _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }
        }
    }

    private suspend fun setupCustomerData() {
        Log.d("TriggerUU", "X6X")
        withContext(Dispatchers.Main) {
            outletSelected.let { outlet ->
                val customerAlreadyExists1 =
                    outlet.listCustomers?.any { it.uidCustomer == userCustomerData.uid } == true
                val customerAlreadyExists2 = outlet.listCustomers?.any { it.uidCustomer == userPhoneNumber } == true

                if (customerAlreadyExists1 || customerAlreadyExists2) {
                    Log.d("TriggerUU", "X6.1X")
                    _addCustomerResult.postValue(ResultState.DisplayError)
                } else {
                    Log.d("TriggerUU", "X6.2X")
                    //                userInputName = binding.etFullname.text.toString().trim()
                    //                userInputGender = binding.genderDropdown.text.toString().trim()
                    when (userRolesData.role) {
                        "admin" -> {
                            Log.d("TriggerUU", "X6.2.1X")
                            userCustomerData.apply {
                                //uid = userAdminData.uid
                                uid = userPhoneNumber
                                photoProfile = userAdminData.imageCompanyProfile
                                gender = userInputGender
                                fullname = if (userAdminData.ownerName.contains("Owner", ignoreCase = true)) {
                                    userInputName
                                } else { userAdminData.ownerName }
                            }

                            _addCustomerResult.postValue(ResultState.DisplayData("admin"))
                        }

                        "employee", "pairAE" -> {
                            Log.d("TriggerUU", "X6.2.2X")
                            userCustomerData.apply {
                                //uid = userEmployeeData.uid
                                uid = userPhoneNumber
                                photoProfile = userEmployeeData.photoProfile
                                gender = userEmployeeData.gender
                                fullname = userEmployeeData.fullname
                            }

                            _addCustomerResult.postValue(ResultState.DisplayData("employee"))
                        }

                        else -> {
                            Log.d("TriggerUU", "X6.2.3X")
                            // Harusnya ketika terdapat data customer tanpa akun role bernilai string kosong
                            _addCustomerResult.postValue(ResultState.DisplayData("customer"))
                        }
                    }

                    // Status Button
                    Log.d("CheckSavedData", "Nanang Kurniawan")
                    buttonStatus = "Sync"
                    isSaveData = false
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun addNewCustomer() {
        Log.d("TriggerUU", "X7X")
        withContext(Dispatchers.IO) {
            userCustomerData.let { userData ->
                val success = db.collection("customers")
                    .document(userData.uid)
                    .set(userData)
                    .awaitWriteWithOfflineFallback(tag = "AddNewCustomer")

                if (success) {
                    if (userAdminData.uid.isNotEmpty() || userEmployeeData.uid != "----------------") {
                        Log.d("TriggerUU", "X7.1X")
                        syncDataForAdminEmployeeRole()
                    } else {
                        Log.d("TriggerUU", "X7.2X")
                        addCustomerToOutlet()
                    }
                } else {
                    _addCustomerResult.postValue(ResultState.Failure("Gagal menambah customer."))
                    // isSaveData = false lewat setter viewModel yang di panggil di fragment
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun syncDataForAdminEmployeeRole() {
        withContext(Dispatchers.IO) {
            try {
                val updateJobs = mutableListOf<Deferred<Boolean>>()

                when (userRolesData.role) {
                    "admin" -> {
                        if (userAdminData.ownerName != userInputName) {
                            updateJobs += async { updateAdminData(userRolesData.adminRef) }
                        }
                        updateJobs += async { updateRoleInUsersCollection() }
                    }
                    "employee" -> {
                        if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                            updateJobs += async { updateEmployeeData(userRolesData.employeeRef) }
                        }
                        updateJobs += async { updateRoleInUsersCollection() }
                    }
                    "pairAE" -> {
                        if (userAdminData.ownerName != userInputName) {
                            updateJobs += async { updateAdminData(userRolesData.adminRef) }
                        }
                        if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                            updateJobs += async { updateEmployeeData(userRolesData.employeeRef) }
                        }
                        updateJobs += async { updateRoleInUsersCollection() }
                    }
                }

                val results = updateJobs.awaitAll()
                val allSuccess = results.all { !it } // false = success, true = failed

                if (allSuccess) {
                    addCustomerToOutlet()
                } else {
                    _addCustomerResult.postValue(ResultState.RetryProcess("AdminEmployeeRole", ""))
                    isSaveData = false
                }

            } catch (e: Exception) {
                _addCustomerResult.postValue(ResultState.Failure(e.message.toString()))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }
        }
    }

    private suspend fun addCustomerToOutlet() {
        Log.d("TriggerUU", "X9X")
        withContext(Dispatchers.Main) {
            outletSelected.let { outlet ->
                val newCustomer = Customer(
                    lastReserve = Timestamp.now(),
                    uidCustomer = userCustomerData.uid
                )

                newCustomer.let { data ->
                    outlet.listCustomers = outlet.listCustomers?.apply {
                        add(data)
                    } ?: mutableListOf(data)
                    updateOutletListCustomers(outlet)
                }
            }
        }
    }

    private suspend fun updateOutletListCustomers(outlet: Outlet) {
        withContext(Dispatchers.IO) {
            Log.d("TriggerUU", "X10X")
            val outletRef = db.document(outlet.rootRef)
                .collection("outlets")
                .document(outlet.uid)

            onCustomerAddResult?.invoke(true)

            val success = outletRef
                .update("list_customers", outlet.listCustomers)
                .awaitWriteWithOfflineFallback(tag = "UpdateOutletCustomers")

            if (success) {
                val lastCustomer = outlet.listCustomers?.lastOrNull()
                lastCustomer?.let {
                    _addCustomerResult.postValue(ResultState.Success(it))
                } ?: run {
                    onCustomerAddResult?.invoke(false)
                    _addCustomerResult.postValue(ResultState.Failure("Terjadi kesalahan saat menambahkan data."))
                    // isSaveData = false lewat setter viewModel yang di panggil di fragment
                }
            } else {
                onCustomerAddResult?.invoke(false)
                _addCustomerResult.postValue(ResultState.Failure("Gagal memperbarui outlet."))
                // isSaveData = false lewat setter viewModel yang di panggil di fragment
            }
        }
    }

    // New function to update fullname and gender in the customer document
    private suspend fun updateCustomerData(customerRef: String?): Boolean {
        if (customerRef.isNullOrEmpty()) return true
        Log.d("SyncData", "Updating Customer Data")

        val updates = mutableMapOf<String, Any>(
            "fullname" to userInputName,
            "gender" to userInputGender
        )

        val success = db.document(customerRef)
            .update(updates)
            .awaitWriteWithOfflineFallback(tag = "UpdateCustomerData")

        if (success)
            Log.d("SyncData", "✅ Customer data updated (local/server)")
        else
            Log.e("SyncData", "❌ Failed to update customer data")

        return !success // false = success, true = failed
    }

    private suspend fun updateAdminData(adminRef: String?): Boolean {
        if (adminRef.isNullOrEmpty()) return true
        Log.d("SyncData", "Updating Admin Data")

        val updates = mapOf("owner_name" to userInputName)

        val success = db.document(adminRef)
            .update(updates)
            .awaitWriteWithOfflineFallback(tag = "UpdateAdminData")

        if (success)
            Log.d("SyncData", "✅ Admin data updated (local/server)")
        else
            Log.e("SyncData", "❌ Failed to update admin data")

        return !success
    }

    private suspend fun updateEmployeeData(employeeRef: String?): Boolean {
        if (employeeRef.isNullOrEmpty()) return true
        Log.d("SyncData", "Updating Employee Data")

        val updates = mutableMapOf<String, Any>(
            "fullname" to userInputName,
            "gender" to userInputGender
        )

        val success = db.document(employeeRef)
            .update(updates)
            .awaitWriteWithOfflineFallback(tag = "UpdateEmployeeData")

        if (success)
            Log.d("SyncData", "✅ Employee data updated (local/server)")
        else
            Log.e("SyncData", "❌ Failed to update employee data")

        return !success
    }

    private suspend fun updateRoleInUsersCollection(): Boolean {
        Log.d("TriggerUU", "X8X")

        userRolesData.apply {
            role = when (role) {
                "admin" -> "pairAC(-)"
                "employee" -> "pairEC(-)"
                "pairAE" -> "hybrid(-)"
                else -> "customer"
            }
            customerRef = "customers/${userCustomerData.uid}"
            customerProvider = "none"
            uid = userPhoneNumber
        }

        val success = db.collection("users")
            .document(userPhoneNumber)
            .set(userRolesData)
            .awaitWriteWithOfflineFallback(tag = "UpdateUserRoles")

        if (success)
            Log.d("SyncData", "✅ User role updated (local/server)")
        else
            Log.d("SyncData", "❌ Failed to update user role")

        return !success
    }

}