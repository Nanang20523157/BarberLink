package com.example.barberlink.UserInterface.SignIn.ViewModel

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.barberlink.DataClass.UserAdminData
import com.example.barberlink.DataClass.UserEmployeeData
import com.example.barberlink.Network.NetworkMonitor
import com.example.barberlink.R
import com.example.barberlink.UserInterface.Capster.HomePageCapster
import com.example.barberlink.UserInterface.Capster.ViewModel.FormInputBonViewModel
import com.example.barberlink.UserInterface.MainActivity
import com.example.barberlink.UserInterface.SignUp.ViewModel.StepThreeViewModel.ResultState
import com.example.barberlink.Utils.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.yourapp.utils.awaitGetWithOfflineFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.collections.firstOrNull

class LoginPageViewModel(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : ViewModel() {

    sealed class ResultState {
        data object Loading: ResultState()
        data class Success(val type: String, val uid: String): ResultState()
        data class Navigate(val type: String): ResultState()
        data class Failure(val authorize: Boolean, val type: String, val errorMessage: String): ResultState()
        data class ShowToast(val message: String, val hideLoading: Boolean): ResultState()
    }

    private val _loginStateResult = MutableLiveData<ResultState?>()
    val loginStateResult: LiveData<ResultState?> = _loginStateResult

    private lateinit var userAdminData: UserAdminData
    private lateinit var userEmployeeData: UserEmployeeData

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

    fun setLoginStateResult(value: ResultState?) {
        viewModelScope.launch {
            _loginStateResult.value = value
        }
    }

    fun getAdminData(): UserAdminData {
        return runBlocking {
            userAdminData
        }
    }

    fun getEmployeeData(): UserEmployeeData {
        return runBlocking {
            userEmployeeData
        }
    }

    fun setUserAdminData(data: UserAdminData) {
        viewModelScope.launch {
            userAdminData = data
        }
    }

    fun setUserEmployeeData(data: UserEmployeeData) {
        viewModelScope.launch {
            userEmployeeData = data
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun performLogin(email: String, password: String, activity: Activity) {
        viewModelScope.launch {
            _loginStateResult.value = ResultState.Loading
            // PENGECHECKAN MANUAL KARENA GAK OFFLINEAWARE
            if (!NetworkMonitor.isOnline.value) {
                val errMessage = NetworkMonitor.errorMessage.value
                NetworkMonitor.showToast(errMessage, true)
                _loginStateResult.value = ResultState.ShowToast("", true)
//            Toast.makeText(
//                this,
//                "Koneksi internet tidak tersedia. Periksa koneksi Anda.",
//                Toast.LENGTH_SHORT
//            ).show()
                return@launch
            }

            // Lanjutkan login jika ada internet
            try {
                Logger.d("LoginCheck", "email: $email || password: $password")
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(activity) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            when (loginType) {
                                "Login as Employee" -> {
                                    user?.let {
                                        _loginStateResult.value = ResultState.Success(loginType, it.uid)
                                    } ?: run {
                                        auth.signOut()
                                        _loginStateResult.value = ResultState.Failure(true, loginType, "ERROR_USER_NOT_FOUND")
                                    }
                                }
                                "Login as Admin" -> {
                                    user?.let {
                                        _loginStateResult.value = ResultState.Success(loginType, it.uid)
                                    } ?: run {
                                        auth.signOut()
                                        _loginStateResult.value = ResultState.Failure(true, loginType, "ERROR_USER_NOT_FOUND")
                                    }
                                }
                                else -> {
                                    auth.signOut()
                                    _loginStateResult.value = ResultState.Failure(true, loginType, "LOGIN_TYPE_NOT_DEFINED")
                                }
                            }
                        } else {
                            val errorMessage = (task.exception as? FirebaseAuthException)?.errorCode ?: (task.exception as? FirebaseAuthException)?.message ?: task.exception?.message ?: "!!!"
                            _loginStateResult.value = ResultState.Failure(true, loginType, errorMessage)
                        }
                    }
            } catch (e: Exception) {
                val errorMessage = (e as? FirebaseAuthException)?.errorCode ?: (e as? FirebaseAuthException)?.message ?: e.message ?: "!!!"
                _loginStateResult.value = ResultState.Failure(true, loginType, errorMessage)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun fetchUserEmployeeData(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collectionGroup("employees")
                        .whereEqualTo("uid", userId)
                        .awaitGetWithOfflineFallback(tag = "FetchUserEmployeeData")
                }

                if (snapshot.isSuccessful) {
                    val documents = snapshot.data
                    if (documents != null && !documents.isEmpty) {
                        val document = documents.firstOrNull()

                        if (document != null) {
                            userEmployeeData =
                                document.toObject(UserEmployeeData::class.java).apply {
                                    userRef = document.reference.path
                                    outletRef = ""
                                }

                            if (userEmployeeData.uid != "----------------") {
                                _loginStateResult.value = ResultState.Navigate(loginType)
                            } else {
                                auth.signOut()
                                _loginStateResult.value = ResultState.Failure(false, loginType, "ERROR_USER_NOT_FOUND")
                            }
                        } else {
                            auth.signOut()
                            _loginStateResult.value = ResultState.Failure(false, loginType, "ERROR_USER_NOT_FOUND")
                        }
                    } else {
                        auth.signOut()
                        _loginStateResult.value = ResultState.Failure(false, loginType, "ERROR_USER_NOT_FOUND")
                    }
                } else {
                    auth.signOut()
                    if (snapshot.displayMessage) {
                        if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                            NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                            _loginStateResult.value = ResultState.ShowToast("", true)
                        } else _loginStateResult.value = ResultState.ShowToast(snapshot.errorMessage.toString(), true)
                    } else _loginStateResult.value = ResultState.ShowToast("Gagal memuat data pengguna!", true)
                }
            } catch (e: Exception) {
                auth.signOut()
                _loginStateResult.value = ResultState.ShowToast("Gagal memuat data pengguna!", true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun fetchUserAdminData(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    db.collection("barbershops")
                        .document(userId)
                        .awaitGetWithOfflineFallback(tag = "FetchUserAdminData")
                }

                if (snapshot.isSuccessful) {
                    val document = snapshot.data
                    if (document != null && document.exists()) {
                        userAdminData =
                            document.toObject(UserAdminData::class.java)?.apply {
                                userRef = document.reference.path
                            } ?: UserAdminData()

                        if (userAdminData.uid.isNotEmpty()) {
                            _loginStateResult.value = ResultState.Navigate(loginType)
                        } else {
                            auth.signOut()
                            _loginStateResult.value = ResultState.Failure(false, loginType, "ERROR_USER_NOT_FOUND")
                        }
                    } else {
                        auth.signOut()
                        _loginStateResult.value = ResultState.Failure(false, loginType, "ERROR_USER_NOT_FOUND")
                    }
                } else {
                    auth.signOut()
                    if (snapshot.displayMessage) {
                        if (snapshot.errorMessage.toString() == NetworkMonitor.errorMessage.value || snapshot.errorMessage.toString() == "Koneksi internet tidak tersedia. Periksa koneksi Anda.") {
                            NetworkMonitor.showToast(snapshot.errorMessage.toString(), true)
                            _loginStateResult.value = ResultState.ShowToast("", true)
                        } else _loginStateResult.value = ResultState.ShowToast(snapshot.errorMessage.toString(), true)
                    } else _loginStateResult.value = ResultState.ShowToast("Gagal memuat data pengguna!", true)
                }
            } catch (e: Exception) {
                auth.signOut()
                _loginStateResult.value = ResultState.ShowToast("Gagal memuat data pengguna!", true)
            }
        }
    }

}