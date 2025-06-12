package com.example.barberlink.UserInterface.SignUp.ViewModel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserCustomerData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.DataClass.UserRolesData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

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
        data object Loading : ResultState()
        data object Success : ResultState()
        data object InvalidState : ResultState()
        data class Failure(val message: String) : ResultState()
    }

    fun setRegisterResult(value: ResultState?) {
        _registerResult.value = value
    }

    fun setUserAdminData(data: UserAdminData) {
        this.userAdminData = data
    }

    fun setUserRolesData(data: UserRolesData) {
        this.userRolesData = data
    }

    fun setUserCustomerData(data: UserCustomerData) {
        this.userCustomerData = data
    }

    fun setUserEmployeeData(data: UserEmployeeData) {
        this.userEmployeeData = data
    }

    fun setFormattedPhoneNumber(phoneNumber: String) {
        this.formattedPhoneNumber = phoneNumber
    }

    fun setPhoneNumberValid(isValid: Boolean) {
        this.isPhoneNumberValid = isValid
    }

    fun getUserAdminData(): UserAdminData {
        return userAdminData
    }

    fun getUserRolesData(): UserRolesData {
        return userRolesData
    }

    fun getUserCustomerData(): UserCustomerData {
        return userCustomerData
    }

    private fun getUserEmployeeData(): UserEmployeeData {
        return userEmployeeData
    }

    fun getFormattedPhoneNumber(): String? {
        return formattedPhoneNumber
    }

    fun isPhoneNumberValid(): Boolean {
        return isPhoneNumberValid
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun checkPhoneNumberAndNavigate() {
        _registerResult.postValue(ResultState.Loading)
        userAdminData = UserAdminData()
        userRolesData = UserRolesData()
        userCustomerData = UserCustomerData()
        userEmployeeData = UserEmployeeData()

        formattedPhoneNumber?.let { phoneNumber ->
            Log.d("TriggerPP", phoneNumber)
            db.collection("users").document(phoneNumber).get()
                .addOnSuccessListener { document ->
                    when {
                        document.exists() -> handleExistingUser(document)
                        else -> {
                            checkCustomerExistenceAndAdd(phoneNumber)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    _registerResult.postValue(ResultState.Failure(exception.message.toString()))
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handleExistingUser(document: DocumentSnapshot) {
        Log.d("TriggerPP", "X1X")
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
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkCustomerExistenceAndAdd(phoneNumber: String) {
        db.collection("customers").document(phoneNumber).get()
            .addOnSuccessListener { customerDocument ->
                if (customerDocument.exists()) {
                    Log.d("TriggerPP", "X4X")
                    userRolesData.role = "undefined"

                    customerDocument.toObject(UserCustomerData::class.java)?.let { customerData ->
                        customerData.userRef = customerDocument.reference.path
                        userCustomerData = customerData
                    }

//                    userAdminData?.apply {
//                        uid = ""
//                        imageCompanyProfile = ""
//                        ownerName = userCustomerData?.fullname.toString()
//                        email = ""
//                        password = ""
//                        userRef = userCustomerData?.userRef.toString()
//                    }

                    setupCustomerData()
                } else {
                    Log.d("TriggerPP", "X5X")
                    isPhoneNumberValid = true
                    _registerResult.postValue(ResultState.Success)
                }
            }
            .addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure(exception.message.toString()))
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getDataReference(reference: String, role: String) {
        Log.d("TriggerUU", "X5X")
        db.document(reference).get()
            .addOnSuccessListener { document ->
                document.takeIf { it.exists() }?.let {
                    when (role) {
                        "employee" -> {
                            Log.d("TriggerUU", "X5.2X")
                            it.toObject(UserEmployeeData::class.java)?.let { data ->
                                data.userRef = document.reference.path
                                userEmployeeData = data
                            }
                        }
                        else -> {
                            Log.d("TriggerUU", "X5.3X")
                            it.toObject(UserCustomerData::class.java)?.let { data ->
                                data.userRef = document.reference.path
                                userCustomerData = data
                            }
                        }
                    }

                    setupCustomerData()
                }
            }
            .addOnFailureListener { exception ->
                _registerResult.postValue(ResultState.Failure(exception.message.toString()))
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