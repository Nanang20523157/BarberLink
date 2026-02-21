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
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class StepOneViewModel(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val context: Context
) : ViewModel() {

    private lateinit var userAdminData: UserAdminData
    private lateinit var userRolesData: UserRolesData
    private lateinit var userCustomerData: UserCustomerData
    private lateinit var userEmployeeData: UserEmployeeData
    private var formattedPhoneNumber: String? = null
    private var isPhoneNumberValid: Boolean = false

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

    private fun getUserEmployeeData(): UserEmployeeData {
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

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkPhoneNumberAndNavigate() {
        viewModelScope.launch {
            _registerResult.postValue(ResultState.Loading)

            userAdminData = UserAdminData()
            userRolesData = UserRolesData()
            userCustomerData = UserCustomerData()
            userEmployeeData = UserEmployeeData()

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
                        if (document.exists()) handleExistingUser(document)
                        else checkCustomerExistenceAndAdd(phoneNumber)
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
    private suspend fun handleExistingUser(document: DocumentSnapshot) {
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
                "employee", "pairEC(-)", "pairEC(+)" -> {
                    Log.d("TriggerPP", "X2X")
                    getDataReference(userRolesData.employeeRef, "employee")
                }
                else -> {
                    Log.d("TriggerPP", "X3X")
                    getDataReference(userRolesData.customerRef, "customer")
                }
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

                        setupCustomerData()
                    } else {
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
    private suspend fun getDataReference(reference: String, role: String) {
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

                        setupCustomerData()
                    } else {
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
    private fun setupCustomerData() {
        when (userRolesData.role) {
            "employee", "pairEC(-)", "pairEC(+)" -> {
                userAdminData.apply {
                    uid = userEmployeeData.uid
                    imageCompanyProfile = userEmployeeData.photoProfile
                    ownerName = userEmployeeData.fullname
                    email = userEmployeeData.email
                    password = userEmployeeData.password
                    userRef = userEmployeeData.userRef
                }
            }
            else -> {
                userAdminData.apply {
                    uid = userCustomerData.uid
                    imageCompanyProfile = userCustomerData.photoProfile
                    ownerName = userCustomerData.fullname
                    email = userCustomerData.email
                    password = userCustomerData.password
                    userRef = userCustomerData.userRef
                }
            }
        }

        isPhoneNumberValid = true
        _registerResult.postValue(ResultState.Success)
    }


}