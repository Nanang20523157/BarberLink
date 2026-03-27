package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserRolesData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.barberlink.Utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class UserPhoneStepViewModel(
    private val db: FirebaseFirestore,
) : ViewModel() {

    private lateinit var userAdminData: UserAdminData
    private lateinit var userEmployeeData: UserEmployeeData
    private lateinit var userRolesData: UserRolesData
    private lateinit var userCustomerData: UserCustomerData
    private var formattedPhoneNumber: String? = null
    private var isPhoneNumberValid: Boolean = false

    private var loginType: String = ""

    private val _registerResult = MutableLiveData<ResultState?>()
    val registerResult: LiveData<ResultState?> = _registerResult

    sealed class ResultState {
        data object Loading: ResultState()
        data object Success: ResultState()
        data object InvalidState: ResultState()
        data class Failure(val message: String): ResultState()
    }

    fun setRegisterResult(value: ResultState?) {
        viewModelScope.launch {
            _registerResult.value = value
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            userAdminData = data
        }
    }

    fun setUserRolesData(data: UserRolesData) {
        viewModelScope.launch {
            userRolesData = data
        }
    }

    fun setUserCustomerData(data: UserCustomerData) {
        viewModelScope.launch {
            userCustomerData = data
        }
    }

    fun setUserEmployeeData(data: UserEmployeeData) {
        viewModelScope.launch {
            userEmployeeData = data
        }
    }

    fun setFormattedPhoneNumber(phoneNumber: String) {
        viewModelScope.launch {
            formattedPhoneNumber = phoneNumber
        }
    }

    fun setPhoneNumberValid(isValid: Boolean) {
        viewModelScope.launch {
            isPhoneNumberValid = isValid
        }
    }

    fun getUserAdminData(): UserAdminData {
        return runBlocking {
            userAdminData
        }
    }

    fun getUserRolesData(): UserRolesData {
        return runBlocking {
            userRolesData
        }
    }

    fun getUserCustomerData(): UserCustomerData {
        return runBlocking {
            userCustomerData
        }
    }

    fun getUserEmployeeData(): UserEmployeeData {
        return runBlocking {
            userEmployeeData
        }
    }

    fun getFormattedPhoneNumber(): String? {
        return runBlocking {
            formattedPhoneNumber
        }
    }

    fun isPhoneNumberValid(): Boolean {
        return runBlocking {
            isPhoneNumberValid
        }
    }

    fun setLoginType(type: String) {
        viewModelScope.launch {
            loginType = type
        }
    }

    fun getLoginType(): String {
        return runBlocking {
            loginType
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkPhoneNumberAndNavigate() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)

            userAdminData = UserAdminData()
            userEmployeeData = UserEmployeeData()
            userRolesData = UserRolesData()
            userCustomerData = UserCustomerData()

            val phoneNumber = formattedPhoneNumber
            if (phoneNumber == null) {
                _registerResult.value = ResultState.Failure("Nomor telepon tidak valid")
                return@launch
            }

            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("users")
                        .document(phoneNumber)
                        .awaitGetWithOfflineFallback(tag = "CheckUserPhone")
                }

                if (snapshot.isSuccessful) {
                    val document = snapshot.data
                    if (document != null) {
                        if (document.exists()) {
                            if (loginType == "Login as Admin") handleExistingAdminCase(document)
                            else handleExistingEmployeeCase(document)
                        } else checkCustomerExistenceAndAdd(phoneNumber)
                    } else {
                        if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                        else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                }
            } catch (e: Exception) {
                _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun handleExistingAdminCase(document: DocumentSnapshot) {
        Log.d("TriggerPP", "X1X")
        try {
            document.toObject(UserRolesData::class.java)?.let {
                userRolesData = it
            }

            when (userRolesData.role) {
                "admin", "pairAE", "pairAC(-)", "pairAC(+)", "hybrid(-)", "hybrid(+)" -> {
                    Log.d("TriggerPP", "X1X")
                    isPhoneNumberValid = false
                    _registerResult.postValue(ResultState.InvalidState)
                }
                "employee" -> {
                    Log.d("TriggerPP", "X2X")
                    getDataReferenceAdminCase(userRolesData.employeeRef, "employee")
                }
                "pairEC(-)", "pairEC(+)" -> {
                    Log.d("TriggerPP", "X2X-special")
                    //getDataReferenceAdminCase(userRolesData.employeeRef, "employee")
                    getDataReferenceSpecialCase(
                        userRolesData.employeeRef,
                        userRolesData.customerRef,
                        callerCase = "admin"
                    )
                }
                else -> {
                    Log.d("TriggerPP", "X3X")
                    getDataReferenceAdminCase(userRolesData.customerRef, "customer")
                }
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun handleExistingEmployeeCase(document: DocumentSnapshot) {
        Log.d("TriggerPP", "X1X")
        try {
            document.toObject(UserRolesData::class.java)?.let {
                userRolesData = it
            }

            when (userRolesData.role) {
                "employee", "pairAE", "pairEC(-)", "pairEC(+)", "hybrid(-)", "hybrid(+)" -> {
                    Log.d("TriggerPP", "X1X")
                    isPhoneNumberValid = false
                    _registerResult.postValue(ResultState.InvalidState)
                }
                "customer" -> {
                    Log.d("TriggerPP", "X2X")
                    getDataReferenceEmployeeCase(userRolesData.customerRef, "customer")
                }
                "pairAC(-)", "pairAC(+)" -> {
                    Log.d("TriggerPP", "X2X-special")
                    getDataReferenceSpecialCase(
                        userRolesData.adminRef,
                        userRolesData.customerRef,
                        callerCase = "employee"
                    )
                }
                else -> {
                    Log.d("TriggerPP", "X3X")
                    getDataReferenceEmployeeCase(userRolesData.adminRef, "admin")
                }
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getDataReferenceAdminCase(reference: String, role: String) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.document(reference)
                    .awaitGetWithOfflineFallback(tag = "GetDataReference")
            }

            if (snapshot.isSuccessful) {
                val document = snapshot.data
                if (document != null) {
                    if (document.exists()) {
                        when (role) {
                            "employee" -> {
                                document.toObject(UserEmployeeData::class.java)?.let { data ->
                                    data.userRef = document.reference.path
                                    userEmployeeData = data
                                }
                            }
                            else -> {
                                document.toObject(UserCustomerData::class.java)?.let { data ->
                                    data.userRef = document.reference.path
                                    userCustomerData = data
                                }
                            }
                        }

                        setupDataAdminCase()
                    } else {
                        // disini negatif case karena dalam block ini seharusnya ada data yang relate kok ini gak ketemu
                        _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                }
            } else {
                if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getDataReferenceEmployeeCase(reference: String, role: String) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.document(reference)
                    .awaitGetWithOfflineFallback(tag = "GetDataReference")
            }

            if (snapshot.isSuccessful) {
                val document = snapshot.data
                if (document != null) {
                    if (document.exists()) {
                        when (role) {
                            "customer" -> {
                                document.toObject(UserCustomerData::class.java)?.let { data ->
                                    data.userRef = document.reference.path
                                    userCustomerData = data
                                }
                            }
                            else -> {
                                document.toObject(UserAdminData::class.java)?.let { data ->
                                    data.userRef = document.reference.path
                                    userAdminData = data
                                }
                            }
                        }

                        setupDataEmployeeCase()
                    } else {
                        // disini negatif case karena dalam block ini seharusnya ada data yang relate kok ini gak ketemu
                        _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                }
            } else {
                if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun getDataReferenceSpecialCase(
        reference1: String,
        reference2: String,
        callerCase: String
    ) {
        try {
            val (snapshot1, snapshot2) = withContext(Dispatchers.IO) {
                coroutineScope {
                    val d1 = async { db.document(reference1).awaitGetWithOfflineFallback(tag = "GetDataReferenceSpecialCase1") }
                    val d2 = async { db.document(reference2).awaitGetWithOfflineFallback(tag = "GetDataReferenceSpecialCase2") }
                    Pair(d1.await(), d2.await())
                }
            }

            if (snapshot1.isSuccessful && snapshot2.isSuccessful) {
                val doc1 = snapshot1.data
                val doc2 = snapshot2.data
                if (doc1 != null && doc2 != null && doc1.exists() && doc2.exists()) {
                    // best-effort: try to map generically
                    // safer mapping: map each doc to a single best-fit model and avoid overwriting previous mapping
                    var tempCustomer: UserCustomerData? = null
                    var tempEmployee: UserEmployeeData? = null
                    var tempAdmin: UserAdminData? = null

                    listOf(doc1, doc2).forEach { doc ->
                        val path = doc.reference.path.lowercase()

                        when {
                            path.contains("barbershops") -> {
                                doc.toObject(UserAdminData::class.java)?.let { data ->
                                    if (tempAdmin == null) {
                                        data.userRef = doc.reference.path
                                        tempAdmin = data
                                    }
                                }
                            }
                            path.contains("customers") -> {
                                doc.toObject(UserCustomerData::class.java)?.let { data ->
                                    if (tempCustomer == null) {
                                        data.userRef = doc.reference.path
                                        tempCustomer = data
                                    }
                                }
                            }
                            path.contains("employees") -> {
                                doc.toObject(UserEmployeeData::class.java)?.let { data ->
                                    if (tempEmployee == null) {
                                        data.userRef = doc.reference.path
                                        tempEmployee = data
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    // commit only the mappings that we actually found (prevents doc2 from blindly replacing doc1)
                    tempAdmin?.let { userAdminData = it }
                    tempEmployee?.let { userEmployeeData = it }
                    tempCustomer?.let { userCustomerData = it }

                    if (tempAdmin == null && tempEmployee == null && tempCustomer == null) {
                        _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                    } else {
                        when (callerCase) {
                            "employee" -> setupDataEmployeeCase()
                            "admin" -> setupDataAdminCase()
                            else -> _registerResult.postValue(ResultState.Failure("Belum diatur untuk kasus ini!"))
                        }
                    }
                } else {
                    if (snapshot1.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot1.errorMessage.toString()))
                    else if (snapshot2.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot2.errorMessage.toString()))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                }
            } else {
                if (snapshot1.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot1.errorMessage.toString()))
                else if (snapshot2.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot2.errorMessage.toString()))
                else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                db.collection("customers")
                    .document(phoneNumber)
                    .awaitGetWithOfflineFallback(tag = "CheckCustomerExistence")
            }

            if (snapshot.isSuccessful) {
                val document = snapshot.data
                if (document != null) {
                    if (document.exists())  {
                        userRolesData.role = "undefined"
                        document.toObject(UserCustomerData::class.java)?.let {
                            it.userRef = document.reference.path
                            userCustomerData = it
                        }

                        if (loginType == "Login as Admin") setupDataAdminCase()
                        else setupDataEmployeeCase()
                    } else {
                        // disini positif case karena dalam block ini memang tidak seharusnya ada data yang relate
                        isPhoneNumberValid = true
                        _registerResult.postValue(ResultState.Success)
                    }
                } else {
                    if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                    else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
                }
            } else {
                if (snapshot.displayMessage) _registerResult.postValue(ResultState.Failure(snapshot.errorMessage.toString()))
                else _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
            }
        } catch (e: Exception) {
            _registerResult.postValue(ResultState.Failure("Terjadi kesalahan saat memeriksa data pengguna!"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupDataAdminCase() {
        when (userRolesData.role) {
            "employee", "pairEC(-)", "pairEC(+)" -> {
                userAdminData.apply {
                    uid = when {
                        userEmployeeData.uid != "----------------" -> userEmployeeData.uid
                        userCustomerData.uid.startsWith("+62") -> ""
                        else -> userCustomerData.uid
                    }
                    imageCompanyProfile = userEmployeeData.photoProfile.ifEmpty { userCustomerData.photoProfile }
                    ownerName = userEmployeeData.fullname.ifEmpty { userCustomerData.fullname }
                    email = userEmployeeData.email.ifEmpty { userCustomerData.email }
                    password = userEmployeeData.password.ifEmpty { userCustomerData.password }
                    // username = userEmployeeData.username
                    userRef = userEmployeeData.userRef.ifEmpty { userCustomerData.userRef }
                }
            }
            else -> {
                userAdminData.apply {
                    uid = userCustomerData.uid
                    imageCompanyProfile = userCustomerData.photoProfile
                    ownerName = userCustomerData.fullname
                    email = userCustomerData.email
                    password = userCustomerData.password
                    // username = userCustomerData.username
                    userRef = userCustomerData.userRef
                }
            }
        }

        isPhoneNumberValid = true
        _registerResult.postValue(ResultState.Success)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupDataEmployeeCase() {
        when (userRolesData.role) {
            "undefined", "customer", "pairAC(-)", "pairAC(+)" -> {
                userEmployeeData.apply {
                    uid = when {
                        userCustomerData.uid.isEmpty() -> userAdminData.uid
                        userCustomerData.uid.startsWith("+62") -> userAdminData.uid
                        else -> userCustomerData.uid
                    }
                    photoProfile = userCustomerData.photoProfile.ifEmpty { userAdminData.imageCompanyProfile }
                    fullname = userCustomerData.fullname.ifEmpty { if (userRolesData.role == "pairAC(-)" || userRolesData.role == "pairAC(+)") userAdminData.ownerName else "" }
                    email = userCustomerData.email.ifEmpty { userAdminData.email }
                    gender = userCustomerData.gender
                    password = userCustomerData.password.ifEmpty { userAdminData.password }
                    // username = userCustomerData.username
                    userRef = userCustomerData.userRef.ifEmpty { userAdminData.userRef }
                }
            }
            else -> {
                userEmployeeData.apply {
                    uid = userAdminData.uid
                    photoProfile = userAdminData.imageCompanyProfile
                    fullname = userAdminData.ownerName
                    email = userAdminData.email
                    // gender = userAdminData.gender
                    password = userAdminData.password
                    // username = userAdminData.username
                    userRef = userAdminData.userRef
                }
            }
        }

        isPhoneNumberValid = true
        _registerResult.postValue(ResultState.Success)
    }

}
