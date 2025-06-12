package com.example.barberlink.UserInterface.Teller.ViewModel

import android.util.Log
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AddCustomerViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

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

    fun setAddCustomerResult(result: ResultState?) {
        _addCustomerResult.value = result
    }

    fun setButtonStatus(status: String) {
        buttonStatus = status
    }

    fun setUserManualInput(isManualInput: Boolean) {
        isUserManualInput = isManualInput
    }

    fun setIsSaveData(isSaveData: Boolean) {
        this.isSaveData = isSaveData
    }

    fun setUserPhoneNumber(phoneNumber: String) {
        userPhoneNumber = phoneNumber
    }

    fun setUserInputName(name: String) {
        userInputName = name
    }

    fun setUserInputGander(gander: String) {
        userInputGender = gander
    }

    fun setUserEmployeeData(userEmployeeData: UserEmployeeData) {
        this.userEmployeeData = userEmployeeData
    }

    fun setUserAdminData(userAdminData: UserAdminData) {
        this.userAdminData = userAdminData
    }

    fun setUserCustomerData(userCustomerData: UserCustomerData) {
        this.userCustomerData = userCustomerData
    }

    fun setOutletSelected(outlet: Outlet) {
        this.outletSelected = outlet
    }

    fun getButtonStatus(): String {
        return buttonStatus
    }

    fun getIsUserManualInput(): Boolean {
        return isUserManualInput
    }

    fun getIsSaveData(): Boolean {
        return isSaveData
    }

    fun getUserPhoneNumber(): String {
        return userPhoneNumber
    }

    fun getUserInputName(): String {
        return userInputName
    }

    fun getUserInputGander(): String {
        return userInputGender
    }

    fun setUserRolesData(userRolesData: UserRolesData) {
        this.userRolesData = userRolesData
    }

    fun getUserEmployeeData(): UserEmployeeData {
        return userEmployeeData
    }

    fun getUserAdminData(): UserAdminData {
        return userAdminData
    }

    fun getUserCustomerData(): UserCustomerData {
        return userCustomerData
    }

    fun getUserRolesData(): UserRolesData {
        return userRolesData
    }

    fun getOutletSelected(): Outlet {
        return outletSelected
    }

    fun checkAndAddCustomer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isSaveData) {
                Log.d("TriggerUU", "====**********====")
                Log.d("TriggerUU", "X0X")
                isSaveData = true
                _addCustomerResult.postValue(ResultState.Loading)
                // if (buttonStatus == "Add") binding.btnSave.isClickable = false
                db.collection("users").document(userPhoneNumber).get()
                    .addOnSuccessListener { document ->
                        when {
                            document.exists() -> handleExistingUser(document)
                            isSaveData -> checkCustomerExistenceAndAdd(userPhoneNumber)
                            else -> _addCustomerResult.postValue(ResultState.ResetingInput)
                        }
                    }
                    .addOnFailureListener { exception ->
                        _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
                    }
            }
        }

    }

    // Function to handle existing user and update their data if manual input is detected
    private fun handleExistingUser(document: DocumentSnapshot) {
        Log.d("TriggerUU", "X1X")
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
    }

    fun syncCustomerRelatedData(customerRef: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val taskFailed = AtomicBoolean(false)
            var updateCustomerJob: Deferred<Unit>? = null
            var updateEmployeeJob: Deferred<Unit>? = null
            var updateAdminJob: Deferred<Unit>? = null
            when (userRolesData.role) {
                "customer" -> {
                    // Hanya update data customer
                    updateCustomerJob = async {
                        val prosesStatusA = updateCustomerData(userRolesData.customerRef)
                        if (prosesStatusA) { taskFailed.set(true) }
                    }
                }
                "pairEC(-)", "pairEC(+)" -> {
                    // Update data customer dan employee
                    var prosesStatusA = false
                    var prosesStatusB = false
                    updateCustomerJob = async {
                        prosesStatusA = updateCustomerData(userRolesData.customerRef)
                    }
                    if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                        updateEmployeeJob = async {
                            prosesStatusB = updateEmployeeData(userRolesData.employeeRef)
                        }
                    }
                    if (prosesStatusA || prosesStatusB) { taskFailed.set(true) }
                }
                "pairAC(-)", "pairAC(+)" -> {
                    // Update data customer dan admin
                    var prosesStatusA = false
                    var prosesStatusB = false
                    updateCustomerJob = async {
                        prosesStatusA = updateCustomerData(userRolesData.customerRef)
                    }
                    if (userAdminData.ownerName != userInputName) {
                        updateAdminJob = async {
                            prosesStatusB = updateAdminData(userRolesData.adminRef)
                        }
                    }
                    if (prosesStatusA || prosesStatusB) { taskFailed.set(true) }
                }
                "hybrid(-)", "hybrid(+)" -> {
                    // Update data customer, employee, dan admin
                    var prosesStatusA = false
                    var prosesStatusB = false
                    var prosesStatusC = false
                    updateCustomerJob = async {
                        prosesStatusA = updateCustomerData(userRolesData.customerRef)
                    }
                    if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                        updateEmployeeJob = async {
                            prosesStatusB = updateEmployeeData(userRolesData.employeeRef)
                        }
                    }
                    if (userAdminData.ownerName != userInputName) {
                        updateAdminJob = async {
                            prosesStatusC = updateAdminData(userRolesData.adminRef)
                        }
                    }
                    if (prosesStatusA || prosesStatusB || prosesStatusC) { taskFailed.set(true) }
                }
                else -> {
                    // Kode yang disediakan untuk private fun checkCustomerExistenceAndAdd(phoneNumber: String)
                    if (customerRef.isNotEmpty()) {
                        updateCustomerJob = async {
                            val prosesStatusA = updateCustomerData(customerRef)
                            if (prosesStatusA) { taskFailed.set(true) }
                        }
                    }
                }
            }

            try {
                updateCustomerJob?.await()
                updateEmployeeJob?.await()
                updateAdminJob?.await()

                if (!taskFailed.get()) {
                    addCustomerToOutlet()
                } else {
                    _addCustomerResult.postValue(ResultState.RetryProcess("CustomerRelatedData", customerRef))
                    isSaveData = false
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
                }
                throw exception
            }
        }
    }

    // New function to update fullname and gender in the customer document
    private suspend fun updateCustomerData(customerRef: String?): Boolean {
        val isFailed = AtomicBoolean(false)
        Log.d("TriggerUU", "X[:]X")
        Log.d("SyncData", "Updating Customer Data")
        if (customerRef.isNullOrEmpty()) {
            Log.d("SyncData", "Customer reference is null or empty")
            return true
        }

        // Create a map with the fields to be updated
        val updates = mutableMapOf<String, Any>()
        updates["fullname"] = userInputName
        updates["gender"] = userInputGender
        Log.d("TriggerUU", "TT $userInputName $userInputGender")

        // Update the customer document in Firestore
        db.document(customerRef)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SyncData", "Customer data updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.d("TriggerUU", "X[N2]X")
                Log.d("SyncData", "Failed to update customer data, $exception")
                isFailed.set(true)
            }.await()

        return isFailed.get()
    }

    private suspend fun updateAdminData(adminRef: String?): Boolean {
        val isFailed = AtomicBoolean(false)
        Log.d("SyncData", "Updating Admin Data")
        if (adminRef.isNullOrEmpty()) {
            Log.e("SyncData", "Admin reference is null or empty")
            return true
        }

        val updates = mutableMapOf<String, Any>("owner_name" to userInputName)
        db.document(adminRef)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SyncData", "Admin data updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e("SyncData", "Failed to update admin data, $exception")
                isFailed.set(true)
            }.await()

        return isFailed.get()
    }

    private suspend fun updateEmployeeData(employeeRef: String?): Boolean {
        val isFailed = AtomicBoolean(false)
        Log.d("SyncData", "Updating Employee Data")
        if (employeeRef.isNullOrEmpty()) {
            Log.e("SyncData", "Employee reference is null or empty")
            return true
        }

        val updates = mutableMapOf<String, Any>()
        updates["fullname"] = userInputName
        updates["gender"] = userInputGender

        db.document(employeeRef)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SyncData", "Employee data updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e("SyncData", "Failed to update employee data, $exception")
                isFailed.set(true)
            }.await()

        return isFailed.get()
    }

    private fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        Log.d("TriggerUU", "X2X")
        db.collection("customers").document(phoneNumber).get()
            .addOnSuccessListener { customerDocument ->
                if (!customerDocument.exists()) {
                    Log.d("TriggerUU", "X2.1X")
                    addNewCustomer()
                } else {
                    Log.d("TriggerUU", "X2.2X")

                    if (buttonStatus == "Add") {
                        customerDocument.toObject(UserCustomerData::class.java)?.apply {
                            userRef = customerDocument.reference.path
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
                            // Update fullname and gender using data from userCustomerData
                            // updateCustomerData("customers/$phoneNumber")
                            syncCustomerRelatedData(customerRef = "customers/$phoneNumber")
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
            }
    }

    private fun setupUserCard(gettingData: Boolean) {
        Log.d("TriggerUU", "X4X")

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

    fun resetObtainedData(savetyData: String) {
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

    // Function getDataReference berdasarkan contoh kode getDataCustomerReference
    private fun getDataReference(reference: String, role: String) {
        Log.d("TriggerUU", "X5X")
        db.document(reference).get()
            .addOnSuccessListener { document ->
                document.takeIf { it.exists() }?.let {
                    when (role) {
                        "admin" -> {
                            Log.d("TriggerUU", "X5.1X")
                            it.toObject(UserAdminData::class.java)?.apply {
                                userRef = it.reference.path
                                userAdminData = this
                            }
                        }
                        "employee" -> {
                            Log.d("TriggerUU", "X5.2X")
                            it.toObject(UserEmployeeData::class.java)?.apply {
                                userRef = it.reference.path
                                userEmployeeData = this
                            }
                        }
                        else -> {
                            Log.d("TriggerUU", "X5.3X")
                            it.toObject(UserCustomerData::class.java)?.apply {
                                userRef = it.reference.path
                                userCustomerData = this
                            }
                        }
                    }

                    setupCustomerData()
                }
            }
            .addOnFailureListener { exception ->
                _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
            }
    }

    private fun setupCustomerData() {
        Log.d("TriggerUU", "X6X")
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

    private fun addNewCustomer() {
        Log.d("TriggerUU", "X7X")
        // binding.progressBar.visibility = View.VISIBLE
        userCustomerData.let { userData ->
            db.collection("customers").document(userData.uid).set(userData)
                .addOnSuccessListener {
                    if (userAdminData.uid.isNotEmpty() || userEmployeeData.uid != "----------------") {
                        Log.d("TriggerUU", "X7.1X")
                        // updateRoleInUsersCollection()
                        syncDataForAdminEmployeeRole()
                    }
                    else {
                        Log.d("TriggerUU", "X7.2X")
                        addCustomerToOutlet()
                    }
                }
                .addOnFailureListener { exception ->
                    _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
                }
        }
    }

    fun syncDataForAdminEmployeeRole() {
        viewModelScope.launch(Dispatchers.IO) {
            val taskFailed = AtomicBoolean(false)
            var updateAdminJob: Deferred<Unit>? = null
            var updateEmployeeJob: Deferred<Unit>? = null
            var updateRoleJob: Deferred<Unit>? = null
            when (userRolesData.role) {
                "admin" -> {
                    var prosesStatusA = false
                    var prosesStatusB = false
                    if (userAdminData.ownerName != userInputName) {
                        updateAdminJob = async {
                            prosesStatusA = updateAdminData(userRolesData.adminRef)
                        }
                    }
                    updateRoleJob = async {
                        prosesStatusB = updateRoleInUsersCollection()
                    }
                    if (prosesStatusA || prosesStatusB) { taskFailed.set(true) }
                }
                "employee" -> {
                    var prosesStatusA = false
                    var prosesStatusB = false
                    if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                        updateEmployeeJob = async {
                            prosesStatusA = updateEmployeeData(userRolesData.employeeRef)
                        }
                    }
                    updateRoleJob = async {
                        prosesStatusB = updateRoleInUsersCollection()
                    }
                    if (prosesStatusA || prosesStatusB) { taskFailed.set(true) }
                }
                "pairAE" -> {
                    var prosesStatusA = false
                    var prosesStatusB = false
                    var prosesStatusC = false
                    if (userAdminData.ownerName != userInputName) {
                        updateAdminJob = async {
                            prosesStatusA = updateAdminData(userRolesData.adminRef)
                        }
                    }
                    if (userEmployeeData.fullname != userInputName || userEmployeeData.gender != userInputGender) {
                        updateEmployeeJob = async {
                            prosesStatusB = updateEmployeeData(userRolesData.employeeRef)
                        }
                    }
                    updateRoleJob = async {
                        prosesStatusC = updateRoleInUsersCollection()
                    }
                    if (prosesStatusA || prosesStatusB || prosesStatusC) { taskFailed.set(true) }
                }
            }

            try {
                updateAdminJob?.await()
                updateEmployeeJob?.await()
                updateRoleJob?.await()

                if (!taskFailed.get()) {
                    addCustomerToOutlet()
                } else {
                    _addCustomerResult.postValue(ResultState.RetryProcess("AdminEmployeeRole", ""))
                    isSaveData = false
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
                }
                throw exception
            }

        }
    }


    private suspend fun updateRoleInUsersCollection(): Boolean {
        val isFailed = AtomicBoolean(false)
        Log.d("TriggerUU", "X8X")
        userRolesData.apply {
            // Menentukan nilai role baru berdasarkan kondisi
            this.role = when (role) {
                "admin" -> "pairAC(-)"
                "employee" -> "pairEC(-)"
                "pairAE" -> "hybrid(-)"
                else -> "customer" // Tidak ada perubahan jika tidak sesuai dengan kondisi di atas
            }

            // Memperbarui customer_ref dan customer_provider
            this.customerRef = "customers/${userCustomerData.uid}"
            this.customerProvider = "none"
            this.uid = userPhoneNumber
        }.let {
            Log.d("SyncData", "Updating user role")
            userRolesData = it

            // Mengirimkan perubahan ke Firestore
            db.collection("users").document(userPhoneNumber).set(it)
                .addOnSuccessListener {
                    Log.d("SyncData", "User role updated successfully")
                }
                .addOnFailureListener { exception ->
                    Log.d("SyncData", "Failed to update user role, $exception")
                    isFailed.set(true)
                    _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
                }.await()
        }

        return isFailed.get()
    }

    private fun addCustomerToOutlet() {
        Log.d("TriggerUU", "X9X")
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

    private fun updateOutletListCustomers(outlet: Outlet) {
        Log.d("TriggerUU", "X10X")
        val outletRef = db.document(outlet.rootRef)
            .collection("outlets")
            .document(outlet.uid)

        outletRef.update("list_customers", outlet.listCustomers)
            .addOnSuccessListener {
                val lastCustomer = outlet.listCustomers?.lastOrNull()
                lastCustomer?.let {
                    onCustomerAddResult?.invoke(true)
                    _addCustomerResult.postValue(ResultState.Success(it))
                } ?: run {
                    onCustomerAddResult?.invoke(false)
                    _addCustomerResult.postValue(ResultState.Failure("Terjadi suatu kesalahan saat menambahkan data."))
                }
            }
            .addOnFailureListener { exception ->
                onCustomerAddResult?.invoke(false)
                _addCustomerResult.postValue(ResultState.Failure(exception.message.toString()))
            }
    }


}